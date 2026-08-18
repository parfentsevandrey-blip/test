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
 * The agenda card's half of what [MonthWidgetProvider] does, minus the month navigation the
 * agenda does not have. The two providers share the schedulers — the midnight alarm, the
 * calendar watch, the theme watch — and either of them keeps those alive for both: the jobs are
 * cancelled only when the home screen holds no Quire calendar card of any kind.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                when (intent.action) {
                    ACTION_REFRESH -> {
                        MidnightScheduler.schedule(context)
                        renderAll(context)
                    }
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        MidnightScheduler.schedule(context)
                        CalendarWatchService.schedule(context)
                        renderAll(context)
                    }
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_LOCALE_CHANGED,
                    -> {
                        MidnightScheduler.schedule(context)
                        renderAll(context)
                    }
                    else -> super.onReceive(context, intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "agenda widget update failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        // Boot re-arm, same as the month card: the pulse cannot be persisted without the boot
        // permission this app refuses to carry, and the launcher's re-bind is the wake instead.
        CalendarWatchService.schedule(context)
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
        // The last agenda card is gone, but the month cards may not be: the schedulers belong
        // to whichever cards remain.
        if (!MonthWidgetProvider.placed(context)) {
            MidnightScheduler.cancel(context)
            CalendarWatchService.cancel(context)
        }
    }

    companion object {
        private const val TAG = "QuireAgenda"

        const val ACTION_REFRESH = "app.quire.calendar.AGENDA_REFRESH"

        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "quire-agenda").apply { isDaemon = true }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, AgendaWidgetRenderer.build(context, manager, widgetId))
        }

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))
                .forEach { render(context, manager, it) }
        }

        /** Whether any agenda card is placed at all. */
        fun placed(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            return manager
                .getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))
                .isNotEmpty()
        }

        /** Ask every placed agenda card to redraw; safe to call from the main thread. */
        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, AgendaWidgetProvider::class.java).setAction(ACTION_REFRESH),
            )
        }
    }
}
