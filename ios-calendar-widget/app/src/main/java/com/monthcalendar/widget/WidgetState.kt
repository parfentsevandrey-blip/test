package com.monthcalendar.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/** Per-widget displayed-month offset (0 = current month, +1 = next, …). */
val MONTH_OFFSET = intPreferencesKey("month_offset")

private val DELTA_KEY = ActionParameters.Key<Int>("delta")

fun shiftParams(delta: Int): ActionParameters = actionParametersOf(DELTA_KEY to delta)

/** Step the displayed month by ±1 (or any delta) for the tapped widget. */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA_KEY] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MONTH_OFFSET] = (prefs[MONTH_OFFSET] ?: 0) + delta
        }
        CalendarWidget().update(context, glanceId)
    }
}

/** Jump the tapped widget back to the current month. */
class ResetMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MONTH_OFFSET] = 0
        }
        CalendarWidget().update(context, glanceId)
    }
}
