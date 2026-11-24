package ru.stepenko.rootme

import ru.stepenko.rootme.TestConstants.Person.FIRST_NAME
import ru.stepenko.rootme.TestConstants.Person.LAST_NAME
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PersonRq

fun formPersonRq(firstName: String = FIRST_NAME, lastName: String? = LAST_NAME) =
    PersonRq(firstName = firstName, lastName = lastName, gender = Gender.UNKNOWN)
