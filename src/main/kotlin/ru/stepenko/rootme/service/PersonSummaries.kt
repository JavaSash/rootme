package ru.stepenko.rootme.service

import org.springframework.stereotype.Component
import ru.stepenko.rootme.model.api.toSummary
import ru.stepenko.rootme.model.entity.PersonEntity
import ru.stepenko.rootme.repository.PersonPhotoRepository
import ru.stepenko.rootme.util.toSummary
import java.util.UUID

@Component
class PersonSummaries(private val photos: PersonPhotoRepository) {
    fun forPeople(people: List<PersonEntity>) = if (people.isEmpty()) emptyList() else {
        val primary = photos.findByPersonIdInAndPrimaryTrue(people.map { requireNotNull(it.id) })
            .associateBy { it.personId }
        people.map { it.toSummary(primary[it.id]?.toSummary()) }
    }

    fun primary(id: UUID) = photos.findByPersonIdAndPrimaryTrue(id)?.toSummary()
}
