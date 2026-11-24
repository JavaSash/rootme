package ru.stepenko.rootme.exception

class InvalidInput(val field: String, message: String) : RuntimeException(message)
class Conflict(val code: String, message: String) : RuntimeException(message)
