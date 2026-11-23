package ru.stepenko.rootme.model.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import ru.stepenko.rootme.model.person.Gender
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "persons")
class PersonEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var firstName: String,
    var lastName: String?,
    var patronymic: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var gender: Gender,
    var birthDate: LocalDate?,
    var deathDate: LocalDate?,
    var birthCountry: String?,
    var birthRegion: String?,
    var birthCity: String?,
    /**
     * First created person in tree is root
     */
    @Column(name = "is_root", nullable = false)
    var isRoot: Boolean,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var relationship: ru.stepenko.rootme.model.person.Relationship,
    /**
     * Generation 0 includes the root person himself and his closest relatives,
     * and the children/parents are shifted by +1/-1
     */
    var generation: Int = 0,
    /**
     * Relationships to children.
     */
    @get:JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "person_parentage",
        joinColumns = [JoinColumn(name = "parent_id")],
        inverseJoinColumns = [JoinColumn(name = "child_id")],
    )
    var children: MutableSet<PersonEntity> = mutableSetOf(),

    /**
     * Relationship to spouse.
     * Instead of using UNDIRECTED, we'll handle spouse relationships symmetrically in application logic.
     * When saving or uploading data, it is necessary to ensure that it is mirrored to the other side.
     */
    @get:JsonIgnore
    @ManyToOne
    @JoinColumn(name = "spouse_id")
    var spouse: PersonEntity? = null,

    /**
     * Relationship to parents.
     */
    @get:JsonIgnore
    @ManyToMany(mappedBy = "children")
    var parents: MutableSet<PersonEntity> = mutableSetOf()
)
