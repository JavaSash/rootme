package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.Conflict
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.NotFoundException
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.model.api.AlternativeNameFields
import ru.stepenko.rootme.model.api.toDto
import ru.stepenko.rootme.model.entity.PersonAlternativeName
import ru.stepenko.rootme.repository.AlternativeNameRepository
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.applyPatch
import java.util.UUID

@Service
class AlternativeNameService(
    private val persons: PersonRepository,
    private val names: AlternativeNameRepository,
    private val json: ObjectMapper,
) {
    @Transactional
    fun create(personId: UUID, fields: AlternativeNameFields): ru.stepenko.rootme.model.api.AlternativeNameDto {
        lockPerson(personId)
        val name = validated(personId, fields, null)
        return names.save(PersonAlternativeName(personId = personId, name = name, type = fields.type)).toDto()
    }

    @Transactional
    fun update(personId: UUID, id: UUID, patch: JsonNode): ru.stepenko.rootme.model.api.AlternativeNameDto {
        lockPerson(personId)
        val name = requireName(personId, id)
        val fields = json.applyPatch(AlternativeNameFields(name.name, name.type), patch)
        name.name = validated(personId, fields, id)
        name.type = fields.type
        return name.toDto()
    }

    @Transactional
    fun delete(personId: UUID, id: UUID) {
        lockPerson(personId)
        names.delete(requireName(personId, id))
    }

    private fun validated(personId: UUID, fields: AlternativeNameFields, id: UUID?): String {
        val name = fields.name.trim().replace(Regex("(?U)\\s+"), " ").trim()
        if (name.isBlank() || name.contains('\u0000') || name.codePointCount(0, name.length) > 255) {
            throw InvalidInput("name", "Use a nonblank name of up to 255 characters.")
        }
        if (names.matchingIds(personId, fields.type, name).any { it != id }) {
            throw Conflict("DUPLICATE_ALTERNATIVE_NAME", "An alternative name of this type already exists.")
        }
        return name
    }

    private fun lockPerson(id: UUID) {
        persons.findForUpdate(id) ?: throw PersonNotFoundException("Person $id was not found.")
    }

    private fun requireName(personId: UUID, id: UUID) =
        names.findByIdAndPersonId(id, personId) ?: throw NotFoundException("Alternative name was not found.")
}
