package app.quire.calendar.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

/** Aggregate load for one square of the grid. */
class DayLoad(val count: Int, val colours: IntArray)

class AgendaEntry(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val location: String?,
    val colour: Int,
    val calendarName: String?,
)

class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colour: Int,
)

/**
 * Reads the system calendar provider. Every entry point degrades to an empty
 * result when READ_CALENDAR has not been granted, so callers never branch on
 * permission state to stay correct — only to explain themselves.
 */
object EventRepository {

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.START_DAY,
        CalendarContract.Instances.END_DAY,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DISPLAY_COLOR,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.CALENDAR_DISPLAY_NAME,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.SELF_ATTENDEE_STATUS,
    )

    private const val I_EVENT_ID = 0
    private const val I_BEGIN = 1
    private const val I_END = 2
    private const val I_START_DAY = 3
    private const val I_END_DAY = 4
    private const val I_TITLE = 5
    private const val I_ALL_DAY = 6
    private const val I_LOCATION = 7
    private const val I_COLOUR = 8
    private const val I_CALENDAR_ID = 9
    private const val I_CALENDAR_NAME = 10
    private const val I_STATUS = 11
    private const val I_SELF_STATUS = 12

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun julian(date: LocalDate): Long = MonthModel.julianDay(date)

    private fun startOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private inline fun query(
        context: Context,
        fromMillis: Long,
        toMillis: Long,
        body: (Cursor) -> Unit,
    ) {
        if (!hasPermission(context)) return
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMillis)
            ContentUris.appendId(this, toMillis)
        }.build()
        try {
            context.contentResolver.query(
                uri,
                PROJECTION,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use(body)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the query.
        } catch (_: IllegalArgumentException) {
            // Some OEM providers reject columns they do not implement.
        }
    }

    private fun Cursor.isDropped(hidden: Set<Long>): Boolean {
        if (getLong(I_CALENDAR_ID) in hidden) return true
        if (!isNull(I_STATUS) && getInt(I_STATUS) == CalendarContract.Events.STATUS_CANCELED) return true
        if (!isNull(I_SELF_STATUS) &&
            getInt(I_SELF_STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
        ) return true
        return false
    }

    /**
     * Event load for a contiguous run of days, keyed by date. Multi-day and
     * all-day entries are expanded through the provider's julian day columns,
     * which are the only values already normalised to the local timezone.
     */
    fun loadFor(
        context: Context,
        from: LocalDate,
        days: Int,
        hidden: Set<Long> = emptySet(),
        maxColours: Int = 3,
    ): Map<LocalDate, DayLoad> {
        val zone = ZoneId.systemDefault()
        val to = from.plusDays(days.toLong())
        val counts = HashMap<LocalDate, Int>(days * 2)
        val colours = HashMap<LocalDate, MutableList<Int>>(days * 2)
        val firstJulian = julian(from)
        val lastJulian = julian(to) - 1

        query(context, startOfDayMillis(from, zone), startOfDayMillis(to, zone)) { c ->
            while (c.moveToNext()) {
                if (c.isDropped(hidden)) continue
                val colour = if (c.isNull(I_COLOUR)) 0 else c.getInt(I_COLOUR)
                val startDay = c.getLong(I_START_DAY).coerceAtLeast(firstJulian)
                val endDay = c.getLong(I_END_DAY).coerceAtMost(lastJulian)
                var j = startDay
                while (j <= endDay) {
                    val date = MonthModel.dateOfJulianDay(j)
                    counts[date] = (counts[date] ?: 0) + 1
                    if (colour != 0) {
                        val list = colours.getOrPut(date) { ArrayList(maxColours) }
                        if (list.size < maxColours && !list.contains(colour)) list.add(colour)
                    }
                    j++
                }
            }
        }

        return counts.mapValues { (date, count) ->
            DayLoad(count, colours[date]?.toIntArray() ?: IntArray(0))
        }
    }

    /** Everything happening on [date], all-day entries first. */
    fun agendaFor(
        context: Context,
        date: LocalDate,
        hidden: Set<Long> = emptySet(),
    ): List<AgendaEntry> {
        val zone = ZoneId.systemDefault()
        val target = julian(date)
        val out = ArrayList<AgendaEntry>()
        // Widened by a day on each side so multi-day and all-day spans surface.
        query(
            context,
            startOfDayMillis(date.minusDays(1), zone),
            startOfDayMillis(date.plusDays(2), zone),
        ) { c ->
            while (c.moveToNext()) {
                if (c.isDropped(hidden)) continue
                if (target < c.getLong(I_START_DAY) || target > c.getLong(I_END_DAY)) continue
                out += AgendaEntry(
                    eventId = c.getLong(I_EVENT_ID),
                    begin = c.getLong(I_BEGIN),
                    end = c.getLong(I_END),
                    allDay = !c.isNull(I_ALL_DAY) && c.getInt(I_ALL_DAY) == 1,
                    title = c.getString(I_TITLE).orEmpty(),
                    location = c.getString(I_LOCATION)?.takeIf { it.isNotBlank() },
                    colour = if (c.isNull(I_COLOUR)) 0 else c.getInt(I_COLOUR),
                    calendarName = c.getString(I_CALENDAR_NAME),
                )
            }
        }
        return out.sortedWith(
            compareBy({ !it.allDay }, { if (it.allDay) 0L else it.begin }, { it.title }),
        )
    }

    /** Calendars the user could choose to hide. */
    fun calendars(context: Context): List<CalendarSource> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val out = ArrayList<CalendarSource>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarSource(
                        id = c.getLong(0),
                        displayName = c.getString(1).orEmpty(),
                        accountName = c.getString(2).orEmpty(),
                        colour = if (c.isNull(3)) 0 else c.getInt(3),
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return out
    }
}
