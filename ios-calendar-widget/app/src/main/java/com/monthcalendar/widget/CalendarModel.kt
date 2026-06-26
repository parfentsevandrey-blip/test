package com.monthcalendar.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure date math for the month view — no Android dependencies, so it stays
 * trivial to reason about and test.
 *
 * The grid is always 6 rows × 7 columns (42 cells) so the widget never changes
 * height as months with 4/5/6 visible weeks come and go. The first column is
 * either Monday or Sunday depending on the user's setting.
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
    /** 6 weeks, each a list of 7 [DayCell]. */
    val weeks: List<List<DayCell>>,
    /** Inclusive first date shown in the grid (top-left cell). */
    val gridStart: LocalDate,
    /** Inclusive last date shown in the grid (bottom-right cell). */
    val gridEnd: LocalDate,
)

object CalendarModel {

    private val MONTHS_RU = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    private val WEEKDAYS_FROM_MONDAY = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val WEEKDAYS_FROM_SUNDAY = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    fun monthName(month: Int): String = MONTHS_RU[month - 1]

    /**
     * @param anchor   the month to display.
     * @param today    used only to mark the "today" cell.
     * @param mondayFirst first column is Monday when true, Sunday when false.
     */
    fun monthFor(anchor: YearMonth, today: LocalDate, mondayFirst: Boolean): MonthData {
        val firstOfMonth = anchor.atDay(1)
        val firstDow = if (mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

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
            title = "${MONTHS_RU[anchor.monthValue - 1]} ${anchor.year}",
            weekdayHeaders = if (mondayFirst) WEEKDAYS_FROM_MONDAY else WEEKDAYS_FROM_SUNDAY,
            weeks = weeks,
            gridStart = gridStart,
            gridEnd = gridStart.plusDays(41),
        )
    }
}
