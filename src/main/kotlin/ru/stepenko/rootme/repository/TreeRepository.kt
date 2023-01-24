package ru.stepenko.rootme.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.stepenko.rootme.model.Tree

interface TreeRepository: JpaRepository<Tree, String> {

    fun getTreesByProfileId(profileId: String): List<Tree> {
        TODO("Not yet implemented")
    }
}