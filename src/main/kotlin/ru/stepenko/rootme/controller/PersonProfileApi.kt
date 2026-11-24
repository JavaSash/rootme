package ru.stepenko.rootme.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.model.api.AlternativeNameFields
import ru.stepenko.rootme.model.api.PhotoFields
import ru.stepenko.rootme.model.api.PrimaryPhotoRequest
import ru.stepenko.rootme.service.AlternativeNameService
import ru.stepenko.rootme.service.PersonPhotoService
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PersonProfileApi(
    private val names: AlternativeNameService,
    private val photos: PersonPhotoService,
) {
    @PostMapping("/persons/{personId}/alternative-names")
    @ResponseStatus(HttpStatus.CREATED)
    fun addName(@PathVariable personId: UUID, @RequestBody fields: AlternativeNameFields) =
        names.create(personId, fields)

    @PatchMapping("/persons/{personId}/alternative-names/{id}")
    fun updateName(@PathVariable personId: UUID, @PathVariable id: UUID, @RequestBody patch: JsonNode) =
        names.update(personId, id, patch)

    @DeleteMapping("/persons/{personId}/alternative-names/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteName(@PathVariable personId: UUID, @PathVariable id: UUID) = names.delete(personId, id)

    @GetMapping("/persons/{personId}/photos")
    fun listPhotos(@PathVariable personId: UUID) = photos.list(personId)

    @PostMapping("/persons/{personId}/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable personId: UUID,
        @RequestParam file: MultipartFile,
        @RequestParam caption: String?,
        @RequestParam takenDate: String?,
    ) = photos.upload(personId, file, PhotoFields(caption, takenDate))

    @GetMapping("/photos/{id}")
    fun original(@PathVariable id: UUID) = image(id, false)

    @GetMapping("/photos/{id}/thumbnail")
    fun thumbnail(@PathVariable id: UUID) = image(id, true)

    @PatchMapping("/photos/{id}")
    fun updatePhoto(@PathVariable id: UUID, @RequestBody patch: JsonNode) = photos.update(id, patch)

    @PutMapping("/persons/{personId}/primary-photo")
    fun primary(@PathVariable personId: UUID, @RequestBody request: PrimaryPhotoRequest) =
        photos.selectPrimary(personId, request.photoId)

    @DeleteMapping("/photos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePhoto(@PathVariable id: UUID) = photos.delete(id)

    private fun image(id: UUID, thumbnail: Boolean): ResponseEntity<org.springframework.core.io.FileSystemResource> {
        val (contentType, file) = photos.image(id, thumbnail)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, immutable")
            .header("X-Content-Type-Options", "nosniff")
            .body(file)
    }
}
