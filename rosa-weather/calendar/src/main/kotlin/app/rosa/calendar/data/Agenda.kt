package app.rosa.calendar.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import app.rosa.calendar.widget.CalendarEvents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One occurrence of an event on the phone's calendars. */
data class Occurrence(
    val eventId: Long,
    val title: String,
    /** Epoch milliseconds. */
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
    val calendar: String,
) {
    /** A key unique among a day's occurrences: a repeating event occurs once a day at most. */
    val key: String get() = "$eventId@$begin"
}

/** The phone's events, read where they are: nothing is copied or sent anywhere. */
object Agenda {
    fun granted(context: Context): Boolean = CalendarEvents.granted(context)

    /**
     * The occurrences from [from] to [to] (both included) by day, in the order they begin, all-day
     * ones first. An event spanning several days is on each of them.
     */
    fun read(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Occurrence>> {
        if (!granted(context) || to.isBefore(from)) return emptyMap()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val days = HashMap<LocalDate, MutableList<Occurrence>>()
        runCatching {
            context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    val occurrence = Occurrence(
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty().trim(),
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                        color = cursor.getInt(5),
                        location = cursor.getString(6).orEmpty().trim(),
                        calendar = cursor.getString(7).orEmpty(),
                    )
                    for (day in daysOf(occurrence, zone, from, to)) days.getOrPut(day) { mutableListOf() } += occurrence
                }
            }
        }
        return days.mapValues { (_, list) -> list.distinctBy { it.key }.sortedWith(compareBy({ !it.allDay }, { it.begin }, { it.title })) }
    }

    /** The days [occurrence] is on, within [from]..[to]. */
    fun daysOf(occurrence: Occurrence, zone: ZoneId, from: LocalDate, to: LocalDate): List<LocalDate> {
        // All-day events are stored at midnight UTC; timed ones at their real instant.
        val eventZone = if (occurrence.allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(occurrence.begin).atZone(eventZone).toLocalDate()
        // An event ending at midnight doesn't reach into the next day.
        val endMillis = maxOf(occurrence.begin, occurrence.end - 1)
        val last = Instant.ofEpochMilli(endMillis).atZone(eventZone).toLocalDate()
        val out = ArrayList<LocalDate>()
        var day = maxOf(first, from)
        while (!day.isAfter(minOf(last, to)) && out.size < 62) {
            out += day
            day = day.plusDays(1)
        }
        return out
    }
}
