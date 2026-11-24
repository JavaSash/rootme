package ru.stepenko.rootme.model.person

import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import ru.stepenko.rootme.exception.InvalidInput
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

enum class DatePrecision { YEAR, MONTH, DAY }

@Embeddable
data class PartialDate(
    val value: LocalDate,
    @Enumerated(EnumType.STRING)
    val precision: DatePrecision,
) {
    val latest: LocalDate
        get() = when (precision) {
            DatePrecision.YEAR -> value.withMonth(12).withDayOfMonth(31)
            DatePrecision.MONTH -> YearMonth.from(value).atEndOfMonth()
            DatePrecision.DAY -> value
        }

    fun toIso(): String = when (precision) {
        DatePrecision.YEAR -> value.toString().take(4)
        DatePrecision.MONTH -> YearMonth.from(value).toString()
        DatePrecision.DAY -> value.toString()
    }

    companion object {
        private val format = Regex("[0-9]{4}(?:-[0-9]{2}(?:-[0-9]{2})?)?")

        fun parse(field: String, text: String?): PartialDate? {
            if (text == null) return null
            if (!format.matches(text) || text.startsWith("0000")) invalidDate(field)
            return try {
                when (text.length) {
                    4 -> PartialDate(LocalDate.of(text.toInt(), 1, 1), DatePrecision.YEAR)
                    7 -> PartialDate(YearMonth.parse(text).atDay(1), DatePrecision.MONTH)
                    else -> PartialDate(LocalDate.parse(text), DatePrecision.DAY)
                }
            } catch (_: DateTimeException) {
                invalidDate(field)
            }
        }

        private fun invalidDate(field: String): Nothing =
            throw InvalidInput(field, "Use a valid date in YYYY, YYYY-MM or YYYY-MM-DD format.")

        fun validateOrder(start: PartialDate?, end: PartialDate?, endField: String) {
            if (start != null && end != null && start.value > end.latest) {
                throw InvalidInput(endField, "The end date is earlier than the start date.")
            }
        }
    }
}
