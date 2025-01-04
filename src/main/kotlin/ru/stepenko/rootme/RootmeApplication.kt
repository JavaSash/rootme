package ru.stepenko.rootme

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories

@SpringBootApplication
@EnableNeo4jRepositories
class RootmeApplication

fun main(args: Array<String>) {
	runApplication<RootmeApplication>(*args)
}