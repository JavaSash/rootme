package ru.stepenko.rootme.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import ru.stepenko.rootme.BaseIntegrationTest
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal class FlywayV2Test(dataSource: DataSource) : BaseIntegrationTest() {
    private val jdbc = JdbcTemplate(dataSource)
    private val schema = "migration_" + UUID.randomUUID().toString().replace("-", "")
    private val configuration = Flyway.configure().dataSource(dataSource)
        .schemas(schema).defaultSchema(schema).cleanDisabled(false)

    @BeforeEach
    fun migrateToV1() {
        configuration.target("1").load().migrate()
    }

    @AfterEach
    fun removeTestSchema() {
        configuration.load().clean()
    }

    @Test
    fun `migration preserves people their full dates and explicit spouse pairs`() {
        val a = insertPerson("Alex", LocalDate.of(1950, 5, 17))
        val b = insertPerson("Dana")
        jdbc.update("UPDATE $schema.persons SET spouse_id = ? WHERE id = ?", b, a)
        jdbc.update("UPDATE $schema.persons SET spouse_id = ? WHERE id = ?", a, b)

        configuration.target("2").load().migrate()

        assertEquals(setOf(a, b), jdbc.queryForList("SELECT id FROM $schema.persons", UUID::class.java).toSet())
        val person = jdbc.queryForMap("SELECT first_name, birth_date, birth_date_precision FROM $schema.persons WHERE id = ?", a)
        assertEquals("Alex", person["first_name"])
        assertEquals("1950-05-17", person["birth_date"].toString())
        assertEquals("DAY", person["birth_date_precision"])
        val partnership = jdbc.queryForMap("SELECT person1_id, person2_id, type, status, start_date FROM $schema.partnerships")
        assertEquals(setOf(a, b), setOf(partnership["person1_id"], partnership["person2_id"]))
        assertEquals("MARRIAGE", partnership["type"])
        assertEquals("UNKNOWN", partnership["status"])
        assertNull(partnership["start_date"])
        assertNull(jdbc.queryForObject("SELECT to_regclass('$schema.person_parentage')", String::class.java))
    }

    @Test
    fun `unknown legacy parentage stops V2 and leaves the original data intact`() {
        val parent = insertPerson("Parent")
        val child = insertPerson("Child")
        jdbc.update("INSERT INTO $schema.person_parentage (parent_id, child_id) VALUES (?, ?)", parent, child)

        val error = assertThrows<FlywayException> { configuration.target("2").load().migrate() }
        assertTrue(error.message.orEmpty().contains("explicit classification"))
        val edge = jdbc.queryForMap("SELECT * FROM $schema.person_parentage")
        assertEquals(parent, edge["parent_id"])
        assertEquals(child, edge["child_id"])
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM $schema.persons WHERE relationship = 'YOU'", Int::class.java))
        assertNull(jdbc.queryForObject("SELECT to_regclass('$schema.parent_child_relationships')", String::class.java))
    }

    private fun insertPerson(name: String, birth: LocalDate? = null): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO $schema.persons (id, first_name, gender, birth_date, is_root, relationship, generation) VALUES (?, ?, 'MALE', ?, false, 'YOU', 0)",
            id, name, birth,
        )
        return id
    }
}
