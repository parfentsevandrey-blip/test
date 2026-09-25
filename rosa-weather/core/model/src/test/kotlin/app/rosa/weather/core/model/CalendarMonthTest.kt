package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Test

class CalendarMonthTest {
    private val today = LocalDate.of(2026, 9, 25)

    @Test
    fun `a month is always six weeks from the chosen first day`() {
        val month = CalendarMonth.of(YearMonth.of(2026, 9), today, DayOfWeek.MONDAY)
        assertThat(month.weeks).hasSize(6)
        assertThat(month.weeks.all { it.size == 7 }).isTrue()
        // 1 September 2026 is a Tuesday: the grid opens on Monday 31 August.
        assertThat(month.first).isEqualTo(LocalDate.of(2026, 8, 31))
        assertThat(month.last).isEqualTo(LocalDate.of(2026, 10, 11))
        assertThat(month.columns.first()).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `neighbouring months fill the edges and today is marked`() {
        val month = CalendarMonth.of(YearMonth.of(2026, 9), today, DayOfWeek.MONDAY)
        val days = month.days
        assertThat(days.first().inMonth).isFalse()
        assertThat(days.count { it.inMonth }).isEqualTo(30)
        assertThat(days.single { it.isToday }.date).isEqualTo(today)
        assertThat(days.filter { it.isWeekend }.map { it.date.dayOfWeek }.toSet())
            .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    @Test
    fun `weeks can begin on Sunday`() {
        val month = CalendarMonth.of(YearMonth.of(2026, 9), today, DayOfWeek.SUNDAY)
        assertThat(month.first).isEqualTo(LocalDate.of(2026, 8, 30))
        assertThat(month.columns.last()).isEqualTo(DayOfWeek.SATURDAY)
    }

    @Test
    fun `a month that begins on the first day starts the grid with it`() {
        // 1 February 2027 is a Monday.
        val month = CalendarMonth.of(YearMonth.of(2027, 2), today, DayOfWeek.MONDAY)
        assertThat(month.first).isEqualTo(LocalDate.of(2027, 2, 1))
    }

    @Test
    fun `iso week numbers run across the new year`() {
        // 2026 has 53 ISO weeks: Monday 28 December is in week 53, 4 January 2027 opens week 1.
        val month = CalendarMonth.of(YearMonth.of(2026, 12), today, DayOfWeek.MONDAY)
        assertThat(month.weekNumbers.first()).isEqualTo(49)
        assertThat(month.weekNumbers).containsAtLeast(53, 1).inOrder()
    }

    @Test
    fun `the language decides where weeks begin`() {
        assertThat(CalendarMonth.firstDayFor(WeekStart.Auto, Locale.forLanguageTag("ru-RU"))).isEqualTo(DayOfWeek.MONDAY)
        assertThat(CalendarMonth.firstDayFor(WeekStart.Auto, Locale.US)).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(CalendarMonth.firstDayFor(WeekStart.Sunday, Locale.forLanguageTag("ru-RU"))).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test
    fun `seasons follow the months`() {
        assertThat((1..12).map { Season.of(it) }).containsExactly(
            Season.Winter, Season.Winter, Season.Spring, Season.Spring, Season.Spring, Season.Summer,
            Season.Summer, Season.Summer, Season.Autumn, Season.Autumn, Season.Autumn, Season.Winter,
        ).inOrder()
    }

    @Test
    fun `old widget configs read as weather widgets`() {
        assertThat(WidgetConfig().face).isEqualTo(WidgetFace.Weather)
        assertThat(WidgetConfig().calendar).isEqualTo(CalendarOptions())
    }
}
