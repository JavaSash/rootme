package ru.stepenko.rootme.model.person

import ru.stepenko.rootme.model.entity.PersonEntity
import java.time.LocalDate
import java.util.*

data class PersonDto(
    val id: UUID,
    val firstName: String,
    val lastName: String?,
    val patronymic: String?,
    val gender: Gender,
    val relationship: String,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val birthCountry: String?,
    val birthRegion: String?,
    val birthCity: String?,
    val isRoot: Boolean,
    val generation: Int,
    val children: MutableSet<PersonEntity>,
    val spouse: PersonEntity?,
    val parents: MutableSet<PersonEntity>
)