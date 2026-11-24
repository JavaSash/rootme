package ru.stepenko.rootme.model.api

import ru.stepenko.rootme.model.entity.AlternativeNameType
import ru.stepenko.rootme.model.entity.PersonAlternativeName
import ru.stepenko.rootme.model.entity.PersonPhoto
import java.time.Instant
import java.util.UUID

data class AlternativeNameFields(val name: String, val type: AlternativeNameType)
data class AlternativeNameDto(val id: UUID, val name: String, val type: AlternativeNameType)

data class PhotoFields(val caption: String? = null, val takenDate: String? = null)
data class PrimaryPhotoRequest(val photoId: UUID)
data class PhotoSummary(val id: UUID, val thumbnailUrl: String)
data class PhotoDto(
    val id: UUID,
    val personId: UUID,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val sha256: String,
    val caption: String?,
    val takenDate: String?,
    val isPrimary: Boolean,
    val createdAt: Instant,
    val originalUrl: String,
    val thumbnailUrl: String,
)

data class PersonPage(val items: List<PersonSummary>, val page: Int, val size: Int, val total: Long)

fun PersonAlternativeName.toDto() = AlternativeNameDto(requireNotNull(id), name, type)
fun PersonPhoto.toSummary() = PhotoSummary(id, "/api/v1/photos/$id/thumbnail")
fun PersonPhoto.toDto() = PhotoDto(
    id, personId, originalFileName, contentType, sizeBytes, width, height, sha256,
    caption, takenDate?.toIso(), primary, createdAt, "/api/v1/photos/$id", "/api/v1/photos/$id/thumbnail",
)
