package ru.stepenko.rootme.model.person

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class PersonRq(
    @field:NotBlank
    @field:Size(max = 255)
    val firstName: String,
    @field:Size(max = 255)
    val lastName: String?,
    @field:Size(max = 255)
    val patronymic: String?,
    val gender: Gender,
    val relationship: Relationship = Relationship.YOU,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    @field:Size(max = 255) val birthCountry: String?,
    @field:Size(max = 255) val birthRegion: String?,
    @field:Size(max = 255) val birthCity: String?,
)
