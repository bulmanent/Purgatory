package com.purgatory.tasks

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val altFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

    fun formatTime(time: LocalTime?): String {
        return time?.format(timeFormatter) ?: ""
    }

    fun parseTime(input: String?): LocalTime? {
        val value = input?.trim().orEmpty()
        if (value.isBlank()) return null
        return try {
            LocalTime.parse(value, timeFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseBoolean(input: String?): Boolean {
        val value = input?.trim().orEmpty()
        return value.equals("true", ignoreCase = true) ||
            value.equals("yes", ignoreCase = true) ||
            value == "1"
    }

    fun formatBoolean(enabled: Boolean): String {
        return if (enabled) "true" else "false"
    }
}
