package ru.stepenko.rootme.controller

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.api.ParentChildRequest
import ru.stepenko.rootme.model.api.PartnershipRequest
import ru.stepenko.rootme.service.FamilyRelationshipService
import ru.stepenko.rootme.service.FamilyTreeService
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class FamilyApi(
    private val relationships: FamilyRelationshipService,
    private val trees: FamilyTreeService,
) {
    @PostMapping("/relationships/parent-child")
    @ResponseStatus(HttpStatus.CREATED)
    fun addParentChild(@RequestBody request: ParentChildRequest) = relationships.addParentChild(request)

    @DeleteMapping("/relationships/parent-child/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteParentChild(@PathVariable id: UUID) = relationships.deleteParentChild(id)

    @PostMapping("/partnerships")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPartnership(@RequestBody request: PartnershipRequest) = relationships.addPartnership(request)

    @PatchMapping("/partnerships/{id}")
    fun updatePartnership(@PathVariable id: UUID, @RequestBody patch: JsonNode) =
        relationships.updatePartnership(id, patch)

    @DeleteMapping("/partnerships/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePartnership(@PathVariable id: UUID) = relationships.deletePartnership(id)

    @GetMapping("/tree")
    fun getTree(
        @RequestParam rootPersonId: UUID,
        @RequestParam(defaultValue = "5") ancestors: Int,
        @RequestParam(defaultValue = "5") descendants: Int,
    ) = trees.getTree(rootPersonId, ancestors, descendants)
}
