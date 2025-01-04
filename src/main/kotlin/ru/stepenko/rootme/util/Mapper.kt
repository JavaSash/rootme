package ru.stepenko.rootme.util

import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq

fun PersonRq.toEntity(isRoot: Boolean) = PersonEntity(
    firstName = this.firstName,
    lastName = this.lastName,
    patronymic = this.patronymic,
    gender = this.gender,
    birthDate = this.birthDate,
    deathDate = this.deathDate,
    birthCountry = this.birthCountry,
    birthRegion = this.birthRegion,
    birthCity = this.birthCity,
    isRoot = isRoot,
    relationship = this.relationship
)

fun PersonEntity.toDto() = PersonDto(
    id = this.id,
    firstName = this.firstName,
    lastName = this.lastName,
    patronymic = this.patronymic,
    gender = this.gender,
    birthDate = this.birthDate,
    deathDate = this.deathDate,
    birthCountry = this.birthCountry,
    birthRegion = this.birthRegion,
    birthCity = this.birthCity,
    relationship = this.relationship.desc,
    isRoot = this.isRoot
)

fun List<PersonEntity>.toListDto(): List<PersonDto> {
    val result: MutableList<PersonDto> = mutableListOf()
    this.forEach {
        result.add(it.toDto())
    }
    return result
}
