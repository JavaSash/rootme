package ru.stepenko.rootme.util

import ru.stepenko.rootme.model.ResponseWrapper
import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.model.person.PersonRq

fun PersonRq.toEntity() = PersonEntity(
    firstName = this.firstName,
    lastName = this.lastName,
    patronymic = this.patronymic,
    gender = this.gender,
    birthDate = this.birthDate,
    birthCountry = this.birthCountry,
    birthRegion = this.birthRegion,
    birthCity = this.birthCity
)

fun formResponseWrapper(msg: String?, data: Any? = null): ResponseWrapper =
    ResponseWrapper(
        message = msg,
        data = data
    )
