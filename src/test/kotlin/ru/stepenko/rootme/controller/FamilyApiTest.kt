package ru.stepenko.rootme.controller

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import ru.stepenko.rootme.BaseIntegrationTest
import ru.stepenko.rootme.formPersonRq
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.entity.ParentChildType.BIOLOGICAL
import ru.stepenko.rootme.service.FamilyRelationshipService
import ru.stepenko.rootme.service.PersonService

internal class FamilyApiTest(
    context: WebApplicationContext,
    private val persons: PersonService,
    private val family: FamilyRelationshipService,
) : BaseIntegrationTest() {
    private val mvc = MockMvcBuilders.webAppContextSetup(context).build()
    private val base = "/api/v1"

    @Test
    fun `person JSON retains precision and patch distinguishes null from absence`() {
        val creation = mvc.post("$base/persons") {
            contentType = APPLICATION_JSON
            content = """{"firstName":"Alex","lastName":"Smith","gender":"UNKNOWN","birthDate":"2000-02"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.birthDate") { value("2000-02") }
        }
        val id = com.jayway.jsonpath.JsonPath.read<String>(creation.andReturn().response.contentAsString, "$.id")

        mvc.patch("$base/persons/$id") {
            contentType = APPLICATION_JSON
            content = """{"lastName":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.lastName") { hasJsonPath(); doesNotExist() }
            jsonPath("$.birthDate") { value("2000-02") }
            jsonPath("$.gender") { value("UNKNOWN") }
        }

        mvc.patch("$base/persons/$id") {
            contentType = APPLICATION_JSON
            content = """{"firstName":null}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }

        mvc.delete("$base/persons/$id").andExpect { status { isNoContent() } }
        mvc.get("$base/persons/$id").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON) }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `duplicate and cycle errors are structured conflicts`() {
        val parent = persons.create(formPersonRq("Parent")).id
        val child = persons.create(formPersonRq("Child")).id
        family.addParentChild(ParentChildRequest(parent, child, BIOLOGICAL))
        for ((from, to, code) in listOf(
            Triple(parent, child, "DUPLICATE_PARENT_CHILD"),
            Triple(child, parent, "ANCESTRY_CYCLE"),
        )) {
            mvc.post("$base/relationships/parent-child") {
                contentType = APPLICATION_JSON
                content = """{"parentId":"$from","childId":"$to","type":"BIOLOGICAL"}"""
            }.andExpect {
                status { isConflict() }
                content { contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value(code) }
            }
        }
    }

    @Test
    fun `tree defaults to five generations in both directions`() {
        val people = (0..12).map { persons.create(formPersonRq("Person $it")).id }
        people.zipWithNext().forEach { (parent, child) -> family.addParentChild(ParentChildRequest(parent, child, BIOLOGICAL)) }

        mvc.get("$base/tree") { param("rootPersonId", people[6].toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.rootPersonId") { value(people[6].toString()) }
            jsonPath("$.nodes.length()") { value(11) }
            jsonPath("$.parentChildEdges.length()") { value(10) }
            jsonPath("$.hiddenBranches['${people[1]}'].parents") { value(true) }
            jsonPath("$.hiddenBranches['${people[11]}'].children") { value(true) }
        }
    }
}
