package app.rosa.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.SkyController
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Glass under the finger, rendered held down: a card pushed in at its right edge tilts away there
 * (its far edge stands taller than the pressed one), a button drawn to the right leans and
 * stretches after the finger.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class GlassPressGalleryTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String): Bitmap {
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screens").apply { mkdirs() }.let { File(it, "$name.png").outputStream().use { o -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, o) } }
        return bitmap
    }

    private fun scene() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(SampleForecast.create(SampleForecast.Scenario.SunnyMild).momentAt(1_758_628_800L)) }
            RosaEnvironment(AppSettings(), sky.palette, richGlass = true) {
                SkyBackdrop(sky.params, EffectsQuality.Balanced, transitionMillis = 0) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        GlassSurface(Modifier.fillMaxWidth().height(CARD_HEIGHT.dp), style = GlassStyle.Frosted, contentPadding = PaddingValues(20.dp)) {
                            Text("Ясно, 18°", color = Rosa.colors.ink)
                        }
                        GlassButton({}, Modifier.padding(start = 40.dp).width(BUTTON_WIDTH.dp).height(56.dp)) {
                            Text("Обновить", color = Rosa.colors.ink)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
    }

    /** How tall the card stands at column [x], px: from its lit top rim to its bottom rim. */
    private fun cardSpan(bitmap: Bitmap, x: Int): Int {
        fun lum(c: Int) = android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)
        val ys = (4 * D until (16 + CARD_HEIGHT + 12) * D).filter { y -> lum(bitmap.getPixel(x, y)) - lum(bitmap.getPixel(x, y - 3 * D)) > 70 }
        val top = ys.firstOrNull() ?: return 0
        val ysb = (4 * D until (16 + CARD_HEIGHT + 12) * D).filter { y -> lum(bitmap.getPixel(x, y)) - lum(bitmap.getPixel(x, y + 3 * D)) > 70 }
        val bottom = ysb.lastOrNull() ?: return 0
        return bottom - top
    }

    @Test
    fun pressAndPull() {
        scene()
        val rest = capture("glass-rest")
        val cardY = (16 + CARD_HEIGHT / 2f) * D
        val buttonCentre = Offset((16 + 40 + BUTTON_WIDTH / 2f) * D, (16 + CARD_HEIGHT + 28 + 28f) * D)
        compose.onRoot().performTouchInput {
            down(0, Offset((16 + 379 * 0.93f) * D, cardY))
            down(1, buttonCentre)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onRoot().performTouchInput { moveTo(1, buttonCentre + Offset(70f * D, 0f)) }
        val held = capture("glass-pressed")
        val leftRest = cardSpan(rest, 30 * D)
        val rightRest = cardSpan(rest, 320 * D)
        val left = cardSpan(held, 30 * D)
        val right = cardSpan(held, 320 * D)
        println("card height at rest: left $leftRest, right $rightRest; pressed at the right: left $left, right $right")
        // Pushed in at the right, that side goes away from the eye: it stands shorter, against the
        // far side, than it did at rest.
        com.google.common.truth.Truth.assertThat(right.toFloat() / left).isLessThan(rightRest.toFloat() / leftRest - 0.04f)
        compose.onRoot().performTouchInput { up(0); up(1) }
        compose.mainClock.advanceTimeBy(160)
        capture("glass-released")
    }

    /** Frames of a press and a release, 40 ms apart, for the README animation. */
    @Test
    fun pressFrames() {
        scene()
        val dir = File("build/screens/glass-press").apply { deleteRecursively(); mkdirs() }
        var i = 0
        fun frame() {
            compose.waitForIdle()
            val b = compose.onRoot().captureToImage().asAndroidBitmap()
            val crop = Bitmap.createBitmap(b, 0, 0, b.width, (16 + CARD_HEIGHT + 28 + 56 + 30) * D)
            File(dir, "%03d.png".format(i++)).outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val card = Offset((16 + 379 * 0.88f) * D, (16 + CARD_HEIGHT * 0.3f) * D)
        val button = Offset((16 + 40 + BUTTON_WIDTH * 0.3f) * D, (16 + CARD_HEIGHT + 28 + 28f) * D)
        repeat(4) { compose.mainClock.advanceTimeBy(40); frame() }
        compose.onRoot().performTouchInput { down(0, card); down(1, button) }
        repeat(8) { k ->
            compose.mainClock.advanceTimeBy(40)
            compose.onRoot().performTouchInput { moveTo(1, button + Offset((k + 1) * 9f * D, 0f)) }
            frame()
        }
        repeat(4) { compose.mainClock.advanceTimeBy(40); frame() }
        compose.onRoot().performTouchInput { up(0); up(1) }
        repeat(26) { compose.mainClock.advanceTimeBy(40); frame() }
    }

    private companion object {
        const val D = 3
        const val CARD_HEIGHT = 150
        const val BUTTON_WIDTH = 190
    }
}
