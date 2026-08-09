package app.quire.weather

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The half of the weather that has nothing to do with pixels: what comes back off the wire, what
 * is made of it, and what survives being put away and taken out again.
 *
 * Nothing here touches a network. The response below is the shape Open-Meteo actually answers
 * with — recorded, trimmed to the fields this app asks for — so parsing is exercised against the
 * real thing without the test depending on the weather, on a key, or on being online.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherDataTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val response = """
        {
          "latitude": 55.75, "longitude": 37.62, "timezone": "Europe/Moscow",
          "current": {
            "time": "2026-08-09T23:00",
            "temperature_2m": 12.8,
            "relative_humidity_2m": 81,
            "apparent_temperature": 11.4,
            "is_day": 0,
            "weather_code": 80,
            "wind_speed_10m": 13.7
          },
          "daily": {
            "time": ["2026-08-09","2026-08-10","2026-08-11","2026-08-12","2026-08-13","2026-08-14"],
            "weather_code": [80,3,0,95,71,45],
            "temperature_2m_max": [22.1,21.4,20.0,19.2,18.6,17.9],
            "temperature_2m_min": [11.3,10.8,9.4,8.7,7.5,6.9],
            "precipitation_probability_max": [70,30,0,80,60,10]
          }
        }
    """.trimIndent()

    @Test
    fun `a real response becomes a forecast`() {
        val forecast = WeatherRepository.parse(response, "Moscow", 55.75, 37.62, 1_000L)

        assertEquals(12.8, forecast.now.temperature, 0.001)
        assertEquals(11.4, forecast.now.feelsLike, 0.001)
        assertEquals(81, forecast.now.humidity)
        assertEquals(Sky.SHOWERS, forecast.now.sky)
        assertTrue("is_day 0 was read as daytime", !forecast.now.day)

        assertEquals(6, forecast.days.size)
        assertEquals(5, forecast.ahead().size)
        assertEquals(LocalDate.of(2026, 8, 9), forecast.days[0].date)
        assertEquals(Sky.THUNDER, forecast.days[3].sky)
        assertEquals(Sky.SNOW, forecast.days[4].sky)
        assertEquals(Sky.FOG, forecast.days[5].sky)
        assertEquals(70, forecast.days[0].rain)
        assertEquals(22.1, forecast.days[0].high, 0.001)
    }

    /**
     * A provider that drops a field must not take the forecast with it. Everything optional is
     * read as optional; only the date, the code and the two temperatures are load-bearing.
     */
    @Test
    fun `a response missing the optional fields still parses`() {
        val sparse = """
            {"current":{"temperature_2m":5.0,"weather_code":0},
             "daily":{"time":["2026-01-01"],"weather_code":[0],
             "temperature_2m_max":[2.0],"temperature_2m_min":[-4.0]}}
        """.trimIndent()
        val forecast = WeatherRepository.parse(sparse, "", 0.0, 0.0, 0L)

        assertEquals(5.0, forecast.now.feelsLike, 0.001)
        assertEquals(-1, forecast.now.humidity)
        assertTrue("a missing is_day should read as daytime", forecast.now.day)
        assertEquals(1, forecast.days.size)
        assertEquals(0, forecast.days[0].rain)
    }

    /** A short row is dropped rather than guessed at: half a day is not a forecast. */
    @Test
    fun `a truncated daily block stops where it runs out`() {
        val ragged = """
            {"current":{"temperature_2m":5.0,"weather_code":0},
             "daily":{"time":["2026-01-01","2026-01-02","2026-01-03"],"weather_code":[0,1,2],
             "temperature_2m_max":[2.0,3.0],"temperature_2m_min":[-4.0,-3.0]}}
        """.trimIndent()
        assertEquals(2, WeatherRepository.parse(ragged, "", 0.0, 0.0, 0L).days.size)
    }

    @Test
    fun `a forecast survives being stored and read back`() {
        val original = WeatherRepository.parse(response, "Москва", 55.75, 37.62, 4_242L)
        WeatherStore.save(context, original)
        val restored = WeatherStore.load(context)

        assertNotNull("nothing came back out of the store", restored)
        restored!!
        assertEquals("Москва", restored.place)
        assertEquals(original.fetched, restored.fetched)
        assertEquals(original.now.sky, restored.now.sky)
        assertEquals(original.now.day, restored.now.day)
        assertEquals(original.days.size, restored.days.size)
        assertEquals(original.days[3].sky, restored.days[3].sky)
        assertEquals(original.days[3].high, restored.days[3].high, 0.001)
        assertEquals(original.days[3].date, restored.days[3].date)
    }

    /**
     * Coordinates go out rounded. This is the one privacy claim the app makes in words, so it is
     * the one that gets an assertion rather than a comment.
     */
    @Test
    fun `only a rounded position is ever sent`() {
        val url = WeatherRepository.url(55.7558296, 37.6172999)
        assertTrue("an unrounded latitude went out: $url", url.contains("latitude=55.76"))
        assertTrue("an unrounded longitude went out: $url", url.contains("longitude=37.62"))
        assertTrue("the position was not rounded at all: $url", !url.contains("55.7558"))
    }

    /** Every WMO code the provider can answer with maps to something drawable. */
    @Test
    fun `every documented weather code has a sky`() {
        val documented = listOf(
            0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57,
            61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99,
        )
        documented.forEach { code ->
            val sky = Sky.of(code)
            assertTrue("code $code has no day icon", sky.dayIcon != 0)
            assertTrue("code $code has no night icon", sky.nightIcon != 0)
            assertTrue("code $code has no label", sky.label != 0)
        }
        // The ones a person would name differently must not have collapsed into each other.
        assertEquals(Sky.CLEAR, Sky.of(0))
        assertEquals(Sky.SNOW, Sky.of(73))
        assertEquals(Sky.THUNDER, Sky.of(99))
        assertEquals(Sky.SLEET, Sky.of(66))
    }

    @Test
    fun `a fetch goes through the injected reader and never the network`() {
        var asked: String? = null
        val forecast = WeatherRepository.fetch(1.0, 2.0, "Nowhere", 7L) { url ->
            asked = url
            response
        }
        assertTrue("the fetch did not use the endpoint: $asked", asked!!.contains("open-meteo"))
        assertTrue("the fetch asked for no forecast days", asked!!.contains("forecast_days=6"))
        assertEquals("Nowhere", forecast.place)
        assertEquals(7L, forecast.fetched)
    }
}
