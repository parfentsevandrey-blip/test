package com.lumina.calendarwidget.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/**
 * A single cell in the month grid.
 *
 * @param date the calendar date this cell represents
 * @param inCurrentMonth whether [date] belongs to the month being displayed (false for the
 *   leading/trailing days that spill in from the neighbouring months)
 * @param isToday whether [date] is the current day
 * @param isWeekend whether [date] falls on one of the configured weekend days
 */
data class DayCell(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isWeekend: Boolean,
) {
    val dayOfMonth: Int get() = date.dayOfMonth
}

/** A row of the grid: an optional week number plus exactly seven [DayCell]s. */
data class WeekRow(
    val weekNumber: Int,
    val days: List<DayCell>,
)

/** The fully-resolved month, ready to render. */
data class MonthGrid(
    val yearMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
    val weekdayOrder: List<DayOfWeek>,
    val weeks: List<WeekRow>,
)

/**
 * Pure, side-effect-free construction of a month grid using [java.time].
 *
 * Keeping this independent of Android/Glance makes it trivial to unit test and lets both the
 * live widget and the in-app preview render from the exact same source of truth.
 */
object CalendarModel {

    /** How many week rows to render. */
    enum class WeekCount {
        /** Only the rows needed for the month (4–6). Height varies month to month. */
        AUTO,

        /** Always six rows for a perfectly stable widget height. */
        FIXED_SIX,
    }

    /**
     * Build the grid for [yearMonth].
     *
     * @param today used to flag the "today" cell (defaults to [LocalDate.now])
     * @param firstDayOfWeek the leftmost column (e.g. [DayOfWeek.MONDAY] or [DayOfWeek.SUNDAY])
     * @param weekendDays the set of days styled as weekend
     * @param weekCount [WeekCount.FIXED_SIX] for a stable height, [WeekCount.AUTO] to fit
     */
    fun buildMonth(
        yearMonth: YearMonth,
        today: LocalDate = LocalDate.now(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        weekendDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        weekCount: WeekCount = WeekCount.FIXED_SIX,
    ): MonthGrid {
        val firstOfMonth = yearMonth.atDay(1)

        // Days from the first-day-of-week column to the 1st of the month (0..6).
        val lead = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val gridStart = firstOfMonth.minusDays(lead.toLong())

        val rows = when (weekCount) {
            WeekCount.FIXED_SIX -> 6
            WeekCount.AUTO -> {
                val used = lead + yearMonth.lengthOfMonth()
                (used + 6) / 7 // ceil(used / 7)
            }
        }

        val weekFields = WeekFields.of(firstDayOfWeek, 4)
        val weeks = ArrayList<WeekRow>(rows)
        var cursor = gridStart
        repeat(rows) {
            val days = ArrayList<DayCell>(7)
            val rowStart = cursor
            repeat(7) {
                days += DayCell(
                    date = cursor,
                    inCurrentMonth = YearMonth.from(cursor) == yearMonth,
                    isToday = cursor == today,
                    isWeekend = cursor.dayOfWeek in weekendDays,
                )
                cursor = cursor.plusDays(1)
            }
            weeks += WeekRow(
                weekNumber = rowStart.get(weekFields.weekOfWeekBasedYear()),
                days = days,
            )
        }

        return MonthGrid(
            yearMonth = yearMonth,
            firstDayOfWeek = firstDayOfWeek,
            weekdayOrder = weekdayOrder(firstDayOfWeek),
            weeks = weeks,
        )
    }

    /** The seven [DayOfWeek]s in display order, starting from [firstDayOfWeek]. */
    fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }
}
