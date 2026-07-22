package com.lumina.calendarwidget.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.lumina.calendarwidget.MainActivity
import com.lumina.calendarwidget.data.SettingsRepository

/** Parameter carrying the tapped day as an epoch-day (days since 1970-01-01). */
val EpochDayKey = ActionParameters.Key<Long>("lumina.epochDay")

/**
 * Toggle the persisted "selected date" and re-render every widget instance. This is the clearest
 * demonstration that the widget is live: tapping a day writes state and the grid redraws with the
 * selection marker, with no app launch.
 */
class SelectDateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[EpochDayKey] ?: return
        SettingsRepository(context).update { s ->
            s.copy(selectedEpochDay = if (s.selectedEpochDay == day) -1L else day)
        }
        CalendarGlanceWidget().updateAll(context)
    }
}

/** Clear any selection (used by the "Jump to today" header tap) and re-render. */
class JumpTodayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SettingsRepository(context).update { it.copy(selectedEpochDay = -1L) }
        CalendarGlanceWidget().updateAll(context)
    }
}

/** VIEW intent that opens the system calendar app at a specific instant. */
fun openCalendarIntent(epochMillis: Long): Intent {
    val uri = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .appendPath(epochMillis.toString())
        .build()
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Intent that opens Lumina's customization screen. */
fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
