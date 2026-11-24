package ru.stepenko.rootme.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import ru.stepenko.rootme.exception.ApiFailure
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipFile

@Component
class BackupRestore(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    private val storage: PhotoStorage,
) {
    private val transaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    internal fun import(contents: BackupContents, archive: Path) {
        requireAcyclicAncestry(contents)
        storage.exclusive(create = true) { root ->
            val published = mutableListOf<UUID>()
            try {
                transaction.executeWithoutResult {
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                                published.forEach { id -> storage.retryable(id) { storage.remove(root, id) } }
                            }
                        }
                    })
                    insertIntoEmptyDatabase(contents)
                    publishPhotos(root, archive, contents.photos, published)
                }
            } catch (_: DataIntegrityViolationException) {
                invalidBackup("The archive contains records that do not match the current database schema.")
            }
            // Markers left after an uncertain commit are resolved by PhotoCleanup.
            published.forEach { id -> storage.retryable(id) { storage.finish(root, id) } }
        }
    }

    private fun insertIntoEmptyDatabase(contents: BackupContents) {
        // Block all writers until the empty check, inserts and file publication have committed.
        val tables = BackupTable.values().joinToString(", ") { it.sqlName }
        jdbc.execute("LOCK TABLE $tables IN SHARE ROW EXCLUSIVE MODE")
        if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM persons)", Boolean::class.java) == true) {
            throw ApiFailure(HttpStatus.CONFLICT, "BACKUP_TARGET_NOT_EMPTY", "Restore requires an empty RootMe database.")
        }
        for (table in BackupTable.values()) {
            val name = table.sqlName
            jdbc.update(
                "INSERT INTO $name SELECT * FROM jsonb_populate_recordset(NULL::$name, CAST(? AS jsonb))",
                contents.data.path(name).toString(),
            )
        }
        if (jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM photo_file_cleanup c JOIN person_photos p ON p.id = c.photo_id)",
            Boolean::class.java,
        ) == true) {
            photoConflict()
        }
    }

    private fun publishPhotos(root: Path, archive: Path, photos: List<BackupPhoto>, published: MutableList<UUID>) {
        for (photo in photos) {
            if (!Files.notExists(root.resolve(photo.id.toString()), NOFOLLOW_LINKS)) photoConflict()
        }
        ZipFile(archive.toFile()).use { zip ->
            for (photo in photos) {
                val stored = zip.getInputStream(zip.getEntry(photo.archivePath)).use { input ->
                    storage.publish(root, photo.id, input)
                }
                published.add(photo.id)
                if (stored.format != photo.format || stored.dimensions != photo.dimensions ||
                    stored.size != photo.size || stored.sha256 != photo.sha256
                ) invalidBackup("A photograph does not match its metadata.")
            }
        }
    }

    private fun requireAcyclicAncestry(contents: BackupContents) {
        val children = mutableMapOf<String, MutableList<String>>()
        val incoming = contents.data.path(BackupTable.PERSONS.sqlName)
            .associate { it.path("id").asText() to 0 }.toMutableMap()
        for (edge in contents.data.path(BackupTable.PARENTAGE.sqlName)) {
            if (edge.path("type").asText() == "STEP") continue
            val parent = edge.path("parent_id").asText()
            val child = edge.path("child_id").asText()
            children.getOrPut(parent) { mutableListOf() }.add(child)
            incoming[child] = incoming.getValue(child) + 1
        }
        val ready = ArrayDeque(incoming.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val id = ready.removeFirst()
            visited++
            for (child in children[id].orEmpty()) {
                val remaining = incoming.getValue(child) - 1
                incoming[child] = remaining
                if (remaining == 0) ready.addLast(child)
            }
        }
        if (visited != incoming.size) invalidBackup("The archive contains an ancestry cycle.")
    }

    private fun photoConflict(): Nothing = throw ApiFailure(
        HttpStatus.CONFLICT,
        "BACKUP_PHOTO_CONFLICT",
        "The destination already contains a matching photo directory or pending file cleanup.",
    )
}
