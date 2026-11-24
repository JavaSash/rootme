package ru.stepenko.rootme.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import ru.stepenko.rootme.model.entity.PersonEntity
import java.time.LocalDate
import java.util.*

private const val SEARCH_FILTER = """
    FROM persons p
    WHERE (CAST(:fromDate AS date) IS NULL OR
        p.birth_date >= CAST(:fromDate AS date) AND p.birth_date <= CAST(:toDate AS date))
    AND NOT EXISTS (
        SELECT 1 FROM unnest(string_to_array(lower(:query), ' ')) AS term(word)
        WHERE strpos(lower(concat_ws(' ', p.first_name, p.last_name, p.patronymic, p.birth_surname)), term.word) = 0
          AND NOT EXISTS (
              SELECT 1 FROM person_alternative_names a
              WHERE a.person_id = p.id AND strpos(lower(a.name), term.word) > 0
          )
    )
"""

interface PersonRepository : JpaRepository<PersonEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PersonEntity p where p.id = :id")
    fun findForUpdate(id: UUID): PersonEntity?

    @Query(
        value = "SELECT p.* " + SEARCH_FILTER +
            " ORDER BY lower(p.last_name) NULLS LAST, lower(p.first_name), lower(p.patronymic) NULLS LAST, p.id",
        countQuery = "SELECT count(*) " + SEARCH_FILTER,
        nativeQuery = true,
    )
    fun search(query: String, fromDate: LocalDate?, toDate: LocalDate?, pageable: Pageable): Page<PersonEntity>
}
