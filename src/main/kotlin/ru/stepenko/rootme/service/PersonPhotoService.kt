package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.api.PhotoDto
import ru.stepenko.rootme.model.api.PhotoFields
import ru.stepenko.rootme.model.api.toDto
import ru.stepenko.rootme.model.entity.PersonPhoto
import ru.stepenko.rootme.model.person.PartialDate
import ru.stepenko.rootme.repository.PersonPhotoRepository
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.applyPatch
import java.io.IOException
import java.util.UUID

@Service
class PersonPhotoService(
    private val persons: PersonRepository,
    private val photos: PersonPhotoRepository,
    private val storage: PhotoStorage,
    private val transaction: TransactionTemplate,
    private val json: ObjectMapper,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(personId: UUID): List<PhotoDto> {
        if (!persons.existsById(personId)) missingPerson(personId)
        return photos.findByPersonIdOrderByCreatedAtAscIdAsc(personId).map { it.toDto() }
    }

    fun upload(personId: UUID, file: MultipartFile, fields: PhotoFields): PhotoDto = withStorageErrors {
        val caption = caption(fields.caption)
        val date = PartialDate.parse("takenDate", fields.takenDate)
        if (!persons.existsById(personId)) missingPerson(personId)
        val id = UUID.randomUUID()
        requireNotNull(storage.exclusive(create = true) { root ->
            val stored = storage.publish(root, id, file)
            val dto = transaction.execute {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            storage.retryable(id) { storage.remove(root, id) }
                        }
                    }
                })
                lockPerson(personId)
                val photo = PersonPhoto(
                    id, personId, filename(file.originalFilename),
                    storage.key(id, stored.format, false), storage.key(id, stored.format, true),
                    stored.format.contentType, stored.size, stored.dimensions.width, stored.dimensions.height,
                    stored.sha256, caption, date, primary = photos.findByPersonIdAndPrimaryTrue(personId) == null,
                )
                photos.saveAndFlush(photo).toDto()
            }
            storage.retryable(id) { storage.finish(root, id) }
            requireNotNull(dto)
        })
    }

    @Transactional
    fun update(id: UUID, patch: JsonNode): PhotoDto {
        val photo = requireLockedPhoto(id)
        val fields = json.applyPatch(PhotoFields(photo.caption, photo.takenDate?.toIso()), patch)
        photo.caption = caption(fields.caption)
        photo.takenDate = PartialDate.parse("takenDate", fields.takenDate)
        return photo.toDto()
    }

    @Transactional
    fun selectPrimary(personId: UUID, id: UUID): PhotoDto {
        lockPerson(personId)
        val photo = requirePhoto(id)
        if (photo.personId != personId) {
            throw ApiFailure(HttpStatus.NOT_FOUND, "PHOTO_PERSON_MISMATCH", "The photograph does not belong to this person.")
        }
        if (!photo.primary) {
            photos.findByPersonIdAndPrimaryTrue(personId)?.let {
                it.primary = false
                photos.flush()
            }
            photo.primary = true
        }
        return photo.toDto()
    }

    @Transactional
    fun delete(id: UUID) {
        val photo = requireLockedPhoto(id)
        photos.delete(photo)
        if (photo.primary) {
            photos.flush()
            photos.findByPersonIdOrderByCreatedAtAscIdAsc(photo.personId).firstOrNull()?.primary = true
        }
    }

    fun image(id: UUID, thumbnail: Boolean) = withStorageErrors {
        val photo = requirePhoto(id)
        val key = if (thumbnail) photo.thumbnailStorageKey else photo.storageKey
        val file = storage.resource(id, key, PhotoFormat.fromContentType(photo.contentType), thumbnail)
        photo.contentType to file
    }

    private fun requireLockedPhoto(id: UUID): PersonPhoto {
        // Read the owner without putting a stale photo entity in the transaction's persistence context.
        val owner = photos.ownerOf(id) ?: missingPhoto()
        if (persons.findForUpdate(owner) == null) missingPhoto()
        return requirePhoto(id)
    }

    private fun requirePhoto(id: UUID): PersonPhoto = photos.findById(id).orElseThrow { missingPhoto() }

    private fun lockPerson(id: UUID) {
        persons.findForUpdate(id) ?: missingPerson(id)
    }

    private fun missingPerson(id: UUID): Nothing = throw PersonNotFoundException("Person $id was not found.")
    private fun missingPhoto(): Nothing =
        throw ApiFailure(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "The photograph was not found.")

    private fun caption(text: String?): String? {
        if (text != null && (text.contains('\u0000') || text.codePointCount(0, text.length) > 1000)) {
            throw InvalidInput("caption", "Use a caption of at most 1000 characters without NUL characters.")
        }
        return text
    }

    private fun filename(input: String?): String {
        val name = input.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .filterNot(Char::isISOControl).trim().ifEmpty { "photo" }
        return name.substring(0, name.offsetByCodePoints(0, minOf(255, name.codePointCount(0, name.length))))
    }

    private fun <T> withStorageErrors(action: () -> T): T = try {
        action()
    } catch (_: IOException) {
        throw ApiFailure(HttpStatus.INTERNAL_SERVER_ERROR, "PHOTO_STORAGE_ERROR", "The photograph storage is unavailable.")
    }
}
