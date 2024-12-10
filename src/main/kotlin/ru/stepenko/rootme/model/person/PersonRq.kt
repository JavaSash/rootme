package ru.stepenko.rootme.model.person

import java.time.LocalDate

data class PersonRq(
    var firstName: String,
    var lastName: String?,
    var patronymic: String?,
    var gender: Gender,
    var birthDate: LocalDate?,
    var birthCountry: String?,
    var birthRegion: String?,
    var birthCity: String?,
)