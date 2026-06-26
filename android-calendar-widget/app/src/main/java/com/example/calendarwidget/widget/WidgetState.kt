package com.example.calendarwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.calendarwidget.data.CalendarMath

/** Per-widget interaction state (currently displayed month) held in Glance prefs. */
object WidgetStateKeys {
    val displayedYear = intPreferencesKey("displayed_year")
    val displayedMonth = intPreferencesKey("displayed_month")
}

/** Reads the displayed (year, month0) for a widget, defaulting to the current month. */
suspend fun readDisplayedMonth(context: Context, id: GlanceId): Pair<Int, Int> {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
    val today = CalendarMath.today()
    val year = prefs[WidgetStateKeys.displayedYear] ?: today.year
    val month = prefs[WidgetStateKeys.displayedMonth] ?: today.month
    return year to month
}

/** Steps the displayed month by ±1 and refreshes the widget (header `‹` / `›`). */
class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val delta = parameters[DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val today = CalendarMath.today()
            val curYear = prefs[WidgetStateKeys.displayedYear] ?: today.year
            val curMonth = prefs[WidgetStateKeys.displayedMonth] ?: today.month
            val (year, month) = CalendarMath.addMonths(curYear, curMonth, delta)
            prefs[WidgetStateKeys.displayedYear] = year
            prefs[WidgetStateKeys.displayedMonth] = month
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}
