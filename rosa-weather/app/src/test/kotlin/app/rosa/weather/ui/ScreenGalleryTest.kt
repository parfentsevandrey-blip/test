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
import app.rosa.weather.core.model.Forecast
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

    private fun capture(name: String, doc: Boolean) {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (doc) exportDocImage(bitmap, name, 540)
    }

    /** @param doc also export the render as a README image (with `-Prosa.docs`). */
    private fun home(scenario: SampleForecast.Scenario, now: Long, name: String, shiftCelsius: Double = 0.0, doc: Boolean = true) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now, placeId = "geo:1").shifted(shiftCelsius)
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
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        HomeScreen(state, {}, {}, {}, {}, {}, {}, {}, fixedNow = now)
                    }
                }
            }
        }
        capture(name, doc)
    }

    @Test
    fun homeRainy() = home(SampleForecast.Scenario.RainyAfternoon, 1_758_628_800L, "home-rainy")

    @Test
    fun homeNight() = home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "home-night")

    @Test
    fun homeSnow() = home(SampleForecast.Scenario.SnowyCold, 1_758_610_800L, "home-snow")

    @Test
    fun homeSunny() = home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-sunny")

    /** 09:13 in Moscow, mostly clear: the low eastern sun used to sit right behind the numerals. */
    @Test
    fun homeMorning() = home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-morning", doc = false)

    /** Same morning at −12°: the widest numerals must still keep the sun clear of them. */
    @Test
    fun homeMorningFrost() = home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-morning-frost", shiftCelsius = -30.0, doc = false)

    /** 17:40, the sun low in the west. */
    @Test
    fun homeEvening() = home(SampleForecast.Scenario.SunnyMild, 1_758_638_400L, "home-evening", doc = false)
}

private fun Forecast.shifted(celsius: Double): Forecast = if (celsius == 0.0) this else copy(
    current = current.copy(temperature = current.temperature + celsius, apparentTemperature = current.apparentTemperature + celsius),
    hourly = hourly.map { it.copy(temperature = it.temperature + celsius, apparentTemperature = it.apparentTemperature + celsius) },
    daily = daily.map { it.copy(temperatureMax = it.temperatureMax + celsius, temperatureMin = it.temperatureMin + celsius) },
)

/** Writes a downscaled JPEG for the README when run with `-Prosa.docs`. */
internal fun exportDocImage(bitmap: android.graphics.Bitmap, name: String, width: Int) {
    val dir = System.getProperty("rosa.docs") ?: return
    val scale = width.toFloat() / bitmap.width
    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, width, (bitmap.height * scale).toInt(), true)
    val opaque = android.graphics.Bitmap.createBitmap(scaled.width, scaled.height, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(opaque).apply {
        drawColor(0xFF141A3A.toInt())
        drawBitmap(scaled, 0f, 0f, null)
    }
    java.io.File(dir).mkdirs()
    java.io.File(dir, "$name.jpg").outputStream().use { opaque.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
}
