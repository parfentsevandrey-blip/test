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
