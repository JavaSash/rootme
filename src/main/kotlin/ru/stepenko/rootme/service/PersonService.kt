package ru.stepenko.rootme.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    fun create(person: PersonRq) = personRepository.save(person.toEntity(isNewTree())).toDto()

    @Transactional
    fun update(person: PersonRq, personId: UUID): PersonDto {
        val existPerson = getPersonOrThrowExc(personId)
        return personRepository.save(
            existPerson.apply {
                this.firstName = person.firstName
                this.lastName = person.lastName
                this.patronymic = person.patronymic
                this.gender = person.gender
                this.birthDate = person.birthDate
                this.deathDate = person.deathDate
                this.birthCountry = person.birthCountry
                this.birthRegion = person.birthRegion
                this.birthCity = person.birthCity
            }).toDto()
    }

    @Transactional(readOnly = true)
    fun getPerson(personId: UUID) = getPersonOrThrowExc(personId).toDto()

    @Transactional
    fun delete(personId: UUID) = personRepository.delete(getPersonOrThrowExc(personId))

    @Transactional(readOnly = true)
    fun getAllPersons() = personRepository.findAll().toListDto()

    fun isNewTree(): Boolean = personRepository.count() == 0L

    private fun getPersonOrThrowExc(personId: UUID) = personRepository.findById(personId)
        .orElseThrow { throw PersonNotFoundException("Person with id=$personId not found") }
}
