package ru.stepenko.rootme.service

import mu.KLogging
import org.springframework.stereotype.Service
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.toDto
import ru.stepenko.rootme.util.toEntity
import ru.stepenko.rootme.util.toListDto
import java.util.*

@Service
class PersonService(private val personRepository: PersonRepository) {

    companion object : KLogging()

    fun create(person: PersonRq) = personRepository.save(person.toEntity(isNewTree())).toDto()

    fun update(person: PersonRq, personId: UUID): PersonDto {
        val existPerson = getPersonOrThrowExc(personId)
        logger.info { ">>> Person to update: $existPerson" }
        return personRepository.save(
            existPerson.apply {
                this.firstName = person.firstName
                this.lastName = person.lastName
                this.patronymic = person.patronymic
                this.gender = person.gender
                this.birthDate = person.birthDate
                this.birthCountry = person.birthCountry
                this.birthRegion = person.birthRegion
                this.birthCity = person.birthCity
            }).toDto()
            .also { logger.info { ">>> Person updated: $it" } }
    }

    fun getPerson(personId: UUID) = getPersonOrThrowExc(personId)//.toDto()

    fun delete(personId: UUID) = runCatching {
        deleteWithRootCheck(personId)
    }.getOrElse { throw PersonNotFoundException("Person with id=$personId not found") }

    fun getAllPersons() = personRepository.findAll().toListDto()

    fun isNewTree(): Boolean =
        personRepository.findAll().isEmpty().also { logger.info { ">>> Is new tree: $it" } } //todo debug log

    fun isRoot(id: UUID): Boolean =
        getPersonOrThrowExc(id).isRoot.also { logger.info { ">>> Person $id is root: $it" } }

    private fun getPersonOrThrowExc(personId: UUID) = personRepository.findById(personId)
        .orElseThrow { throw PersonNotFoundException("Person with id=$personId not found") }
        .also { logger.info { ">>> Person found: $it" } }

    private fun deleteWithRootCheck(personId: UUID) {
        if (getPersonOrThrowExc(personId).isRoot) personRepository.deleteAll()
            .also { logger.info { ">>> Root person $personId deleted. Tree cleared" } }
        else personRepository.deleteById(personId)
            .also { logger.info { ">>> Person $personId deleted" } }
    }
}