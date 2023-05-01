package ru.stepenko.rootme.controller

import mu.KLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.entity.Person
import ru.stepenko.rootme.service.PersonService

@RestController
@RequestMapping("/person")
class PersonController(private val personService: PersonService) {

    companion object : KLogging()

    @PostMapping("/create")
    fun create(@RequestBody person: Person): ResponseEntity<Person> = ResponseEntity<Person>(personService.create(person), HttpStatus.CREATED)
        .also { logger.info { "Created person ${person.firstName + person.lastName}" } }

    /**
     * Чтобы обновить конкретного человека, надо в теле указать имеющийся id помимо новых значений полей
     */
    @PutMapping("/update")
    fun update(@RequestBody person: Person): Person = personService.update(person)

    @GetMapping("/get/{personId}")
    fun getPerson(@PathVariable personId: Int): Person = personService.getPerson(personId)

    @DeleteMapping("/delete/{personId}")
    fun delete(@PathVariable personId: Int) = personService.delete(personId)
}