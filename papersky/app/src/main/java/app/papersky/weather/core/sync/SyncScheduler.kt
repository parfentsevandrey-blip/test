package app.papersky.weather.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.papersky.weather.container
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.widget.WidgetDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeps widgets honest with four kinds of work:
 *  - a periodic job (user interval, no constraints) that downloads when it can and *always*
 *    re-renders, so hourly strips and day/night advance even offline;
 *  - an expedited "now" job for pull-to-refresh, widget taps and first launch;
 *  - an "online" job that fires as soon as connectivity returns after an offline period;
 *  - a "scene shift" job at the next sunrise/sunset so the illustration flips exactly on time.
 */
object SyncScheduler {
    private const val PERIODIC = "papersky.periodic"
    private const val NOW = "papersky.now"
    private const val ONLINE = "papersky.online"
    private const val SCENE = "papersky.scene"

    fun ensurePeriodic(context: Context, intervalMinutes: Int, replace: Boolean = false) {
        val interval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(interval, TimeUnit.MINUTES, (interval / 4).coerceAtLeast(5), TimeUnit.MINUTES)
            .setInputData(workDataOf(WeatherSyncWorker.KEY_PERIODIC to true))
            .addTag(PERIODIC)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun refreshNow(context: Context, force: Boolean = true) {
        val c = context.container
        c.syncStatus.pending.value = true
        // If the job is parked waiting for a network, don't leave a spinner turning forever.
        c.appScope.launch {
            delay(45_000)
            c.syncStatus.pending.value = false
        }
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(WeatherSyncWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun syncWhenOnline(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONLINE, ExistingWorkPolicy.KEEP, request)
    }

    /** Re-render only (no network) at the next sunrise or sunset among places shown on widgets. */
    suspend fun scheduleSceneShift(context: Context) {
        val weather = context.container.weather
        weather.ensureLoaded()
        val now = System.currentTimeMillis() / 1000
        val placeIds = WidgetDirectory.placeIdsInUse(context)
        val next = placeIds.mapNotNull { weather.peek(it)?.nextSunEvent(now) }.minOrNull() ?: return
        val delaySec = (next - now + 45).coerceAtLeast(60)
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_RENDER))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SCENE, ExistingWorkPolicy.REPLACE, request)
    }

    private fun Forecast.nextSunEvent(now: Long): Long? =
        daily.asSequence().flatMap { sequenceOf(it.sunrise, it.sunset) }.filter { it > now }.minOrNull()
}
