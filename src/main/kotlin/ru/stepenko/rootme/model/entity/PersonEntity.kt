package ru.stepenko.rootme.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import ru.stepenko.rootme.model.person.Gender
import java.time.LocalDate
import java.util.*

@Entity(name = "persons")
data class PersonEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column(name = "first_name")
    var firstName: String,
    @Column(name = "last_name")
    var lastName: String?,
    @Column
    var patronymic: String?,
    @Column
    var gender: Gender,
    @Column(name = "birth_date")
    var birthDate: LocalDate?,
    @Column(name = "birth_country")
    var birthCountry: String?,
    @Column(name = "birth_region")
    var birthRegion: String?,
    @Column(name = "birth_city")
    var birthCity: String?,
    /**
     * First created person in tree is root
     */
    @Column(name = "is_root")
    var isRoot: Boolean
): AuditEntity()