package app.rosa.weather.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.glass.GlassEnvironment
import app.rosa.weather.core.designsystem.glass.LocalGlassEnvironment
import app.rosa.weather.core.designsystem.glass.backdropSource
import app.rosa.weather.core.designsystem.glass.rememberBackdrop
import app.rosa.weather.core.designsystem.theme.RosaColors
import app.rosa.weather.core.designsystem.theme.RosaTheme
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tab bar at the bottom of the screen: the finger slides across it without lifting, the lens
 * follows, and only the tab under the finger when it lifts opens — once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h400dp-xxhdpi")
class TabBarTest {
    @get:Rule val compose = createComposeRule()

    private val tabs = listOf(
        GlassTab(RosaIcon.Weather, "Погода"),
        GlassTab(RosaIcon.Pin, "Места"),
        GlassTab(RosaIcon.Widgets, "Виджеты"),
        GlassTab(RosaIcon.Settings, "Настройки"),
    )

    private var selected by mutableIntStateOf(0)
    private val opened = mutableListOf<Int>()

    private fun bar(night: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val backdrop = rememberBackdrop()
            RosaTheme(colors(light = !night)) {
                Box(Modifier.fillMaxSize().testTag("screen")) {
                    Canvas(Modifier.fillMaxSize().backdropSource(backdrop)) {
                        drawRect(Brush.verticalGradient(if (night) listOf(Color(0xFF0B1330), Color(0xFF1C2A55)) else listOf(Color(0xFF3E8EF7), Color(0xFF8CC8FF))))
                    }
                    CompositionLocalProvider(LocalBackdrop provides backdrop, LocalGlassEnvironment provides GlassEnvironment().apply { tint = if (night) Color(0xFF0B1020) else Color.White }) {
                        GlassTabBar(
                            tabs = tabs,
                            selected = selected,
                            onSelect = {
                                opened += it
                                selected = it
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 24.dp).testTag("bar"),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    /** The middle of tab [i] in the bar's own coordinates. */
    private fun tab(i: Int, width: Float, height: Float) = Offset(width * (i + 0.5f) / tabs.size, height / 2f)

    @Test
    fun slidingAcrossTheBarOpensTheTabUnderTheFingerOnLift() {
        bar()
        val frames = mutableListOf<Bitmap>()
        frames += capture()
        compose.onNodeWithTag("bar").performTouchInput {
            down(tab(0, width.toFloat(), height.toFloat()))
        }
        compose.mainClock.advanceTimeBy(120)
        frames += capture()
        // Across Места and Виджеты to Настройки and back to Виджеты, never lifting the finger.
        for (x in listOf(0.2f, 0.35f, 0.5f, 0.62f, 0.75f, 0.88f, 0.8f, 0.7f, 0.63f)) {
            compose.onNodeWithTag("bar").performTouchInput { moveTo(Offset(width * x, height / 2f)) }
            compose.mainClock.advanceTimeBy(48)
            if (x == 0.5f || x == 0.88f) frames += capture()
        }
        // Nothing opens while the finger is down.
        assertThat(opened).isEmpty()
        assertThat(selected).isEqualTo(0)
        compose.onNodeWithTag("bar").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        frames += capture()
        // The tab under the finger when it lifted, once.
        assertThat(opened).containsExactly(2)
        assertThat(selected).isEqualTo(2)
        compose.mainClock.advanceTimeBy(1_200)
        frames += capture()
        sheet(frames, "tabbar-scrub.png")
    }

    @Test
    fun aTapOpensItsTabAndATapOnTheOpenTabNothing() {
        bar()
        compose.onNodeWithTag("bar").performTouchInput { click(tab(3, width.toFloat(), height.toFloat())) }
        compose.mainClock.advanceTimeBy(600)
        assertThat(opened).containsExactly(3)
        compose.onNodeWithTag("bar").performTouchInput { click(tab(3, width.toFloat(), height.toFloat())) }
        compose.mainClock.advanceTimeBy(600)
        assertThat(opened).containsExactly(3)
    }

    @Test
    fun slidingPastTheEndStaysOnTheLastTab() {
        bar()
        compose.onNodeWithTag("bar").performTouchInput {
            down(tab(1, width.toFloat(), height.toFloat()))
            moveTo(Offset(width * 1.4f, height / 2f))
            moveTo(Offset(width * 1.8f, height * 3f))
            up()
        }
        compose.mainClock.advanceTimeBy(600)
        assertThat(opened).containsExactly(3)
    }

    /**
     * A film of one slide for review: the finger lands on Погода, slides slowly to Настройки and
     * back to Места, and lifts. Frames every 32 ms to `build/glass-lab/tabbar-frames/`.
     */
    @Test
    fun film() {
        bar(night = true)
        val dir = File("build/glass-lab/tabbar-frames").apply { deleteRecursively(); mkdirs() }
        var frame = 0
        fun shoot() {
            File(dir, "%03d.png".format(frame++)).outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        repeat(6) { compose.mainClock.advanceTimeBy(32); shoot() }
        compose.onNodeWithTag("bar").performTouchInput { down(tab(0, width.toFloat(), height.toFloat())) }
        repeat(6) { compose.mainClock.advanceTimeBy(32); shoot() }
        // To the right end and back to Места, a little each frame, as a thumb moves.
        val path = (0..40).map { 0.125f + 0.75f * it / 40f } + (0..24).map { 0.875f - 0.5f * it / 24f }
        for (x in path) {
            compose.onNodeWithTag("bar").performTouchInput { moveTo(Offset(width * x, height / 2f)) }
            compose.mainClock.advanceTimeBy(32)
            shoot()
        }
        repeat(4) { compose.mainClock.advanceTimeBy(32); shoot() }
        compose.onNodeWithTag("bar").performTouchInput { up() }
        repeat(30) { compose.mainClock.advanceTimeBy(32); shoot() }
        assertThat(opened).containsExactly(1)
    }

    /** By night: the bar and its lens over a dark sky, mid-slide. */
    @Test
    fun night() {
        bar(night = true)
        val frames = mutableListOf(capture())
        compose.onNodeWithTag("bar").performTouchInput {
            down(tab(0, width.toFloat(), height.toFloat()))
            moveTo(Offset(width * 0.45f, height / 2f))
        }
        compose.mainClock.advanceTimeBy(200)
        frames += capture()
        compose.onNodeWithTag("bar").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_200)
        frames += capture()
        sheet(frames, "tabbar-night.png")
    }

    private fun capture(): Bitmap = compose.onNodeWithTag("screen").captureToImage().asAndroidBitmap().let { full ->
        // The bottom of the screen, where the bar is.
        val top = (full.height - 150 * 3).coerceAtLeast(0)
        Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
    }

    private fun sheet(frames: List<Bitmap>, name: String) {
        val sheet = Bitmap.createBitmap(frames[0].width, frames.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        var y = 0f
        frames.forEach { canvas.drawBitmap(it, 0f, y, null); y += it.height }
        File(File("build/glass-lab").apply { mkdirs() }, name).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun colors(light: Boolean): RosaColors {
        val ink = if (light) Color(0xFF14233B) else Color.White
        return RosaColors(
            ink = ink,
            inkSoft = ink.copy(alpha = 0.72f),
            inkFaint = ink.copy(alpha = 0.45f),
            accent = if (light) Color(0xFF2F7BE0) else Color(0xFF9DB4FF),
            warm = Color(0xFFFFB26B),
            cool = Color(0xFF7FB6FF),
            rain = if (light) Color(0xFF2F7BE0) else Color(0xFF8CCBFF),
            glassTint = if (light) Color.White else Color(0xFF0B1020),
            fill = ink.copy(alpha = if (light) 0.07f else 0.1f),
            isLightSky = light,
            zenith = Color(0xFF3E8EF7),
            horizon = Color(0xFF9CD2FF),
        )
    }
}
