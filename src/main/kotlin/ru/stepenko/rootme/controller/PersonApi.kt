package ru.stepenko.rootme.controller

import mu.KLogging
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.service.PersonService
import java.util.*

@RestController
@RequestMapping("/api/v1/person")
class PersonApi(private val personService: PersonService) {

    companion object : KLogging()

    @PostMapping("/create")
    fun create(@RequestBody person: PersonRq): PersonDto
            = personService.create(person)
        .also { logger.info { ">>> Created person ${person.firstName} ${person.lastName}" } }

    @PutMapping("/edit/{personUuid}")
    fun update(
        @PathVariable personUuid: UUID,
        @RequestBody person: PersonRq
    ): PersonDto = personService.update(person, personUuid)

    @GetMapping("/get/{personId}")
    fun getPerson(@PathVariable personId: UUID)//: PersonDto
            = personService.getPerson(personId)

    @GetMapping("/get")
    fun getAllPersons(): List<PersonDto> =
        personService.getAllPersons().also { logger.info { ">>> All persons in tree: $it" } }

    @DeleteMapping("/delete/{personId}")
    fun delete(@PathVariable personId: UUID) = personService.delete(personId)
}