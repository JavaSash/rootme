package ru.stepenko.rootme.model.entity

import org.springframework.data.annotation.Id
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Relationship
import ru.stepenko.rootme.model.person.Gender
import java.time.LocalDate
import java.util.*

@Node("Person")
data class PersonEntity(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),
    var firstName: String,
    var lastName: String?,
    var patronymic: String?,
    var gender: Gender,
    var birthDate: LocalDate?,
    var deathDate: LocalDate?,
    var birthCountry: String?,
    var birthRegion: String?,
    var birthCity: String?,
    /**
     * First created person in tree is root
     */
    var isRoot: Boolean,
    var relationship: ru.stepenko.rootme.model.person.Relationship,
    /**
     * Generation 0 includes the root person himself and his closest relatives,
     * and the children/parents are shifted by +1/-1
     */
    var generation: Int = 0,
    /**
     * Relationships to children.
     */
    @Relationship(type = "PARENT_OF", direction = Relationship.Direction.OUTGOING)
    var children: MutableSet<PersonEntity> = mutableSetOf(),

    /**
     * Relationship to spouse.
     * Instead of using UNDIRECTED, we'll handle spouse relationships symmetrically in application logic.
     * When saving or uploading data, it is necessary to ensure that it is mirrored to the other side.
     */
    @Relationship(type = "SPOUSE_OF", direction = Relationship.Direction.OUTGOING)
    var spouse: PersonEntity? = null,

    /**
     * Relationship to parents.
     */
    @Relationship(type = "CHILD_OF", direction = Relationship.Direction.INCOMING)
    var parents: MutableSet<PersonEntity> = mutableSetOf()
)