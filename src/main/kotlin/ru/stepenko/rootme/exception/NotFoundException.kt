package ru.stepenko.rootme.exception

open class NotFoundException(message: String): RuntimeException(message)
class PersonNotFoundException(message: String): NotFoundException(message)