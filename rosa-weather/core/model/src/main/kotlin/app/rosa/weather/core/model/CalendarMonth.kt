package app.rosa.weather.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.serialization.Serializable

/** Which day a calendar week begins with; [Auto] follows the language and region. */
@Serializable
enum class WeekStart { Auto, Monday, Sunday, Saturday }

/** What the calendar widget shows besides the month itself. */
@Serializable
data class CalendarOptions(
    val weekStart: WeekStart = WeekStart.Auto,
    val weekNumbers: Boolean = false,
    /** The forecast's days carry their weather. */
    val forecastInDays: Boolean = true,
    /** Dots under days with events from the phone's calendars (needs calendar access). */
    val events: Boolean = false,
)

/** One square of the month grid. */
data class CalendarDay(
    val date: LocalDate,
    /** False for the days of the months before and after that fill the first and last weeks. */
    val inMonth: Boolean,
    val isToday: Boolean,
    val isWeekend: Boolean,
)

/**
 * A month laid out as the widget shows it: always six weeks, so the grid keeps its size from
 * month to month, beginning on [firstDay], with the neighbouring months' days filling the gaps.
 */
data class CalendarMonth(
    val month: YearMonth,
    val firstDay: DayOfWeek,
    val weeks: List<List<CalendarDay>>,
    /** Each week's number: ISO weeks when weeks begin on Monday, otherwise counted from 1 January. */
    val weekNumbers: List<Int>,
) {
    val days: List<CalendarDay> get() = weeks.flatten()

    /** The weekdays in the order the columns show them. */
    val columns: List<DayOfWeek> get() = List(7) { firstDay.plus(it.toLong()) }

    val first: LocalDate get() = weeks.first().first().date
    val last: LocalDate get() = weeks.last().last().date

    companion object {
        const val WEEKS = 6

        fun of(month: YearMonth, today: LocalDate, firstDay: DayOfWeek): CalendarMonth {
            val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
            val weeks = List(WEEKS) { w ->
                List(7) { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    CalendarDay(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                    )
                }
            }
            val numbers = weeks.map { week ->
                val anchor = week.first().date
                if (firstDay == DayOfWeek.MONDAY) {
                    anchor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                } else {
                    // Count from the week holding 1 January, as calendars that start on Sunday do.
                    anchor.get(WeekFields.of(firstDay, 1).weekOfWeekBasedYear())
                }
            }
            return CalendarMonth(month, firstDay, weeks, numbers)
        }

        /** The first day of the week for [start], asking the [locale] when it is [WeekStart.Auto]. */
        fun firstDayFor(start: WeekStart, locale: Locale): DayOfWeek = when (start) {
            WeekStart.Auto -> WeekFields.of(locale).firstDayOfWeek
            WeekStart.Monday -> DayOfWeek.MONDAY
            WeekStart.Sunday -> DayOfWeek.SUNDAY
            WeekStart.Saturday -> DayOfWeek.SATURDAY
        }
    }
}

/** The four seasons of the calendar's scenes, as the northern temperate year turns. */
enum class Season {
    Winter, Spring, Summer, Autumn;

    companion object {
        fun of(month: Int): Season = when (month) {
            12, 1, 2 -> Winter
            3, 4, 5 -> Spring
            6, 7, 8 -> Summer
            else -> Autumn
        }
    }
}
