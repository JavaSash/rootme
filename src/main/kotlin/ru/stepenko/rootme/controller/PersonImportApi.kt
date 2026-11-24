package ru.stepenko.rootme.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.service.ImportConfirmation
import ru.stepenko.rootme.service.ImportFiles
import ru.stepenko.rootme.service.ImportMapping
import ru.stepenko.rootme.service.PersonImportService

@RestController
@RequestMapping("/api/v1/imports/persons", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
class PersonImportApi(private val files: ImportFiles, private val importer: PersonImportService) {
    @PostMapping("/sources")
    fun sources(@RequestPart file: MultipartFile) = files.sources(file)

    @PostMapping("/preview")
    fun preview(@RequestPart file: MultipartFile, @RequestPart options: ImportMapping) =
        importer.preview(file, options)

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(@RequestPart file: MultipartFile, @RequestPart options: ImportConfirmation) =
        importer.apply(file, options)
}
