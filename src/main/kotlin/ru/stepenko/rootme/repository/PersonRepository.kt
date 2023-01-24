package ru.stepenko.rootme.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.stepenko.rootme.model.entity.Person

interface PersonRepository: JpaRepository<Person, String> {
}