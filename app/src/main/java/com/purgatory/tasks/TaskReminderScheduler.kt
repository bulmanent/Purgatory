package com.purgatory.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object TaskReminderScheduler {
    private const val WORK_PREFIX = "task_reminder_"

    fun sync(context: Context, tasks: List<Task>) {
        tasks.forEach { task ->
            if (shouldSchedule(task)) {
                schedule(context, task)
            } else {
                cancel(context, task)
            }
        }
    }

    private fun shouldSchedule(task: Task): Boolean {
        return task.notifyEnabled &&
            task.notifyTime != null &&
            task.status != TaskStatus.COMPLETE
    }

    private fun schedule(context: Context, task: Task) {
        val notifyTime = task.notifyTime ?: return
        val initialDelay = computeInitialDelay(notifyTime)
        val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay)
            .setInputData(
                workDataOf(
                    TaskReminderWorker.KEY_TASK_DETAILS to task.details,
                    TaskReminderWorker.KEY_TASK_OWNER to task.owner?.displayName.orEmpty(),
                    TaskReminderWorker.KEY_TASK_ROW to task.rowIndex
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_PREFIX + task.rowIndex,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    private fun cancel(context: Context, task: Task) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_PREFIX + task.rowIndex)
    }

    private fun computeInitialDelay(targetTime: LocalTime): Duration {
        val now = LocalDateTime.now()
        val nextRun = if (now.toLocalTime().isBefore(targetTime)) {
            now.toLocalDate().atTime(targetTime)
        } else {
            now.toLocalDate().plusDays(1).atTime(targetTime)
        }
        return Duration.between(now, nextRun)
    }
}
