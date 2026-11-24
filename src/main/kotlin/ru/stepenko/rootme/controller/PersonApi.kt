package ru.stepenko.rootme.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.api.RelativeRequest
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.service.FamilyRelationshipService
import ru.stepenko.rootme.service.PersonService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/persons")
class PersonApi(
    private val persons: PersonService,
    private val relationships: FamilyRelationshipService,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "") query: String) = persons.search(query)

    @GetMapping("/{id}")
    fun details(@PathVariable id: UUID) = persons.details(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: PersonRq) = persons.create(request)

    @PatchMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody patch: JsonNode) = persons.update(id, patch)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) = persons.delete(id)

    @PostMapping("/{id}/relatives")
    @ResponseStatus(HttpStatus.CREATED)
    fun addRelative(@PathVariable id: UUID, @RequestBody request: RelativeRequest) =
        relationships.addRelative(id, request)
}
