package ru.stepenko.rootme.controller

import mu.KLogging
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.ResponseWrapper
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.service.PersonService
import java.util.*

@RestController
@RequestMapping("/api/v1/person")
class PersonApi(private val personService: PersonService) {

    companion object : KLogging()

    @PostMapping("/create")
    fun create(@RequestBody person: PersonRq): ResponseWrapper = personService.create(person)
        .also { logger.info { "Created person ${person.firstName} ${person.lastName}" } }

    /**
     * Чтобы обновить конкретного человека, надо в теле указать имеющийся id помимо новых значений полей
     */
    @PutMapping("/edit/{personUuid}")
    fun update(
        @PathVariable personUuid: UUID,
        @RequestBody person: PersonRq
    ): ResponseWrapper = personService.update(person, personUuid)

    @GetMapping("/get/{personId}")
    fun getPerson(@PathVariable personId: UUID): ResponseWrapper = personService.getPerson(personId)

    @GetMapping("/get")
    fun getAllPersons(): ResponseWrapper = personService.getAllPersons()

    @DeleteMapping("/delete/{personId}")
    fun delete(@PathVariable personId: UUID): ResponseWrapper = personService.delete(personId)
}