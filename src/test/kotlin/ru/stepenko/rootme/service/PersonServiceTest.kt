package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.formPersonRq
import java.util.UUID

internal class PersonServiceTest(private val persons: PersonService, private val json: ObjectMapper) : BaseIntegrationTest() {
    @Test
    fun `create stores person details and preserves all supported date precisions`() {
        for (date in listOf("1950", "1950-05", "1950-05-17", null)) {
            val request = formPersonRq(lastName = null).copy(birthDate = date, birthCity = "Казань")
            val created = persons.create(request)
            assertEquals(request.firstName, created.firstName)
            assertNull(created.lastName)
            assertEquals(date, created.birthDate)
            assertEquals("Казань", created.birthCity)
            assertEquals(created, persons.details(created.id).person)
        }
    }

    @Test
    fun `patch preserves absent fields and clears optional fields passed as null`() {
        val created = persons.create(formPersonRq().copy(birthDate = "1950-05"))
        val updated = persons.update(created.id, json.readTree("""{"lastName":null,"notes":"Новая запись"}"""))
        assertEquals(created.copy(lastName = null, notes = "Новая запись"), updated)
        assertEquals(updated, persons.details(created.id).person)
    }

    @Test
    fun `rejects invalid calendar dates and only rejects definitely impossible chronology`() {
        assertThrows<InvalidInput> { persons.create(formPersonRq().copy(birthDate = "1950-02-29")) }
        assertThrows<InvalidInput> { persons.create(formPersonRq().copy(birthDate = "1950-06", deathDate = "1950-05")) }
        val person = persons.create(formPersonRq().copy(birthDate = "1950-05-31", deathDate = "1950-05"))
        assertThrows<InvalidInput> { persons.update(person.id, json.readTree("""{"deathDate":"1949"}""")) }
        assertEquals("1950-05", persons.details(person.id).person.deathDate)
    }

    @Test
    fun `list and search match all three name fields case insensitively`() {
        assertTrue(persons.search("").items.isEmpty())
        val first = persons.create(formPersonRq().copy(patronymic = "Алиевич"))
        val second = persons.create(formPersonRq("Мария", "Северова"))
        assertEquals(setOf(first.id, second.id), persons.search("").items.map { it.id }.toSet())
        for (query in listOf("БРАГ", "ШАСС", "АЛИЕВ")) {
            assertEquals(listOf(first.id), persons.search(query).items.map { it.id })
        }
        assertTrue(persons.search("%").items.isEmpty())
    }

    @Test
    fun `unknown and deleted person are not found while other people remain`() {
        assertThrows<PersonNotFoundException> { persons.details(UUID.randomUUID()) }
        val removed = persons.create(formPersonRq())
        val kept = persons.create(formPersonRq("Мария"))
        persons.delete(removed.id)
        assertThrows<PersonNotFoundException> { persons.details(removed.id) }
        assertEquals(listOf(kept.id), persons.search("").items.map { it.id })
    }
}
