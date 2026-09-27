package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The AMOLED black and tinted glass appearances. */
class ThemesTest {
    private val rainy = WeatherVisual.from(WeatherCondition.Rain, 90)

    @Test
    fun `amoled puts the sky out and keeps the light ink of the night`() {
        for (elevation in listOf(-30.0, -4.0, 10.0, 45.0)) {
            for (visual in listOf(WeatherVisual.ClearDay, rainy)) {
                val p = SkyPalette.of(Appearance.Amoled, elevation, visual)
                assertThat(p.zenith).isEqualTo(Argb.Black)
                assertThat(p.horizon).isEqualTo(Argb.Black)
                assertThat(p.glow).isEqualTo(Argb.Black)
                assertThat(p.brightness).isEqualTo(0.0)
                assertThat(p.isLight).isFalse()
                assertThat(p.ink.luminance).isGreaterThan(0.9)
            }
        }
    }

    @Test
    fun `tinted glass keeps the real sky`() {
        for (elevation in listOf(-30.0, -4.0, 10.0, 45.0)) {
            assertThat(SkyPalette.of(Appearance.Tinted, elevation, rainy)).isEqualTo(SkyPalette.of(Appearance.Auto, elevation, rainy))
        }
        assertThat(Appearance.Tinted.followsRealSky).isTrue()
        assertThat(Appearance.Auto.followsRealSky).isTrue()
        assertThat(Appearance.Amoled.followsRealSky).isFalse()
        assertThat(Appearance.Dark.followsRealSky).isFalse()
    }

    /**
     * Every hue, not just the swatches: the slider reaches all of them. The glass has the same
     * luminance whatever its hue, so the ink on it reads equally well; the accent stands out from
     * the glass and from the sky's own extremes.
     */
    @Test
    fun `every hue of glass keeps its type and accent legible`() {
        val darkInk = Argb.hex(0x1B2030)
        val lightInk = Argb.hex(0xFFFBF5)
        for (hue in 0 until 360) {
            val paleGlass = GlassTint.glass(hue, lightSky = true)
            val deepGlass = GlassTint.glass(hue, lightSky = false)
            assertWithMessage("pale glass, hue $hue").that(paleGlass.luminance).isWithin(0.015).of(0.6)
            assertWithMessage("deep glass, hue $hue").that(deepGlass.luminance).isWithin(0.008).of(0.045)
            assertWithMessage("dark ink on pale glass, hue $hue").that(contrast(darkInk, paleGlass)).isAtLeast(7.0)
            assertWithMessage("light ink on deep glass, hue $hue").that(contrast(lightInk, deepGlass)).isAtLeast(10.0)

            val deepAccent = GlassTint.accent(hue, lightSky = true)
            val brightAccent = GlassTint.accent(hue, lightSky = false)
            assertWithMessage("deep accent on pale glass, hue $hue").that(contrast(deepAccent, paleGlass)).isAtLeast(4.5)
            assertWithMessage("deep accent on white, hue $hue").that(contrast(deepAccent, Argb.White)).isAtLeast(6.0)
            assertWithMessage("bright accent on deep glass, hue $hue").that(contrast(brightAccent, deepGlass)).isAtLeast(4.5)
            assertWithMessage("bright accent on black, hue $hue").that(contrast(brightAccent, Argb.Black)).isAtLeast(9.0)
        }
    }

    @Test
    fun `the tone keeps the hue it was asked for`() {
        // Pure hues land on their own channel mix.
        val red = GlassTint.tone(0, 1.0, 0.2)
        assertThat(red.red).isGreaterThan(red.green + 100)
        assertThat(red.green).isEqualTo(red.blue)
        val blue = GlassTint.tone(240, 1.0, 0.2)
        assertThat(blue.blue).isGreaterThan(blue.red + 100)
        assertThat(GlassTint.hsl(120, 1.0, 0.5)).isEqualTo(Argb.rgb(0, 255, 0))
        assertThat(GlassTint.hsl(-120, 1.0, 0.5)).isEqualTo(GlassTint.hsl(240, 1.0, 0.5))
        assertThat(GlassTint.Swatches).contains(GlassTint.DEFAULT_HUE)
    }

    private fun contrast(a: Argb, b: Argb): Double {
        val hi = maxOf(a.luminance, b.luminance)
        val lo = minOf(a.luminance, b.luminance)
        return (hi + 0.05) / (lo + 0.05)
    }
}
