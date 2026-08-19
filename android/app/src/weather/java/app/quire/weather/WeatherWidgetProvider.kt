package app.quire.weather

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

/**
 * The weather card's half of the bargain with the launcher.
 *
 * Painting is cheap — it reads the stored forecast and nothing else — so the only thing this has
 * to be careful about is never fetching on the broadcast thread. Fetching is [WeatherRefresh]'s
 * job, and it repaints when it lands.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                when (intent.action) {
                    ACTION_REFRESH -> renderAll(context)
                    ACTION_PEEK -> peek(context, intent)
                    Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        // A job keeps the triggers it was scheduled with, and only re-arms itself
                        // when it runs. An update that adds one — the theme watch — would
                        // otherwise never reach a widget that was already placed.
                        WeatherRefresh.schedule(context)
                        WeatherWatch.schedule(context)
                        renderAll(context)
                    }
                    Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                        renderAll(context)
                    }
                    else -> super.onReceive(context, intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "weather widget update failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * A tap on a day column: open that day on the card, or close it if it is the one open.
     *
     * The peek walks back to "now" on its own as well — an inexact alarm a couple of minutes
     * out asks for a repaint, and the renderer ignores a peek older than [PEEK_MILLIS]. A card
     * showing Friday all afternoon because somebody looked once and walked away would be a
     * forecast wearing the wrong headline.
     */
    private fun peek(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val day = intent.getStringExtra(EXTRA_DAY).orEmpty()
        val wp = app.quire.calendar.core.Prefs.get(context).widget(widgetId)
        if (wp.peekDay == day) {
            wp.peekDay = ""
        } else {
            wp.peekDay = day
            wp.peekAt = System.currentTimeMillis()
            armPeekTimeout(context)
        }
        render(context, AppWidgetManager.getInstance(context), widgetId)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        // Content-trigger jobs are one-shot and this arrives on boot and on placement, so the
        // watch is re-armed from here too; scheduling over a live job is a no-op.
        WeatherWatch.schedule(context)
        // A card that has just been placed has nothing to show, so the first fetch is not left to
        // the hourly job an hour from now.
        WeatherRefresh.request(context) { renderAll(context) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        render(context, manager, widgetId)
    }

    override fun onEnabled(context: Context) {
        WeatherRefresh.schedule(context)
        WeatherWatch.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WeatherRefresh.cancel(context)
        WeatherWatch.cancel(context)
    }

    companion object {
        private const val TAG = "QuireWeather"
        const val ACTION_REFRESH = "app.quire.weather.REFRESH"
        const val ACTION_PEEK = "app.quire.weather.PEEK"
        const val EXTRA_DAY = "day"

        /** How long a peeked day holds the hero before the card goes back to now. */
        const val PEEK_MILLIS = 150_000L

        private const val PEEK_TIMEOUT_REQUEST = 0x9119

        /** The walk back: an inexact repaint just past the peek's expiry. */
        private fun armPeekTimeout(context: Context) {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            val intent = android.app.PendingIntent.getBroadcast(
                context,
                PEEK_TIMEOUT_REQUEST,
                Intent(context, WeatherWidgetProvider::class.java).setAction(ACTION_REFRESH),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarms.setWindow(
                    android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + PEEK_MILLIS + 5_000L,
                    60_000L,
                    intent,
                )
            }
        }

        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "quire-weather-widget").apply { isDaemon = true }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, WeatherWidgetRenderer.build(context, manager, widgetId))
        }

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                .forEach { render(context, manager, it) }
            // Whatever asked for this paint, the cards now wear the current look, so the pulse
            // does not ask again for the same change.
            WeatherWatch.markPainted(context)
        }

        fun requestUpdate(context: Context) {
            context.sendBroadcast(
                Intent(context, WeatherWidgetProvider::class.java).setAction(ACTION_REFRESH),
            )
        }
    }
}
