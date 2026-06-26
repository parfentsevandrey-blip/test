package com.example.calendarwidget.data

import java.util.Calendar

/**
 * Pure date helpers shared by the widget grid and the in-app week strip.
 * Ported 1:1 from the design reference `WidgetMonth.dc.html -> renderVals()`
 * so the layout maths stay identical to the handoff.
 */
object CalendarMath {

    val MONTHS = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    val MONTHS_SHORT = listOf(
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
        "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек",
    )
    val MONTHS_GENITIVE = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val WEEKDAYS_MON = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val WEEKDAYS_SUN = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    fun weekdayLabels(firstDayMonday: Boolean): List<String> =
        if (firstDayMonday) WEEKDAYS_MON else WEEKDAYS_SUN

    /** True when the column [col] (0-based, in the chosen week order) is a weekend. */
    fun isWeekendColumn(col: Int, firstDayMonday: Boolean): Boolean =
        if (firstDayMonday) col >= 5 else (col == 0 || col == 6)

    /** Number of leading blanks before day 1, given the first-day-of-week setting. */
    fun leadingOffset(year: Int, month: Int, firstDayMonday: Boolean): Int {
        val first = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        // Calendar.DAY_OF_WEEK: Sunday = 1 .. Saturday = 7  ->  JS getDay(): Sunday = 0
        val dow = first.get(Calendar.DAY_OF_WEEK) - 1
        return if (firstDayMonday) (dow + 6) % 7 else dow
    }

    fun daysInMonth(year: Int, month: Int): Int =
        Calendar.getInstance().apply { clear(); set(year, month, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

    /** Total cells (rows * 7) needed to render the month. */
    fun cellCount(year: Int, month: Int, firstDayMonday: Boolean): Int {
        val offset = leadingOffset(year, month, firstDayMonday)
        val dim = daysInMonth(year, month)
        val rows = Math.ceil((offset + dim) / 7.0).toInt()
        return rows * 7
    }

    /** Steps a (year, month0) pair by [delta] months, normalising the year. */
    fun addMonths(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val total = year * 12 + month + delta
        return Pair(Math.floorDiv(total, 12), Math.floorMod(total, 12))
    }

    data class Today(val year: Int, val month: Int, val day: Int)

    fun today(): Today = Calendar.getInstance().let {
        Today(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
    }
}
