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
        formResponseWrapper(msg = CrudStatus.CREATED.name, data = personRepository.save(person.toEntity(isNewTree())))

    fun update(person: PersonRq, personId: UUID): ResponseWrapper {
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
            data = deleteWithRootCheck(personId)
        ) }.getOrElse { throw PersonNotFoundException("Person with id=$personId not found") }

    fun getAllPersons(): ResponseWrapper =
        formResponseWrapper(
            msg = CrudStatus.FOUND.name,
            data = personRepository.findAll()
        )

    fun isNewTree() = personRepository.findAll().isEmpty()

    private fun getPersonOrThrowExc(personId: UUID) = personRepository.findById(personId)
        .orElseThrow { throw PersonNotFoundException("Person with id=$personId not found") }

    private fun deleteWithRootCheck(personId: UUID) {
        if (getPersonOrThrowExc(personId).isRoot) personRepository.deleteAll()
        else personRepository.deleteById(personId)
    }
}