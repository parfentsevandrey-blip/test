package app.papersky.weather.core

import app.papersky.weather.core.model.Current
import app.papersky.weather.core.model.Day
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Hour
import app.papersky.weather.core.text.Narrator
import app.papersky.weather.core.text.Note
import app.papersky.weather.core.text.PrecipKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarratorTest {
    private val now = 1_790_000_000L - 1_790_000_000L % 3600

    private fun forecast(codes: (Int) -> Int, precip: (Int) -> Double, temp: (Int) -> Double = { 12.0 }, currentCode: Int = 1): Forecast {
        val hours = (0 until 48).map { i ->
            Hour(
                time = now + i * 3600L, temperature = temp(i), feelsLike = temp(i), code = codes(i),
                precipProbability = if (precip(i) > 0) 80 else 5, precipitation = precip(i), cloudCover = 50,
                windSpeed = 3.0, windDirection = 200, windGusts = 6.0, humidity = 70, isDay = true,
                uvIndex = 2.0, visibility = 20_000.0, pressure = 1012.0,
            )
        }
        val midnight = now - 10 * 3600
        val days = (0 until 3).map { d ->
            Day(midnight + d * 86_400L, 3, 15.0, 8.0, midnight + d * 86_400L + 6 * 3600, midnight + d * 86_400L + 20 * 3600, 1.0, 50, 5.0, 9.0, 200, 3.0, 50_000.0)
        }
        return Forecast(
            placeId = "p", latitude = 0.0, longitude = 0.0, timezone = "UTC", utcOffsetSeconds = 0, fetchedAt = now * 1000,
            current = Current(now, temp(0), temp(0), 70, true, currentCode, 50, 1012.0, 3.0, 200, 6.0, precip(0)),
            hourly = hours, daily = days,
        )
    }

    @Test
    fun announcesRainThatIsComing() {
        val f = forecast(codes = { if (it >= 3) 63 else 2 }, precip = { if (it >= 3) 1.2 else 0.0 })
        val first = Narrator.headline(f, now)
        assertTrue("got $first", first is Note.PrecipSoon && first.kind == PrecipKind.Rain && first.at == now + 3 * 3600)
    }

    @Test
    fun saysWhenRainStops() {
        val f = forecast(codes = { if (it < 4) 63 else 2 }, precip = { if (it < 4) 1.0 else 0.0 }, currentCode = 63)
        val first = Narrator.headline(f, now)
        assertTrue("got $first", first is Note.PrecipEnds)
        assertEquals(now + 4 * 3600, (first as Note.PrecipEnds).at)
    }

    @Test
    fun warnsAboutFrostOnMildDays() {
        val f = forecast(codes = { 1 }, precip = { 0.0 }, temp = { if (it < 6) 6.0 else -3.0 })
            .let { it.copy(hourly = it.hourly.map { h -> h.copy(isDay = h.time - now < 6 * 3600) }) }
        assertTrue(Narrator.notes(f, now).any { it is Note.Frost })
    }

    @Test
    fun snowIsCalledSnow() {
        assertEquals(PrecipKind.Snow, Narrator.kindFor(73, -2.0))
        assertEquals(PrecipKind.Storm, Narrator.kindFor(95, 20.0))
        assertEquals(PrecipKind.Sleet, Narrator.kindFor(66, 0.0))
    }
}
