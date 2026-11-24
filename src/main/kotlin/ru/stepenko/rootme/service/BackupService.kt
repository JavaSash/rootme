package ru.stepenko.rootme.service

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import ru.stepenko.rootme.exception.ApiFailure
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupEntry(val size: Long, val sha256: String)
data class BackupManifest(val format: String, val version: Int, val createdAt: Instant, val entries: Map<String, BackupEntry>)
data class BackupVerification(
    val version: Int,
    val createdAt: Instant,
    val counts: Map<String, Int>,
    val size: Long,
    val archiveSha256: String,
)
data class CreatedBackup(val id: UUID, val downloadUrl: String, val verification: BackupVerification)

@Service
class BackupService(
    private val snapshot: BackupSnapshot,
    private val photoStorage: PhotoStorage,
    private val restore: BackupRestore,
    private val json: ObjectMapper,
    @Value("\${rootme.storage.backups-directory:backups}") directory: String,
) {
    private val location = Path.of(directory).toAbsolutePath().normalize()
    private val reader = json.readerFor(JsonNode::class.java)
        .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    fun create(): CreatedBackup = storageOperation {
        val id = UUID.randomUUID()
        val root = Files.createDirectories(location).toRealPath()
        val target = root.resolve("$id.zip")
        temporary(root) { path ->
            val expected = requireNotNull(photoStorage.exclusive(create = true) {
                writeArchive(path)
            })
            val descriptor = Files.newInputStream(path).buffered().use { copy(it, null, MAX_ARCHIVE_BYTES) }
            val (verification) = readArchive(path, descriptor)
            check(verification.createdAt == expected)
            FileChannel.open(path, WRITE).use { it.force(true) }
            Files.move(path, target, ATOMIC_MOVE)
            CreatedBackup(id, "/api/v1/backups/$id", verification)
        }
    }

    fun download(id: UUID): FileSystemResource = storageOperation {
        val file = location.resolve("$id.zip")
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) {
            throw ApiFailure(HttpStatus.NOT_FOUND, "BACKUP_NOT_FOUND", "The backup was not found.")
        }
        FileSystemResource(file)
    }

    // The request is streamed to a bounded temporary file to validate the ZIP central directory.
    fun verify(input: InputStream): BackupVerification = withIncomingArchive(input) { path, descriptor ->
        readArchive(path, descriptor).first
    }

    fun restore(input: InputStream, expectedSha256: String): BackupVerification = withIncomingArchive(input) { path, descriptor ->
        if (descriptor.sha256 != expectedSha256) {
            throw ApiFailure(HttpStatus.CONFLICT, "BACKUP_PREVIEW_CHANGED", "Verify this archive again before restoring it.")
        }
        val (verification, contents) = readArchive(path, descriptor)
        restore.import(contents, path)
        verification
    }

    private fun <T> withIncomingArchive(input: InputStream, action: (Path, BackupEntry) -> T): T = storageOperation {
        val root = Files.createDirectories(location).toRealPath()
        temporary(root) { path ->
            val descriptor = Files.newOutputStream(path).buffered().use { output -> copy(input, output, MAX_ARCHIVE_BYTES) }
            action(path, descriptor)
        }
    }

    private fun writeArchive(path: Path): Instant {
        val contents = BackupContents.validate(snapshot.read())
        val data = json.writeValueAsBytes(contents.data)
        if (data.size > MAX_DATA_BYTES || contents.photos.sumOf { it.size } + data.size > MAX_ARCHIVE_BYTES) tooLarge()
        val entries = linkedMapOf<String, BackupEntry>()
        val createdAt = Instant.now()
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(path))).use { zip ->
            entries[DATA] = writeEntry(zip, DATA, data.inputStream(), MAX_DATA_BYTES)
            for (photo in contents.photos) {
                val original = try {
                    photoStorage.resource(photo.id, photo.key, photo.format, false)
                } catch (_: ApiFailure) {
                    throw ApiFailure(HttpStatus.CONFLICT, "BACKUP_PHOTO_MISSING", "An original photograph is missing.")
                }
                val written = original.inputStream.use {
                    writeEntry(zip, photo.archivePath, it, BackupContents.MAX_PHOTO_BYTES)
                }
                if (written != BackupEntry(photo.size, photo.sha256)) {
                    throw ApiFailure(HttpStatus.CONFLICT, "BACKUP_PHOTO_MISMATCH", "An original photograph differs from its recorded checksum.")
                }
                entries[photo.archivePath] = written
            }
            val manifest = json.writeValueAsBytes(BackupManifest(FORMAT, VERSION, createdAt, entries))
            writeEntry(zip, MANIFEST, manifest.inputStream(), MAX_MANIFEST_BYTES)
        }
        return createdAt
    }

    private fun readArchive(path: Path, archive: BackupEntry): Pair<BackupVerification, BackupContents> {
        try {
            ZipFile(path.toFile()).use { zip ->
                if (zip.size() !in 2..(BackupContents.MAX_PHOTOS + 2)) invalidBackup()
                val names = zip.entries().asSequence().map { it.name }.toSet()
                if (names.size != zip.size()) invalidBackup()
                val manifest = readManifest(zip)
                if (manifest.format != FORMAT || manifest.version != VERSION) {
                    invalidBackup("The backup format or version is unsupported.")
                }
                if (names != manifest.entries.keys + MANIFEST) invalidBackup()
                for ((name, entry) in manifest.entries) {
                    val limit = if (name == DATA) MAX_DATA_BYTES else BackupContents.MAX_PHOTO_BYTES
                    if (entry.size !in 1..limit || !BackupContents.sha256Pattern.matches(entry.sha256)) invalidBackup()
                }
                if (manifest.entries.values.sumOf { it.size } > MAX_ARCHIVE_BYTES) tooLarge()

                val descriptor = manifest.entries[DATA] ?: invalidBackup()
                val bytes = readJsonBytes(zip, DATA, MAX_DATA_BYTES)
                if (copy(bytes.inputStream(), null, MAX_DATA_BYTES) != descriptor) invalidBackup()
                val contents = BackupContents.validate(readJson(bytes))
                if (manifest.entries.keys != contents.photos.map { it.archivePath }.toSet() + DATA) invalidBackup()
                for (photo in contents.photos) {
                    val declared = manifest.entries[photo.archivePath]
                    if (declared != BackupEntry(photo.size, photo.sha256)) invalidBackup()
                    val entry = zip.getEntry(photo.archivePath)
                    if (entry.size != photo.size) invalidBackup()
                    val checked = zip.getInputStream(entry).use {
                        copy(it, null, BackupContents.MAX_PHOTO_BYTES)
                    }
                    if (checked != declared) invalidBackup()
                }
                return BackupVerification(VERSION, manifest.createdAt, contents.counts, archive.size, archive.sha256) to contents
            }
        } catch (_: JsonProcessingException) {
            invalidBackup()
        } catch (_: IOException) {
            invalidBackup("The ZIP archive is unreadable or incomplete.")
        }
    }

    private fun readManifest(zip: ZipFile): BackupManifest {
        val node = readJson(readJsonBytes(zip, MANIFEST, MAX_MANIFEST_BYTES))
        if (!node.path("format").isTextual || !node.path("version").isInt ||
            !node.path("createdAt").isTextual || !node.path("entries").isObject
        ) invalidBackup()
        node.path("entries").forEach { entry ->
            if (!entry.path("size").isIntegralNumber || !entry.path("size").canConvertToLong() ||
                !entry.path("sha256").isTextual
            ) invalidBackup()
        }
        return json.treeToValue(node, BackupManifest::class.java)
    }

    private fun readJsonBytes(zip: ZipFile, name: String, limit: Long): ByteArray {
        val entry = zip.getEntry(name) ?: invalidBackup()
        if (entry.size !in 1..limit) invalidBackup()
        return zip.getInputStream(entry).use {
            val bytes = it.readNBytes(entry.size.toInt() + 1)
            if (bytes.size.toLong() != entry.size) invalidBackup()
            bytes
        }
    }

    private fun readJson(bytes: ByteArray): JsonNode {
        json.factory.createParser(bytes).use { parser ->
            var depth = 0
            while (true) when (parser.nextToken() ?: break) {
                JsonToken.START_ARRAY, JsonToken.START_OBJECT -> if (++depth > 16) invalidBackup()
                JsonToken.END_ARRAY, JsonToken.END_OBJECT -> depth--
                else -> Unit
            }
        }
        return reader.readValue(bytes)
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, input: InputStream, limit: Long): BackupEntry {
        zip.putNextEntry(ZipEntry(name))
        val result = copy(input, zip, limit)
        zip.closeEntry()
        return result
    }

    private fun copy(input: InputStream, output: OutputStream?, limit: Long): BackupEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            if (count > limit) tooLarge()
            digest.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        return BackupEntry(count, HexFormat.of().formatHex(digest.digest()))
    }

    private fun <T> temporary(root: Path, work: (Path) -> T): T {
        val path = Files.createTempFile(root, ".rootme-backup-", ".part")
        try {
            return work(path)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun <T> storageOperation(work: () -> T): T = try {
        work()
    } catch (_: IOException) {
        throw ApiFailure(HttpStatus.INTERNAL_SERVER_ERROR, "BACKUP_STORAGE_ERROR", "Backup storage is unavailable.")
    }

    private fun tooLarge(): Nothing =
        throw ApiFailure(HttpStatus.PAYLOAD_TOO_LARGE, "BACKUP_TOO_LARGE", "The backup exceeds a supported size limit.")

    private companion object {
        const val FORMAT = "rootme-backup"
        const val VERSION = 1
        const val DATA = "data.json"
        const val MANIFEST = "manifest.json"
        const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_DATA_BYTES = 64L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
    }
}
