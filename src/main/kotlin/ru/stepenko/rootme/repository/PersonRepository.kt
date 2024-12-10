package ru.stepenko.rootme.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.stepenko.rootme.model.entity.PersonEntity
import java.util.*

interface PersonRepository: JpaRepository<PersonEntity, UUID>