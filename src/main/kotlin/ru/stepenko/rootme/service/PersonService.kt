package ru.stepenko.rootme.service

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import ru.stepenko.rootme.model.entity.Person

@Service
class PersonService(private val personRepository: JpaRepository<Person, Int>) {

    fun create(person: Person): Person = personRepository.save(person)

    fun update(person: Person): Person {
//        TODO добавить проверку на существование дерева с таким id. Если такого id нет, то создаём нового человека,
//         если есть обновляем. Возможно есть смысл create+update везде заменить на patch
//        personRepository.findById(person.uuid).isEmpty
        return personRepository.save(person)
    }

    fun getPerson(personId: Int): Person = personRepository.getReferenceById(personId)

    fun delete(personId: Int) = personRepository.deleteById(personId)
}