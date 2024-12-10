package ru.stepenko.rootme

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
// todo authorities
// todo пользователь должен видеть только своё дерево и только членов своей семьи
@SpringBootApplication
@EnableJpaRepositories
class RootmeApplication

fun main(args: Array<String>) {
	runApplication<RootmeApplication>(*args)
}