package ru.stepenko.rootme.exception

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidInput::class)
    fun invalidInput(error: InvalidInput) =
        problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", error.message.orEmpty(), error.field)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(error: NotFoundException) =
        problem(HttpStatus.NOT_FOUND, "NOT_FOUND", error.message.orEmpty())

    @ExceptionHandler(Conflict::class)
    fun conflict(error: Conflict) =
        problem(HttpStatus.CONFLICT, error.code, error.message.orEmpty())

    @ExceptionHandler(ApiFailure::class)
    fun apiFailure(error: ApiFailure) = problem(error.status, error.code, error.message.orEmpty())

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun uploadTooLarge() =
        problem(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Use an image of at most 20 MiB.")

    @ExceptionHandler(HttpMessageNotReadableException::class, JsonProcessingException::class)
    fun malformedBody(error: Exception): ProblemDetail {
        val mappingError = (error as? JsonMappingException) ?: (error.cause as? JsonMappingException)
        val field = mappingError?.path?.mapNotNull { it.fieldName }?.joinToString(".")?.ifEmpty { null }
        return problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid or missing JSON value.", field ?: "body")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun wrongParameter(error: MethodArgumentTypeMismatchException) =
        problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid parameter value.", error.name)

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(error: MissingServletRequestParameterException) =
        problem(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "This parameter is required.", error.parameterName)

    @ExceptionHandler(Exception::class)
    fun failure(error: Exception): ProblemDetail {
        if (error is ErrorResponse) {
            val status = HttpStatus.resolve(error.statusCode.value())
            if (status != null && status.is4xxClientError) {
                return problem(status, "REQUEST_ERROR", status.reasonPhrase)
            }
        }
        // Avoid recording SQL bindings or private person data from exception messages.
        logger.error("Unexpected API error of type {}", error.javaClass.name)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected server error occurred.")
    }

    private fun problem(status: HttpStatus, code: String, detail: String, field: String? = null): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty("code", code)
            if (field != null) setProperty("errors", mapOf(field to detail))
        }
}
