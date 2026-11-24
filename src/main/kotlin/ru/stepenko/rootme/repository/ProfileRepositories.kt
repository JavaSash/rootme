package ru.stepenko.rootme.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.stepenko.rootme.model.entity.AlternativeNameType
import ru.stepenko.rootme.model.entity.PersonAlternativeName
import ru.stepenko.rootme.model.entity.PersonPhoto
import java.util.UUID

interface AlternativeNameRepository : JpaRepository<PersonAlternativeName, UUID> {
    fun findByPersonIdOrderByNameAscIdAsc(personId: UUID): List<PersonAlternativeName>
    fun findByIdAndPersonId(id: UUID, personId: UUID): PersonAlternativeName?

    @Query("""
        select a.id from PersonAlternativeName a
        where a.personId = :personId and a.type = :type and lower(a.name) = lower(:name)
    """)
    fun matchingIds(personId: UUID, type: AlternativeNameType, name: String): List<UUID>
}

interface PersonPhotoRepository : JpaRepository<PersonPhoto, UUID> {
    @Query("select p.personId from PersonPhoto p where p.id = :id")
    fun ownerOf(id: UUID): UUID?

    fun findByPersonIdOrderByCreatedAtAscIdAsc(personId: UUID): List<PersonPhoto>
    fun findByPersonIdAndPrimaryTrue(personId: UUID): PersonPhoto?
    fun findByPersonIdInAndPrimaryTrue(ids: Collection<UUID>): List<PersonPhoto>
}
