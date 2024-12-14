package ru.stepenko.rootme.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ViewApi {

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("name", "Пользователь")
        return "index"
    }

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
    fun getAllPeople(): String? {
        return "people-list"
    }
}