package com.meditation.timer

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_NAME = "daily_reminder"

    fun schedule(context: Context) {
        val initialDelay = computeInitialDelay()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun computeInitialDelay(): Duration {
        val now = LocalDateTime.now()
        val targetTime = LocalTime.of(9, 0)
        val nextRun = if (now.toLocalTime().isBefore(targetTime)) {
            now.toLocalDate().atTime(targetTime)
        } else {
            now.toLocalDate().plusDays(1).atTime(targetTime)
        }
        return Duration.between(now, nextRun)
    }
}
