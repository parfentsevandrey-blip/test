package com.example.calendarwidget.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Reads occurrences from the system calendar via [CalendarContract.Instances]
 * (раздел 4 — Календарь). All queries degrade gracefully to empty results when
 * the `READ_CALENDAR` permission has not been granted, so the widget never
 * crashes when the user declines (acceptance criteria — graceful degradation).
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Events of [month] (0-based) grouped by day-of-month (1..31). */
    fun eventsForMonth(year: Int, month: Int): Map<Int, List<CalendarEvent>> {
        if (!hasPermission()) return emptyMap()
        val start = startOfMonth(year, month)
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val cal = Calendar.getInstance()
        return queryInstances(start.timeInMillis, end.timeInMillis)
            .groupBy { e ->
                cal.timeInMillis = e.start
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { (_, list) -> list.sortedBy { it.start } }
    }

    /** Events occurring on a single [day] of [month] (0-based). */
    fun eventsForDay(year: Int, month: Int, day: Int): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val start = Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return queryInstances(start.timeInMillis, end.timeInMillis).sortedBy { it.start }
    }

    private fun startOfMonth(year: Int, month: Int): Calendar =
        Calendar.getInstance().apply { clear(); set(year, month, 1, 0, 0, 0) }

    private fun queryInstances(startMs: Long, endMs: Long): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, startMs)
            ContentUris.appendId(it, endMs)
            it.build()
        }
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
        )
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val calColorIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR)
                val evColorIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_COLOR)
                val calIdIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                while (c.moveToNext()) {
                    val calId = c.getLong(calIdIdx)
                    val category = EventCategory.fromCalendarId(calId)
                    val eventColor = c.getInt(evColorIdx)
                    val calColor = c.getInt(calColorIdx)
                    val color = when {
                        eventColor != 0 -> eventColor
                        calColor != 0 -> calColor
                        else -> category.fallbackColor
                    }
                    result += CalendarEvent(
                        id = c.getLong(idIdx),
                        title = c.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "Без названия",
                        start = c.getLong(beginIdx),
                        end = c.getLong(endIdx),
                        allDay = c.getInt(allDayIdx) == 1,
                        color = color,
                        category = category,
                    )
                }
            }
        }
        return result
    }
}
