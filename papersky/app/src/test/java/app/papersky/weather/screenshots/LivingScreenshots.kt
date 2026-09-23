package app.papersky.weather.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.papersky.weather.TestApp
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.Haptics
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.scene.PaletteMode
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.scene.LivingScene
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The living scenes as the app composes them, on a hardware canvas: the only place the GPU effects
 * (fire, fog, water) can be seen, since the one-pass renders draw their Canvas fallbacks.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = TestApp::class, qualifiers = "w1240dp-h640dp-xhdpi")
class LivingScreenshots {
    @get:Rule val compose = createComposeRule()

    private val fog = SceneState(daylight = 0.9f, sunProgress = 0.12f, cloudCover = 0.6f, fog = 0.85f, temperature = 4f, seed = 18)
    private val storm = SceneState(daylight = 0.05f, sunProgress = -0.2f, nightProgress = 0.3f, cloudCover = 1f, rain = 0.9f, thunder = 1f, windX = -7f, windSpeed = 9f, temperature = 16f, seed = 16)
    private val rain = SceneState(daylight = 1f, sunProgress = 0.5f, cloudCover = 0.95f, rain = 0.8f, windX = 4f, temperature = 13f, seed = 15)
    private val snow = SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.8f, snow = 0.9f, snowGround = 1f, windX = 1.5f, temperature = -4f, seed = 17)
    private val golden = SceneState(daylight = 1f, sunProgress = 0.9f, cloudCover = 0.3f, windX = 2f, temperature = 18f, seed = 12)
    private val night = SceneState(daylight = 0f, sunProgress = -0.4f, nightProgress = 0.45f, cloudCover = 0.15f, temperature = 9f, moonPhase = 0.5f, seed = 14)

    private fun row(name: String, mode: PaletteMode, states: List<SceneState>) {
        Shots.assumeEnabled()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val context = LocalContext.current
            CompositionLocalProvider(LocalHaptics provides remember { Haptics(context) }) {
                Row {
                    for (s in states) {
                        Box(Modifier.size(310.dp, 640.dp)) {
                            LivingScene(s, Modifier.size(310.dp, 640.dp), mode = mode, horizon = 0.42f, motion = MotionLevel.Still, glass = true, transitionMillis = 0)
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage(File(Shots.dir, "living_$name.png").path)
    }

    @Test
    fun sky() = row("sky", PaletteMode.Auto, listOf(fog, storm, rain, snow))

    @Test
    fun ophelia() = row("ophelia", PaletteMode.Ophelia, listOf(golden, rain, night, fog))

    @Test
    fun hearth() = row("hearth", PaletteMode.Hearth, listOf(golden, rain, night, snow))
}
