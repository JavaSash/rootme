package ru.stepenko.rootme

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RootmeApplication

fun main(args: Array<String>) {
	runApplication<RootmeApplication>(*args)
}
