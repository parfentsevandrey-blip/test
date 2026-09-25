package app.rosa.weather.widget.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.rosa.weather.widget.provider.widgetGraph

/**
 * After an arrow has flipped a calendar, the months around the new one are drawn in a job of
 * their own. The job keeps the process alive until their pages are kept, and the tap's broadcast
 * ends as soon as the month is on screen: broadcasts reach an app one at a time, so a next tap
 * would otherwise wait for this drawing.
 */
internal object CalendarAhead {
    private const val KEY_IDS = "ids"

    fun request(context: Context, widgetId: Int) {
        val work = OneTimeWorkRequestBuilder<CalendarAheadWorker>()
            .setInputData(workDataOf(KEY_IDS to intArrayOf(widgetId)))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // Queued behind a run still drawing for an earlier flip, which the flip has already cut short.
        WorkManager.getInstance(context).enqueueUniqueWork("calendar_ahead_$widgetId", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    internal fun ids(worker: CoroutineWorker): IntArray = worker.inputData.getIntArray(KEY_IDS) ?: IntArray(0)
}

class CalendarAheadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ids = CalendarAhead.ids(this)
        if (ids.isEmpty()) return Result.success()
        val updater = applicationContext.widgetGraph().updater()
        updater.drawAhead(ids)
        updater.settle()
        return Result.success()
    }
}
