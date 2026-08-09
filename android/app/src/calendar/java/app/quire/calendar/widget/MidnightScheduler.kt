package app.quire.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * Re-arms itself once per local midnight so "today" moves without the user
 * touching anything. `setWindow` needs no exact-alarm permission; a minute of
 * slack is invisible on a month grid, and ACTION_DATE_CHANGED usually beats it
 * to the punch anyway.
 */
object MidnightScheduler {

    private const val REQUEST = 0x9111
    private const val SLACK_MILLIS = 60_000L

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, MonthWidgetProvider::class.java)
            .setAction(MonthWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val nextMidnight = LocalDate.now(zone)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        alarms.setWindow(
            AlarmManager.RTC,
            nextMidnight + 1_000L,
            SLACK_MILLIS,
            intent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
    }
}
