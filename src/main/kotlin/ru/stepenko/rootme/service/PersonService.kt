package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.api.PersonDetailsDto
import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.model.person.PartialDate
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.repository.ParentChildRepository
import ru.stepenko.rootme.repository.PartnershipRepository
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.*
import java.util.UUID

@Service
class PersonService(
    private val persons: PersonRepository,
    private val parentChild: ParentChildRepository,
    private val partnerships: PartnershipRepository,
    private val graphLock: FamilyGraphLock,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun search(query: String) = persons.search(query.trim()).map { it.toSummary() }

    @Transactional(readOnly = true)
    fun details(id: UUID): PersonDetailsDto {
        val person = requirePerson(id)
        val edges = parentChild.findByParentIdOrChildId(id, id)
        val unions = partnerships.findByPerson1IdOrPerson2Id(id, id)
        val relativeIds = buildSet {
            edges.forEach { add(it.parentId); add(it.childId) }
            unions.forEach { add(it.person1Id); add(it.person2Id) }
            remove(id)
        }
        return PersonDetailsDto(
            person.toDto(),
            persons.findAllById(relativeIds).map { it.toSummary() }.sortedBy { it.id },
            edges.map { it.toDto() }.sortedBy { it.id },
            unions.map { it.toDto() }.sortedBy { it.id },
        )
    }

    @Transactional
    fun create(request: PersonRq) = persons.save(PersonEntity().apply { setFields(request) }).toDto()

    @Transactional
    fun update(id: UUID, patch: JsonNode): PersonDto {
        val person = persons.findForUpdate(id) ?: throw PersonNotFoundException("Person $id was not found.")
        person.setFields(objectMapper.applyPatch(person.toRequest(), patch))
        return person.toDto()
    }

    @Transactional
    fun delete(id: UUID) {
        graphLock.lock()
        persons.delete(requirePerson(id))
    }

    internal fun requirePerson(id: UUID): PersonEntity =
        persons.findById(id).orElseThrow { PersonNotFoundException("Person $id was not found.") }

    private fun PersonEntity.setFields(request: PersonRq) {
        if (request.firstName.isBlank()) throw InvalidInput("firstName", "First name is required.")
        mapOf(
            "firstName" to request.firstName,
            "lastName" to request.lastName,
            "patronymic" to request.patronymic,
            "birthCountry" to request.birthCountry,
            "birthRegion" to request.birthRegion,
            "birthCity" to request.birthCity,
            "birthSurname" to request.birthSurname,
            "notes" to request.notes,
        ).forEach { (field, value) ->
            if (value?.contains('\u0000') == true) throw InvalidInput(field, "NUL characters are not allowed.")
            if (field != "notes" && value != null && value.codePointCount(0, value.length) > 255) {
                throw InvalidInput(field, "Use no more than 255 characters.")
            }
        }
        val birth = PartialDate.parse("birthDate", request.birthDate)
        val death = PartialDate.parse("deathDate", request.deathDate)
        PartialDate.validateOrder(birth, death, "deathDate")
        firstName = request.firstName
        lastName = request.lastName
        patronymic = request.patronymic
        gender = request.gender
        birthDate = birth
        deathDate = death
        birthCountry = request.birthCountry
        birthRegion = request.birthRegion
        birthCity = request.birthCity
        birthSurname = request.birthSurname
        notes = request.notes
    }
}
