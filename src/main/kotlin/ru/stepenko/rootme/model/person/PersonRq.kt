package ru.stepenko.rootme.model.person

import java.time.LocalDate

data class PersonRq(
    val firstName: String,
    val lastName: String?,
    val patronymic: String?,
    val gender: Gender,
    val relationship: Relationship = Relationship.YOU,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val birthCountry: String?,
    val birthRegion: String?,
    val birthCity: String?,
)