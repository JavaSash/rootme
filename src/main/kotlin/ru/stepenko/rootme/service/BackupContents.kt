package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.ApiFailure
import java.util.UUID
import java.nio.charset.StandardCharsets.UTF_8

internal enum class BackupTable(val sqlName: String, val personReferences: List<String> = emptyList()) {
    PERSONS("persons"),
    PARENTAGE("parent_child_relationships", listOf("parent_id", "child_id")),
    PARTNERSHIPS("partnerships", listOf("person1_id", "person2_id")),
    NAMES("person_alternative_names", listOf("person_id")),
    PHOTOS("person_photos", listOf("person_id")),
}

internal data class BackupPhoto(
    val id: UUID,
    val key: String,
    val format: PhotoFormat,
    val size: Long,
    val sha256: String,
    val dimensions: ImageDimensions,
) {
    val archivePath get() = "photos/$key"
}

internal class BackupContents private constructor(
    val data: JsonNode,
    val photos: List<BackupPhoto>,
    val counts: Map<String, Int>,
) {
    companion object {
        internal const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
        internal const val MAX_PHOTOS = 50_000
        internal val sha256Pattern = Regex("[a-f0-9]{64}")

        fun validate(data: JsonNode): BackupContents {
            val tableNames = BackupTable.values().map { it.sqlName }.toSet()
            if (!data.isObject || data.fieldNames().asSequence().toSet() != tableNames) invalidBackup()
            val people = mutableSetOf<UUID>()
            val photos = mutableListOf<BackupPhoto>()
            val primaryOwners = mutableSetOf<UUID>()
            val counts = linkedMapOf<String, Int>()

            for (table in BackupTable.values()) {
                val rows = data.path(table.sqlName)
                if (!rows.isArray || (table == BackupTable.PHOTOS && rows.size() > MAX_PHOTOS)) invalidBackup()
                counts[table.sqlName] = rows.size()
                val ids = if (table == BackupTable.PERSONS) people else mutableSetOf()
                for (row in rows) {
                    val id = uuid(row.path("id"))
                    if (!ids.add(id)) invalidBackup()
                    val refs = table.personReferences.map { uuid(row.path(it)) }
                    if (!people.containsAll(refs) || refs.size == 2 && refs[0] == refs[1]) invalidBackup()
                    if (table != BackupTable.PHOTOS) continue
                    val format = PhotoFormat.values().find { it.contentType == row.path("content_type").asText() }
                        ?: invalidBackup()
                    val key = "$id/original.${format.extension}"
                    val checksum = row.path("sha256").asText()
                    val bytes = row.path("size_bytes")
                    val primary = row.path("is_primary")
                    val width = row.path("width")
                    val height = row.path("height")
                    if (row.path("storage_key").asText() != key ||
                        row.path("thumbnail_storage_key").asText() != "$id/thumbnail.${format.extension}" ||
                        !sha256Pattern.matches(checksum) || !bytes.isIntegralNumber || !bytes.canConvertToLong() ||
                        bytes.longValue() !in 1..MAX_PHOTO_BYTES || !primary.isBoolean ||
                        !width.isInt || !height.isInt || width.intValue() <= 0 || height.intValue() <= 0 ||
                        width.longValue() * height.longValue() > 40_000_000 ||
                        (primary.booleanValue() && !primaryOwners.add(refs.single()))
                    ) invalidBackup()
                    photos.add(BackupPhoto(id, key, format, bytes.longValue(), checksum, ImageDimensions(width.intValue(), height.intValue())))
                }
            }
            return BackupContents(data, photos, counts)
        }

        private fun uuid(node: JsonNode): UUID {
            if (!node.isTextual) invalidBackup()
            val text = node.textValue()
            return runCatching { UUID.fromString(text) }.getOrNull()?.takeIf { it.toString() == text }
                ?: invalidBackup()
        }
    }
}

internal fun invalidBackup(detail: String = "The backup contents or checksums are invalid."): Nothing =
    throw ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BACKUP", detail)

@Component
class BackupSnapshot(private val jdbc: JdbcTemplate, private val json: ObjectMapper) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun read(): JsonNode = json.createObjectNode().apply {
        var bytes = 0L
        for (table in BackupTable.values()) {
            val rows = putArray(table.sqlName)
            jdbc.query("SELECT to_jsonb(record_data)::text FROM ${table.sqlName} record_data ORDER BY id", RowCallbackHandler { result ->
                val row = result.getString(1)
                bytes += row.toByteArray(UTF_8).size
                if (bytes > 64L * 1024 * 1024) {
                    throw ApiFailure(HttpStatus.PAYLOAD_TOO_LARGE, "BACKUP_TOO_LARGE", "Backup metadata exceeds a supported size limit.")
                }
                rows.add(json.readTree(row))
            })
        }
    }
}
