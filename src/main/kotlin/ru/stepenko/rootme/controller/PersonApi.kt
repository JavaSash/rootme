package ru.stepenko.rootme.controller

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.model.person.PersonDto
import ru.stepenko.rootme.model.person.PersonRq
import ru.stepenko.rootme.service.PersonService
import java.util.*

@RestController
@RequestMapping("/api/v1/person")
class PersonApi(private val personService: PersonService) {
    @PostMapping("/create")
    fun create(@Valid @RequestBody person: PersonRq): PersonDto
            = personService.create(person)

    @PutMapping("/edit/{personUuid}")
    fun update(
        @PathVariable personUuid: UUID,
        @Valid @RequestBody person: PersonRq
    ): PersonDto = personService.update(person, personUuid)

    @GetMapping("/get/{personId}")
    fun getPerson(@PathVariable personId: UUID): PersonDto = personService.getPerson(personId)

    @GetMapping("/get")
    fun getAllPersons(): List<PersonDto> =
        personService.getAllPersons()

    @DeleteMapping("/delete/{personId}")
    fun delete(@PathVariable personId: UUID) = personService.delete(personId)
}
