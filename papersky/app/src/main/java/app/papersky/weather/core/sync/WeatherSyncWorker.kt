package app.papersky.weather.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.papersky.weather.container
import app.papersky.weather.widget.WidgetDirectory
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class WeatherSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val mode = inputData.getString(KEY_MODE) ?: MODE_FULL
        val periodic = inputData.getBoolean(KEY_PERIODIC, false)
        val force = inputData.getBoolean(KEY_FORCE, false)

        var outcome: WeatherSync.Outcome? = null
        try {
            if (mode == MODE_FULL) outcome = c.sync.sync(foreground = false, force = force)
        } finally {
            withContext(NonCancellable) {
                c.syncStatus.pending.value = false
                // Always re-render: even without new data the clock moved and "now" shifted.
                WidgetDirectory.updateAll(applicationContext)
                runCatching { SyncScheduler.scheduleSceneShift(applicationContext) }
            }
        }

        val result = outcome ?: return Result.success()
        if (result.succeeded > 0) WidgetDirectory.publishPreviewsIfDue(applicationContext)
        return when {
            !result.allFailed -> Result.success()
            periodic -> {
                // Don't let the periodic chain back off; hand the download to a network-gated job.
                SyncScheduler.syncWhenOnline(applicationContext)
                Result.success()
            }
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_FORCE = "force"
        const val KEY_PERIODIC = "periodic"
        const val MODE_FULL = "full"
        const val MODE_RENDER = "render"
        private const val MAX_ATTEMPTS = 4
    }
}
