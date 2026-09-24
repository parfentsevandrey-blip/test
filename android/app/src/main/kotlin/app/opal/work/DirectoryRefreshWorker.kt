package app.opal.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.opal.AppGraph
import java.util.concurrent.TimeUnit

/**
 * Keeps Tor's directory "reasonably live" while the VPN is off, so the next connection skips the
 * full directory download (warm cache). Tor is brought up through the last working bridge just long
 * enough to fetch consensus diffs, then stopped again. Skipped when the cache is still fresh or the
 * tunnel is running (Tor refreshes itself then).
 */
class DirectoryRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppGraph.settings.current()
        if (!settings.onboardingCompleted || !settings.backgroundDirectoryRefresh) {
            return Result.success()
        }
        val memory = AppGraph.memory.current()
        val last = maxOf(memory.lastBootstrapAt ?: 0L, memory.lastDirectoryRefreshAt ?: 0L)
        if (System.currentTimeMillis() - last < FRESH_ENOUGH_MS) return Result.success()
        // A failure (no route, blocked network) simply waits for the next period: retrying sooner
        // would only spend battery on a network that does not let Tor through.
        AppGraph.tunnel.refreshDirectory(REFRESH_TIMEOUT_MS)
        return Result.success()
    }

    companion object {
        /** A consensus is "reasonably live" for 24 h; refreshing after 10 h keeps a wide margin. */
        private val FRESH_ENOUGH_MS = TimeUnit.HOURS.toMillis(10)
        private val REFRESH_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(4)
        private const val PERIOD_HOURS = 12L
        private const val WORK_UNMETERED = "directory-refresh-unmetered"
        private const val WORK_CHARGING = "directory-refresh-charging"

        /**
         * Two periodic requests, whichever gets its constraints first wins the cycle (the second
         * one then finds the cache fresh and exits): Wi-Fi/unmetered with enough battery, or
         * charging on any network.
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val unmetered =
                PeriodicWorkRequestBuilder<DirectoryRefreshWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            val charging =
                PeriodicWorkRequestBuilder<DirectoryRefreshWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresCharging(true)
                            .build()
                    )
                    .build()
            wm.enqueueUniquePeriodicWork(WORK_UNMETERED, ExistingPeriodicWorkPolicy.KEEP, unmetered)
            wm.enqueueUniquePeriodicWork(WORK_CHARGING, ExistingPeriodicWorkPolicy.KEEP, charging)
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_UNMETERED)
            wm.cancelUniqueWork(WORK_CHARGING)
        }
    }
}
