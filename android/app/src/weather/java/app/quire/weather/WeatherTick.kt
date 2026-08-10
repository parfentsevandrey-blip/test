package app.quire.weather

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

/**
 * The short-interval refresh, for the intervals JobScheduler will not do.
 *
 * Periodic jobs have a floor of fifteen minutes, and below it the platform does not refuse — it
 * clamps, quietly. An app that offered five minutes and got fifteen would be lying to the person
 * who chose it, so five and ten are driven by the alarm manager instead, re-armed on each firing
 * the same way the calendar's midnight alarm is.
 *
 * These are inexact alarms — `setWindow`, not `setExact` — so no special permission is involved
 * and the system is free to batch them with whatever else it is waking for. The practical shape
 * of that: while the phone is awake the interval is about what it says, and once the phone has
 * been idle a while Doze holds everything until its next maintenance window. Five minutes means
 * five minutes when somebody is using the phone, and longer when nobody is — which is the right
 * way round, and is what the setting's own description says.
 */
class WeatherTick : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val pending = goAsync()
        EXECUTOR.execute {
            try {
                // Re-armed first, so a failed fetch does not also stop the clock.
                arm(app, WeatherSettings.get(app).periodMinutes)
                WeatherRefresh.refreshNow(app)
                WeatherWidgetProvider.renderAll(app)
            } catch (t: Throwable) {
                Log.w(TAG, "weather tick failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "QuireWeather"
        private const val ACTION = "app.quire.weather.TICK"
        private const val REQUEST = 0x9115

        /** A minute of slack: invisible on a temperature, and it lets the system batch. */
        private const val SLACK_MILLIS = 60_000L

        private val EXECUTOR = Executors.newSingleThreadExecutor { r ->
            Thread(r, "quire-weather-tick").apply { isDaemon = true }
        }

        private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, WeatherTick::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun arm(context: Context, minutes: Int) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val next = System.currentTimeMillis() + minutes * 60L * 1000L
            runCatching {
                alarms.setWindow(AlarmManager.RTC, next, SLACK_MILLIS, intent(context))
            }
        }

        fun disarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
        }
    }
}
