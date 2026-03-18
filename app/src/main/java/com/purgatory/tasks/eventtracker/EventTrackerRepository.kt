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
    private val eventTypeSheetRange = "Events!A2:A"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun getEventTypes(): List<EventType> {
        val (token, spreadsheetId) = resolveCredentials()
        val rows = client.fetchRange(token, spreadsheetId, eventTypeSheetRange)
        val names = readEventNames(rows)
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
            val rows = client.fetchRange(token, spreadsheetId, eventTypeSheetRange)
            val existingNames = readEventNames(rows)
            if (existingNames.any { it.equals(newName, ignoreCase = true) }) {
                throw IllegalStateException("Event already exists")
            }

            client.appendRange(token, spreadsheetId, eventTypeSheetRange, listOf(newName))
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

    suspend fun exportToReport(eventNames: List<String>? = null): Result<Int> {
        return runCatching {
            val selectedNames = eventNames
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()
            val entries = getEventLog(null).filter { entry ->
                selectedNames.isEmpty() || selectedNames.any { it.equals(entry.event, ignoreCase = true) }
            }
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

    private fun readEventNames(rows: List<List<String>>): List<String> {
        return rows
            .mapNotNull { row -> row.getOrNull(0)?.trim() }
            .filter { value ->
                value.isNotBlank() &&
                    !value.equals("Event", ignoreCase = true) &&
                    !value.equals("Event Type", ignoreCase = true)
            }
    }

    private fun resolveCredentials(): Pair<String, String> {
        val spreadsheetId = AppSettings.getSpreadsheetId(context)
            ?: throw IllegalStateException("Add the Spreadsheet ID in Settings to load tasks.")
        val token = ServiceAccountAuth.getAccessToken(context)
        return token to spreadsheetId
    }
}
