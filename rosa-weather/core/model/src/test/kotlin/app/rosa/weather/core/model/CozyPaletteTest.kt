package app.rosa.weather.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cozy mood: warm ivory ink over a deep blue-hour sky, in any weather, and amber glass. */
class CozyPaletteTest {
    private val weathers = listOf(
        WeatherVisual.ClearDay,
        WeatherVisual.from(WeatherCondition.PartlyCloudy, 45),
        WeatherVisual.from(WeatherCondition.Overcast, 100),
        WeatherVisual.from(WeatherCondition.Rain, 95, precipitationMm = 3.0),
        WeatherVisual.from(WeatherCondition.HeavySnow, 100, precipitationMm = 4.0),
        WeatherVisual.from(WeatherCondition.Fog, 100),
        WeatherVisual.from(WeatherCondition.Thunderstorm, 100, precipitationMm = 8.0),
    )

    @Test
    fun `the same cozy dusk at any hour`() {
        val noon = SkyPalette.of(Appearance.Cozy, 45.0, WeatherVisual.ClearDay)
        val night = SkyPalette.of(Appearance.Cozy, -30.0, WeatherVisual.ClearDay)
        assertEquals(noon, night)
    }

    @Test
    fun `warm light ink reads over the sky in every weather`() {
        weathers.forEach { visual ->
            val p = SkyPalette.of(Appearance.Cozy, 30.0, visual)
            assertFalse("light type over the cozy sky in $visual", p.isLight)
            // Warm ivory: more red than blue.
            assertTrue("warm ink in $visual", p.ink.red > p.ink.blue)
            // WCAG contrast of the ink against the sky behind the big type.
            val behind = p.zenith.lerp(p.horizon, 0.3f).luminance
            val ratio = (p.ink.luminance + 0.05) / (behind + 0.05)
            assertTrue("ink contrast %.2f in $visual".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun `the glass turns amber only in the cozy mood`() {
        val cozy = SkyPalette.of(Appearance.Cozy, 30.0, WeatherVisual.ClearDay)
        assertNotNull(cozy.glass)
        val glass = cozy.glass!!
        assertTrue("amber glass", glass.red > glass.green && glass.green > glass.blue)
        Appearance.entries.filter { it != Appearance.Cozy }.forEach {
            assertNull("no glass of its own in $it", SkyPalette.of(it, 30.0, WeatherVisual.ClearDay).glass)
        }
    }
}
