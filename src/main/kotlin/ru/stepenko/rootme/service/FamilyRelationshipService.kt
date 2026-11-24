package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.Conflict
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.NotFoundException
import ru.stepenko.rootme.model.api.*
import ru.stepenko.rootme.model.entity.ParentChildRelationship
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.Partnership
import ru.stepenko.rootme.model.entity.PartnershipStatus
import ru.stepenko.rootme.model.person.DatePrecision
import ru.stepenko.rootme.model.person.PartialDate
import ru.stepenko.rootme.repository.ParentChildRepository
import ru.stepenko.rootme.repository.PartnershipRepository
import ru.stepenko.rootme.util.applyPatch
import ru.stepenko.rootme.util.toDto
import ru.stepenko.rootme.util.toFields
import java.util.UUID

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
class FamilyRelationshipService(
    private val persons: PersonService,
    private val parentChild: ParentChildRepository,
    private val partnerships: PartnershipRepository,
    private val graphLock: FamilyGraphLock,
    private val objectMapper: ObjectMapper,
) {
    fun addParentChild(request: ParentChildRequest): ParentChildDto {
        graphLock.lock()
        return createParentChild(request.parentId, request.childId, request.type)
    }

    fun deleteParentChild(id: UUID) {
        graphLock.lock()
        val edge = parentChild.findById(id)
            .orElseThrow { NotFoundException("Parent-child relationship $id was not found.") }
        parentChild.delete(edge)
    }

    fun addPartnership(request: PartnershipRequest): PartnershipDto {
        graphLock.lock()
        return createPartnership(request.person1Id, request.person2Id, request.fields())
    }

    fun updatePartnership(id: UUID, patch: JsonNode): PartnershipDto {
        graphLock.lock()
        val union = requirePartnership(id)
        applyPartnershipFields(union, objectMapper.applyPatch(union.toFields(), patch))
        return union.toDto()
    }

    fun deletePartnership(id: UUID) {
        graphLock.lock()
        partnerships.delete(requirePartnership(id))
    }

    fun addRelative(id: UUID, request: RelativeRequest): RelativeDto {
        if ((request.person == null) == (request.existingPersonId == null)) {
            throw InvalidInput("person", "Provide exactly one of person or existingPersonId.")
        }
        val isPartner = request.role == RelativeRole.PARTNER
        if (isPartner && (request.partnership == null || request.parentChildType != null)) {
            throw InvalidInput("partnership", "Provide partnership fields for this role.")
        }
        if (!isPartner && (request.parentChildType == null || request.partnership != null)) {
            throw InvalidInput("parentChildType", "Provide a parent-child type for this role.")
        }
        graphLock.lock()
        persons.requirePerson(id)
        val relative = request.person?.let { persons.create(it) }
            ?: persons.requirePerson(requireNotNull(request.existingPersonId)).toDto()
        return when (request.role) {
            RelativeRole.PARENT -> RelativeDto(
                relative, parentChild = createParentChild(relative.id, id, requireNotNull(request.parentChildType)),
            )
            RelativeRole.CHILD -> RelativeDto(
                relative, parentChild = createParentChild(id, relative.id, requireNotNull(request.parentChildType)),
            )
            RelativeRole.PARTNER -> RelativeDto(
                relative, partnership = createPartnership(id, relative.id, requireNotNull(request.partnership)),
            )
        }
    }

    private fun createParentChild(parentId: UUID, childId: UUID, type: ParentChildType): ParentChildDto {
        if (parentId == childId) throw InvalidInput("childId", "A person cannot be their own parent.")
        persons.requirePerson(parentId)
        persons.requirePerson(childId)
        if (parentChild.existsByParentIdAndChildIdAndType(parentId, childId, type)) {
            throw Conflict("DUPLICATE_PARENT_CHILD", "This parent-child relationship already exists.")
        }
        if (type != ParentChildType.STEP && parentChild.canReach(childId, parentId)) {
            throw Conflict("ANCESTRY_CYCLE", "This relationship would create an ancestry cycle.")
        }
        return parentChild.save(ParentChildRelationship(parentId = parentId, childId = childId, type = type)).toDto()
    }

    private fun createPartnership(firstId: UUID, secondId: UUID, fields: PartnershipFields): PartnershipDto {
        if (firstId == secondId) throw InvalidInput("person2Id", "A person cannot be their own partner.")
        persons.requirePerson(firstId)
        persons.requirePerson(secondId)
        // Canonical UUID strings follow PostgreSQL's unsigned byte order.
        val (person1Id, person2Id) = listOf(firstId, secondId).sortedBy(UUID::toString)
        val union = Partnership(
            person1Id = person1Id, person2Id = person2Id, type = fields.type, status = fields.status,
            startDate = null, endDate = null,
        )
        applyPartnershipFields(union, fields)
        return partnerships.save(union).toDto()
    }

    private fun applyPartnershipFields(union: Partnership, fields: PartnershipFields) {
        val start = PartialDate.parse("startDate", fields.startDate)
        val end = PartialDate.parse("endDate", fields.endDate)
        PartialDate.validateOrder(start, end, "endDate")

        val changed = union.id == null || union.type != fields.type || union.status != fields.status ||
            union.startDate != start || union.endDate != end
        if (changed) {
            val exactMatch = partnerships.findByPerson1IdAndPerson2Id(union.person1Id, union.person2Id).any {
                it.id != union.id && it.type == fields.type && it.status == fields.status &&
                    it.startDate == start && it.endDate == end
            }
            val canConfirmRepeat = fields.status != PartnershipStatus.ACTIVE &&
                (start?.precision != DatePrecision.DAY || end?.precision != DatePrecision.DAY)
            if (exactMatch && !(canConfirmRepeat && fields.allowRepeat)) {
                val detail = if (canConfirmRepeat) {
                    "A matching partnership exists. Confirm allowRepeat for a distinct historical union."
                } else {
                    "A matching partnership already exists."
                }
                throw Conflict("DUPLICATE_PARTNERSHIP", detail)
            }
        }

        union.type = fields.type
        union.status = fields.status
        union.startDate = start
        union.endDate = end
    }

    private fun requirePartnership(id: UUID): Partnership =
        partnerships.findById(id).orElseThrow { NotFoundException("Partnership $id was not found.") }
}
