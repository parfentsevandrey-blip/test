package com.monthcalendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Re-renders every widget instance so the grid keeps pace with the calendar.
 * Scheduled roughly at the next local midnight, then every 24h.
 */
class CalendarRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        CalendarWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "calendar_daily_refresh"

        fun enqueueDaily(context: Context) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIN)
            val initialDelay = Duration.between(now, nextMidnight)

            val request = PeriodicWorkRequestBuilder<CalendarRefreshWorker>(Duration.ofDays(1))
                .setInitialDelay(initialDelay)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
