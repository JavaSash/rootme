package ru.stepenko.rootme.exception

import org.springframework.http.HttpStatus

class InvalidInput(val field: String, message: String) : RuntimeException(message)
class Conflict(val code: String, message: String) : RuntimeException(message)
class ApiFailure(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)
