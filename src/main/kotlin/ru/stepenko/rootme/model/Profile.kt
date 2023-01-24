package ru.stepenko.rootme.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany

@Entity(name = "profiles")
data class Profile(
    @Id
    @Column
    val uuid: String,
    @Column
    val username: String,
    @Column
    val password: String,
    @OneToMany
    @Column
    val treeUuids: List<String>
)
