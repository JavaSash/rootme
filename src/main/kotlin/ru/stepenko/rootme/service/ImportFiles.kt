package ru.stepenko.rootme.service

import com.healthmarketscience.jackcess.DatabaseBuilder
import com.healthmarketscience.jackcess.DateTimeType
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import ru.stepenko.rootme.exception.ApiFailure
import ru.stepenko.rootme.exception.InvalidInput
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.zip.ZipFile

data class ImportColumn(val index: Int, val label: String)
data class ImportSource(val name: String, val columns: List<ImportColumn>)
internal data class ImportRow(val number: Int, val values: Map<Int, String>)

@Component
class ImportFiles {
    internal fun <T> genealogyMdb(file: MultipartFile, read: (com.healthmarketscience.jackcess.Database) -> T): T =
        readFile(file) { path, extension ->
            if (extension != "mdb") throw ApiFailure(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "GENEALOGY_MDB_SCHEMA_UNSUPPORTED", "Choose an MDB genealogy database.",
            )
            access(path, read)
        }

    fun sources(file: MultipartFile): List<ImportSource> = readFile(file) { path, extension ->
        if (extension in spreadsheets) excel(path) { workbook ->
            (0 until workbook.numberOfSheets).map { sheetIndex ->
                val sheet = workbook.getSheetAt(sheetIndex)
                ImportSource(sheet.sheetName, excelColumns(header(sheet)))
            }
        } else access(path) { database ->
            database.tableNames.filter { database.getTableMetaData(it)?.isLinked == false }.sorted().map { name ->
                val table = database.getTable(name)
                ImportSource(name, columns(table.columns.map { it.name }))
            }
        }
    }

    internal fun rows(file: MultipartFile, source: String, selected: Set<Int>): List<ImportRow> = readFile(file) { path, extension ->
        if (extension in spreadsheets) excel(path) { workbook ->
            val sheet = workbook.getSheet(source) ?: invalidSource()
            val header = header(sheet) ?: invalidSource()
            val columns = excelColumns(header)
            validateColumns(columns, selected)
            val formatter = DataFormatter(Locale.ROOT)
            val result = mutableListOf<ImportRow>()
            for (row in sheet) {
                if (row.rowNum <= header.rowNum) continue
                append(result, row.rowNum + 1, selected.associateWith { index -> cellText(row.getCell(index), formatter) })
            }
            result
        } else access(path) { database ->
            if (source !in database.tableNames || database.getTableMetaData(source)?.isLinked != false) invalidSource()
            val table = database.getTable(source)
            val columns = columns(table.columns.map { it.name })
            validateColumns(columns, selected)
            val result = mutableListOf<ImportRow>()
            for ((index, row) in table.withIndex()) {
                append(result, index + 1, selected.associateWith { column ->
                    when (val value = row[columns[column].label]) {
                        null -> ""
                        is LocalDate -> value.toString()
                        is LocalDateTime -> value.toLocalDate().toString()
                        is String, is Number, is Boolean -> value.toString()
                        else -> throw InvalidInput("mapping", "Select text, number or date columns.")
                    }
                })
            }
            result
        }
    }

    private fun append(rows: MutableList<ImportRow>, number: Int, values: Map<Int, String>) {
        if (values.values.all { it.isBlank() }) return
        if (rows.size == 5_000) throw InvalidInput("file", "Import a maximum of 5,000 populated rows at a time.")
        if (values.values.any { it.length > 100_000 }) throw InvalidInput("file", "An imported cell exceeds 100,000 characters.")
        rows.add(ImportRow(number, values))
    }

    private fun excelColumns(row: org.apache.poi.ss.usermodel.Row?): List<ImportColumn> {
        if (row == null) return emptyList()
        val formatter = DataFormatter(Locale.ROOT)
        return columns((0 until row.lastCellNum.toInt()).map { index ->
            cellText(row.getCell(index), formatter).trim().ifBlank { "Column ${index + 1}" }
        })
    }

    private fun header(sheet: Sheet) = sheet.firstOrNull { row ->
        row.any { cell -> when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.isNotBlank()
            CellType.BLANK, CellType.ERROR, CellType.FORMULA -> false
            else -> true
        } }
    }

    private fun columns(names: List<String>): List<ImportColumn> {
        if (names.size > 128) throw InvalidInput("source", "A source can contain at most 128 columns.")
        return names.mapIndexed { index, name -> ImportColumn(index, name.take(255)) }
    }

    private fun validateColumns(columns: List<ImportColumn>, selected: Set<Int>) {
        if (selected.any { it !in columns.indices }) throw InvalidInput("mapping", "Select columns from the chosen source.")
    }

    private fun cellText(cell: Cell?, formatter: DataFormatter): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(cell)) {
                cell.localDateTimeCellValue.toLocalDate().toString()
            } else formatter.formatCellValue(cell)
            CellType.FORMULA -> "" // Formulas are deliberately not evaluated or imported.
            CellType.ERROR -> ""
            else -> formatter.formatCellValue(cell)
        }
    }

    private fun <T> excel(path: Path, read: (org.apache.poi.ss.usermodel.Workbook) -> T): T =
        WorkbookFactory.create(path.toFile(), null, true).use(read)

    private fun <T> access(path: Path, read: (com.healthmarketscience.jackcess.Database) -> T): T =
        DatabaseBuilder(path.toFile()).setReadOnly(true).open().use {
            it.dateTimeType = DateTimeType.LOCAL_DATE_TIME
            read(it)
        }

    private fun <T> readFile(file: MultipartFile, read: (Path, String) -> T): T {
        if (file.isEmpty || file.size > 20L * 1024 * 1024) throw InvalidInput("file", "Choose a file of at most 20 MiB.")
        val extension = file.originalFilename.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in spreadsheets && extension !in databases) throw ApiFailure(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMPORT_FORMAT_UNSUPPORTED", "Use XLS, XLSX, MDB or ACCDB.",
        )
        val path = Files.createTempFile("rootme-import-", ".$extension")
        try {
            file.transferTo(path)
            when (extension) {
                "xlsx" -> {
                    requireSignature(path, byteArrayOf(80, 75, 3, 4))
                    guardWorkbookZip(path)
                }
                "xls" -> requireSignature(path, byteArrayOf(-48, -49, 17, -32, -95, -79, 26, -31))
            }
            return read(path, extension)
        } catch (error: Exception) {
            if (error is InvalidInput || error is ApiFailure) throw error
            throw ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "IMPORT_FILE_INVALID", "The file cannot be read or is protected.")
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun requireSignature(path: Path, signature: ByteArray) {
        if (!Files.newInputStream(path).use { it.readNBytes(signature.size) }.contentEquals(signature)) {
            throw InvalidInput("file", "The file content does not match its extension.")
        }
    }

    private fun guardWorkbookZip(path: Path) {
        ZipFile(path.toFile()).use { zip ->
            if (zip.size() > 2_000) throw InvalidInput("file", "The workbook contains too many entries.")
            var size = 0L
            val buffer = ByteArray(32 * 1024)
            for (entry in zip.entries().asSequence()) {
                if (entry.size !in 0..MAX_EXPANDED || size + entry.size > MAX_EXPANDED) {
                    throw InvalidInput("file", "The expanded workbook exceeds 64 MiB.")
                }
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > MAX_EXPANDED) throw InvalidInput("file", "The expanded workbook exceeds 64 MiB.")
                    }
                }
            }
        }
    }

    private fun invalidSource(): Nothing = throw InvalidInput("source", "The selected source was not found.")

    private companion object {
        val spreadsheets = setOf("xls", "xlsx")
        val databases = setOf("mdb", "accdb")
        const val MAX_EXPANDED = 64L * 1024 * 1024
    }
}
