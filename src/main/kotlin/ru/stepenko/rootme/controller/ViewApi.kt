package ru.stepenko.rootme.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import ru.stepenko.rootme.service.PersonService
import java.util.*

@Controller
class ViewApi(
    private val personService: PersonService
) {

    @GetMapping("/")
    fun main(model: Model): String {
        val isNewTree = personService.isNewTree()
        model.addAttribute("isRoot", isNewTree)
        return if (isNewTree) "create-person" else "people-list"
    }

    @GetMapping("/create-person")
    fun createPerson(model: Model): String {
        val isRoot = personService.isNewTree()
        model.addAttribute("isRoot", isRoot)
        return "create-person"
    }

    @GetMapping("/edit-person")
    fun editPerson(@RequestParam("id") personId: UUID, model: Model): String {
        val person = personService.getPerson(personId)
        model.addAttribute("isRoot", person.isRoot)
        model.addAttribute("person", person) // Передача данных о персоне
        return "edit-person"
    }

    @GetMapping("/people-list")
    fun getAllPeople(): String =
        if (personService.isNewTree()) "create-person"
        else "people-list"

}