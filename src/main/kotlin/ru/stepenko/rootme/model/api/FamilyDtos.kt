package ru.stepenko.rootme.model.api

import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.PartnershipStatus
import ru.stepenko.rootme.model.entity.PartnershipType
import ru.stepenko.rootme.model.person.Gender
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import java.time.Instant
import java.util.UUID

data class PersonSummary(
    val id: UUID,
    val firstName: String,
    val lastName: String?,
    val patronymic: String?,
    val gender: Gender,
    val birthDate: String?,
    val deathDate: String?,
)

data class ParentChildRequest(val parentId: UUID, val childId: UUID, val type: ParentChildType)

data class ParentChildDto(
    val id: UUID,
    val parentId: UUID,
    val childId: UUID,
    val type: ParentChildType,
    val createdAt: Instant,
)

data class PartnershipFields(
    val type: PartnershipType,
    val status: PartnershipStatus,
    val startDate: String? = null,
    val endDate: String? = null,
    val allowRepeat: Boolean = false,
)

data class PartnershipRequest(
    val person1Id: UUID,
    val person2Id: UUID,
    val type: PartnershipType,
    val status: PartnershipStatus,
    val startDate: String? = null,
    val endDate: String? = null,
    val allowRepeat: Boolean = false,
) {
    fun fields() = PartnershipFields(type, status, startDate, endDate, allowRepeat)
}

data class PartnershipDto(
    val id: UUID,
    val person1Id: UUID,
    val person2Id: UUID,
    val type: PartnershipType,
    val status: PartnershipStatus,
    val startDate: String?,
    val endDate: String?,
)

enum class RelativeRole { PARENT, CHILD, PARTNER }

data class RelativeRequest(
    val role: RelativeRole,
    val person: PersonRq? = null,
    val existingPersonId: UUID? = null,
    val parentChildType: ParentChildType? = null,
    val partnership: PartnershipFields? = null,
)

data class RelativeDto(
    val person: PersonDto,
    val parentChild: ParentChildDto? = null,
    val partnership: PartnershipDto? = null,
)

data class PersonDetailsDto(
    val person: PersonDto,
    val relatives: List<PersonSummary>,
    val parentChildEdges: List<ParentChildDto>,
    val partnerships: List<PartnershipDto>,
)

data class HiddenBranches(
    val parents: Boolean = false,
    val children: Boolean = false,
    val stepParents: Boolean = false,
    val stepChildren: Boolean = false,
    val partners: Boolean = false,
)

data class TreeDto(
    val rootPersonId: UUID,
    val nodes: List<PersonSummary>,
    val parentChildEdges: List<ParentChildDto>,
    val partnerships: List<PartnershipDto>,
    val hiddenBranches: Map<UUID, HiddenBranches>,
)
