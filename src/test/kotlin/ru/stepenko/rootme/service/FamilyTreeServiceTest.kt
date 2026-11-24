package ru.stepenko.rootme.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.PersonNotFoundException
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.api.PartnershipRequest
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.ParentChildType.*
import ru.stepenko.rootme.model.entity.PartnershipStatus.*
import ru.stepenko.rootme.model.entity.PartnershipType.MARRIAGE
import java.util.UUID

internal class FamilyTreeServiceTest(
    private val persons: PersonService,
    private val family: FamilyRelationshipService,
    private val trees: FamilyTreeService,
) : BaseIntegrationTest() {
    private fun person(name: String) = persons.create(formPersonRq(name)).id
    private fun edge(parent: UUID, child: UUID, type: ParentChildType = BIOLOGICAL) =
        family.addParentChild(ParentChildRequest(parent, child, type))

    @Test
    fun `two paths keep one ancestor node and both sets of edges`() {
        val root = person("Root")
        val left = person("Left")
        val right = person("Right")
        val common = person("Common ancestor")
        val outside = person("Earlier ancestor")
        val includedEdges = listOf(edge(left, root), edge(right, root), edge(common, left), edge(common, right, ADOPTIVE))
        edge(outside, common)

        val tree = trees.getTree(root, 2, 0)
        assertEquals(root, tree.rootPersonId)
        assertEquals(setOf(root, left, right, common), tree.nodes.map { it.id }.toSet())
        assertEquals(4, tree.nodes.size)
        assertEquals(includedEdges.map { it.id }.toSet(), tree.parentChildEdges.map { it.id }.toSet())
        assertTrue(tree.hiddenBranches.getValue(common).parents)
        assertFalse(tree.hiddenBranches.getValue(common).children)
    }

    @Test
    fun `partners siblings and other known parents are included without expanding partner families`() {
        val root = person("Root")
        val parent = person("Parent")
        val sibling = person("Sibling")
        val oldPartner = person("Former partner")
        val currentPartner = person("Current partner")
        val firstChild = person("First child")
        val secondChild = person("Second child")
        val partnerParent = person("Partner parent")
        val partnerChild = person("Partner child")
        val disconnected = person("Disconnected")
        edge(parent, root)
        edge(parent, sibling, ADOPTIVE)
        edge(root, firstChild)
        edge(root, secondChild)
        edge(oldPartner, firstChild)
        edge(currentPartner, secondChild)
        edge(partnerParent, oldPartner)
        edge(currentPartner, partnerChild)
        val old = family.addPartnership(PartnershipRequest(root, oldPartner, MARRIAGE, ENDED))
        val current = family.addPartnership(PartnershipRequest(root, currentPartner, MARRIAGE, ACTIVE))

        val tree = trees.getTree(root, 1, 1)
        val visible = tree.nodes.map { it.id }.toSet()
        assertEquals(setOf(root, parent, sibling, oldPartner, currentPartner, firstChild, secondChild), visible)
        assertFalse(disconnected in visible)
        assertEquals(setOf(old, current), tree.partnerships.toSet())
        assertTrue(tree.hiddenBranches.getValue(oldPartner).parents)
        assertTrue(tree.hiddenBranches.getValue(currentPartner).children)
    }

    @Test
    fun `step relationship appears but is not recursively traversed`() {
        val root = person("Root")
        val stepChild = person("Step child")
        val grandchild = person("Grandchild")
        val step = edge(root, stepChild, STEP)
        edge(stepChild, grandchild)

        val zero = trees.getTree(root, 0, 0)
        assertEquals(listOf(root), zero.nodes.map { it.id })
        assertTrue(zero.hiddenBranches.getValue(root).stepChildren)
        assertFalse(zero.hiddenBranches.getValue(root).children)

        val expanded = trees.getTree(root, 0, 5)
        assertEquals(setOf(root, stepChild), expanded.nodes.map { it.id }.toSet())
        assertEquals(listOf(step.id), expanded.parentChildEdges.map { it.id })
        assertTrue(expanded.hiddenBranches.getValue(stepChild).children)
    }

    @Test
    fun `invalid depths and missing root are rejected`() {
        val root = person("Root")
        assertThrows<InvalidInput> { trees.getTree(root, -1, 0) }
        assertThrows<InvalidInput> { trees.getTree(root, 0, Int.MAX_VALUE) }
        assertThrows<PersonNotFoundException> { trees.getTree(UUID.randomUUID(), 0, 0) }
    }
}
