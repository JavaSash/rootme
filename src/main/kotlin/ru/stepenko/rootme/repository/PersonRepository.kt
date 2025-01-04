package ru.stepenko.rootme.repository

import org.springframework.data.neo4j.repository.Neo4jRepository
import ru.stepenko.rootme.model.entity.PersonEntity
import java.util.*

interface PersonRepository: Neo4jRepository<PersonEntity, UUID>