package com.monthcalendar.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure date math for the month view — no Android dependencies, so it stays
 * trivial to reason about and test.
 *
 * The grid is always 6 rows × 7 columns (42 cells) so the widget never changes
 * height as months with 4/5/6 visible weeks come and go. The first column is
 * either Monday or Sunday depending on the user's setting. All display names are
 * derived from [Locale] via java.time, never hardcoded.
 */
data class DayCell(
    val date: LocalDate,
    val day: Int,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isWeekend: Boolean,
)

data class MonthData(
    val yearMonth: YearMonth,
    val title: String,
    val weekdayHeaders: List<String>,
    val weekdayIsWeekend: List<Boolean>,
    /** 6 weeks, each a list of 7 [DayCell]. */
    val weeks: List<List<DayCell>>,
    /** Inclusive first date shown in the grid (top-left cell). */
    val gridStart: LocalDate,
    /** Inclusive last date shown in the grid (bottom-right cell). */
    val gridEnd: LocalDate,
)

object CalendarModel {

    /** Localised standalone month name, capitalised (e.g. "June" / "Июнь"). */
    fun monthName(month: Int, locale: Locale = Locale.getDefault()): String =
        java.time.Month.of(month)
            .getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    /**
     * @param anchor   the month to display.
     * @param today    used only to mark the "today" cell.
     * @param mondayFirst first column is Monday when true, Sunday when false.
     */
    fun monthFor(
        anchor: YearMonth,
        today: LocalDate,
        mondayFirst: Boolean,
        locale: Locale = Locale.getDefault(),
    ): MonthData {
        val firstOfMonth = anchor.atDay(1)
        val firstDow = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

        // Order the seven weekday columns starting from the chosen first day.
        val orderedDows = (0 until 7).map { DayOfWeek.of((firstDow.value - 1 + it) % 7 + 1) }
        val headers = orderedDows.map {
            it.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                .replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(locale) else c.toString() }
        }
        val headerWeekend = orderedDows.map { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

        // Step back to the first weekday-of-week on or before the 1st.
        val lead = (firstOfMonth.dayOfWeek.value - firstDow.value + 7) % 7
        val gridStart = firstOfMonth.minusDays(lead.toLong())

        val weeks = (0 until 6).map { week ->
            (0 until 7).map { dow ->
                val date = gridStart.plusDays((week * 7 + dow).toLong())
                DayCell(
                    date = date,
                    day = date.dayOfMonth,
                    inCurrentMonth = YearMonth.from(date) == anchor,
                    isToday = date == today,
                    isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                )
            }
        }

        return MonthData(
            yearMonth = anchor,
            title = "${monthName(anchor.monthValue, locale)} ${anchor.year}",
            weekdayHeaders = headers,
            weekdayIsWeekend = headerWeekend,
            weeks = weeks,
            gridStart = gridStart,
            gridEnd = gridStart.plusDays(41),
        )
    }
}
