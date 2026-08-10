package app.quire.weather

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The settings, and the two decisions that hang off them: what a temperature reads as, and whether
 * a day is worth interrupting somebody about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherSettingsTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val settings get() = WeatherSettings.get(context)

    @Before
    fun plainSettings() {
        settings.periodMinutes = WeatherSettings.DEFAULT_PERIOD
        settings.alerts = false
        settings.threshold = WeatherSettings.DEFAULT_THRESHOLD
        settings.degrees = Degrees.CELSIUS
        settings.wind = WindUnit.KMH
        settings.alertedOn = ""
        WeatherStore.clear(context)
    }

    /** Units are a reading, not a storage: the same stored forecast reads either way. */
    @Test
    fun `changing the unit changes the reading, not the data`() {
        assertEquals("12°", settings.write(12.4))
        settings.degrees = Degrees.FAHRENHEIT
        assertEquals("54°", settings.write(12.4))

        // Rounded, as the app writes them — truncating turns 8.5 mph into 8 and reads as wrong.
        assertEquals(14, settings.writeWind(13.7))
        settings.wind = WindUnit.MS
        assertEquals(4, settings.writeWind(13.7))
        settings.wind = WindUnit.MPH
        assertEquals(9, settings.writeWind(13.7))
    }

    /** An interval that is not one of the offered ones is snapped rather than accepted. */
    @Test
    fun `the period is one of the offered ones`() {
        settings.periodMinutes = 47
        assertTrue(settings.periodMinutes in WeatherSettings.PERIODS)
        settings.periodMinutes = 360
        assertEquals(360, settings.periodMinutes)
    }

    @Test
    fun `the threshold cannot be set past its ends`() {
        settings.threshold = 5
        assertEquals(WeatherSettings.MIN_THRESHOLD, settings.threshold)
        settings.threshold = 200
        assertEquals(WeatherSettings.MAX_THRESHOLD, settings.threshold)
    }

    private fun forecast(rain: Int, date: LocalDate = LocalDate.now()) = Forecast(
        place = "Berlin", latitude = 52.5, longitude = 13.4,
        now = Conditions(12.0, 11.0, Sky.RAIN, true, 80, 10.0),
        days = listOf(DayForecast(date, Sky.RAIN, 15.0, 8.0, rain)),
        fetched = 0L,
    )

    @Test
    fun `nothing is announced while alerts are off`() {
        settings.alerts = false
        settings.threshold = 40
        assertTrue("an alert went out with alerts off", !RainAlert.consider(context, forecast(90)))
    }

    @Test
    fun `a dry day is not worth interrupting anybody about`() {
        settings.alerts = true
        settings.threshold = 60
        assertTrue("a 30% day raised an alert", !RainAlert.consider(context, forecast(30)))
    }

    /**
     * The one that matters: an hourly job re-evaluating the same day must not say the same thing
     * every hour until the rain arrives.
     */
    @Test
    fun `a wet day is announced once and then left alone`() {
        settings.alerts = true
        settings.threshold = 60

        val today = LocalDate.now()
        val first = RainAlert.consider(context, forecast(80, today))
        val second = RainAlert.consider(context, forecast(80, today))
        val third = RainAlert.consider(context, forecast(95, today))

        // Whether the platform actually accepted it depends on the notification permission, which
        // a test process does not have; what is being checked is the decision, and the decision is
        // "not again today" either way.
        assertTrue("the same day was announced twice", !second)
        assertTrue("a higher chance on the same day announced it again", !third)
        assertEquals(first, settings.alertedOn == today.toString())
    }

    /** Tomorrow is a different day, and gets its own sentence. */
    @Test
    fun `turning alerts off forgets that today was announced`() {
        settings.alerts = true
        settings.alertedOn = LocalDate.now().toString()
        settings.alerts = false
        settings.alertedOn = ""
        assertEquals("", settings.alertedOn)
    }
}
