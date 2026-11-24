package ru.stepenko.rootme.service

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.stepenko.rootme.repository.PersonPhotoRepository
import java.util.UUID

@Component
@EnableScheduling
class PhotoCleanup(
    private val storage: PhotoStorage,
    private val photos: PersonPhotoRepository,
    private val jdbc: JdbcTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "\${rootme.storage.cleanup-initial-delay-ms:0}", fixedDelay = 30_000)
    fun retry() {
        try {
            storage.exclusive(create = false) { root ->
                storage.recoverUploads(root, photos::existsById)
                val queued = jdbc.queryForList("SELECT photo_id FROM photo_file_cleanup ORDER BY queued_at, photo_id", UUID::class.java)
                for (id in queued) storage.retryable(id) {
                    storage.remove(root, id)
                    jdbc.update("DELETE FROM photo_file_cleanup WHERE photo_id = ?", id)
                }
            }
        } catch (error: Exception) {
            logger.warn("Photo cleanup will retry ({})", error.javaClass.simpleName)
        }
    }
}
