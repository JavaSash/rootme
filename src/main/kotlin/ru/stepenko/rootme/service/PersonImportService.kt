package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PersonRq
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale

data class ImportMapping(val source: String, val mapping: Map<String, Int>)
data class ImportConfirmation(val source: String, val mapping: Map<String, Int>, val fingerprint: String)
enum class ImportRowStatus { READY, DUPLICATE, INVALID }
data class ImportPreviewRow(val row: Int, val name: String, val status: ImportRowStatus, val message: String? = null)
data class ImportPreview(
    val fingerprint: String,
    val ready: Int,
    val duplicates: Int,
    val invalid: Int,
    val rows: List<ImportPreviewRow>,
    val total: Int,
)
data class ImportResult(val created: Int, val skippedDuplicates: Int)

@Service
class PersonImportService(
    private val files: ImportFiles,
    private val persons: PersonService,
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) {
    private data class Plan(val preview: ImportPreview, val ready: List<PersonRq>)

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun preview(file: MultipartFile, request: ImportMapping) = plan(file, request).preview

    @Transactional
    fun apply(file: MultipartFile, request: ImportConfirmation): ImportResult {
        jdbc.execute("LOCK TABLE persons IN SHARE ROW EXCLUSIVE MODE")
        val (preview, ready) = plan(file, ImportMapping(request.source, request.mapping))
        if (preview.fingerprint != request.fingerprint) {
            throw ApiFailure(HttpStatus.CONFLICT, "IMPORT_PREVIEW_CHANGED", "The file, mapping or duplicate candidates changed. Preview the import again.")
        }
        if (preview.invalid > 0 || ready.isEmpty()) throw InvalidInput("file", "Correct invalid rows and include at least one new person.")
        ready.forEach(persons::create)
        return ImportResult(ready.size, preview.duplicates)
    }

    private fun plan(file: MultipartFile, request: ImportMapping): Plan {
        if ("firstName" !in request.mapping || request.mapping.keys.any { it !in supportedFields }) {
            throw InvalidInput("mapping", "Map the first name and use supported profile fields.")
        }
        if (request.mapping.values.toSet().size != request.mapping.size) {
            throw InvalidInput("mapping", "Map each source column to at most one profile field.")
        }
        val sourceRows = files.rows(file, request.source, request.mapping.values.toSet())
        val existing = jdbc.query(
            "SELECT first_name, last_name, patronymic, birth_date, birth_date_precision FROM persons",
        ) { result, _ ->
            val date = result.getString("birth_date")
            val length = when (result.getString("birth_date_precision")) {
                "YEAR" -> 4
                "MONTH" -> 7
                else -> 10
            }
            identity(result.getString("first_name"), result.getString("last_name"), result.getString("patronymic"), date?.take(length))
        }.toMutableSet()
        val rows = mutableListOf<ImportPreviewRow>()
        val ready = mutableListOf<PersonRq>()
        val seen = mutableSetOf<List<String>>()
        for (row in sourceRows) {
            fun field(key: String) = request.mapping[key]?.let { row.values[it] }?.trim()?.takeIf { it.isNotEmpty() }
            val name = listOfNotNull(field("lastName"), field("firstName"), field("patronymic")).joinToString(" ").take(255)
            try {
                val person = PersonRq(
                    firstName = field("firstName").orEmpty(),
                    lastName = field("lastName"),
                    patronymic = field("patronymic"),
                    gender = parseGender(field("gender")),
                    birthDate = field("birthDate"),
                    deathDate = field("deathDate"),
                    birthCountry = field("birthCountry"),
                    birthRegion = field("birthRegion"),
                    birthCity = field("birthCity"),
                    birthSurname = field("birthSurname"),
                    notes = field("notes"),
                    biography = field("biography"),
                    deathCountry = field("deathCountry"),
                    deathRegion = field("deathRegion"),
                    deathCity = field("deathCity"),
                    occupation = field("occupation"),
                    burialPlace = field("burialPlace"),
                )
                persons.validateNew(person)
                val key = identity(person.firstName, person.lastName, person.patronymic, person.birthDate)
                val status = if (key in existing || !seen.add(key)) ImportRowStatus.DUPLICATE else ImportRowStatus.READY
                rows.add(ImportPreviewRow(row.number, name, status))
                if (status == ImportRowStatus.READY) ready.add(person)
            } catch (error: InvalidInput) {
                rows.add(ImportPreviewRow(row.number, name, ImportRowStatus.INVALID, error.message))
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream, digest).use { it.transferTo(java.io.OutputStream.nullOutputStream()) }
        digest.update(json.writeValueAsBytes(listOf(request.source, request.mapping.toSortedMap(), rows)))
        val fingerprint = HexFormat.of().formatHex(digest.digest())
        val preview = ImportPreview(
            fingerprint, ready.size, rows.count { it.status == ImportRowStatus.DUPLICATE },
            rows.count { it.status == ImportRowStatus.INVALID },
            rows.sortedBy { if (it.status == ImportRowStatus.INVALID) 0 else 1 }.take(100), rows.size,
        )
        return Plan(preview, ready)
    }

    private fun parseGender(value: String?) = when (value?.uppercase(Locale.ROOT)) {
        "MALE", "M", "М" -> Gender.MALE
        "FEMALE", "F", "Ж" -> Gender.FEMALE
        "UNKNOWN", null -> Gender.UNKNOWN
        else -> throw InvalidInput("gender", "Use MALE, FEMALE, UNKNOWN, M, F, М or Ж.")
    }

    private fun identity(vararg values: String?) = values.map { value ->
        value.orEmpty().trim().replace(whitespace, " ").lowercase(Locale.ROOT)
    }

    private companion object {
        val whitespace = Regex("(?U)\\s+")
        val supportedFields = setOf("firstName", "lastName", "patronymic", "gender", "birthDate", "deathDate", "birthCountry", "birthRegion", "birthCity", "birthSurname", "notes", "biography", "deathCountry", "deathRegion", "deathCity", "occupation", "burialPlace")
    }
}
