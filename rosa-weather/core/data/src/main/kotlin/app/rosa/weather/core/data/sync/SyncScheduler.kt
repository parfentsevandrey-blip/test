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
import androidx.core.content.edit
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
    private val prefs by lazy { context.getSharedPreferences("rosa_sync", Context.MODE_PRIVATE) }

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
        // A forced (user) refresh supersedes anything queued; an automatic one never cancels it.
        workManager.enqueueUniqueWork(NOW, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
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

    /**
     * Re-render widgets (no network) at [atMillis]. An already scheduled *earlier* tick is kept —
     * it will recompute the following deadline when it fires — so ticks only ever move closer.
     */
    fun scheduleRenderTick(atMillis: Long) {
        val now = System.currentTimeMillis()
        val target = atMillis.coerceAtLeast(now + 60_000)
        val existing = prefs.getLong(KEY_TICK_AT, 0L)
        if (existing > now + 30_000 && existing <= target) return
        prefs.edit { putLong(KEY_TICK_AT, target) }
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MODE to WeatherSyncWorker.MODE_RENDER))
            .setInitialDelay(target - now, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        // No recorded deadline means the previous tick is running (or done): queue behind it
        // rather than cancel the worker that is scheduling us. Otherwise a later pending tick
        // is simply replaced by this earlier one.
        val policy = if (existing == 0L) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(RENDER_TICK, policy, request)
    }

    /** Called when a tick fires so the next deadline can be scheduled freely. */
    internal fun onRenderTickStarted() {
        prefs.edit { remove(KEY_TICK_AT) }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
        // Forget the pending tick too, or a later widget could never schedule an earlier one.
        prefs.edit { remove(KEY_TICK_AT) }
    }

    companion object {
        const val TAG = "rosa.sync"
        const val PERIODIC = "rosa.sync.periodic"
        const val NOW = "rosa.sync.now"
        const val CATCH_UP = "rosa.sync.catchup"
        const val RENDER_TICK = "rosa.render.tick"
        private const val KEY_TICK_AT = "tick_at"
    }
}
