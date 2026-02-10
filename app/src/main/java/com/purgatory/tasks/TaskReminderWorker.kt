package com.purgatory.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.ensureChannel(applicationContext)

        val details = inputData.getString(KEY_TASK_DETAILS).orEmpty()
        val owner = inputData.getString(KEY_TASK_OWNER).orEmpty()
        val title = applicationContext.getString(R.string.notification_task_title)
        val content = if (owner.isBlank()) {
            details
        } else {
            "$details ($owner)"
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val id = inputData.getInt(KEY_TASK_ROW, 0).takeIf { it != 0 } ?: 2001
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_DETAILS = "task_details"
        const val KEY_TASK_OWNER = "task_owner"
        const val KEY_TASK_ROW = "task_row"
    }
}
