package ru.stepenko.rootme.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import ru.stepenko.rootme.BaseIntegrationTest
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal class FlywayMigrationTest(dataSource: DataSource) : BaseIntegrationTest() {
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

    @Test
    fun `V3 preserves V2 data and enforces storage constraints in the migrated schema`() {
        val parent = insertPerson("Parent", LocalDate.of(1950, 5, 1))
        val child = insertPerson("Child")
        configuration.target("2").load().migrate()
        val notes = "research ".repeat(1500)
        jdbc.update("UPDATE $schema.persons SET notes = ?, birth_date_precision = 'MONTH' WHERE id = ?", notes, parent)
        val relationship = UUID.randomUUID()
        jdbc.update("""
            INSERT INTO $schema.parent_child_relationships (id, parent_id, child_id, type, created_at)
            VALUES (?, ?, ?, 'ADOPTIVE', now())
        """, relationship, parent, child)

        configuration.target("3").load().migrate()

        val person = jdbc.queryForMap("SELECT * FROM $schema.persons WHERE id = ?", parent)
        assertEquals(notes, person["notes"])
        assertEquals("MONTH", person["birth_date_precision"])
        assertEquals("1950-05-01", person["birth_date"].toString())
        assertNull(person["biography"])
        assertEquals(relationship, jdbc.queryForObject("SELECT id FROM $schema.parent_child_relationships", UUID::class.java))

        val photo = UUID.randomUUID()
        insertPhoto(photo, parent)
        assertThrows<DataIntegrityViolationException> { insertPhoto(UUID.randomUUID(), parent) }
        jdbc.update("DELETE FROM $schema.persons WHERE id = ?", parent)
        assertEquals(photo, jdbc.queryForObject("SELECT photo_id FROM $schema.photo_file_cleanup", UUID::class.java))
    }

    private fun insertPhoto(id: UUID, personId: UUID) {
        jdbc.update("""
            INSERT INTO $schema.person_photos (
                id, person_id, original_file_name, storage_key, thumbnail_storage_key,
                content_type, size_bytes, width, height, sha256, is_primary, created_at
            ) VALUES (?, ?, 'image.png', ?, ?, 'image/png', 100, 10, 10, repeat('a', 64), true, now())
        """, id, personId, "$id/original.png", "$id/thumbnail.png")
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
