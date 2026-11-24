package ru.stepenko.rootme.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import ru.stepenko.rootme.model.entity.ParentChildRelationship
import ru.stepenko.rootme.model.entity.ParentChildType
import ru.stepenko.rootme.model.entity.Partnership
import java.util.UUID

interface ParentChildRepository : JpaRepository<ParentChildRelationship, UUID> {
    fun existsByParentIdAndChildIdAndType(parentId: UUID, childId: UUID, type: ParentChildType): Boolean

    @Query(
        value = """
        WITH RECURSIVE reachable(id) AS (
            SELECT CAST(:childId AS uuid)
            UNION
            SELECT edge.child_id
            FROM parent_child_relationships edge JOIN reachable ON edge.parent_id = reachable.id
            WHERE edge.type IN ('BIOLOGICAL', 'ADOPTIVE')
        )
        SELECT EXISTS (SELECT 1 FROM reachable WHERE id = :parentId)
        """,
        nativeQuery = true,
    )
    fun canReach(childId: UUID, parentId: UUID): Boolean

    fun findByParentIdInAndTypeIn(
        parentIds: Collection<UUID>, types: Collection<ParentChildType>, page: Pageable,
    ): List<ParentChildRelationship>

    fun findByChildIdInAndTypeIn(
        childIds: Collection<UUID>, types: Collection<ParentChildType>, page: Pageable,
    ): List<ParentChildRelationship>

    fun findByParentIdOrChildId(parentId: UUID, childId: UUID): List<ParentChildRelationship>

    fun findByParentIdInAndChildIdIn(
        parentIds: Collection<UUID>, childIds: Collection<UUID>, page: Pageable,
    ): List<ParentChildRelationship>

    @Query("""
        select distinct edge.childId from ParentChildRelationship edge
        where edge.childId in :visible and edge.parentId not in :visible and edge.type in :types
    """)
    fun childrenWithHiddenParents(visible: Collection<UUID>, types: Collection<ParentChildType>): Set<UUID>

    @Query("""
        select distinct edge.parentId from ParentChildRelationship edge
        where edge.parentId in :visible and edge.childId not in :visible and edge.type in :types
    """)
    fun parentsWithHiddenChildren(visible: Collection<UUID>, types: Collection<ParentChildType>): Set<UUID>
}

interface PartnershipRepository : JpaRepository<Partnership, UUID> {
    fun findByPerson1IdAndPerson2Id(person1Id: UUID, person2Id: UUID): List<Partnership>

    fun findByPerson1IdOrPerson2Id(person1Id: UUID, person2Id: UUID): List<Partnership>

    fun findByPerson1IdInOrPerson2IdIn(
        firstIds: Collection<UUID>, secondIds: Collection<UUID>, page: Pageable,
    ): List<Partnership>

    fun findByPerson1IdInAndPerson2IdIn(
        firstIds: Collection<UUID>, secondIds: Collection<UUID>, page: Pageable,
    ): List<Partnership>

    @Query("""
        select distinct unionEdge.person1Id from Partnership unionEdge
        where unionEdge.person1Id in :visible and unionEdge.person2Id not in :visible
    """)
    fun firstParticipantsWithHiddenPartners(visible: Collection<UUID>): Set<UUID>

    @Query("""
        select distinct unionEdge.person2Id from Partnership unionEdge
        where unionEdge.person2Id in :visible and unionEdge.person1Id not in :visible
    """)
    fun secondParticipantsWithHiddenPartners(visible: Collection<UUID>): Set<UUID>
}
