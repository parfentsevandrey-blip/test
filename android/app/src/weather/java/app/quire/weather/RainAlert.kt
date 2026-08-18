package app.quire.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.quire.R

/**
 * Telling somebody it is going to rain, once.
 *
 * The rule is deliberately dull: one notification per day, for today, when the chance of
 * precipitation is at or above what the user asked for. Anything chattier — hourly nagging, a
 * push for every shower — is attention this app has not been given permission to take. The
 * minute-cast ("rain in 25 min") lives on the widget and the screen, where glancing is free;
 * a notification is an interruption, and it stays once a day.
 *
 * [WeatherSettings.alertedOn] is what makes "once" true: an hourly job that re-evaluates the same
 * day would otherwise post the same sentence every hour until the rain arrived.
 */
object RainAlert {

    private const val CHANNEL = "rain"
    private const val NOTIFICATION = 0x9114

    /**
     * Posts an alert if today's forecast has earned one.
     *
     * Returns whether anything was posted, which is what the tests read: the decision is the part
     * worth checking, and it is entirely separable from the platform's notification plumbing.
     */
    fun consider(context: Context, forecast: Forecast): Boolean {
        val settings = WeatherSettings.get(context)
        if (!settings.alerts) return false

        val today = forecast.days.firstOrNull() ?: return false
        if (today.rain < settings.threshold) return false
        // Already said so for this day.
        if (settings.alertedOn == today.date.toString()) return false
        if (!allowed(context)) return false

        settings.alertedOn = today.date.toString()
        post(context, forecast, today)
        return true
    }

    /** Whether the platform will accept a notification from this app at all. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Guarded by [allowed] on the only path that reaches here, and wrapped besides; lint cannot
    // follow the check through the helper.
    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, forecast: Forecast, today: DayForecast) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION,
            Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("quire://weather"))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(today.sky.dayIcon)
            .setContentTitle(
                context.getString(
                    R.string.wx_alert_title,
                    forecast.place.ifBlank { context.getString(R.string.weather) },
                ),
            )
            .setContentText(
                context.getString(
                    R.string.wx_alert_body,
                    today.rain,
                    context.getString(today.sky.label),
                ),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.wx_alert_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
