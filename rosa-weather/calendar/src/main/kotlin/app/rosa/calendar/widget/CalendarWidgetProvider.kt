package app.rosa.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.rosa.weather.widget.provider.launchAsync
import java.time.LocalDate

/**
 * The calendar widget: arrows move its month, midnight brings a new day. Drawing never happens on
 * the main thread and never waits for the network: the weather is whatever Rosa Weather has.
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val graph = context.calendarGraph()
        launchAsync {
            graph.calendars().update(appWidgetIds)
            graph.calendars().settle()
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized, rotated or moved: laid out again for the new exact sizes.
        val graph = context.calendarGraph()
        launchAsync {
            graph.calendars().update(intArrayOf(appWidgetId))
            graph.calendars().settle()
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CalendarNavigation(context).forget(appWidgetIds)
        CalendarPages.forget(context, appWidgetIds)
        val graph = context.calendarGraph()
        launchAsync { graph.calendarConfigs().remove(appWidgetIds.toList()) }
    }

    override fun onDisabled(context: Context) {
        // The last calendar is gone: nothing to wake up for at midnight or on calendar edits.
        CalendarChanges.stop(context)
        CalendarAlarm.cancel(context, ComponentName(context, CalendarWidgetProvider::class.java))
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val graph = context.calendarGraph()
        launchAsync {
            graph.calendarConfigs().remap(oldWidgetIds, newWidgetIds)
            graph.calendars().update(newWidgetIds)
            graph.calendars().settle()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CalendarIntents.ACTION_MONTH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val delta = intent.getIntExtra(CalendarIntents.EXTRA_DELTA, 0)
                val graph = context.calendarGraph()
                launchAsync {
                    CalendarNavigation(context).move(id, delta, LocalDate.now())
                    val updater = graph.calendars()
                    if (updater.flip(id)) {
                        // Drawn ahead: it only went to the launcher. The months around it are drawn
                        // in a job, so the broadcast ends now and a next tap is taken at once.
                        CalendarAhead.request(context, id)
                    } else {
                        updater.update(intArrayOf(id))
                        updater.settle()
                    }
                }
            }
            CalendarIntents.ACTION_NEW_DAY -> redrawCalendars(context)
            else -> super.onReceive(context, intent)
        }
    }
}

/** Redraws every calendar, keeping this receiver's broadcast alive until they're drawn. */
internal fun BroadcastReceiver.redrawCalendars(context: Context) {
    val graph = context.calendarGraph()
    launchAsync {
        graph.calendars().update()
        graph.calendars().settle()
    }
}

/**
 * The clock, the time zone or the language changed, the phone started or the app was updated:
 * every calendar is drawn again (another day, other names, pages drawn by an older version).
 */
class CalendarSystemEvents : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                if (intent.action == Intent.ACTION_LOCALE_CHANGED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    CalendarPages.clearMemory()
                }
                redrawCalendars(context)
            }
        }
    }
}
