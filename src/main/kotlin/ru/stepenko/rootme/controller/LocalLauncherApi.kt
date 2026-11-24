package ru.stepenko.rootme.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

@RestController
@RequestMapping("/api/v1/local/launcher")
@ConditionalOnProperty(prefix = "rootme.launcher", name = ["enabled"], havingValue = "true")
class LocalLauncherApi(
    private val context: ConfigurableApplicationContext,
    @Value("\${rootme.launcher.token}") private val token: String,
) {
    private val shuttingDown = AtomicBoolean()

    @GetMapping
    fun status(request: HttpServletRequest): Map<String, Long> {
        authorize(request)
        return mapOf("pid" to ProcessHandle.current().pid())
    }

    @PostMapping("/shutdown")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun shutdown(request: HttpServletRequest) {
        authorize(request)
        if (shuttingDown.compareAndSet(false, true)) {
            Thread({ context.close() }, "rootme-launcher-shutdown").apply {
                isDaemon = false
                start()
            }
        }
    }

    private fun authorize(request: HttpServletRequest) {
        val received = request.getHeader("X-RootMe-Launcher").orEmpty()
        if (request.remoteAddr != "127.0.0.1" || token.length < 32 ||
            !MessageDigest.isEqual(token.toByteArray(UTF_8), received.toByteArray(UTF_8))
        ) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}
