package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

data class GedcomFile(val content: String, val warnings: Set<String>)

@Service
class GedcomExport(private val snapshot: BackupSnapshot) {
    private data class Family(
        val ref: String,
        val parents: List<String>,
        val partnership: JsonNode? = null,
        val children: MutableMap<String, String> = linkedMapOf(),
    )

    fun export(): GedcomFile {
        val data = snapshot.read()
        val people = data.path("persons").associateBy { it.text("id")!! }
        val refs = people.keys.withIndex().associate { (index, id) -> id to "@I${index + 1}@" }
        val families = mutableListOf<Family>()
        val unionsByParents = linkedMapOf<List<String>, MutableList<Family>>()
        val ancestryByParents = mutableMapOf<Pair<List<String>, String>, Family>()
        val warnings = linkedSetOf<String>()
        val names = data.path("person_alternative_names").groupBy { it.text("person_id") }
        fun newFamily(parents: List<String>, partnership: JsonNode? = null) =
            Family("@F${families.size + 1}@", parents, partnership).also(families::add)

        for (union in data.path("partnerships")) {
            val parents = listOf(union.text("person1_id")!!, union.text("person2_id")!!).sorted()
            unionsByParents.getOrPut(parents) { mutableListOf() }.add(newFamily(parents, union))
            if (union.text("status") != "UNKNOWN" || union.text("end_date") != null) warnings += "UNION_STATUS_IN_NOTES"
        }
        val ancestry = data.path("parent_child_relationships").groupBy {
            it.text("child_id")!! to it.text("type")!!
        }
        for ((key, edges) in ancestry) {
            val (child, type) = key
            val parentIds = edges.map { it.text("parent_id")!! }.sorted()
            if (parentIds.size > 2) warnings += "MULTIPLE_PARENTS_SPLIT"
            if (type == "STEP") warnings += "STEP_USES_PEDI_PHRASE"
            val groups = if (parentIds.size > 2 || type == "STEP") parentIds.map { listOf(it) } else listOf(parentIds)
            for (parents in groups) {
                val unions = unionsByParents[parents].orEmpty()
                val single = unions.singleOrNull()
                if (unions.size > 1) warnings += "MULTIPLE_UNIONS_CHILDREN_SEPARATE"
                val family = if (type == "BIOLOGICAL" && single != null) single
                    else ancestryByParents.getOrPut(parents to type) { newFamily(parents) }
                family.children[child] = type
            }
        }
        if (!data.path("person_photos").isEmpty) warnings += "PHOTOS_EXCLUDED"
        val familiesByParent = families.flatMap { family -> family.parents.map { it to family } }.groupBy({ it.first }, { it.second })
        val familiesByChild = families.flatMap { family -> family.children.keys.map { it to family } }.groupBy({ it.first }, { it.second })

        val output = buildString {
            appendLine("0 HEAD")
            appendLine("1 GEDC")
            appendLine("2 VERS 7.0")
            appendLine("1 SOUR RootMe")
            for ((id, person) in people) {
                appendLine("0 ${refs.getValue(id)} INDI")
                value(1, "UID", id)
                val given = person.fields("first_name", "patronymic").joinToString(" ")
                val surname = person.text("last_name")
                value(1, "NAME", personalName(given, surname))
                person.text("birth_surname")?.let {
                    value(1, "NAME", personalName(given, it))
                    value(2, "TYPE", "BIRTH")
                }
                for (alias in names[id].orEmpty()) {
                    value(1, "NAME", alias.text("name")?.let(::namePart))
                    value(2, "NOTE", "RootMe name type: ${alias.text("type")}")
                }
                value(1, "SEX", when (person.text("gender")) { "MALE" -> "M"; "FEMALE" -> "F"; else -> "U" })
                event("BIRT", date(person, "birth_date"), person.fields("birth_city", "birth_region", "birth_country").joinToString(", "))
                event("DEAT", date(person, "death_date"), person.fields("death_city", "death_region", "death_country").joinToString(", "))
                event("BURI", null, person.text("burial_place"))
                value(1, "OCCU", person.text("occupation"))
                person.text("biography")?.let { value(1, "NOTE", "Biography\n$it") }
                value(1, "NOTE", person.text("notes"))
                for (family in familiesByParent[id].orEmpty()) appendLine("1 FAMS ${family.ref}")
                for (family in familiesByChild[id].orEmpty()) {
                    appendLine("1 FAMC ${family.ref}")
                    when (family.children.getValue(id)) {
                        "BIOLOGICAL" -> value(2, "PEDI", "BIRTH")
                        "ADOPTIVE" -> value(2, "PEDI", "ADOPTED")
                        else -> { value(2, "PEDI", "OTHER"); value(3, "PHRASE", "Stepchild") }
                    }
                }
            }
            for (family in families) {
                appendLine("0 ${family.ref} FAM")
                val participants = family.parents.sortedBy { if (people.getValue(it).text("gender") == "FEMALE") 1 else 0 }
                for ((index, id) in participants.withIndex()) {
                    val tag = if (index == 1 || participants.size == 1 && people.getValue(id).text("gender") == "FEMALE") "WIFE" else "HUSB"
                    appendLine("1 $tag ${refs.getValue(id)}")
                }
                for (id in family.children.keys) appendLine("1 CHIL ${refs.getValue(id)}")
                family.partnership?.let { union ->
                    value(1, "UID", union.text("id"))
                    val start = date(union, "start_date")
                    if (union.text("type") == "MARRIAGE") {
                        if (start == null) value(1, "MARR", "Y") else event("MARR", start, null)
                    } else {
                        appendLine("1 EVEN")
                        value(2, "TYPE", "Partnership")
                        value(2, "DATE", start)
                    }
                    if (union.text("status") != "UNKNOWN") value(1, "NOTE", "RootMe union status: ${union.text("status")}")
                    date(union, "end_date")?.let { value(1, "NOTE", "RootMe union end: $it") }
                }
            }
            appendLine("0 TRLR")
        }
        return GedcomFile(output, warnings)
    }

    private fun personalName(given: String, surname: String?) =
        if (surname == null) namePart(given) else "${namePart(given)} /${namePart(surname)}/"

    private fun namePart(value: String) = value.replace('/', '∕').replace(Regex("[\\r\\n]+"), " ")

    private fun date(row: JsonNode, field: String): String? {
        val parts = row.text(field)?.split('-') ?: return null
        val year = parts[0]
        if (row.text("${field}_precision") == "YEAR") return year
        val month = months[parts[1].toInt() - 1]
        return if (row.text("${field}_precision") == "MONTH") "$month $year" else "${parts[2].toInt()} $month $year"
    }

    private fun StringBuilder.event(tag: String, date: String?, place: String?) {
        if (date == null && place.isNullOrBlank()) return
        appendLine("1 $tag")
        value(2, "DATE", date)
        value(2, "PLAC", place)
    }

    private fun StringBuilder.value(level: Int, tag: String, text: String?) {
        if (text.isNullOrBlank()) return
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        fun escaped(line: String) = line.filterNot { it.code in 0..31 && it != '\t' }.let {
            if (it.startsWith('@')) "@$it" else it
        }
        appendLine("$level $tag${lines.first().takeIf { it.isNotEmpty() }?.let { " ${escaped(it)}" }.orEmpty()}")
        for (line in lines.drop(1)) {
            appendLine("${level + 1} CONT${line.takeIf { it.isNotEmpty() }?.let { " ${escaped(it)}" }.orEmpty()}")
        }
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf { it.isTextual }?.textValue()
    private fun JsonNode.fields(vararg fields: String) = fields.mapNotNull { text(it) }.filter { it.isNotBlank() }

    private companion object {
        val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    }
}
