package ru.stepenko.rootme.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.stepenko.rootme.model.entity.PersonEntity
import java.util.*

interface PersonRepository : JpaRepository<PersonEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PersonEntity p where p.id = :id")
    fun findForUpdate(id: UUID): PersonEntity?

    @Query("""
        select p from PersonEntity p
        where locate(lower(:query), lower(p.firstName)) > 0
           or locate(lower(:query), lower(coalesce(p.lastName, ''))) > 0
           or locate(lower(:query), lower(coalesce(p.patronymic, ''))) > 0
           or locate(lower(:query), lower(concat(p.firstName, ' ', coalesce(p.lastName, ''), ' ', coalesce(p.patronymic, '')))) > 0
        order by p.lastName, p.firstName, p.patronymic, p.id
    """)
    fun search(query: String): List<PersonEntity>
}
