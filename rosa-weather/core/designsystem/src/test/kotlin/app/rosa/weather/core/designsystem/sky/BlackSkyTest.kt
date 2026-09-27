package app.rosa.weather.core.designsystem.sky

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.momentAt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The AMOLED sky, rendered by the real [SkyScene]: true black — pixels that stay off — with only
 * the weather glowing on it. Renders to `build/weather/black-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h720dp-mdpi")
class BlackSkyTest {
    @get:Rule val compose = createComposeRule()

    private fun render(
        name: String,
        scenario: SampleForecast.Scenario,
        epoch: Long,
        appearance: Appearance = Appearance.Amoled,
        change: (SkyParams) -> SkyParams = { it },
    ): Bitmap {
        val moment = SampleForecast.create(scenario, nowEpochSeconds = epoch).momentAt(epoch)
        val palette = SkyPalette.of(appearance, moment.sun.elevation, moment.visual, moment.moonPhase.illumination)
        val params = change(SkyParams.from(moment, palette, appearance))
        compose.setContent {
            SkyScene(params, Modifier.fillMaxSize(), quality = SceneQuality.Balanced, animate = false, interactive = false, transitionMillis = 0)
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/weather").apply { mkdirs() }
        File(dir, "black-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private class Stats(val black: Double, val mean: Double, val brightest: Double)

    private fun stats(bitmap: Bitmap): Stats {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var black = 0
        var sum = 0.0
        var max = 0.0
        for (c in pixels) {
            if (c and 0xFFFFFF == 0) black++
            val l = luminance(c)
            sum += l
            if (l > max) max = l
        }
        return Stats(black.toDouble() / pixels.size, sum / pixels.size, max)
    }

    private fun luminance(c: Int): Double {
        fun ch(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    @Test
    fun aClearDayIsBlackToTheLastPixel() {
        val s = stats(render("day", SampleForecast.Scenario.SunnyMild, 1_758_621_600L))
        assertThat(s.black).isEqualTo(1.0)
    }

    /** Night: the stars come out on the black, and nothing else. */
    @Test
    fun aClearNightIsBlackWithStars() {
        val s = stats(render("night", SampleForecast.Scenario.ClearNight, 1_758_664_800L))
        println("black night: %.4f black, brightest %.3f".format(s.black, s.brightest))
        assertThat(s.black).isAtLeast(0.97)
        assertWithMessage("the stars").that(s.brightest).isAtLeast(0.3)
    }

    /**
     * Rain on black: the streaks and the beads on the pane catch the light, but no grey veil of
     * rain, no mist on the pane — most of the screen stays off.
     */
    @Test
    fun rainFallsThroughTheBlackWithoutAVeil() {
        val s = stats(
            render("rain", SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L) {
                it.copy(rain = 0.6f, condensation = 0.4f)
            },
        )
        println("black rain: %.4f black, mean %.4f, brightest %.3f".format(s.black, s.mean, s.brightest))
        assertThat(s.mean).isLessThan(0.02)
        assertWithMessage("the rain itself").that(s.brightest).isAtLeast(0.08)
    }

    /** A freezing, humid day: frost and mist would grey the pane from its edges; on black they stay away. */
    @Test
    fun frostAndMistStayOffTheBlack() {
        val s = stats(
            render("frost", SampleForecast.Scenario.SnowyCold, 1_758_610_800L) {
                it.copy(frost = 0.85f, condensation = 0.6f, snow = 0f, rain = 0f)
            },
        )
        assertThat(s.black).isEqualTo(1.0)
    }

    /**
     * Switching to it, the sky goes out gradually: halfway it is neither the real sky any more nor
     * black yet. (Its colours fade in OKLab, perceptually, and the sky fades over them: halfway is
     * dim to a photometer, not to the eye.)
     */
    @Test
    fun theSkyGoesOutGradually() {
        val moment = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = 1_758_621_600L).momentAt(1_758_621_600L)
        val auto = SkyParams.from(moment, SkyPalette.of(Appearance.Auto, moment.sun.elevation, moment.visual), Appearance.Auto)
        val black = SkyParams.from(moment, SkyPalette.of(Appearance.Amoled, moment.sun.elevation, moment.visual), Appearance.Amoled)
        // The real sky and the one halfway out, side by side.
        compose.setContent {
            Row(Modifier.fillMaxSize()) {
                SkyScene(auto, Modifier.weight(1f).fillMaxHeight(), quality = SceneQuality.Balanced, animate = false, interactive = false, transitionMillis = 0)
                SkyScene(auto.lerp(black, 0.5f), Modifier.weight(1f).fillMaxHeight(), quality = SceneQuality.Balanced, animate = false, interactive = false, transitionMillis = 0)
            }
        }
        compose.waitForIdle()
        val both = compose.onRoot().captureToImage().asAndroidBitmap()
        val half = both.width / 2
        val real = stats(Bitmap.createBitmap(both, 0, 0, half, both.height))
        val goingOut = stats(Bitmap.createBitmap(both, half, 0, half, both.height))
        println("going out: real %.3f, halfway %.3f".format(real.mean, goingOut.mean))
        assertThat(goingOut.mean).isLessThan(real.mean * 0.6)
        assertThat(goingOut.mean).isGreaterThan(0.008)
        assertThat(goingOut.black).isLessThan(0.01)
    }
}
