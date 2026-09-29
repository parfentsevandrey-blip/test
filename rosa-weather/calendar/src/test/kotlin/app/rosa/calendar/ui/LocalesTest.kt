package app.rosa.calendar.ui

import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.WeekStart
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Test

class LocalesTest {
    @Test
    fun `a language chosen for the app alone takes the phone's region`() {
        val russian = withRegion(Locale.forLanguageTag("ru"), Locale.forLanguageTag("ru-RU"))
        assertThat(russian.country).isEqualTo("RU")
        assertThat(CalendarMonth.firstDayFor(WeekStart.Auto, russian)).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `a locale with its own region keeps it`() {
        val american = withRegion(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("ru-RU"))
        assertThat(american.country).isEqualTo("US")
        assertThat(CalendarMonth.firstDayFor(WeekStart.Auto, american)).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test
    fun `without a region anywhere the locale stays as it is`() {
        assertThat(withRegion(Locale.forLanguageTag("ru"), null)).isEqualTo(Locale.forLanguageTag("ru"))
    }
}
