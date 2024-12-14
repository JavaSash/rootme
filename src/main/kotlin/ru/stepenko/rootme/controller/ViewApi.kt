package ru.stepenko.rootme.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import ru.stepenko.rootme.service.PersonService

@Controller
class ViewApi(
    private val personService: PersonService
) {

    @GetMapping("/")
    fun main(): String = chooseMainView()

    @GetMapping("/create-person")
    fun createPerson(): String = "create-person"

    @GetMapping("/edit-person")
    fun editPerson(): String = "edit-person"

    @GetMapping("/people-list")
    fun getAllPeople(): String = chooseMainView()

    private fun chooseMainView(): String =
        if (personService.isNewTree()) "create-person"
        else "people-list"
}