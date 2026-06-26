package com.monthcalendar.widget.weather

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Binds [WeatherWidget] to the host and schedules the periodic refresh. */
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.enqueuePeriodic(context)
        WeatherWorker.enqueueOnce(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWorker.enqueuePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWorker.cancel(context)
    }
}
