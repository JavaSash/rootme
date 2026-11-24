package ru.stepenko.rootme.model.entity

import jakarta.persistence.*
import ru.stepenko.rootme.model.person.PartialDate
import java.time.Instant
import java.util.UUID

enum class ParentChildType { BIOLOGICAL, ADOPTIVE, STEP }
enum class PartnershipType { MARRIAGE, PARTNERSHIP }
enum class PartnershipStatus { ACTIVE, ENDED, UNKNOWN }

@Entity
@Table(name = "parent_child_relationships")
class ParentChildRelationship(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    val parentId: UUID,
    val childId: UUID,
    @Enumerated(EnumType.STRING)
    val type: ParentChildType,
    val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "partnerships")
class Partnership(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @Column(name = "person1_id")
    val person1Id: UUID,
    @Column(name = "person2_id")
    val person2Id: UUID,
    @Enumerated(EnumType.STRING)
    var type: PartnershipType,
    @Enumerated(EnumType.STRING)
    var status: PartnershipStatus,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "value", column = Column(name = "start_date")),
        AttributeOverride(name = "precision", column = Column(name = "start_date_precision")),
    )
    var startDate: PartialDate?,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "value", column = Column(name = "end_date")),
        AttributeOverride(name = "precision", column = Column(name = "end_date_precision")),
    )
    var endDate: PartialDate?,
)
