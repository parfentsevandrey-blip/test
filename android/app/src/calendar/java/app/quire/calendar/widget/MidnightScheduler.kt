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

    private const val REQUEST_MONTH = 0x9111
    private const val REQUEST_AGENDA = 0x911A
    private const val SLACK_MILLIS = 60_000L

    // One alarm per provider, because a PendingIntent points at one component. A firing with no
    // cards placed for its provider renders nothing and costs one broadcast a day.
    private fun monthIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_MONTH,
        Intent(context, MonthWidgetProvider::class.java)
            .setAction(MonthWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun agendaIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_AGENDA,
        Intent(context, AgendaWidgetProvider::class.java)
            .setAction(AgendaWidgetProvider.ACTION_REFRESH),
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
        listOf(monthIntent(context), agendaIntent(context)).forEach { intent ->
            alarms.setWindow(
                AlarmManager.RTC,
                nextMidnight + 1_000L,
                SLACK_MILLIS,
                intent,
            )
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(monthIntent(context))
        alarms.cancel(agendaIntent(context))
    }
}
