package ru.stepenko.rootme.model.entity

import jakarta.persistence.*

@Entity(name = "profiles")
data class Profile(
    @Id
    @Column
    val uuid: String,
    @Column
    val username: String,
    @Column
    val password: String,
//    @OneToMany
//    @JoinColumn(name="treeUuids")
//    val treeUuids: MutableList<String>
)
