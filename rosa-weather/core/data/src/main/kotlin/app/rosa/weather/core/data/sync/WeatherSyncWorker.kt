package app.rosa.weather.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class WeatherSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncer: WeatherSyncer,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: MODE_PERIODIC
        if (mode == MODE_RENDER) {
            scheduler.onRenderTickStarted()
            syncer.render(SyncReason.Render)
            return Result.success()
        }
        val reason = when (mode) {
            MODE_CATCH_UP -> SyncReason.CatchUp
            MODE_NOW -> inputData.getString(KEY_REASON)?.let { runCatching { SyncReason.valueOf(it) }.getOrNull() }
                ?: SyncReason.UserRequest
            else -> SyncReason.Periodic
        }
        val outcome = try {
            syncer.sync(reason, force = inputData.getBoolean(KEY_FORCE, false), inBackground = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Storage or location failures must not crash the process; try again later.
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
        return when (outcome) {
            SyncOutcome.Offline -> {
                scheduler.scheduleCatchUp()
                Result.success()
            }
            SyncOutcome.Failed -> when {
                mode == MODE_PERIODIC -> {
                    scheduler.scheduleCatchUp()
                    Result.success()
                }
                runAttemptCount < MAX_RETRIES -> Result.retry()
                else -> Result.success()
            }
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_FORCE = "force"
        const val KEY_REASON = "reason"
        const val MODE_PERIODIC = "periodic"
        const val MODE_NOW = "now"
        const val MODE_CATCH_UP = "catch_up"
        const val MODE_RENDER = "render"
        private const val MAX_RETRIES = 3
    }
}
