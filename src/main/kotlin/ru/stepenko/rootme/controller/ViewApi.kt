package ru.stepenko.rootme.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import ru.stepenko.rootme.service.PersonService

@Controller
class ViewApi(
    private val personService: PersonService
) {

    @GetMapping("/")
    fun index(model: Model): String =
        if (personService.isNewTree()) "create-person"
        else "people-list"

    @GetMapping("/create-person")
    fun createPerson(): String? {
        return "create-person"
    }

    @GetMapping("/edit-person")
    fun editPerson(): String? {
        return "edit-person"
    }

    @GetMapping("/create-relationship")
    fun createRelationship(): String? {
        return "create-relationship"
    }

    @GetMapping("/people-list")
    fun getAllPeople(): String =
        if (personService.isNewTree()) "create-person"
        else "people-list"
}