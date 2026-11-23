package ru.stepenko.rootme

import ru.stepenko.rootme.TestConstants.Person.FIRST_NAME
import ru.stepenko.rootme.TestConstants.Person.LAST_NAME
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.model.person.Relationship

fun formPersonRq(
    firstName: String = FIRST_NAME,
    lastName: String = LAST_NAME,
    gender: Gender = Gender.MALE,
    relationship: Relationship
) = PersonRq(
    firstName = firstName,
    lastName = lastName,
    patronymic = null,
    gender = gender,
    relationship = relationship,
    birthDate = null,
    deathDate = null,
    birthCountry = null,
    birthRegion = null,
    birthCity = null
)