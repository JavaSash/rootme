package ru.stepenko.rootme.service

import org.springframework.stereotype.Service
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.CrudStatus
import ru.stepenko.rootme.model.ResponseWrapper
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.formResponseWrapper
import ru.stepenko.rootme.util.toEntity
import java.util.*

@Service
class PersonService(private val personRepository: PersonRepository) {

    fun create(person: PersonRq) =
        formResponseWrapper(msg = CrudStatus.CREATED.name, data = personRepository.save(person.toEntity()))

    fun update(person: PersonRq, personId: UUID): ResponseWrapper {
//        TODO добавить проверку на существование дерева с таким id. Если такого id нет, то создаём нового человека,
//         если есть обновляем. Возможно есть смысл create+update везде заменить на patch
        val existPerson = getPersonOrThrowExc(personId)
        return formResponseWrapper(
            msg = CrudStatus.UPDATED.name,
            data = personRepository.save(
                existPerson.apply {
                    this.firstName = person.firstName
                    this.lastName = person.lastName
                    this.patronymic = person.patronymic
                    this.gender = person.gender
                    this.birthDate = person.birthDate
                    this.birthCountry = person.birthCountry
                    this.birthRegion = person.birthRegion
                    this.birthCity = person.birthCity
                })
        )
    }

    fun getPerson(personId: UUID) = formResponseWrapper(
        msg = CrudStatus.FOUND.name,
        data = getPersonOrThrowExc(personId)
    )

    fun delete(personId: UUID) = runCatching {
        formResponseWrapper(
            msg = CrudStatus.DELETED.name,
            data = personRepository.deleteById(personId)
        ) }.getOrElse { throwPersonNotFound(personId) }

    private fun getPersonOrThrowExc(personId: UUID) = personRepository.findById(personId)
        .orElseThrow { throwPersonNotFound(personId) }

    private fun throwPersonNotFound(personId: UUID): Throwable? {
        throw PersonNotFoundException("Person with id=$personId not found")
    }
}