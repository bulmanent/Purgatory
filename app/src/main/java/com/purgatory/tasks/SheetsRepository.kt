package com.purgatory.tasks

import java.time.LocalDate

class SheetsRepository(private val client: SheetsClient = SheetsClient()) {
    suspend fun loadTasks(accessToken: String, spreadsheetId: String): List<Task> {
        val rows = client.fetchTasks(accessToken, spreadsheetId)
        return rows.mapIndexedNotNull { index, row ->
            val details = row.getOrNull(0)?.trim().orEmpty()
            if (details.isBlank()) return@mapIndexedNotNull null
            val importance = row.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val owner = AppUsers.byDisplayName(row.getOrNull(2))
            val status = TaskStatus.fromSheet(row.getOrNull(3))
            val date = DateUtils.parse(row.getOrNull(4))
            Task(
                rowIndex = index + 2,
                details = details,
                importance = importance,
                owner = owner,
                status = status,
                dueDate = date
            )
        }
    }

    suspend fun addTask(
        accessToken: String,
        spreadsheetId: String,
        details: String,
        importance: Int,
        owner: AppUser?,
        status: TaskStatus,
        dueDate: LocalDate?
    ) {
        val values = listOf(
            details,
            importance.toString(),
            owner?.displayName.orEmpty(),
            status.sheetValue,
            DateUtils.format(dueDate)
        )
        client.appendTask(accessToken, spreadsheetId, values)
    }

    suspend fun updateTask(
        accessToken: String,
        spreadsheetId: String,
        task: Task
    ) {
        val values = listOf(
            task.details,
            task.importance.toString(),
            task.owner?.displayName.orEmpty(),
            task.status.sheetValue,
            DateUtils.format(task.dueDate)
        )
        client.updateTask(accessToken, spreadsheetId, task.rowIndex, values)
    }
}
