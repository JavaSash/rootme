package ru.stepenko.rootme.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.service.GenealogyMdbConfirmation
import ru.stepenko.rootme.service.GenealogyMdbImport

@RestController
@RequestMapping("/api/v1/imports/genealogy-mdb", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
class GenealogyMdbImportApi(private val importer: GenealogyMdbImport) {
    @PostMapping("/preview")
    fun preview(@RequestPart file: MultipartFile) = importer.preview(file)

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(@RequestPart file: MultipartFile, @RequestPart options: GenealogyMdbConfirmation) =
        importer.apply(file, options)
}
