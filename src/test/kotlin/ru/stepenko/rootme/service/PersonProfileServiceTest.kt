package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.Conflict
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.NotFoundException
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.AlternativeNameFields
import ru.stepenko.rootme.model.entity.AlternativeNameType.*

internal class PersonProfileServiceTest(
    private val persons: PersonService,
    private val names: AlternativeNameService,
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) : BaseIntegrationTest() {
    @Test
    fun `updates optional details and preserves legacy notes on unrelated patch`() {
        val person = persons.create(formPersonRq().copy(birthSurname = "West", birthDate = "1950"))
        val legacy = "x".repeat(12_000)
        jdbc.update("UPDATE persons SET notes = ? WHERE id = ?", legacy, person.id)
        val fields = json.createObjectNode().put("biography", "First line\nSecond line")
            .put("deathCountry", "Russia").put("deathRegion", "Ural").put("deathCity", "Perm")
            .put("occupation", "Teacher").put("burialPlace", "Perm cemetery")
        persons.update(person.id, fields)
        val saved = persons.details(person.id).person
        for ((key, value) in fields.fields().asSequence()) {
            assertEquals(value, json.valueToTree<com.fasterxml.jackson.databind.JsonNode>(saved).get(key))
        }
        assertEquals(legacy, saved.notes)
        assertEquals("1950", saved.birthDate)
        assertEquals("West", saved.birthSurname)
        assertThrows<InvalidInput> {
            persons.update(person.id, json.createObjectNode().put("notes", "n".repeat(10_001)))
        }
        assertThrows<InvalidInput> {
            persons.update(person.id, json.createObjectNode().put("biography", "b".repeat(100_001)))
        }
        assertNull(persons.update(person.id, json.readTree("""{"notes":null,"biography":null}""")).notes)
        assertNull(persons.details(person.id).person.biography)
    }

    @Test
    fun `alternative names normalize spaces validate ownership and are removed with person`() {
        val person = persons.create(formPersonRq("Anna"))
        val other = persons.create(formPersonRq("Boris"))
        assertThrows<InvalidInput> { names.create(person.id, AlternativeNameFields("  ", NICKNAME)) }
        val name = names.create(person.id, AlternativeNameFields("  North    Wind  ", NICKNAME))
        assertEquals("North Wind", name.name)
        assertEquals("DUPLICATE_ALTERNATIVE_NAME", assertThrows<Conflict> {
            names.create(person.id, AlternativeNameFields("north wind", NICKNAME))
        }.code)
        assertThrows<NotFoundException> { names.delete(other.id, name.id) }
        assertThrows<NotFoundException> { names.update(other.id, name.id, json.readTree("""{"type":"OTHER"}""")) }
        val updated = names.update(person.id, name.id, json.readTree("""{"type":"OTHER"}"""))
        assertEquals(OTHER, updated.type)
        assertEquals("North Wind", updated.name)
        val second = names.create(person.id, AlternativeNameFields("Other Wind", ALTERNATIVE_SPELLING))
        names.delete(person.id, second.id)
        assertEquals(listOf(updated), persons.details(person.id).alternativeNames)
        persons.delete(person.id)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM person_alternative_names WHERE person_id = ?", Int::class.java, person.id))
    }

    @Test
    fun `search combines unordered words and birth year without duplicating matching personas`() {
        val a = persons.create(formPersonRq("Anna", "North").copy(birthSurname = "West", birthDate = "1950"))
        val b = persons.create(formPersonRq("Anna", "North").copy(birthDate = "1950-05"))
        persons.create(formPersonRq("Anna", "North").copy(birthDate = "1951-01-01"))
        names.create(a.id, AlternativeNameFields("Wind", NICKNAME))
        names.create(a.id, AlternativeNameFields("Wind", OTHER))
        for (query in listOf("north   annA", "Anna NORTH")) {
            val first = persons.search(query, 1950, page = 0, size = 1)
            val second = persons.search(query, 1950, page = 1, size = 1)
            assertEquals(2L, first.total)
            assertEquals(2L, second.total)
            assertEquals(setOf(a.id, b.id), (first.items + second.items).map { it.id }.toSet())
            assertEquals(first.items, persons.search(query, 1950, page = 0, size = 1).items)
        }
        assertEquals(listOf(a.id), persons.search("wind anna west").items.map { it.id })
        assertThrows<InvalidInput> { persons.search("", size = 101) }
    }
}
