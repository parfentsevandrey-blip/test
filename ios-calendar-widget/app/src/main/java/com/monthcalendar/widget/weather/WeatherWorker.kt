package com.monthcalendar.widget.weather

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Refreshes the cached weather snapshot and re-renders every widget. */
class WeatherWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Only re-render when the fetch actually advanced the cache. A soft
            // failure (offline) must NOT trigger updateAll → provideGlance →
            // re-enqueue, which would spin the stale/cold self-heal loop.
            val fresh = WeatherRepository.refresh(applicationContext, System.currentTimeMillis())
            if (fresh != null) WeatherWidget().updateAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "weather_periodic"
        private const val ONCE = "weather_once"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** User-triggered (tap-to-retry / city change): run as soon as possible. */
        fun enqueueExpedited(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

/** Tap-to-retry from an error/offline widget state. */
class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WeatherWorker.enqueueExpedited(context)
    }
}
