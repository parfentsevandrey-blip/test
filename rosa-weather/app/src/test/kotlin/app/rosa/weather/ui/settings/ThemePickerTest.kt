package app.rosa.weather.ui.settings

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.LocalSky
import app.rosa.weather.ui.common.RosaTab
import app.rosa.weather.ui.common.SkyController
import app.rosa.weather.ui.common.TabBarScaffold
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings → Appearance with real touches: the six tiles, the beads of colour and the strip of the
 * spectrum under tinted glass, which the whole app's glass follows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ThemePickerTest {
    @get:Rule val compose = createComposeRule()

    private var settings by mutableStateOf(AppSettings(effects = EffectsQuality.Balanced))

    /** Every hue saved while the finger works the picker, in order. */
    private val saved = mutableListOf<Int>()

    private fun screen(start: AppSettings) {
        settings = start
        val now = 1_758_621_600L
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = "geo:1")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)) }
            LaunchedEffect(settings.appearance) { sky.applyAppearance(settings.appearance) }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = sky.transitionMillis) {
                        TabBarScaffold(RosaTab.Settings, {}) {
                            SettingsScreen(
                                settings = settings,
                                onUpdate = { change ->
                                    val next = change(settings)
                                    if (next.glassHue != settings.glassHue) saved += next.glassHue
                                    settings = next
                                },
                                onUpdateUnits = {},
                                onBackgroundLocation = {},
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
    }

    /** Where the knob stands for [hue] across a strip [width] px wide (the knob is 28 dp). */
    private fun xOf(hue: Float, width: Float): Float {
        val knob = 28f * 3f
        return knob / 2f + hue / 359f * (width - knob)
    }

    @Test
    fun aTileChoosesItsAppearanceAndTintedGlassOpensItsColours() {
        screen(AppSettings(effects = EffectsQuality.Balanced))
        compose.onNodeWithContentDescription("Оттенок").assertDoesNotExist()
        compose.onNodeWithContentDescription("AMOLED").performClick()
        compose.mainClock.advanceTimeBy(600)
        assertThat(settings.appearance).isEqualTo(Appearance.Amoled)
        compose.onNodeWithContentDescription("Цветное стекло").performClick()
        compose.mainClock.advanceTimeBy(900)
        assertThat(settings.appearance).isEqualTo(Appearance.Tinted)
        compose.onNodeWithContentDescription("Оттенок").assertExists()
        compose.onNodeWithContentDescription("Лазурь").assertExists()
    }

    @Test
    fun aBeadSetsItsHue() {
        screen(AppSettings(effects = EffectsQuality.Balanced, appearance = Appearance.Tinted))
        compose.onNodeWithContentDescription("Лазурь").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertThat(settings.glassHue).isEqualTo(205)
        compose.onNodeWithContentDescription("Янтарь").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertThat(settings.glassHue).isEqualTo(36)
    }

    /**
     * The strip: the glass follows the finger a few times a second — not on every move, each is
     * saved — and settles on the hue under the finger where it lifts.
     */
    @Test
    fun slidingTheStripSetsTheHueWhereTheFingerLifts() {
        screen(AppSettings(effects = EffectsQuality.Balanced, appearance = Appearance.Tinted, glassHue = 340))
        // A 0.64 s slide: a move every 16 ms, as a 60 Hz touchscreen reports them.
        val moves = 40
        compose.onNodeWithContentDescription("Оттенок").performTouchInput {
            val y = height / 2f
            down(Offset(xOf(20f, width.toFloat()), y))
            for (i in 1..moves) moveTo(Offset(xOf(20f + 160f * i / moves, width.toFloat()), y), delayMillis = 16)
            up()
        }
        compose.mainClock.advanceTimeBy(300)
        println("strip: saved ${saved.size} times over $moves moves: $saved")
        assertThat(settings.glassHue.toFloat()).isWithin(2f).of(180f)
        // At most every 90 ms of the slide, plus the first touch and the lift.
        assertThat(saved.size).isAtLeast(4)
        assertThat(saved.size).isAtMost(moves * 16 / 90 + 2)
        assertThat(saved).isInOrder()
    }

    /** TalkBack sets the hue as a value on the strip. */
    @Test
    fun theHueCanBeSetWithoutSliding() {
        screen(AppSettings(effects = EffectsQuality.Balanced, appearance = Appearance.Tinted))
        compose.onNodeWithContentDescription("Оттенок").performSemanticsAction(SemanticsActions.SetProgress) { it(120.4f) }
        compose.mainClock.advanceTimeBy(300)
        assertThat(settings.glassHue).isEqualTo(120)
    }

    /**
     * A film for review and the README: the finger slides the strip from rose down to mint, and
     * every pane follows. Frames every 50 ms to `build/theme-frames/`. Some 70 full-screen frames
     * of glass take minutes to draw on the CPU: only with `-Prosa.docs`.
     */
    @Test
    fun film() {
        assumeTrue("a film for the README: run with -Prosa.docs", System.getProperty("rosa.docs") != null)
        screen(AppSettings(effects = EffectsQuality.Balanced, appearance = Appearance.Tinted, glassHue = 340))
        val dir = File("build/theme-frames").apply { deleteRecursively(); mkdirs() }
        var frame = 0
        fun shoot() {
            val full = compose.onRoot().captureToImage().asAndroidBitmap()
            // Appearance, and the top of the next pane: every pane on screen takes the colour.
            val top = Bitmap.createBitmap(full, 0, 0, full.width, (full.height * 0.68f).roundToInt())
            File(dir, "%03d.png".format(frame++)).outputStream().use { top.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        repeat(8) { compose.mainClock.advanceTimeBy(50); shoot() }
        compose.onNodeWithContentDescription("Оттенок").performTouchInput { down(Offset(xOf(340f, width.toFloat()), height / 2f)) }
        compose.mainClock.advanceTimeBy(50)
        shoot()
        // Down the spectrum to mint, a little each frame, as a thumb moves.
        val steps = 36
        for (i in 1..steps) {
            val hue = 340f - 190f * (i / steps.toFloat()).let { it * it * (3f - 2f * it) }
            compose.onNodeWithContentDescription("Оттенок").performTouchInput {
                advanceEventTime(50)
                moveTo(Offset(xOf(hue, width.toFloat()), height / 2f))
            }
            compose.mainClock.advanceTimeBy(50)
            shoot()
        }
        compose.onNodeWithContentDescription("Оттенок").performTouchInput { up() }
        repeat(26) { compose.mainClock.advanceTimeBy(50); shoot() }
        assertThat(settings.glassHue.toFloat()).isWithin(2f).of(150f)
    }
}
