package ru.stepenko.rootme.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.stepenko.rootme.service.BackupService
import ru.stepenko.rootme.exception.ApiFailure
import java.util.UUID

@RestController
@RequestMapping("/api/v1/backups")
class BackupApi(private val backups: BackupService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create() = backups.create()

    @GetMapping("/{id}")
    fun download(@PathVariable id: UUID): ResponseEntity<FileSystemResource> = ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("rootme-$id.zip").toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(backups.download(id))

    @PostMapping("/verify", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/zip"])
    fun verify(request: HttpServletRequest) = request.inputStream.use { backups.verify(it) }

    @PostMapping("/restore", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/zip"])
    fun restore(
        request: HttpServletRequest,
        @RequestHeader(name = "X-RootMe-Confirm-Restore", required = false) confirmation: String?,
        @RequestHeader(name = "X-RootMe-Backup-SHA256", required = false) sha256: String?,
    ): ru.stepenko.rootme.service.BackupVerification {
        if (confirmation != "true" || sha256 == null || !Regex("[a-f0-9]{64}").matches(sha256)) throw ApiFailure(
            HttpStatus.BAD_REQUEST, "BACKUP_RESTORE_CONFIRMATION_REQUIRED", "Confirm the restore explicitly.",
        )
        return request.inputStream.use { backups.restore(it, sha256) }
    }
}
