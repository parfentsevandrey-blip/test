package app.rosa.calendar.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import app.rosa.calendar.R
import app.rosa.calendar.data.Occurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a tap on a day or an event opens: the phone's own calendar app. */
object CalendarActions {
    /** The phone's calendar on [date]. */
    fun openDay(context: Context, date: LocalDate) {
        val millis = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        start(
            context,
            Intent(Intent.ACTION_VIEW)
                .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build()),
        )
    }

    /** A new event on [date]: at the next full hour today, at nine on any other day. */
    fun addEvent(context: Context, date: LocalDate, today: LocalDate) {
        val zone = ZoneId.systemDefault()
        val begin = if (date == today) {
            Instant.now().atZone(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            date.atTime(9, 0).atZone(zone)
        }
        start(
            context,
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin.toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin.plusHours(1).toInstant().toEpochMilli()),
        )
    }

    fun openEvent(context: Context, occurrence: Occurrence) {
        start(
            context,
            Intent(Intent.ACTION_VIEW)
                .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, occurrence.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, occurrence.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, occurrence.end),
        )
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * When [occurrence] takes place on [date], as its row says it: "10:00 – 11:30", "from 18:00",
     * "until 10:00" or "all day".
     */
    fun timeOf(context: Context, occurrence: Occurrence, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        if (occurrence.allDay) return context.getString(R.string.day_all_day)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val time = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", locale)
        val begin = Instant.ofEpochMilli(occurrence.begin).atZone(zone)
        val end = Instant.ofEpochMilli(maxOf(occurrence.end, occurrence.begin)).atZone(zone)
        val beginsToday = begin.toLocalDate() == date
        // An event ending at midnight ends on the day before.
        val endsToday = end.toLocalDate() == date || (end.toLocalDate() == date.plusDays(1) && end.toLocalTime().toSecondOfDay() == 0)
        return when {
            beginsToday && endsToday -> if (occurrence.end <= occurrence.begin) time.format(begin) else time.format(begin) + " – " + time.format(end)
            beginsToday -> context.getString(R.string.day_from, time.format(begin))
            endsToday -> context.getString(R.string.day_until, time.format(end))
            else -> context.getString(R.string.day_all_day)
        }
    }
}
