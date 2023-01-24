package ru.stepenko.rootme.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity(name = "trees")
data class Tree(
    @Id
    @Column
    val uuid: String,
    @Column
    val profileUuid: String
)
