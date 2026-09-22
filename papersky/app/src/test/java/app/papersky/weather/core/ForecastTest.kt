package app.papersky.weather.core

import app.papersky.weather.Fixtures
import app.papersky.weather.core.data.toForecast
import app.papersky.weather.core.model.Astro
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.MoonPhaseName
import app.papersky.weather.core.model.Precipitation
import app.papersky.weather.core.model.TempUnit
import app.papersky.weather.core.model.WindUnit
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.core.text.Convert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTest {

    @Test
    fun parsesTheRecordedOpenMeteoResponse() {
        val f = Fixtures.dto().toForecast("here", 0)
        assertEquals("Europe/Moscow", f.timezone)
        assertEquals(10, f.daily.size)
        assertTrue(f.hourly.size > 200)
        assertEquals(8, f.nowcast.size)
        assertEquals(15.6, f.current.temperature, 0.01)
        assertEquals(Condition.Rain, Condition.fromWmo(f.current.code))
        assertTrue(f.hourly.zipWithNext().all { (a, b) -> b.time - a.time == 3600L })
    }

    @Test
    fun liveObservationIsPreferredOnlyWhileFresh() {
        val now = 1_790_099_100_000L
        val f = Fixtures.moscow(now)
        val live = f.momentAt(now / 1000)!!
        assertTrue(live.isLive)
        assertEquals(f.current.temperature, live.temperature, 0.001)
        val later = f.momentAt(now / 1000 + 5 * 3600)!!
        assertTrue(!later.isLive)
    }

    @Test
    fun hourlyValuesAreInterpolated() {
        val f = Fixtures.moscow(1_790_099_100_000L)
        val i = 10
        val a = f.hourly[i]
        val b = f.hourly[i + 1]
        val mid = f.momentAt(a.time + 1800, preferLive = false)!!
        assertEquals((a.temperature + b.temperature) / 2, mid.temperature, 0.01)
    }

    @Test
    fun wmoCodesMapToConditionsAndChannels() {
        assertEquals(Condition.Clear, Condition.fromWmo(0))
        assertEquals(Condition.Fog, Condition.fromWmo(48))
        assertEquals(Condition.ThunderHail, Condition.fromWmo(99))
        assertTrue(Condition.fromWmo(86).isSnowy)
        val downpour = Precipitation.from(61, mmPerHour = 8.0)
        assertTrue("slight-rain code with 8 mm/h should look heavy", downpour.rain > 0.9f)
        assertEquals(0f, Precipitation.from(2, 0.0).total, 0f)
        assertTrue(Precipitation.from(71, 0.2).snow > 0f)
    }

    @Test
    fun daylightRampsThroughTwilight() {
        val rise = 1_000_000L
        val set = rise + 12 * 3600
        assertEquals(1f, Astro.daylight(rise + 6 * 3600, rise, set, true), 0.001f)
        assertEquals(0f, Astro.daylight(set + 3 * 3600, rise, set, true), 0.001f)
        assertEquals(0.5f, Astro.daylight(set, rise, set, true), 0.01f)
        assertEquals(0.5f, Astro.sunProgress(rise + 6 * 3600, rise, set, true), 0.001f)
    }

    @Test
    fun moonPhasesCycle() {
        // 2026-09-26 is a full moon (UTC ~16:49).
        val fullMoon = 1_790_441_340L
        assertEquals(MoonPhaseName.Full, MoonPhaseName.of(Astro.moonPhase(fullMoon)))
        assertTrue(Astro.moonIllumination(Astro.moonPhase(fullMoon)) > 0.97f)
    }

    @Test
    fun unitConversions() {
        assertEquals(32.0, Convert.temp(0.0, TempUnit.Fahrenheit), 0.001)
        assertEquals(36.0, Convert.wind(10.0, WindUnit.KilometersPerHour), 0.001)
        assertEquals("−5", Convert.signed(-5))
        assertEquals(0, Convert.roundTemp(-0.4, TempUnit.Celsius))
        assertNotNull(Convert.signed(0))
    }
}
