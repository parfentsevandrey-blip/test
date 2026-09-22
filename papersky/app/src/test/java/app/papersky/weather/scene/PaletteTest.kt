package app.papersky.weather.scene

import org.junit.Assert.assertTrue
import org.junit.Test

/** Legibility guard: text drawn straight onto any sky, or onto paper, must stay readable. */
class PaletteTest {
    private val states = buildList {
        for (daylight in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (progress in listOf(-0.3f, 0.03f, 0.5f, 0.95f, 1.05f)) {
                add(SceneState(daylight = daylight, sunProgress = progress, cloudCover = 0.1f))
                add(SceneState(daylight = daylight, sunProgress = progress, cloudCover = 1f, rain = 0.8f))
                add(SceneState(daylight = daylight, sunProgress = progress, cloudCover = 0.8f, snow = 0.8f))
                add(SceneState(daylight = daylight, sunProgress = progress, cloudCover = 1f, rain = 0.9f, thunder = 1f))
                add(SceneState(daylight = daylight, sunProgress = progress, cloudCover = 0.5f, fog = 0.9f))
            }
        }
    }

    @Test
    fun textOnTheSkyHasContrast() {
        for (s in states) for (mode in PaletteMode.entries.filter { it != PaletteMode.Wallpaper }) {
            val p = Palettes.resolve(mode, s)
            val c = ColorMath.contrast(p.onSky, p.skyMid)
            assertTrue("$mode $s: sky text contrast $c", c >= 3.0f)
        }
    }

    @Test
    fun inkOnPaperIsReadable() {
        for (s in states) for (mode in PaletteMode.entries.filter { it != PaletteMode.Wallpaper }) {
            val p = Palettes.resolve(mode, s)
            val c = ColorMath.contrast(p.paperInk, p.paper)
            assertTrue("$mode $s: paper contrast $c", c >= 7f)
        }
    }

    @Test
    fun weightsCoverEveryTimeOfDay() {
        for (d in 0..20) for (pr in -5..25) {
            val w = Palettes.timeWeights(d / 20f, pr / 20f)
            assertTrue(kotlin.math.abs(w.sum() - 1f) < 1e-3f)
            assertTrue(w.all { it >= -1e-6f })
        }
    }
}
