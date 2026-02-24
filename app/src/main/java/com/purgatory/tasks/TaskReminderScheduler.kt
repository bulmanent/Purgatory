package com.purgatory.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object TaskReminderScheduler {
    private const val WORK_PREFIX = "task_reminder_"

    fun sync(context: Context, tasks: List<Task>) {
        val defaultUser = AppSettings.getDefaultUser(context)
        val bothOwnerName = AppUsers.byId("both")?.displayName
        tasks.forEach { task ->
            if (shouldSchedule(task, defaultUser, bothOwnerName)) {
                schedule(context, task)
            } else {
                cancel(context, task)
            }
        }
    }

    fun eligibleTasks(context: Context, tasks: List<Task>): List<Task> {
        val defaultUser = AppSettings.getDefaultUser(context)
        val bothOwnerName = AppUsers.byId("both")?.displayName
        return tasks.filter { shouldSchedule(it, defaultUser, bothOwnerName) }
    }

    fun nextReminderTime(tasks: List<Task>, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        return tasks.mapNotNull { task ->
            val notifyTime = task.notifyTime ?: return@mapNotNull null
            nextRunAt(now, notifyTime)
        }.minOrNull()
    }

    private fun shouldSchedule(task: Task, defaultUser: String?, bothOwnerName: String?): Boolean {
        return task.notifyEnabled &&
            task.notifyTime != null &&
            task.status != TaskStatus.COMPLETE &&
            isWithinScope(task) &&
            isOwnedByCurrentUser(task, defaultUser, bothOwnerName)
    }

    private fun isOwnedByCurrentUser(task: Task, defaultUser: String?, bothOwnerName: String?): Boolean {
        if (defaultUser.isNullOrBlank()) return true
        val ownerName = task.owner?.displayName ?: return false
        if (ownerName.equals(defaultUser, ignoreCase = true)) return true
        if (!bothOwnerName.isNullOrBlank() && ownerName.equals(bothOwnerName, ignoreCase = true)) return true
        return false
    }

    private fun isWithinScope(task: Task): Boolean {
        val dueDate = task.dueDate ?: return false
        val today = LocalDate.now()
        return !dueDate.isAfter(today.plusDays(7))
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
        val nextRun = nextRunAt(now, targetTime)
        return Duration.between(now, nextRun)
    }

    private fun nextRunAt(now: LocalDateTime, targetTime: LocalTime): LocalDateTime {
        return if (now.toLocalTime().isBefore(targetTime)) {
            now.toLocalDate().atTime(targetTime)
        } else {
            now.toLocalDate().plusDays(1).atTime(targetTime)
        }
    }
}
