package ru.stepenko.rootme.util

import ru.stepenko.rootme.model.api.ParentChildDto
import ru.stepenko.rootme.model.api.PartnershipDto
import ru.stepenko.rootme.model.api.PartnershipFields
import ru.stepenko.rootme.model.api.PersonSummary
import ru.stepenko.rootme.model.entity.ParentChildRelationship
import ru.stepenko.rootme.model.entity.Partnership
import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq

fun PersonEntity.toDto() = PersonDto(
    requireNotNull(id), firstName, lastName, patronymic, gender, birthDate?.toIso(), deathDate?.toIso(),
    birthCountry, birthRegion, birthCity, birthSurname, notes,
)

fun PersonEntity.toRequest() = PersonRq(
    firstName, lastName, patronymic, gender, birthDate?.toIso(), deathDate?.toIso(),
    birthCountry, birthRegion, birthCity, birthSurname, notes,
)

fun PersonEntity.toSummary() = PersonSummary(
    requireNotNull(id), firstName, lastName, patronymic, gender, birthDate?.toIso(), deathDate?.toIso(),
)

fun ParentChildRelationship.toDto() = ParentChildDto(requireNotNull(id), parentId, childId, type, createdAt)

fun Partnership.toDto() = PartnershipDto(
    requireNotNull(id), person1Id, person2Id, type, status, startDate?.toIso(), endDate?.toIso(),
)

fun Partnership.toFields() = PartnershipFields(type, status, startDate?.toIso(), endDate?.toIso())
