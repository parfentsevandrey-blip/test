package app.quire.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import app.quire.calendar.core.Prefs
import java.util.concurrent.Executors

/**
 * Every branch of onReceive runs off the main thread behind goAsync(): the
 * calendar provider is a cross-process query and a widget update must not sit
 * on the broadcast thread waiting for it.
 */
class MonthWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                when (intent.action) {
                    ACTION_PREV, ACTION_NEXT, ACTION_TODAY -> navigate(context, intent)
                    ACTION_REFRESH -> {
                        MidnightScheduler.schedule(context)
                        renderAll(context)
                    }
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        // A job keeps the triggers it was scheduled with, and only re-arms itself
                        // when it runs. An update that adds one — the theme setting did — would
                        // otherwise never reach a widget that was already placed.
                        MidnightScheduler.schedule(context)
                        CalendarWatchService.schedule(context)
                        renderAll(context)
                    }
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_LOCALE_CHANGED,
                    -> {
                        returnToToday(context)
                        MidnightScheduler.schedule(context)
                        renderAll(context)
                    }
                    else -> super.onReceive(context, intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "widget update failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        render(context, manager, widgetId)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = Prefs.get(context)
        ids.forEach { prefs.forgetWidget(it) }
    }

    override fun onEnabled(context: Context) {
        MidnightScheduler.schedule(context)
        CalendarWatchService.schedule(context)
    }

    override fun onDisabled(context: Context) {
        MidnightScheduler.cancel(context)
        CalendarWatchService.cancel(context)
    }

    private fun navigate(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val prefs = Prefs.get(context).widget(widgetId)
        prefs.monthOffset = when (intent.action) {
            ACTION_PREV -> (prefs.monthOffset - 1).coerceAtLeast(-1200)
            ACTION_NEXT -> (prefs.monthOffset + 1).coerceAtMost(1200)
            else -> 0
        }
        render(context, AppWidgetManager.getInstance(context), widgetId)
    }

    companion object {
        private const val TAG = "QuireWidget"

        const val ACTION_PREV = "app.quire.calendar.PREV"
        const val ACTION_NEXT = "app.quire.calendar.NEXT"
        const val ACTION_TODAY = "app.quire.calendar.TODAY"
        const val ACTION_REFRESH = "app.quire.calendar.REFRESH"

        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "quire-widget").apply { isDaemon = true }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, WidgetRenderer.build(context, manager, widgetId))
        }

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
                .forEach { render(context, manager, it) }
        }

        private fun returnToToday(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val prefs = Prefs.get(context)
            manager.getAppWidgetIds(ComponentName(context, MonthWidgetProvider::class.java))
                .forEach { prefs.widget(it).monthOffset = 0 }
        }

        /** Ask every placed widget to redraw; safe to call from the main thread. */
        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, MonthWidgetProvider::class.java).setAction(ACTION_REFRESH),
            )
        }
    }
}
