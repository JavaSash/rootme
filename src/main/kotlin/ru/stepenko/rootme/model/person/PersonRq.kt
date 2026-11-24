package ru.stepenko.rootme.model.person

data class PersonRq(
    val firstName: String,
    val lastName: String? = null,
    val patronymic: String? = null,
    val gender: Gender,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val birthCountry: String? = null,
    val birthRegion: String? = null,
    val birthCity: String? = null,
    val birthSurname: String? = null,
    val notes: String? = null,
    val biography: String? = null,
    val deathCountry: String? = null,
    val deathRegion: String? = null,
    val deathCity: String? = null,
    val occupation: String? = null,
    val burialPlace: String? = null,
)
