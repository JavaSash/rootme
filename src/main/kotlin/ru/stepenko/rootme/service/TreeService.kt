//package ru.stepenko.rootme.service
//
//import org.springframework.stereotype.Service
//import ru.stepenko.rootme.model.entity.Tree
//import ru.stepenko.rootme.repository.TreeRepository
//
//@Service
//class TreeService(private val treeRepository: TreeRepository) {
//
//    fun create(tree: Tree): Tree = treeRepository.save(tree)
//
//    fun update(tree: Tree): Tree {
////        TODO добавить проверку на существование дерева с таким id
//        return treeRepository.save(tree)
//    }
//
//    fun getTree(treeId: Int): Tree = treeRepository.getReferenceById(treeId)
//
//    fun getTrees(profileId: String): List<Tree> = treeRepository.getTreesByProfileId(profileId)
//
//    fun delete(treeId: Int) = treeRepository.deleteById(treeId)
//
//}
