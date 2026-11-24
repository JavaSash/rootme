package ru.stepenko.rootme.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.model.api.HiddenBranches
import ru.stepenko.rootme.model.api.TreeDto
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.ParentChildType.*
import ru.stepenko.rootme.repository.ParentChildRepository
import ru.stepenko.rootme.repository.PartnershipRepository
import ru.stepenko.rootme.repository.PersonRepository
import ru.stepenko.rootme.util.toDto
import java.util.UUID

@Service
class FamilyTreeService(
    private val personService: PersonService,
    private val persons: PersonRepository,
    private val parentChild: ParentChildRepository,
    private val partnerships: PartnershipRepository,
    private val summaries: PersonSummaries,
    @Value("\${rootme.tree.max-depth:10}") private val maxDepth: Int,
) {
    private val ancestry = setOf(BIOLOGICAL, ADOPTIVE)
    private val stepOnly = setOf(STEP)
    private val maxNodes = 1000
    private val maxEdges = 5000
    private val edgePage = PageRequest.of(0, maxEdges + 1)

    init {
        require(maxDepth >= 5) { "rootme.tree.max-depth must be at least the default depth of 5." }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun getTree(root: UUID, ancestors: Int, descendants: Int): TreeDto {
        validateDepth("ancestors", ancestors)
        validateDepth("descendants", descendants)
        personService.requirePerson(root)

        val above = traverse(root, ancestors) { parentsOf(it, ancestry) }
        val below = traverse(root, descendants) { childrenOf(it, ancestry) }
        val core = above.keys + below.keys
        val visible = core.toMutableSet()
        checkSize(visible.size, maxNodes)

        if (ancestors > 0 && descendants > 0) {
            val siblingParents = parentsOf(core, ancestry)
                .filter { it in above || (below[it] ?: descendants) < descendants }
                .toSet()
            visible.addAll(childrenOf(siblingParents, ancestry))
        }
        val descendantIds = below.keys - root
        visible.addAll(parentsOf(descendantIds, ancestry))
        visible.addAll(parentsOf(above.filterValues { it < ancestors }.keys, stepOnly))
        visible.addAll(childrenOf(below.filterValues { it < descendants }.keys, stepOnly))
        checkSize(visible.size, maxNodes)

        val directPartners = bounded(partnerships.findByPerson1IdInOrPerson2IdIn(visible, visible, edgePage))
        directPartners.forEach {
            visible.add(it.person1Id)
            visible.add(it.person2Id)
        }
        checkSize(visible.size, maxNodes)

        val edges = bounded(parentChild.findByParentIdInAndChildIdIn(visible, visible, edgePage))
        val unions = bounded(partnerships.findByPerson1IdInAndPerson2IdIn(visible, visible, edgePage))
        return TreeDto(
            rootPersonId = root,
            nodes = summaries.forPeople(persons.findAllById(visible)).sortedBy { it.id },
            parentChildEdges = edges.map { it.toDto() }.sortedBy { it.id },
            partnerships = unions.map { it.toDto() }.sortedBy { it.id },
            hiddenBranches = hiddenBranches(visible, above, below, ancestors, descendants),
            maxDepth = maxDepth,
            ancestorDepth = ancestors,
            descendantDepth = descendants,
        )
    }

    private fun hiddenBranches(
        visible: Set<UUID>,
        above: Map<UUID, Int>,
        below: Map<UUID, Int>,
        ancestors: Int,
        descendants: Int,
    ): Map<UUID, HiddenBranches> {
        val parents = parentChild.childrenWithHiddenParents(visible, ancestry)
        val children = parentChild.parentsWithHiddenChildren(visible, ancestry)
        val stepParents = parentChild.childrenWithHiddenParents(visible, stepOnly)
        val stepChildren = parentChild.parentsWithHiddenChildren(visible, stepOnly)
        val partners = partnerships.firstParticipantsWithHiddenPartners(visible) +
            partnerships.secondParticipantsWithHiddenPartners(visible)
        return (parents + children + stepParents + stepChildren + partners).sorted().associateWith { id ->
            // A missing direct neighbor of a frontier node will enter the next traversal.
            // Contextual nodes have no frontier depth in the corresponding direction.
            HiddenBranches(
                parents = id in parents,
                children = id in children,
                stepParents = id in stepParents,
                stepChildren = id in stepChildren,
                partners = id in partners,
                nextAncestorDepth = (ancestors + 1).takeIf {
                    ancestors < maxDepth && above[id] == ancestors && (id in parents || id in stepParents)
                },
                nextDescendantDepth = (descendants + 1).takeIf {
                    descendants < maxDepth && below[id] == descendants && (id in children || id in stepChildren)
                },
            )
        }
    }

    private fun traverse(
        root: UUID,
        depth: Int,
        neighbors: (Set<UUID>) -> List<UUID>,
    ): Map<UUID, Int> {
        val distance = mutableMapOf(root to 0)
        var frontier = setOf(root)
        for (level in 1..depth) {
            if (frontier.isEmpty()) break
            frontier = neighbors(frontier).filterNot(distance::containsKey).toSet()
            frontier.forEach { distance[it] = level }
            checkSize(distance.size, maxNodes)
        }
        return distance
    }

    private fun parentsOf(ids: Set<UUID>, types: Set<ParentChildType>): List<UUID> =
        if (ids.isEmpty()) emptyList() else bounded(
            parentChild.findByChildIdInAndTypeIn(ids, types, edgePage),
        ).map { it.parentId }

    private fun childrenOf(ids: Set<UUID>, types: Set<ParentChildType>): List<UUID> =
        if (ids.isEmpty()) emptyList() else bounded(
            parentChild.findByParentIdInAndTypeIn(ids, types, edgePage),
        ).map { it.childId }

    private fun validateDepth(field: String, value: Int) {
        if (value !in 0..maxDepth) throw InvalidInput(field, "Use a depth from 0 to $maxDepth.")
    }

    private fun <T> bounded(results: List<T>): List<T> {
        checkSize(results.size, maxEdges)
        return results
    }

    private fun checkSize(size: Int, limit: Int) {
        if (size > limit) throw InvalidInput("tree", "The tree is too wide. Select a different root or reduce depth.")
    }
}
