package com.purgatory.tasks

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

object DateUtils {
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val altFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val altTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val secondsTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm:ss")
    private val numericTimeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("H[:mm[:ss]]")
        .withResolverStyle(java.time.format.ResolverStyle.SMART)

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
        return tryParseTime(value)
            ?: parseSpreadsheetTimeSerial(value)
    }

    private fun tryParseTime(value: String): LocalTime? {
        val formatters = listOf(timeFormatter, altTimeFormatter, secondsTimeFormatter, numericTimeFormatter)
        for (formatter in formatters) {
            try {
                val parsed = formatter.parse(value)
                val hour = parsed.get(ChronoField.HOUR_OF_DAY)
                val minute = if (parsed.isSupported(ChronoField.MINUTE_OF_HOUR)) {
                    parsed.get(ChronoField.MINUTE_OF_HOUR)
                } else {
                    0
                }
                val second = if (parsed.isSupported(ChronoField.SECOND_OF_MINUTE)) {
                    parsed.get(ChronoField.SECOND_OF_MINUTE)
                } else {
                    0
                }
                return LocalTime.of(hour, minute, second)
            } catch (_: DateTimeParseException) {
                // Try next formatter.
            }
        }
        return null
    }

    private fun parseSpreadsheetTimeSerial(value: String): LocalTime? {
        val serial = value.toDoubleOrNull() ?: return null
        if (serial < 0) return null
        val normalized = serial % 1.0
        val secondsInDay = 24 * 60 * 60
        val seconds = (normalized * secondsInDay).toLong().coerceIn(0, (secondsInDay - 1).toLong())
        return LocalTime.ofSecondOfDay(seconds)
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
