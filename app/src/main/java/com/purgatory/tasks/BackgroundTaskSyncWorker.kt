package com.purgatory.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackgroundTaskSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = SheetsRepository()

    override suspend fun doWork(): Result {
        val spreadsheetId = AppSettings.getSpreadsheetId(applicationContext)
        if (spreadsheetId.isNullOrBlank()) return Result.success()

        return try {
            val token = withContext(Dispatchers.IO) {
                ServiceAccountAuth.getAccessToken(applicationContext)
            }
            val tasks = withContext(Dispatchers.IO) {
                repository.loadTasks(token, spreadsheetId)
            }
            TaskReminderScheduler.sync(applicationContext, tasks)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
