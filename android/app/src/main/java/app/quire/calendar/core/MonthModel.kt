package app.quire.calendar.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The grid is always six rows. A month that needs five leaves the sixth to the
 * neighbouring month rather than resizing the page — the geometry never moves,
 * which is the whole point of a grid.
 */
object MonthModel {

    const val ROWS = 6
    const val COLUMNS = 7
    const val CELLS = ROWS * COLUMNS

    /**
     * Julian day 2440588 is 1970-01-01. The calendar provider's START_DAY and
     * END_DAY columns speak this dialect, and they are the only event columns
     * already resolved to the device's timezone.
     */
    const val JULIAN_EPOCH = 2440588L

    fun julianDay(date: LocalDate): Long = date.toEpochDay() + JULIAN_EPOCH

    fun dateOfJulianDay(julian: Long): LocalDate = LocalDate.ofEpochDay(julian - JULIAN_EPOCH)

    /** Epoch month index: months since 1970-01, used as a stable pager position. */
    fun indexOf(month: YearMonth): Int = (month.year - 1970) * 12 + (month.monthValue - 1)

    fun monthAt(index: Int): YearMonth =
        YearMonth.of(1970 + Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)

    fun firstDayOfWeek(setting: String?, locale: Locale): DayOfWeek = when (setting) {
        "mon" -> DayOfWeek.MONDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> WeekFields.of(locale).firstDayOfWeek
    }

    fun weekdayOrder(first: DayOfWeek): List<DayOfWeek> =
        (0 until COLUMNS).map { first.plus(it.toLong()) }

    /** The 42 dates painted for [month], starting on [first]. */
    fun cells(month: YearMonth, first: DayOfWeek): List<LocalDate> {
        val firstOfMonth = month.atDay(1)
        val lead = Math.floorMod(firstOfMonth.dayOfWeek.value - first.value, COLUMNS)
        val start = firstOfMonth.minusDays(lead.toLong())
        return (0 until CELLS).map { start.plusDays(it.toLong()) }
    }

    /**
     * Column headers. English narrow forms (S M T W T F S) are the iOS look and
     * stay legible; Russian narrow forms collapse to П В С Ч П С В — four unique
     * letters for seven columns — so those locales fall back to the short form.
     */
    fun weekdayLabels(first: DayOfWeek, locale: Locale): List<String> {
        val order = weekdayOrder(first)
        val narrow = order.map {
            it.getDisplayName(TextStyle.NARROW_STANDALONE, locale).uppercase(locale)
        }
        val narrowIsReadable = narrow.all { it.length == 1 } && narrow.toSet().size >= 5
        if (narrowIsReadable) return narrow
        return order.map {
            it.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                .replace(".", "")
                .uppercase(locale)
                .take(3)
        }
    }

    fun monthName(month: YearMonth, locale: Locale): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    fun isWeekend(day: DayOfWeek): Boolean =
        day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    fun weekOfYear(date: LocalDate, locale: Locale): Int =
        date.get(WeekFields.of(locale).weekOfWeekBasedYear())
}
