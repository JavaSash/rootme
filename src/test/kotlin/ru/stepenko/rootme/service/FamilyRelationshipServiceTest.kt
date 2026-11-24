package ru.stepenko.rootme.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.exception.Conflict
import ru.stepenko.rootme.exception.InvalidInput
import ru.stepenko.rootme.exception.NotFoundException
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.*
import ru.stepenko.rootme.model.entity.ParentChildType.*
import ru.stepenko.rootme.model.entity.PartnershipStatus
import ru.stepenko.rootme.model.entity.PartnershipType
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class FamilyRelationshipServiceTest(
    private val persons: PersonService,
    private val family: FamilyRelationshipService,
    private val json: ObjectMapper,
) : BaseIntegrationTest() {
    private fun person(name: String) = persons.create(formPersonRq(name)).id

    @Test
    fun `rejects self links missing people and duplicate edges while allowing distinct types`() {
        val a = person("A")
        val b = person("B")
        assertThrows<InvalidInput> { family.addParentChild(ParentChildRequest(a, a, BIOLOGICAL)) }
        assertThrows<NotFoundException> { family.addParentChild(ParentChildRequest(a, UUID.randomUUID(), BIOLOGICAL)) }
        val biological = family.addParentChild(ParentChildRequest(a, b, BIOLOGICAL))
        val adoptive = family.addParentChild(ParentChildRequest(a, b, ADOPTIVE))
        assertNotEquals(biological.id, adoptive.id)
        assertEquals("DUPLICATE_PARENT_CHILD", assertThrows<Conflict> {
            family.addParentChild(ParentChildRequest(a, b, BIOLOGICAL))
        }.code)
        family.deleteParentChild(biological.id)
        assertEquals(adoptive.id, persons.details(a).parentChildEdges.single().id)
        assertEquals(b, persons.details(b).person.id)
        assertThrows<NotFoundException> { family.deleteParentChild(biological.id) }
    }

    @Test
    fun `cycle detection crosses more than five generations and excludes step links`() {
        val ids = (0..7).map { person("Person $it") }
        ids.zipWithNext().forEachIndexed { index, (parent, child) ->
            family.addParentChild(ParentChildRequest(parent, child, if (index % 2 == 0) BIOLOGICAL else ADOPTIVE))
        }
        assertEquals("ANCESTRY_CYCLE", assertThrows<Conflict> {
            family.addParentChild(ParentChildRequest(ids.last(), ids.first(), BIOLOGICAL))
        }.code)
        assertEquals(STEP, family.addParentChild(ParentChildRequest(ids.last(), ids.first(), STEP)).type)

        val a = person("Step parent")
        val b = person("Step child")
        family.addParentChild(ParentChildRequest(a, b, STEP))
        assertEquals(ADOPTIVE, family.addParentChild(ParentChildRequest(b, a, ADOPTIVE)).type)
    }

    @Test
    fun `concurrent opposing ancestry writes cannot both commit`() {
        val a = person("A")
        val b = person("B")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(a to b, b to a).map { (parent, child) ->
                pool.submit<String> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    try {
                        family.addParentChild(ParentChildRequest(parent, child, BIOLOGICAL))
                        "created"
                    } catch (error: Conflict) {
                        error.code
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(listOf("ANCESTRY_CYCLE", "created"), futures.map { it.get(15, TimeUnit.SECONDS) }.sorted())
            assertEquals(1, persons.details(a).parentChildEdges.size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `partnerships validate parties dates and intentional repeat unions`() {
        val a = person("A")
        val b = person("B")
        val request = PartnershipRequest(a, b, PartnershipType.MARRIAGE, PartnershipStatus.ENDED, "1950")
        assertThrows<InvalidInput> { family.addPartnership(request.copy(person2Id = a)) }
        assertThrows<NotFoundException> { family.addPartnership(request.copy(person2Id = UUID.randomUUID())) }
        assertThrows<InvalidInput> { family.addPartnership(request.copy(endDate = "1949-12")) }
        val union = family.addPartnership(request)
        assertTrue(union.person1Id.toString() < union.person2Id.toString())
        val reverse = request.copy(person1Id = b, person2Id = a)
        assertEquals("PARTNERSHIP_REPEAT_CONFIRMATION_REQUIRED", assertThrows<Conflict> {
            family.addPartnership(reverse)
        }.code)
        val repeated = family.addPartnership(reverse.copy(allowRepeat = true))
        assertNotEquals(union.id, repeated.id)
        val updated = family.updatePartnership(union.id, json.readTree("""{"startDate":null,"endDate":"1950-05"}"""))
        assertEquals(union.copy(startDate = null, endDate = "1950-05"), updated)
        assertEquals(2, persons.details(a).partnerships.size)

        family.deletePartnership(repeated.id)
        assertEquals(listOf(updated), persons.details(a).partnerships)
        assertEquals(b, persons.details(b).person.id)
    }

    @Test
    fun `creating a relative rolls back the person when relationship validation fails`() {
        val root = person("Root")
        val request = RelativeRequest(
            RelativeRole.PARTNER,
            person = formPersonRq("Relative"),
            partnership = PartnershipFields(PartnershipType.MARRIAGE, PartnershipStatus.ENDED, "1950", "1949"),
        )
        assertThrows<InvalidInput> { family.addRelative(root, request) }
        assertTrue(persons.search("Relative").items.isEmpty())
        assertTrue(persons.details(root).partnerships.isEmpty())
        val valid = family.addRelative(root, request.copy(partnership = request.partnership!!.copy(endDate = "1950")))
        assertEquals(valid.partnership, persons.details(root).partnerships.single())

        val existing = person("Existing")
        val linked = family.addRelative(root, RelativeRequest(RelativeRole.PARENT, existingPersonId = existing, parentChildType = ADOPTIVE))
        assertEquals(existing, linked.parentChild?.parentId)
        assertEquals(root, linked.parentChild?.childId)
        assertEquals(3, persons.search("").items.size)
    }

    @Test
    fun `deleting a person cleans up both kinds of relationships and keeps the other people`() {
        val root = person("Root")
        val child = person("Child")
        val partner = person("Partner")
        val edge = family.addParentChild(ParentChildRequest(root, child, BIOLOGICAL))
        val union = family.addPartnership(PartnershipRequest(root, partner, PartnershipType.MARRIAGE, PartnershipStatus.ACTIVE))
        persons.delete(root)
        assertTrue(persons.details(child).parentChildEdges.isEmpty())
        assertTrue(persons.details(partner).partnerships.isEmpty())
        assertThrows<NotFoundException> { family.deleteParentChild(edge.id) }
        assertThrows<NotFoundException> { family.deletePartnership(union.id) }
    }
}
