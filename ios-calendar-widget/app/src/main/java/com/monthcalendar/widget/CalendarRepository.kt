package com.monthcalendar.widget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A trimmed-down calendar event, just what the widget renders. */
data class EventLite(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val date: LocalDate,
)

/**
 * Reads real events from the device calendar via [CalendarContract.Instances]
 * (the expanded-recurrence view, so repeating events show on every occurrence).
 * All queries are off the main thread and degrade to empty when the
 * READ_CALENDAR permission hasn't been granted.
 */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
    )

    /** Events grouped by local date across [start]..[endInclusive]. */
    suspend fun eventsByDay(
        context: Context,
        start: LocalDate,
        endInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, List<EventLite>> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyMap()

        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        query(context, startMs, endMs, zone)
            .groupBy { it.date }
    }

    /** The next [limit] events starting from [from] within [withinDays]. */
    suspend fun upcoming(
        context: Context,
        from: LocalDate,
        withinDays: Long = 30,
        limit: Int = 6,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EventLite> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()

        val startMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = from.plusDays(withinDays).atStartOfDay(zone).toInstant().toEpochMilli()

        query(context, startMs, endMs, zone)
            .sortedBy { it.begin }
            .take(limit)
    }

    private fun query(context: Context, startMs: Long, endMs: Long, zone: ZoneId): List<EventLite> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMs)
            ContentUris.appendId(this, endMs)
        }.build()

        val out = ArrayList<EventLite>()
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                val idIdx = 0
                val titleIdx = 1
                val beginIdx = 2
                val endIdx = 3
                val allDayIdx = 4
                val colorIdx = 5
                while (c.moveToNext()) {
                    val begin = c.getLong(beginIdx)
                    val allDay = c.getInt(allDayIdx) == 1
                    // All-day instances are stored at UTC midnight; everything
                    // else is a real epoch instant in the device zone.
                    val date = if (allDay) {
                        Instant.ofEpochMilli(begin).atZone(ZoneId.of("UTC")).toLocalDate()
                    } else {
                        Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    }
                    out += EventLite(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                        begin = begin,
                        end = c.getLong(endIdx),
                        allDay = allDay,
                        color = c.getInt(colorIdx),
                        date = date,
                    )
                }
            }
        }
        return out
    }
}
