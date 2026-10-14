package app.rosa.weather.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.LocalSky
import app.rosa.weather.ui.common.RosaTab
import app.rosa.weather.ui.common.SkyController
import app.rosa.weather.ui.common.TabBarScaffold
import app.rosa.weather.ui.home.HomeScreen
import app.rosa.weather.ui.home.HomeUiState
import app.rosa.weather.ui.home.PlacePage
import app.rosa.weather.ui.places.PlacesScreen
import app.rosa.weather.ui.places.PlacesUiState
import app.rosa.weather.ui.settings.SettingsScreen
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The app's screens in sea glass (Settings → Appearance → Sea glass), for review and the README. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class SeaGlassScreensTest {
    @get:Rule val compose = createComposeRule()

    private val settings = AppSettings(effects = EffectsQuality.Balanced, appearance = Appearance.SeaGlass)

    private fun capture(name: String, doc: Boolean = true): Bitmap {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screens").apply { mkdirs() }.let { File(it, "$name.png").outputStream().use { o -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, o) } }
        if (doc) exportDocImage(bitmap, name, 540)
        return bitmap
    }

    private fun home(scenario: SampleForecast.Scenario, now: Long, name: String, scrollPx: Float = 0f, swipes: Int = 1, age: Long = 0) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now - age, placeId = "geo:1")
        val state = HomeUiState(
            loaded = true,
            pages = listOf(PlacePage(Place("geo:1", "Москва", 55.75, 37.62), forecast)),
            selectedId = "geo:1",
            units = Units(),
            settings = settings,
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)).apply { applyAppearance(Appearance.SeaGlass) } }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Weather, {}) {
                            HomeScreen(state, {}, {}, {}, {}, {}, fixedNow = now)
                        }
                    }
                }
            }
        }
        if (scrollPx > 0f) {
            compose.mainClock.advanceTimeBy(1_500)
            repeat(swipes) {
                compose.onRoot().performTouchInput {
                    val from = Offset(centerX, centerY + 500f)
                    swipe(from, from - Offset(0f, scrollPx), durationMillis = 1_200)
                }
                compose.mainClock.advanceTimeBy(600)
            }
        }
        capture(name)
    }

    @Test
    fun homeSunny() = home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "sea-home-sunny")

    @Test
    fun homeRainy() = home(SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, "sea-home-rainy", age = 2_400)

    @Test
    fun homeNight() = home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "sea-home-night")

    @Test
    fun homeDetails() = home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "sea-home-details", scrollPx = 1_500f, swipes = 2)

    @Test
    fun settings() {
        val now = 1_758_621_600L
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = "geo:1")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)).apply { applyAppearance(Appearance.SeaGlass) } }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Settings, {}) {
                            SettingsScreen(settings, {}, {}, {})
                        }
                    }
                }
            }
        }
        capture("sea-settings")
    }

    @Test
    fun places() {
        val now = 1_758_621_600L
        fun place(id: String, name: String, region: String, scenario: SampleForecast.Scenario, lat: Double, lon: Double, offset: Int) =
            Place(id, name, lat, lon, region = region) to SampleForecast.create(scenario, nowEpochSeconds = now, latitude = lat, longitude = lon, utcOffsetSeconds = offset * 3600, placeId = id)
        val entries = listOf(
            place("msk", "Москва", "Москва", SampleForecast.Scenario.RainyAfternoon, 55.76, 37.62, 3),
            place("ams", "Амстердам", "Северная Голландия", SampleForecast.Scenario.SunnyMild, 52.37, 4.9, 2),
            place("nyc", "Нью-Йорк", "Нью-Йорк", SampleForecast.Scenario.ClearNight, 40.71, -74.0, -4),
            place("jnb", "Йоханнесбург", "Гаутенг", SampleForecast.Scenario.SunnyMild, -26.2, 28.05, 2),
        )
        val state = PlacesUiState(followDevice = true, places = entries.map { it.first }, forecasts = entries.associate { (p, f) -> p.id to f })
        val forecast = entries.first().second
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)).apply { applyAppearance(Appearance.SeaGlass) } }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Places, {}) {
                            PlacesScreen(state, {}, {}, {}, {}, { _, _ -> }, now = now)
                        }
                    }
                }
            }
        }
        capture("sea-places")
    }
}
