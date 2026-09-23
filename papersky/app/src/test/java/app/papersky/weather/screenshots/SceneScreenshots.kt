package app.papersky.weather.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.TestApp
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.HearthRenderer
import app.papersky.weather.scene.PaletteMode
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.scene.SceneVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApp::class)
class SceneScreenshots {

    private val states = mapOf(
        "clear_day" to SceneState(daylight = 1f, sunProgress = 0.42f, cloudCover = 0.15f, windX = 2f, temperature = 22f, seed = 11),
        "golden_hour" to SceneState(daylight = 1f, sunProgress = 0.93f, cloudCover = 0.35f, windX = -1f, temperature = 17f, seed = 12),
        "dusk" to SceneState(daylight = 0.5f, sunProgress = 1.02f, nightProgress = 0.05f, cloudCover = 0.3f, temperature = 12f, seed = 13),
        "night_clear" to SceneState(daylight = 0f, sunProgress = -0.4f, nightProgress = 0.45f, cloudCover = 0.1f, temperature = 6f, moonPhase = 0.3f, seed = 14),
        "rain_day" to SceneState(daylight = 1f, sunProgress = 0.5f, cloudCover = 0.95f, rain = 0.7f, windX = 4f, temperature = 13f, seed = 15),
        "storm_night" to SceneState(daylight = 0.05f, sunProgress = -0.2f, nightProgress = 0.3f, cloudCover = 1f, rain = 0.9f, thunder = 1f, windX = -7f, windSpeed = 9f, temperature = 16f, seed = 16),
        "snow_day" to SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.8f, snow = 0.8f, snowGround = 1f, windX = 1.5f, temperature = -4f, seed = 17),
        "fog_morning" to SceneState(daylight = 0.9f, sunProgress = 0.12f, cloudCover = 0.6f, fog = 0.85f, temperature = 4f, seed = 18),
        "rainbow" to SceneState(daylight = 1f, sunProgress = 0.8f, cloudCover = 0.45f, rainbow = 1f, temperature = 19f, seed = 19),
        "snow_night" to SceneState(daylight = 0f, sunProgress = -0.3f, nightProgress = 0.6f, cloudCover = 0.5f, snow = 0.5f, snowGround = 1f, temperature = -9f, moonPhase = 0.5f, seed = 20),
    )

    @Test
    fun renderScenes() {
        Shots.assumeEnabled()
        val density = 2.5f
        for ((name, s) in states) {
            val w = (412 * density).toInt()
            val h = (560 * density).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PaperSceneRenderer(density).draw(Canvas(bmp), w.toFloat(), h.toFloat(), s, Palettes.forState(s), PaperSceneRenderer.Options(time = 12.5f, staticBolt = s.thunder > 0.5f))
            Shots.save("scene_$name", bmp)
        }
    }

    /** Ophelia's river (§17) under the same weathers. */
    @Test
    fun renderOphelia() {
        Shots.assumeEnabled()
        val density = 2.5f
        for ((name, s) in states) {
            val w = (412 * density).toInt()
            val h = (560 * density).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val o = PaperSceneRenderer.Options(time = 12.5f, staticBolt = s.thunder > 0.5f, variant = SceneVariant.River)
            PaperSceneRenderer(density).draw(Canvas(bmp), w.toFloat(), h.toFloat(), s, Palettes.resolve(PaletteMode.Ophelia, s), o)
            Shots.save("ophelia_$name", bmp)
        }
    }

    /** The room by the fire (§16) under the same weathers, phone-tall and thumbnail-wide. */
    @Test
    fun renderHearth() {
        Shots.assumeEnabled()
        val density = 2f
        for ((name, s) in states) {
            for ((suffix, wDp, hDp) in listOf(Triple("", 412, 900), Triple("_wide", 412, 300))) {
                val w = (wDp * density).toInt()
                val h = (hDp * density).toInt()
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val frost = if (s.temperature < -2f) 0.8f else 0f
                HearthRenderer(density).draw(Canvas(bmp), w.toFloat(), h.toFloat(), s, Palettes.hearth(s), Palettes.forState(s), 12.5f, true, frost)
                Shots.save("hearth_$name$suffix", bmp)
            }
        }
    }

    /** Every glyph on day paper, night paper and a day sky: a proof sheet for the icon set. */
    @Test
    fun renderGlyphSheet() {
        Shots.assumeEnabled()
        val cell = 96
        val glyphs = Glyph.entries
        val rows = listOf(
            Triple(Palettes.ClearDay, true, Palettes.ClearDay.paper),
            Triple(Palettes.ClearNight, true, Palettes.ClearNight.paper),
            Triple(Palettes.ClearDay, false, Palettes.ClearDay.skyMid),
        )
        val bmp = Bitmap.createBitmap(cell * glyphs.size, cell * rows.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val renderer = GlyphRenderer()
        rows.forEachIndexed { r, (palette, onPaper, bg) ->
            canvas.save()
            canvas.clipRect(0, r * cell, bmp.width, (r + 1) * cell)
            canvas.drawColor(bg)
            canvas.restore()
            val colors = GlyphColors.from(palette, onPaper)
            glyphs.forEachIndexed { i, g -> renderer.draw(canvas, g, i * cell + 12f, r * cell + 12f, cell - 24f, colors, 0f, 30f) }
        }
        Shots.save("glyphs", bmp)
    }

    /** The phone hero: tall canvas, horizon high, sun kept to the right of the temperature. */
    @Test
    fun renderAppHero() {
        Shots.assumeEnabled()
        val density = 2f
        for ((name, s) in states) {
            val w = (412 * density).toInt()
            val h = (900 * density).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val o = PaperSceneRenderer.Options(time = 21f, horizon = 0.42f, laneStart = 0.5f, laneEnd = 0.92f, glass = true, staticBolt = s.thunder > 0.5f, vignette = 0.7f)
            PaperSceneRenderer(density).draw(Canvas(bmp), w.toFloat(), h.toFloat(), s, Palettes.forState(s), o)
            Shots.save("hero_$name", bmp)
        }
    }
}
