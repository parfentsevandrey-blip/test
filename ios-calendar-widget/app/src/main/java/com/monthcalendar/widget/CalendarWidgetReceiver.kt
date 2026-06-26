package com.monthcalendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Binds [CalendarWidget] to the AppWidget host. The manifest already wakes us on
 * DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED so "today" moves at midnight; the
 * periodic worker is a belt-and-braces re-render in case those broadcasts are
 * missed (e.g. Doze).
 */
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CalendarRefreshWorker.enqueueDaily(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CalendarRefreshWorker.enqueueDaily(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CalendarRefreshWorker.cancel(context)
    }
}
