package ru.stepenko.rootme.model.entity

import jakarta.persistence.*
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PartialDate
import java.util.UUID

@Entity
@Table(name = "persons")
class PersonEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    var firstName: String = "",
    var lastName: String? = null,
    var patronymic: String? = null,
    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.UNKNOWN,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "value", column = Column(name = "birth_date")),
        AttributeOverride(name = "precision", column = Column(name = "birth_date_precision")),
    )
    var birthDate: PartialDate? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "value", column = Column(name = "death_date")),
        AttributeOverride(name = "precision", column = Column(name = "death_date_precision")),
    )
    var deathDate: PartialDate? = null,
    var birthCountry: String? = null,
    var birthRegion: String? = null,
    var birthCity: String? = null,
    var birthSurname: String? = null,
    var notes: String? = null,
)
