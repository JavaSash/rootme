//package ru.stepenko.rootme.controller
//
//import org.springframework.web.bind.annotation.RestController
//import ru.stepenko.rootme.model.entity.Tree
//import ru.stepenko.rootme.service.TreeService
//
//@RestController
//class TreeController(private val treeService: TreeService) {
//
//    fun create(tree: Tree): Tree = treeService.create(tree)
//
//    fun update(tree: Tree): Tree = treeService.update(tree)
//
//    fun getTree(treeId: Int): Tree = treeService.getTree(treeId)
//
//    fun getTrees(profileId: String): List<Tree> = treeService.getTrees(profileId)
//
//    fun delete(treeId: Int) = treeService.delete(treeId)
//}