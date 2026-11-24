package ru.stepenko.rootme.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LocalMutationOriginFilter(private val json: ObjectMapper) : OncePerRequestFilter() {
    private val mutations = setOf("POST", "PUT", "PATCH", "DELETE")
    private val origins = listOf("localhost", "127.0.0.1").flatMap { host ->
        listOf(8080, 5173, 4173).map { port -> "http://$host:$port" }
    }.toSet()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val origin = request.getHeader("Origin")
        if (request.method in mutations &&
            (origin != null && origin !in origins || origin == null && request.getHeader("Sec-Fetch-Site") == "cross-site")
        ) {
            val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "This origin may not modify RootMe data.")
            problem.setProperty("code", "ORIGIN_NOT_ALLOWED")
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            json.writeValue(response.outputStream, problem)
            return
        }
        chain.doFilter(request, response)
    }
}
