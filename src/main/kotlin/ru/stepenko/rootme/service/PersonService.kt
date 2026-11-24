package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.api.PersonDetailsDto
import ru.stepenko.rootme.model.api.PersonPage
import ru.stepenko.rootme.model.api.toDto
import ru.stepenko.rootme.repository.AlternativeNameRepository
import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.model.person.PartialDate
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.repository.ParentChildRepository
import ru.stepenko.rootme.repository.PartnershipRepository
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.*
import java.time.LocalDate
import java.util.UUID

@Service
class PersonService(
    private val persons: PersonRepository,
    private val parentChild: ParentChildRepository,
    private val partnerships: PartnershipRepository,
    private val graphLock: FamilyGraphLock,
    private val objectMapper: ObjectMapper,
    private val summaries: PersonSummaries,
    private val names: AlternativeNameRepository,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun search(query: String, birthYear: Int? = null, page: Int = 0, size: Int = 30): PersonPage {
        if (page < 0 || page.toLong() * size > Int.MAX_VALUE) throw InvalidInput("page", "Invalid page number.")
        if (size !in 1..100) throw InvalidInput("size", "Use a page size from 1 to 100.")
        if (birthYear != null && birthYear !in 1..9999) throw InvalidInput("birthYear", "Use a year from 1 to 9999.")
        val normalized = query.trim().replace(Regex("(?U)\\s+"), " ").trim()
        if (normalized.length > 255 || normalized.contains('\u0000')) throw InvalidInput("query", "Invalid search query.")
        val start = birthYear?.let { LocalDate.of(it, 1, 1) }
        val end = birthYear?.let { LocalDate.of(it, 12, 31) }
        val result = persons.search(normalized, start, end, PageRequest.of(page, size))
        return PersonPage(summaries.forPeople(result.content), page, size, result.totalElements)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
            summaries.forPeople(persons.findAllById(relativeIds)).sortedBy { it.id },
            edges.map { it.toDto() }.sortedBy { it.id },
            unions.map { it.toDto() }.sortedBy { it.id },
            names.findByPersonIdOrderByNameAscIdAsc(id).map { it.toDto() },
            summaries.primary(id),
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
        val person = persons.findForUpdate(id) ?: throw PersonNotFoundException("Person $id was not found.")
        persons.delete(person)
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
            "biography" to request.biography,
            "deathCountry" to request.deathCountry,
            "deathRegion" to request.deathRegion,
            "deathCity" to request.deathCity,
            "occupation" to request.occupation,
            "burialPlace" to request.burialPlace,
        ).forEach { (field, value) ->
            if (value?.contains('\u0000') == true) throw InvalidInput(field, "NUL characters are not allowed.")
            val limit = when (field) {
                "notes" -> if (value == notes) null else 10_000
                "biography" -> 100_000
                else -> 255
            }
            if (limit != null && value != null && value.codePointCount(0, value.length) > limit) {
                throw InvalidInput(field, "Use no more than $limit characters.")
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
        biography = request.biography
        deathCountry = request.deathCountry
        deathRegion = request.deathRegion
        deathCity = request.deathCity
        occupation = request.occupation
        burialPlace = request.burialPlace
    }
}
