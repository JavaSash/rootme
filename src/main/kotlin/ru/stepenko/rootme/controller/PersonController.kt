package ru.stepenko.rootme.controller

import org.springframework.web.bind.annotation.RestController
import ru.stepenko.rootme.model.entity.Person
import ru.stepenko.rootme.service.PersonService

@RestController
class PersonController(private val personService: PersonService) {

    fun create(person: Person): Person = personService.create(person)

    fun update(person: Person): Person = personService.update(person)

    fun getPerson(personId: String): Person = personService.getPerson(personId)

    fun delete(personId: String) = personService.delete(personId)
}