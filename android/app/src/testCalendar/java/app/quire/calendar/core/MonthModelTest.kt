package app.quire.calendar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MonthModelTest {

    @Test
    fun `epoch month index round trips`() {
        val months = listOf(
            YearMonth.of(1970, 1),
            YearMonth.of(1999, 12),
            YearMonth.of(2026, 8),
            YearMonth.of(2100, 12),
        )
        months.forEach { month ->
            assertEquals(month, MonthModel.monthAt(MonthModel.indexOf(month)))
        }
        assertEquals(0, MonthModel.indexOf(YearMonth.of(1970, 1)))
        assertEquals(19, MonthModel.indexOf(YearMonth.of(1971, 8)))
    }

    @Test
    fun `grid is always six full weeks`() {
        val month = YearMonth.of(2026, 2) // 28 days, starts on a Sunday
        val cells = MonthModel.cells(month, DayOfWeek.MONDAY)
        assertEquals(MonthModel.CELLS, cells.size)
        assertEquals(DayOfWeek.MONDAY, cells.first().dayOfWeek)
        cells.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
        assertTrue(cells.contains(month.atDay(1)))
        assertTrue(cells.contains(month.atEndOfMonth()))
    }

    @Test
    fun `month starting on the first column has no leading days`() {
        // 2026-06-01 is a Monday.
        val cells = MonthModel.cells(YearMonth.of(2026, 6), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
    }

    @Test
    fun `sunday start shifts the whole grid by one column`() {
        val monday = MonthModel.cells(YearMonth.of(2026, 8), DayOfWeek.MONDAY)
        val sunday = MonthModel.cells(YearMonth.of(2026, 8), DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, sunday.first().dayOfWeek)
        assertEquals(monday.first().minusDays(1), sunday.first())
    }

    @Test
    fun `julian day matches the provider anchor`() {
        assertEquals(2440588L, MonthModel.julianDay(LocalDate.of(1970, 1, 1)))
        val date = LocalDate.of(2026, 8, 8)
        assertEquals(date, MonthModel.dateOfJulianDay(MonthModel.julianDay(date)))
    }

    @Test
    fun `english headers keep the single letter form`() {
        val labels = MonthModel.weekdayLabels(DayOfWeek.SUNDAY, Locale.ENGLISH)
        assertEquals(7, labels.size)
        assertTrue(labels.joinToString(), labels.all { it.length == 1 })
    }

    @Test
    fun `locales with ambiguous narrow days fall back to distinct labels`() {
        val labels = MonthModel.weekdayLabels(DayOfWeek.MONDAY, Locale("ru"))
        assertEquals(7, labels.size)
        assertEquals(labels.joinToString(), 7, labels.toSet().size)
    }

    @Test
    fun `first day of week honours the explicit setting over the locale`() {
        assertEquals(
            DayOfWeek.SUNDAY,
            MonthModel.firstDayOfWeek("sun", Locale("ru")),
        )
        assertEquals(
            DayOfWeek.MONDAY,
            MonthModel.firstDayOfWeek("mon", Locale.US),
        )
        assertEquals(
            DayOfWeek.SUNDAY,
            MonthModel.firstDayOfWeek("auto", Locale.US),
        )
    }

    @Test
    fun `auto follows the region, and the user's override when the locale carries one`() {
        assertEquals(DayOfWeek.MONDAY, MonthModel.firstDayOfWeek("auto", Locale("ru", "RU")))
        assertEquals(DayOfWeek.MONDAY, MonthModel.firstDayOfWeek("auto", Locale.FRANCE))
        assertEquals(DayOfWeek.SUNDAY, MonthModel.firstDayOfWeek("auto", Locale.US))
        // Android 13+ hands the user's own choice over as a `fw` extension.
        assertEquals(
            DayOfWeek.MONDAY,
            MonthModel.firstDayOfWeek("auto", Locale.forLanguageTag("en-US-u-fw-mon")),
        )
        assertEquals(
            DayOfWeek.SATURDAY,
            MonthModel.firstDayOfWeek("auto", Locale.forLanguageTag("ru-RU-u-fw-sat")),
        )
    }

    @Test
    fun `weekends are saturday and sunday`() {
        assertTrue(MonthModel.isWeekend(DayOfWeek.SATURDAY))
        assertTrue(MonthModel.isWeekend(DayOfWeek.SUNDAY))
        assertTrue(DayOfWeek.entries.count { MonthModel.isWeekend(it) } == 2)
    }

    @Test
    fun `month names are title cased for standalone use`() {
        val name = MonthModel.monthName(YearMonth.of(2026, 8), Locale.ENGLISH)
        assertEquals("August", name)
        val russian = MonthModel.monthName(YearMonth.of(2026, 8), Locale("ru"))
        assertTrue(russian, russian.first().isUpperCase())
    }
}
