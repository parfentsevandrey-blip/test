package com.lumina.calendarwidget.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads which days in a month have calendar events, purely to draw event indicators.
 *
 * This is strictly optional: without the [Manifest.permission.READ_CALENDAR] grant every call
 * returns an empty set, so the widget renders identically minus the dots. All access is wrapped
 * in defensive try/catch — a calendar provider hiccup can never crash the widget.
 *
 * Multi-day events light every day they cover (clamped to the month), and all-day events are read
 * in UTC as the provider stores them, so they never bleed into an adjacent day for off-UTC users.
 */
object EventRepository {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Days of [yearMonth] (1..31) that contain at least one event, or empty if unavailable. */
    fun eventDays(context: Context, yearMonth: YearMonth): Set<Int> {
        if (!hasPermission(context)) return emptySet()

        val zone = ZoneId.systemDefault()
        val monthFirst = yearMonth.atDay(1)
        val monthLast = yearMonth.atEndOfMonth()
        val startMs = monthFirst.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        val days = HashSet<Int>()
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                if (beginIdx < 0) return emptySet()

                while (cursor.moveToNext()) {
                    val allDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) == 1
                    val begin = cursor.getLong(beginIdx)
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
                    val readZone = if (allDay) ZoneOffset.UTC else zone

                    var day = Instant.ofEpochMilli(begin).atZone(readZone).toLocalDate()
                    // For all-day events the provider's END is the exclusive next-midnight.
                    var lastDay = Instant.ofEpochMilli(end).atZone(readZone).toLocalDate()
                    if (allDay) lastDay = lastDay.minusDays(1)

                    if (day.isBefore(monthFirst)) day = monthFirst
                    if (lastDay.isAfter(monthLast)) lastDay = monthLast

                    while (!day.isAfter(lastDay)) {
                        if (YearMonth.from(day) == yearMonth) days += day.dayOfMonth
                        day = day.plusDays(1)
                    }
                }
            }.let { days }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun eventDaysForCurrentMonth(context: Context): Set<Int> =
        eventDays(context, YearMonth.from(LocalDate.now()))
}
