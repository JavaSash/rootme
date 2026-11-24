package ru.stepenko.rootme.service

import com.healthmarketscience.jackcess.ColumnBuilder
import com.healthmarketscience.jackcess.DataType
import com.healthmarketscience.jackcess.Database
import com.healthmarketscience.jackcess.DatabaseBuilder
import com.healthmarketscience.jackcess.TableBuilder
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.api.PartnershipRequest
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.PartnershipStatus
import ru.stepenko.rootme.model.entity.PartnershipType
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

internal class DataTransferTest(
    private val files: ImportFiles,
    private val importer: PersonImportService,
    private val persons: PersonService,
    private val family: FamilyRelationshipService,
    private val exporter: GedcomExport,
) : BaseIntegrationTest() {
    @TempDir lateinit var directory: Path

    private val options = ImportMapping("People", mapOf("firstName" to 0, "lastName" to 1, "birthDate" to 2))

    @Test
    fun `Excel formats preview valid rows and skip duplicate records without overwriting`() {
        val existing = persons.create(formPersonRq("Анна", "Иванова").copy(birthDate = "1950"))
        for ((name, workbook) in listOf("people.xlsx" to XSSFWorkbook(), "people.xls" to HSSFWorkbook())) {
            val file = excel(name, workbook, listOf(listOf("АННА", "Иванова", "1950"), listOf(name, "Петров", "1955-06")))
            assertEquals(listOf("Имя", "Фамилия", "Дата"), files.sources(file).single().columns.map { it.label })
            val preview = importer.preview(file, options)
            assertEquals(1, preview.ready)
            assertEquals(1, preview.duplicates)
            assertEquals(0, preview.invalid)
            assertEquals(ImportResult(1, 1), importer.apply(file, confirmation(preview)))
            assertEquals("1955-06", persons.search(name).items.single { it.firstName == name }.birthDate)
            assertEquals(existing, persons.details(existing.id).person)
        }
    }

    @Test
    fun `imports Access MDB and ACCDB from a selected table`() {
        for ((name, format) in listOf("older.mdb" to Database.FileFormat.V2000, "current.accdb" to Database.FileFormat.V2010)) {
            val path = directory.resolve(name)
            DatabaseBuilder.create(format, path.toFile()).use { database ->
                val table = TableBuilder("People")
                    .addColumn(ColumnBuilder("Given", DataType.TEXT))
                    .addColumn(ColumnBuilder("Surname", DataType.TEXT))
                    .addColumn(ColumnBuilder("Born", DataType.SHORT_DATE_TIME))
                    .toTable(database)
                table.addRow(name, "Северова", LocalDateTime.of(1982, 6, 19, 12, 0))
            }
            val file = MockMultipartFile("file", name, "application/octet-stream", Files.readAllBytes(path))
            assertEquals(listOf("People"), files.sources(file).map { it.name })
            val preview = importer.preview(file, options)
            assertEquals(1, preview.ready)
            assertEquals(ImportResult(1, 0), importer.apply(file, confirmation(preview)))
            assertEquals("1982-06-19", persons.search(name).items.single().birthDate)
        }
    }

    @Test
    fun `refuses invalid rows and stale preview when duplicate candidates change`() {
        val invalid = excel("invalid.xlsx", XSSFWorkbook(), listOf(listOf("Anna", "", "1950-02-30"), listOf("Jordan", "", "1950")))
        val invalidPreview = importer.preview(invalid, options)
        assertEquals(1, invalidPreview.invalid)
        assertThrows<InvalidInput> { importer.apply(invalid, confirmation(invalidPreview)) }
        assertTrue(persons.search("").items.isEmpty())

        val valid = excel("valid.xlsx", XSSFWorkbook(), listOf(listOf("Anna", "", "1950")))
        val preview = importer.preview(valid, options)
        persons.create(formPersonRq("Anna", null).copy(birthDate = "1950"))
        assertEquals("IMPORT_PREVIEW_CHANGED", assertThrows<ApiFailure> {
            importer.apply(valid, confirmation(preview))
        }.code)
        assertEquals(1, persons.search("").items.size)
    }

    @Test
    fun `GEDCOM exports reciprocal family links partial dates and warns about lossy relationships`() {
        val first = persons.create(formPersonRq("Анна", "Северова").copy(birthDate = "1950-05", biography = "Строка 1\n0 TRLR"))
        val second = persons.create(formPersonRq("Саша"))
        val child = persons.create(formPersonRq("Маша"))
        family.addParentChild(ParentChildRequest(first.id, child.id, ParentChildType.BIOLOGICAL))
        family.addParentChild(ParentChildRequest(second.id, child.id, ParentChildType.ADOPTIVE))
        family.addParentChild(ParentChildRequest(second.id, child.id, ParentChildType.STEP))
        family.addPartnership(PartnershipRequest(first.id, second.id, PartnershipType.MARRIAGE, PartnershipStatus.ENDED, "1970", "1980"))
        val file = exporter.export()
        assertTrue(file.content.startsWith("0 HEAD\n1 GEDC\n2 VERS 7.0\n"))
        assertTrue(file.content.endsWith("0 TRLR\n"))
        assertEquals(1, file.content.lineSequence().count { it == "0 TRLR" })
        assertTrue(file.content.contains("2 CONT 0 TRLR"))
        assertTrue(file.content.contains("2 DATE MAY 1950"))
        assertTrue(file.content.contains("1 UID ${first.id}"))
        assertTrue(file.content.contains("2 PEDI ADOPTED"))
        assertTrue(file.content.contains("3 PHRASE Stepchild"))
        assertFalse(file.content.contains("1 DIV"))
        assertTrue(file.warnings.containsAll(listOf("STEP_USES_PEDI_PHRASE", "UNION_STATUS_IN_NOTES")))
        for (line in file.content.lines().filter { it.startsWith("1 FAMC ") || it.startsWith("1 FAMS ") }) {
            assertTrue(file.content.contains("0 ${line.substringAfterLast(' ')} FAM"))
        }
    }

    private fun confirmation(preview: ImportPreview) = ImportConfirmation(options.source, options.mapping, preview.fingerprint)

    private fun excel(name: String, workbook: Workbook, rows: List<List<String>>): MockMultipartFile {
        val content = ByteArrayOutputStream()
        workbook.use {
            val sheet = it.createSheet("People")
            val values = listOf(listOf("Имя", "Фамилия", "Дата")) + rows
            values.forEachIndexed { row, cells ->
                val output = sheet.createRow(row)
                cells.forEachIndexed { column, value -> output.createCell(column).setCellValue(value) }
            }
            it.write(content)
        }
        return MockMultipartFile("file", name, "application/octet-stream", content.toByteArray())
    }
}
