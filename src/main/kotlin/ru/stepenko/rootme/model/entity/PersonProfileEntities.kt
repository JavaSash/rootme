package ru.stepenko.rootme.model.entity

import jakarta.persistence.*
import ru.stepenko.rootme.model.person.PartialDate
import java.time.Instant
import java.util.UUID

enum class AlternativeNameType { FORMER_NAME, NICKNAME, ALTERNATIVE_SPELLING, OTHER }

@Entity
@Table(name = "person_alternative_names")
class PersonAlternativeName(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    val personId: UUID,
    var name: String,
    @Enumerated(EnumType.STRING)
    var type: AlternativeNameType,
)

@Entity
@Table(name = "person_photos")
class PersonPhoto(
    @Id val id: UUID,
    val personId: UUID,
    val originalFileName: String,
    val storageKey: String,
    val thumbnailStorageKey: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val sha256: String,
    var caption: String?,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "value", column = Column(name = "taken_date")),
        AttributeOverride(name = "precision", column = Column(name = "taken_date_precision")),
    )
    var takenDate: PartialDate?,
    @Column(name = "is_primary")
    var primary: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
