package ru.stepenko.rootme

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableJpaRepositories
class RootmeApplication

fun main(args: Array<String>) {
	runApplication<RootmeApplication>(*args)
}