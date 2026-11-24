package ru.stepenko.rootme.service

import com.healthmarketscience.jackcess.DataType
import com.healthmarketscience.jackcess.Database
import com.healthmarketscience.jackcess.Row
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.api.PartnershipRequest
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.PartnershipStatus
import ru.stepenko.rootme.model.entity.PartnershipType
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PartialDate
import ru.stepenko.rootme.model.person.PersonRq
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

data class GenealogyMdbPreview(
    val format: String,
    val fingerprint: String,
    val persons: Int,
    val parentChildRelationships: Int,
    val partnerships: Int,
    val photos: Int,
    val targetEmpty: Boolean,
    val missingGivenNames: Int,
    val invalidRecords: Int,
    val unresolvedReferences: Int,
    val errors: List<String>,
    val warnings: List<String>,
)

data class GenealogyMdbConfirmation(val fingerprint: String, val allowUnnamedPersons: Boolean = false)
data class GenealogyMdbResult(val persons: Int, val parentChildRelationships: Int, val partnerships: Int, val rootPersonId: UUID)

@Service
class GenealogyMdbImport(
    private val files: ImportFiles,
    private val persons: PersonService,
    private val relationships: FamilyRelationshipService,
    private val graphLock: FamilyGraphLock,
    private val jdbc: JdbcTemplate,
) {
    fun preview(file: MultipartFile): GenealogyMdbPreview = read(file).preview(fingerprint(file), targetEmpty())

    @Transactional
    fun apply(file: MultipartFile, confirmation: GenealogyMdbConfirmation): GenealogyMdbResult {
        if (fingerprint(file) != confirmation.fingerprint) throw ApiFailure(
            HttpStatus.CONFLICT, "IMPORT_PREVIEW_CHANGED", "The source changed. Preview the import again.",
        )
        val plan = read(file)
        if (plan.errors.isNotEmpty() || plan.people.isEmpty()) throw InvalidInput("file", "Resolve the preview errors before importing.")
        if (plan.missingNames > 0 && !confirmation.allowUnnamedPersons) throw ApiFailure(
            HttpStatus.CONFLICT, "IMPORT_UNNAMED_PERSONS_CONFIRMATION_REQUIRED",
            "Confirm using an explicit unknown-name label for source persons without a given name.",
        )
        graphLock.lock()
        jdbc.execute("LOCK TABLE persons IN SHARE ROW EXCLUSIVE MODE")
        if (!targetEmpty()) throw ApiFailure(
            HttpStatus.CONFLICT, "IMPORT_TARGET_NOT_EMPTY", "A genealogy MDB import requires an empty RootMe database.",
        )
        val ids = plan.people.mapValues { (_, person) -> persons.create(person).id }
        for (edge in plan.edges) relationships.addParentChild(
            ParentChildRequest(ids.getValue(edge.parent), ids.getValue(edge.child), edge.type),
        )
        for (union in plan.unions) relationships.addPartnership(
            PartnershipRequest(
                ids.getValue(union.first), ids.getValue(union.second), union.type, union.status,
                union.start, union.end, allowRepeat = true,
            ),
        )
        val root = plan.edges.groupingBy { it.parent }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key ?: ids.keys.min()
        return GenealogyMdbResult(ids.size, plan.edges.size, plan.unions.size, ids.getValue(root))
    }

    private fun targetEmpty() = jdbc.queryForObject("SELECT NOT EXISTS (SELECT 1 FROM persons)", Boolean::class.java) == true

    private fun fingerprint(file: MultipartFile): String {
        if (file.isEmpty || file.size > 20L * 1024 * 1024) throw InvalidInput("file", "Choose a file of at most 20 MiB.")
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(FORMAT.toByteArray(Charsets.UTF_8))
        DigestInputStream(file.inputStream, digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun read(file: MultipartFile): Plan = files.genealogyMdb(file) { db ->
        val source = Source(db)
        val plan = Plan()
        val dates = source.rows("DATES").associateBy { it.number("DATE_ID") }
        val families = source.rows("FAM").associateBy { it.number("FAM_ID") }
        val childTypes = source.dictionary("CHILD_TYPE")
        val familyTypes = source.dictionary("FAM_TYPE")
        val places = source.rows("PLACE").associateBy { it.number("PLACE_ID") }
        for (row in source.rows("PERS")) {
            val id = row.number("PERS_ID")
            plan.validate("PERS", id) {
                val given = row.text("GIVN")
                if (given == null) plan.missingNames++
                val birth = readDate(row, "BIRTH_DATE_ID", dates, plan)
                val death = readDate(row, "DEAD_DATE_ID", dates, plan)
                val request = PersonRq(
                    firstName = given ?: "Имя неизвестно",
                    lastName = row.text("SURN"),
                    patronymic = row.text("MIDN"),
                    gender = when (row.text("SEX")) {
                        "M" -> Gender.MALE
                        "F" -> Gender.FEMALE
                        null -> Gender.UNKNOWN
                        else -> throw InvalidInput("gender", "Unrecognized source gender code.")
                    },
                    birthDate = birth.iso,
                    deathDate = death.iso,
                    notes = listOfNotNull(
                        row.text("NOTES"),
                        birth.original?.let { "MDB birth date: $it" },
                        death.original?.let { "MDB death date: $it" },
                        readPlace(row, "BIRTH_PLACE_ID", places, plan)?.let { "MDB birthplace: $it" },
                        readPlace(row, "DEAD_PLACE_ID", places, plan)?.let { "MDB place of death: $it" },
                    ).joinToString("\n").ifBlank { null },
                )
                persons.validateNew(request)
                plan.people[id] = request
            }
        }
        for ((id, row) in families) plan.validate("FAM", id) {
            val participants = participants(row, plan)
            if (participants.size != 2) return@validate
            val type = when (familyTypes[row.number("TYPE_ID")]) {
                "зарегистрированный" -> PartnershipType.MARRIAGE
                "не зарегистрированный", "незарегистрированный" -> PartnershipType.PARTNERSHIP
                else -> throw InvalidInput("type", "Unrecognized family type.")
            }
            val status = if (row["IS_RAZVOD"] == true) PartnershipStatus.ENDED else PartnershipStatus.UNKNOWN
            val start = readDate(row, "DATE_START_ID", dates, plan)
            val end = readDate(row, "DATE_RAZ_ID", dates, plan)
            if (start.original != null || end.original != null) {
                plan.warnings += "UNSUPPORTED_UNION_DATES"
            }
            PartialDate.validateOrder(PartialDate.parse("startDate", start.iso), PartialDate.parse("endDate", end.iso), "endDate")
            val (first, second) = participants.sorted()
            val union = Union(first, second, type, status, start.iso, end.iso)
            if (union in plan.unions && start.iso?.length == 10 && end.iso?.length == 10) {
                throw InvalidInput("partnership", "Duplicate union with identical complete dates.")
            }
            plan.unions += union
        }
        for (row in source.rows("CHILD")) plan.validate("CHILD", row.number("CHILD_ID")) {
            val child = row.number("PERS_ID")
            if (child !in plan.people) plan.unresolved("Missing or invalid child person.")
            val family = families[row.number("FAM_ID")] ?: plan.unresolved("Missing family for child.")
            val parents = participants(family, plan)
            if (parents.isEmpty()) plan.unresolved("No known parent for child.")
            val edges = parents.map { parent ->
                if (parent == child) throw InvalidInput("child", "A person cannot be their own parent.")
                val roleColumn = if (parent == family.number("PERS1_ID")) "TYPE1_ID" else "TYPE2_ID"
                val code = row.number(roleColumn).takeIf { it > 0 } ?: row.number("TYPE_ID")
                val type = when (childTypes[code]) {
                    "родной" -> ParentChildType.BIOLOGICAL
                    "приемный", "приёмный" -> ParentChildType.ADOPTIVE
                    else -> throw InvalidInput("type", "Unrecognized parent-child type.")
                }
                Edge(parent, child, type)
            }
            if (edges.any { it in plan.edges }) plan.warnings += "DUPLICATE_PARENT_CHILD_LINKS"
            plan.edges.addAll(edges)
        }
        if (plan.hasCycle()) plan.error("An ancestry cycle exists in the source.")
        if (plan.people.isEmpty()) plan.error("The source contains no valid persons.")
        if (plan.missingNames > 0) plan.warnings += "UNNAMED_PERSONS"
        plan.warnings += "UNSUPPORTED_SOURCE_DATA"
        plan
    }

    private fun participants(row: Row, plan: Plan): List<Int> {
        val ids = listOf(row.number("PERS1_ID"), row.number("PERS2_ID")).filter { it > 0 }
        if (ids.size != ids.distinct().size) throw InvalidInput("family", "A family refers to the same person twice.")
        if (ids.any { it !in plan.people }) plan.unresolved("Missing or invalid family participant.")
        return ids
    }

    private fun readDate(row: Row, field: String, dates: Map<Int, Row>, plan: Plan): SourceDate {
        val id = row.number(field).takeIf { it > 0 } ?: return SourceDate()
        val date = dates[id] ?: plan.unresolved("Missing date reference in $field.")
        val year = date.number("Y1")
        val month = date.number("M1")
        val day = date.number("D1")
        if (date.number("STYLE_ID") != 0 || date.number("TYPE_ID") != 1 || date.number("TYPE_D1") != 1 ||
            date.number("Y2") > 0 || year !in 1..9999 || (day > 0 && month <= 0)
        ) {
            plan.warnings += "UNSUPPORTED_DATE_PRECISION"
            return SourceDate(original = date.text("DATE_STR") ?: "unrepresentable source date")
        }
        val iso = buildString {
            append(year.toString().padStart(4, '0'))
            if (month > 0) append("-" + month.toString().padStart(2, '0'))
            if (day > 0) append("-" + day.toString().padStart(2, '0'))
        }
        return SourceDate(iso = PartialDate.parse(field, iso)?.toIso())
    }

    private fun readPlace(row: Row, field: String, places: Map<Int, Row>, plan: Plan): String? {
        val id = row.number(field).takeIf { it > 0 } ?: return null
        val place = places[id] ?: plan.unresolved("Missing place reference in $field.")
        return place.text("LONG_NAME") ?: place.text("SHORT_NAME")
    }

    private data class SourceDate(val iso: String? = null, val original: String? = null)
    private data class Edge(val parent: Int, val child: Int, val type: ParentChildType)
    private data class Union(
        val first: Int, val second: Int, val type: PartnershipType, val status: PartnershipStatus,
        val start: String?, val end: String?,
    )

    private class Plan {
        val people = linkedMapOf<Int, PersonRq>()
        val edges = linkedSetOf<Edge>()
        val unions = mutableListOf<Union>()
        val errors = mutableListOf<String>()
        val warnings = linkedSetOf<String>()
        var missingNames = 0
        var invalid = 0
        var unresolved = 0

        fun error(message: String) {
            invalid++
            if (errors.size < 100) errors += message
        }

        fun validate(table: String, id: Int, action: () -> Unit) {
            try {
                if (id <= 0) throw InvalidInput("id", "Expected a positive source identifier.")
                action()
            } catch (failure: InvalidInput) {
                error("$table / $id: ${failure.message}")
            }
        }

        fun unresolved(message: String): Nothing {
            unresolved++
            throw InvalidInput("reference", message)
        }

        fun hasCycle(): Boolean {
            val incoming = people.keys.associateWith { 0 }.toMutableMap()
            val children = edges.groupBy { it.parent }
            edges.forEach { incoming[it.child] = incoming.getValue(it.child) + 1 }
            val ready = ArrayDeque(incoming.filterValues { it == 0 }.keys)
            var visited = 0
            while (ready.isNotEmpty()) {
                val parent = ready.removeFirst()
                visited++
                for (edge in children[parent].orEmpty()) {
                    val count = incoming.getValue(edge.child) - 1
                    incoming[edge.child] = count
                    if (count == 0) ready.addLast(edge.child)
                }
            }
            return visited != people.size
        }

        fun preview(fingerprint: String, targetEmpty: Boolean) = GenealogyMdbPreview(
            FORMAT, fingerprint, people.size, edges.size, unions.size, 0, targetEmpty,
            missingNames, invalid, unresolved, errors, warnings.toList(),
        )
    }

    private class Source(private val db: Database) {
        fun rows(name: String): List<Row> {
            if (name !in db.tableNames || db.getTableMetaData(name)?.isLinked != false) unsupported()
            val table = db.getTable(name)
            val columns = table.columns.associate { it.name to it.type }
            if (schema.getValue(name).any { (column, type) -> columns[column] != type }) unsupported()
            val id = keys.getValue(name)
            if (table.indexes.none { it.isPrimaryKey && it.columns.map { column -> column.name } == listOf(id) }) unsupported()
            if (table.rowCount > 5_000) throw InvalidInput("file", "A genealogy table exceeds 5,000 rows.")
            return table.toList()
        }

        fun dictionary(name: String) = rows(name).associate { it.number("${name}_ID") to it.text("${name}_NAME")?.lowercase() }
    }

    private companion object {
        const val FORMAT = "ROOTME_GENEALOGY_MDB_V1"

        fun Row.number(column: String) = (this[column] as? Number)?.toInt() ?: 0
        fun Row.text(column: String) = (this[column] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        fun columns(ids: String, text: String = "") =
            ids.split(' ').associateWith { DataType.LONG } +
                text.split(' ').filter { it.isNotBlank() }.associateWith { DataType.TEXT }

        val keys = mapOf(
            "PERS" to "PERS_ID", "FAM" to "FAM_ID", "CHILD" to "CHILD_ID", "DATES" to "DATE_ID",
            "PLACE" to "PLACE_ID", "CHILD_TYPE" to "CHILD_TYPE_ID", "FAM_TYPE" to "FAM_TYPE_ID",
        )
        val schema = mapOf(
            "PERS" to (columns("PERS_ID BIRTH_DATE_ID DEAD_DATE_ID BIRTH_PLACE_ID DEAD_PLACE_ID", "GIVN SURN MIDN SEX") +
                ("NOTES" to DataType.MEMO)),
            "FAM" to (columns("FAM_ID PERS1_ID PERS2_ID TYPE_ID DATE_START_ID DATE_RAZ_ID") + ("IS_RAZVOD" to DataType.BOOLEAN)),
            "CHILD" to columns("CHILD_ID FAM_ID PERS_ID TYPE1_ID TYPE2_ID TYPE_ID"),
            "DATES" to columns("DATE_ID STYLE_ID TYPE_ID TYPE_D1 Y1 M1 D1 Y2", "DATE_STR"),
            "PLACE" to columns("PLACE_ID", "LONG_NAME SHORT_NAME"),
            "CHILD_TYPE" to columns("CHILD_TYPE_ID", "CHILD_TYPE_NAME"),
            "FAM_TYPE" to columns("FAM_TYPE_ID", "FAM_TYPE_NAME"),
        )

        fun unsupported(): Nothing = throw ApiFailure(
            HttpStatus.UNPROCESSABLE_ENTITY, "GENEALOGY_MDB_SCHEMA_UNSUPPORTED", "The MDB genealogy schema is not supported.",
        )
    }
}
