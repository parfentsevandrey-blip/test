package com.cozyhome.weather.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cozyhome.weather.data.Place
import com.cozyhome.weather.data.WeatherRepository
import java.util.concurrent.TimeUnit

/** Refreshes the forecast and re-renders every widget instance. */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = WeatherRepository(applicationContext)
        val place = repository.savedPlace()
            ?: repository.cachedSnapshot()?.place
            ?: Place.DEFAULT
        val fetched = runCatching { repository.refresh(place) }
        runCatching { WeatherWidget().updateAll(applicationContext) }
        return if (fetched.isSuccess || runAttemptCount >= 2) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "cozy_widget_refresh"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
