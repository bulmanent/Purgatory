package com.purgatory.tasks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val altFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun format(date: LocalDate?): String {
        return date?.format(displayFormatter) ?: ""
    }

    fun parse(input: String?): LocalDate? {
        val value = input?.trim().orEmpty()
        if (value.isBlank()) return null
        return try {
            LocalDate.parse(value, displayFormatter)
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(value, altFormatter)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun unassignedDate(): LocalDate = LocalDate.of(2099, 12, 31)
}
