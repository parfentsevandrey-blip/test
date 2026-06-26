package com.claude.tokenwidget.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Binds [TokenWidget] to the system AppWidget host and schedules the periodic
 * background refresh as soon as the first widget instance is placed.
 */
class TokenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokenWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UsageWorker.enqueuePeriodic(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Ensure the schedule survives reboots / re-adds.
        UsageWorker.enqueuePeriodic(context)
        UsageWorker.enqueueOnce(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        UsageWorker.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
