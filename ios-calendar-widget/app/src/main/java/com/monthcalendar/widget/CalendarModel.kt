package com.monthcalendar.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure date math for the month view — no Android dependencies, so it stays
 * trivial to reason about and test.
 *
 * The grid is Monday-first (matching the ru/most-of-Europe convention) and
 * always 6 rows × 7 columns (42 cells) so the widget never changes height as
 * months with 4/5/6 visible weeks come and go — exactly how the iOS month
 * widget behaves.
 */
data class DayCell(
    val day: Int,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isWeekend: Boolean,
)

data class MonthData(
    val title: String,
    val weekdayHeaders: List<String>,
    /** 6 weeks, each a list of 7 [DayCell]. */
    val weeks: List<List<DayCell>>,
)

object CalendarModel {

    private val MONTHS_RU = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    val WEEKDAYS_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun monthFor(today: LocalDate): MonthData {
        val ym = YearMonth.from(today)
        val firstOfMonth = ym.atDay(1)

        // Step back to the Monday on or before the 1st.
        val lead = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val gridStart = firstOfMonth.minusDays(lead.toLong())

        val weeks = (0 until 6).map { week ->
            (0 until 7).map { dow ->
                val date = gridStart.plusDays((week * 7 + dow).toLong())
                DayCell(
                    day = date.dayOfMonth,
                    inCurrentMonth = YearMonth.from(date) == ym,
                    isToday = date == today,
                    isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                )
            }
        }

        return MonthData(
            title = "${MONTHS_RU[today.monthValue - 1]} ${today.year}",
            weekdayHeaders = WEEKDAYS_RU,
            weeks = weeks,
        )
    }
}
