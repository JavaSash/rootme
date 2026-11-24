package ru.stepenko.rootme.model.person

import java.util.UUID

data class PersonDto(
    val id: UUID,
    val firstName: String,
    val lastName: String?,
    val patronymic: String?,
    val gender: Gender,
    val birthDate: String?,
    val deathDate: String?,
    val birthCountry: String?,
    val birthRegion: String?,
    val birthCity: String?,
    val birthSurname: String?,
    val notes: String?,
    val biography: String? = null,
    val deathCountry: String? = null,
    val deathRegion: String? = null,
    val deathCity: String? = null,
    val occupation: String? = null,
    val burialPlace: String? = null,
)
