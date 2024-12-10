package ru.stepenko.rootme.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import ru.stepenko.rootme.util.formResponseWrapper

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(exc: NotFoundException) =
        ResponseEntity(formResponseWrapper(exc.message), HttpStatus.NOT_FOUND)
}