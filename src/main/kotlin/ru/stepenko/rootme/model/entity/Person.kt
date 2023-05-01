package ru.stepenko.rootme.model.entity

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import jakarta.persistence.*
import ru.stepenko.rootme.model.Gender
import ru.stepenko.rootme.model.RelatedRelationship
import java.time.LocalDate

@Entity(name = "persons")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Person(
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    @Column
    val id: Int,
    @Column
    val firstName: String,
    @Column
    val lastName: String?,
    @Column
    val patronymic: String?,
    @Column
    val gender: Gender,
    @Column
    val birthDate: LocalDate?,
//    TODO интеграция с сервисом геолокации с автоподстановкой
    @Column
    val birthCountry: String?,
    @Column
    val birthRegion: String?,
    @Column
    val birthCity: String?,
//    TODO обязательный, если не корневая личность (isRoot = false)
    @Column
    val relatedRelationship: RelatedRelationship?,
//TODO скорей всего придётся делать прослойку из ДАО и туда выносить это поле,
// оно будет меняться в процессе работы с деревом и в бд не нужно, только в рантайме
    @Column
    var isRoot: Boolean = false,
//    @OneToOne
//    @JoinColumn(name="treeUuid")
//    val treeUuid: String
)