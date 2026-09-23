package app.rosa.weather.core.data.sync

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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Freshness strategy, in layers:
 *  1. **Periodic** work (user interval, no network constraint) re-renders widgets for the current
 *     time and refreshes stale data when online.
 *  2. **Catch-up** one-shot with a network constraint fires the moment connectivity returns after
 *     a periodic run found the device offline.
 *  3. **Render ticks** — one-shot, no network — at the next moment the picture changes
 *     (nowcast rain start, hour boundary, sunrise/sunset) so widgets never show a stale sentence.
 *  4. **Refresh now** — expedited, on app start, widget tap or system events.
 */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun ensurePeriodic(intervalMinutes: Int, replaceExisting: Boolean = false) {
        val interval = intervalMinutes.coerceAtLeast(15).toLong()
        val flex = (interval / 4).coerceIn(5, 30)
        val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(interval, TimeUnit.MINUTES, flex, TimeUnit.MINUTES)
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_PERIODIC))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC,
            if (replaceExisting) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun refreshNow(force: Boolean = true, reason: SyncReason = SyncReason.UserRequest) {
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInputData(
                workDataOf(
                    WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_NOW,
                    WeatherSyncWorker.KEY_FORCE to force,
                    WeatherSyncWorker.KEY_REASON to reason.name,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleCatchUp() {
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_CATCH_UP))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(CATCH_UP, ExistingWorkPolicy.KEEP, request)
    }

    /** Re-render widgets (no network) after [delayMillis]; replaces any pending tick. */
    fun scheduleRenderTick(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_RENDER))
            .setInitialDelay(delayMillis.coerceAtLeast(60_000), TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(RENDER_TICK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "rosa.sync"
        const val PERIODIC = "rosa.sync.periodic"
        const val NOW = "rosa.sync.now"
        const val CATCH_UP = "rosa.sync.catchup"
        const val RENDER_TICK = "rosa.render.tick"
    }
}
