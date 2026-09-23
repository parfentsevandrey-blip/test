package app.rosa.weather.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.LocalSky
import app.rosa.weather.ui.common.SkyController
import app.rosa.weather.ui.home.HomeScreen
import app.rosa.weather.ui.home.HomeUiState
import app.rosa.weather.ui.home.PlacePage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders key screens to `build/screens/` for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenGalleryTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun home(scenario: SampleForecast.Scenario, now: Long, name: String) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now, placeId = "geo:1")
        val state = HomeUiState(
            loaded = true,
            pages = listOf(PlacePage(Place("geo:1", "Москва", 55.75, 37.62), forecast)),
            selectedId = "geo:1",
            units = Units(),
            settings = AppSettings(effects = EffectsQuality.Balanced),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)) }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(state.settings, sky.palette) {
                    SkyBackdrop(sky.params, state.settings.effects, transitionMillis = 0) {
                        HomeScreen(state, {}, {}, {}, {}, {}, {}, {}, fixedNow = now)
                    }
                }
            }
        }
        capture(name)
    }

    @Test
    fun homeRainy() = home(SampleForecast.Scenario.RainyAfternoon, 1_758_628_800L, "home-rainy")

    @Test
    fun homeNight() = home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "home-night")

    @Test
    fun homeSnow() = home(SampleForecast.Scenario.SnowyCold, 1_758_610_800L, "home-snow")

    @Test
    fun homeSunny() = home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-sunny")
}
