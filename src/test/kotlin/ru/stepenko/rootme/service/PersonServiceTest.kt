package ru.stepenko.rootme.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.TestConstants.Person.FATHER
import ru.stepenko.rootme.TestConstants.Person.FIRST_NAME
import ru.stepenko.rootme.TestConstants.Person.LAST_NAME
import ru.stepenko.rootme.TestConstants.Person.MOTHER
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.Relationship
import ru.stepenko.rootme.util.toDto
import java.time.LocalDate
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

internal class PersonServiceTest(
    private val personService: PersonService
) : BaseIntegrationTest() {

    @Disabled("Stage 1: use explicit parentage and partnership operations")
    @Test
    fun `create family with grandparents and grandchildren`() {
        //create root person
        var root = personService.create(
            formPersonRq(
                firstName = FIRST_NAME,
                lastName = LAST_NAME,
                gender = Gender.MALE,
                relationship = Relationship.YOU
            )
        )

        assertAll(
            { assertEquals(FIRST_NAME, root.firstName) },
            { assertEquals(LAST_NAME, root.lastName) },
            { assertEquals(Relationship.YOU.desc, root.relationship) },
            { assertEquals(Gender.MALE, root.gender) },
            { assertTrue(root.children.isEmpty()) },
            { assertTrue(root.parents.isEmpty()) },
            { assertNull(root.spouse) },
        )

        // create parents
        val dad = personService.create(
            formPersonRq(
                firstName = FATHER,
                lastName = LAST_NAME,
                gender = Gender.MALE,
                relationship = Relationship.FATHER
            )
        )

        val mom = personService.create(
            formPersonRq(
                firstName = MOTHER,
                lastName = LAST_NAME,
                gender = Gender.FEMALE,
                relationship = Relationship.MOTHER
            )
        )

        root = personRepository.findById(root.id).getOrNull()!!.toDto()

        assertAll(
            { assertEquals(FIRST_NAME, root.firstName) },
            { assertEquals(LAST_NAME, root.lastName) },
            { assertEquals(Relationship.YOU.desc, root.relationship) },
            { assertEquals(Gender.MALE, root.gender) },
            { assertTrue(root.children.isEmpty()) },
            { assertEquals(2, root.parents.size) },
            { assertEquals(dad.id, root.parents.filter { it.firstName == dad.firstName }.first().id) },
            { assertEquals(mom.id, root.parents.filter { it.firstName == mom.firstName }.first().id) },
            { assertNull(root.spouse) },

            { assertEquals(FATHER, dad.firstName) },
            { assertEquals(LAST_NAME, dad.lastName) },
            { assertEquals(Relationship.FATHER.desc, dad.relationship) },
            { assertEquals(Gender.MALE, dad.gender) },
            { assertEquals(root.id, dad.children.first().id) },
            { assertTrue(dad.parents.isEmpty()) },
            { assertEquals(mom.id, dad.spouse?.id) },

            { assertEquals(MOTHER, mom.firstName) },
            { assertEquals(LAST_NAME, mom.lastName) },
            { assertEquals(Relationship.MOTHER.desc, mom.relationship) },
            { assertEquals(Gender.FEMALE, mom.gender) },
            { assertEquals(root.id, mom.children.first().id) },
            { assertTrue(mom.parents.isEmpty()) },
            { assertEquals(dad.id, mom.spouse?.id) },
        )
    }

    @Test
    fun `create stores person details and marks only the first person as root`() {
        val request = formPersonRq(relationship = Relationship.YOU).copy(
            birthDate = LocalDate.of(1990, 2, 3),
            birthCity = "Kazan",
        )
        val first = personService.create(request)
        val second = personService.create(request.copy(firstName = MOTHER, gender = Gender.FEMALE))

        assertAll(
            { assertNotEquals(first.id, second.id) },
            { assertTrue(first.isRoot) },
            { assertFalse(second.isRoot) },
            { assertEquals(request.firstName, first.firstName) },
            { assertEquals(request.lastName, first.lastName) },
            { assertEquals(request.birthDate, first.birthDate) },
            { assertEquals(request.birthCity, first.birthCity) },
            { assertEquals(first, personService.getPerson(first.id)) },
        )
    }

    @Test
    fun `update replaces editable fields and preserves root identity`() {
        val request = formPersonRq(relationship = Relationship.YOU)
        val created = personService.create(request)
        val replacement = request.copy(
            firstName = MOTHER,
            lastName = null,
            gender = Gender.FEMALE,
            birthDate = LocalDate.of(1985, 6, 12),
        )

        val updated = personService.update(replacement, created.id)

        assertAll(
            { assertEquals(created.id, updated.id) },
            { assertTrue(updated.isRoot) },
            { assertEquals(replacement.firstName, updated.firstName) },
            { assertNull(updated.lastName) },
            { assertEquals(replacement.gender, updated.gender) },
            { assertEquals(replacement.birthDate, updated.birthDate) },
            { assertEquals(updated, personService.getPerson(created.id)) },
        )
    }

    @Test
    fun `get person rejects an unknown id`() {
        assertThrows<PersonNotFoundException> { personService.getPerson(UUID.randomUUID()) }
    }

    @Test
    fun `deleting the root keeps the other person`() {
        val request = formPersonRq(relationship = Relationship.YOU)
        val root = personService.create(request)
        val other = personService.create(request.copy(firstName = MOTHER))

        personService.delete(root.id)

        assertThrows<PersonNotFoundException> { personService.getPerson(root.id) }
        assertEquals(listOf(other), personService.getAllPersons())
    }

    @Test
    fun `list returns all people and is empty before the first creation`() {
        assertTrue(personService.getAllPersons().isEmpty())
        val request = formPersonRq(relationship = Relationship.YOU)
        val first = personService.create(request)
        val second = personService.create(request.copy(firstName = MOTHER))

        val people = personService.getAllPersons()

        assertEquals(2, people.size)
        assertEquals(setOf(first, second), people.toSet())
    }
}
