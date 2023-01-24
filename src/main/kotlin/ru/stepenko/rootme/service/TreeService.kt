package ru.stepenko.rootme.service

import org.springframework.stereotype.Service
import ru.stepenko.rootme.model.Tree
import ru.stepenko.rootme.repository.TreeRepository

@Service
class TreeService(private val treeRepository: TreeRepository) {

    fun create(tree: Tree): Tree = treeRepository.save(tree)

    fun getTree(treeId: String): Tree = treeRepository.getReferenceById(treeId)

    fun getTrees(profileId: String): List<Tree> = treeRepository.getTreesByProfileId(profileId)

    fun delete(treeId: String) = treeRepository.deleteById(treeId)


}
