package ru.stepenko.rootme.controller

import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.stepenko.rootme.service.GedcomExport

@RestController
class GedcomApi(private val exporter: GedcomExport) {
    @GetMapping("/api/v1/exports/gedcom")
    fun export(): ResponseEntity<String> {
        val file = exporter.export()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("rootme.ged").toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("X-RootMe-Gedcom-Warnings", file.warnings.joinToString(","))
            .body(file.content)
    }
}
