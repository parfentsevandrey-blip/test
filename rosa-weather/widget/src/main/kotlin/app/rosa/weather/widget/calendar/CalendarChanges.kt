package app.rosa.weather.widget.calendar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import app.rosa.weather.core.model.WidgetFace
import app.rosa.weather.widget.provider.CalendarWidgetProvider
import app.rosa.weather.widget.provider.widgetGraph
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * An event added, moved or deleted on the phone's calendars shows on the calendar widgets within
 * seconds, not at their next tick: a job the system runs when the calendars change. It is armed
 * again on every run, so nothing stays alive in between, and lapses once no widget shows events.
 */
internal object CalendarChanges {
    private const val TAG = "calendar_changes"

    /** Waits for the next change on the phone's calendars, unless a watch is already waiting. */
    suspend fun watch(context: Context) {
        val work = WorkManager.getInstance(context)
        if (work.getWorkInfosByTagFlow(TAG).first().any { it.state == WorkInfo.State.ENQUEUED }) return
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            // One edit in a calendar app writes several rows: redraw once, when they have settled.
            .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(15, TimeUnit.SECONDS)
            .build()
        work.enqueue(OneTimeWorkRequestBuilder<CalendarChangesWorker>().setConstraints(constraints).addTag(TAG).build()).await()
    }

    /** No calendar widget left: stop waking up for calendar edits. */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    /** Whether any placed calendar widget shows events it may read. */
    suspend fun wanted(context: Context, ids: IntArray): Boolean {
        if (ids.isEmpty() || !CalendarEvents.granted(context)) return false
        val configs = context.widgetGraph().configs().snapshot()
        return ids.any { configs[it].let { config -> config.face == WidgetFace.Calendar && config.calendar.events } }
    }
}

/** The phone's calendars changed: watch for the next change, then redraw the calendar widgets. */
class CalendarChangesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
        if (!CalendarChanges.wanted(context, ids)) return Result.success()
        // Armed before the redraw reads the events, so a change made meanwhile is not missed.
        CalendarChanges.watch(context)
        context.widgetGraph().updater().update(ids)
        return Result.success()
    }
}
