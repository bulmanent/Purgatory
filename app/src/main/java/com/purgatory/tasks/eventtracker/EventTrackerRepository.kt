package com.purgatory.tasks.eventtracker

import android.content.Context
import com.purgatory.tasks.AppSettings
import com.purgatory.tasks.ServiceAccountAuth
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class EventTrackerRepository(
    private val context: Context,
    private val client: EventTrackerSheetsClient = EventTrackerSheetsClient()
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun getEventTypes(): List<EventType> {
        val (token, spreadsheetId) = resolveCredentials()
        val rows = client.fetchRange(token, spreadsheetId, "EventTracker!J2:J")
        val names = readContiguousEventNames(rows)
        return names
            .sortedBy { it.lowercase() }
            .map { EventType(it) }
    }

    suspend fun addEventType(name: String): Result<Unit> {
        val newName = name.trim()
        if (newName.isBlank()) {
            return Result.failure(IllegalArgumentException("Event name is required"))
        }

        return runCatching {
            val (token, spreadsheetId) = resolveCredentials()
            val rows = client.fetchRange(token, spreadsheetId, "EventTracker!J2:J")
            val existingNames = readContiguousEventNames(rows)
            if (existingNames.any { it.equals(newName, ignoreCase = true) }) {
                throw IllegalStateException("Event already exists")
            }

            val nextRow = existingNames.size + 2
            client.updateRange(
                token,
                spreadsheetId,
                "EventTracker!J$nextRow",
                listOf(listOf(newName))
            )
        }
    }

    suspend fun logEvent(
        eventName: String,
        details: String,
        severity: String,
        action: String
    ): Result<Unit> {
        return runCatching {
            val (token, spreadsheetId) = resolveCredentials()
            val row = listOf(
                eventName.trim(),
                LocalDate.now().toString(),
                LocalTime.now().format(timeFormatter),
                details.trim(),
                severity.trim(),
                action.trim()
            )
            client.appendRange(token, spreadsheetId, "EventTracker!A:F", row)
        }
    }

    suspend fun getEventLog(eventName: String?): List<EventEntry> {
        val (token, spreadsheetId) = resolveCredentials()
        val rows = client.fetchRange(token, spreadsheetId, "EventTracker!A2:F")
        val normalizedName = eventName?.trim().orEmpty()
        val entries = rows.mapNotNull { row ->
            val event = row.getOrNull(0)?.trim().orEmpty()
            if (event.isBlank()) {
                return@mapNotNull null
            }
            EventEntry(
                event = event,
                date = row.getOrNull(1)?.trim().orEmpty(),
                time = row.getOrNull(2)?.trim().orEmpty(),
                details = row.getOrNull(3)?.trim().orEmpty(),
                severity = row.getOrNull(4)?.trim().orEmpty(),
                action = row.getOrNull(5)?.trim().orEmpty()
            )
        }

        return entries
            .asReversed()
            .filter {
                normalizedName.isBlank() || it.event.equals(normalizedName, ignoreCase = true)
            }
    }

    suspend fun exportToReport(): Result<Int> {
        return runCatching {
            val entries = getEventLog(null)
            val (token, spreadsheetId) = resolveCredentials()
            val allRows = mutableListOf(
                listOf("Event", "Date", "Time", "Details", "Severity", "Action")
            )
            allRows.addAll(entries.map {
                listOf(it.event, it.date, it.time, it.details, it.severity, it.action)
            })

            client.clearRange(token, spreadsheetId, "Report!A:F")
            client.updateRange(
                token,
                spreadsheetId,
                "Report!A1:F${allRows.size}",
                allRows
            )
            entries.size
        }
    }

    private fun readContiguousEventNames(rows: List<List<String>>): List<String> {
        val names = mutableListOf<String>()
        for (row in rows) {
            val value = row.getOrNull(0)?.trim().orEmpty()
            if (value.isBlank()) {
                break
            }
            names.add(value)
        }
        return names
    }

    private fun resolveCredentials(): Pair<String, String> {
        val spreadsheetId = AppSettings.getSpreadsheetId(context)
            ?: throw IllegalStateException("Add the Spreadsheet ID in Settings to load tasks.")
        val token = ServiceAccountAuth.getAccessToken(context)
        return token to spreadsheetId
    }
}
