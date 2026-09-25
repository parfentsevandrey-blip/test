package app.rosa.weather.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Appearance
import app.rosa.weather.ui.settings.AppearancePicker
import app.rosa.weather.ui.settings.SettingsScreen
import app.rosa.weather.ui.places.PlacesScreen
import app.rosa.weather.ui.places.PlacesUiState
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
    private fun home(
        scenario: SampleForecast.Scenario,
        now: Long,
        name: String,
        shiftCelsius: Double = 0.0,
        doc: Boolean = true,
        appearance: Appearance = Appearance.Auto,
        forecastAgeSeconds: Long = 0,
        scrollPx: Float = 0f,
    ) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now - forecastAgeSeconds, placeId = "geo:1").shifted(shiftCelsius)
        val state = HomeUiState(
            loaded = true,
            pages = listOf(PlacePage(Place("geo:1", "Москва", 55.75, 37.62), forecast)),
            selectedId = "geo:1",
            units = Units(),
            settings = AppSettings(effects = EffectsQuality.Balanced, appearance = appearance),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)).apply { applyAppearance(appearance) } }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(state.settings, sky.palette) {
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        HomeScreen(state, {}, {}, {}, {}, {}, {}, {}, fixedNow = now)
                    }
                }
            }
        }
        if (scrollPx > 0f) {
            compose.mainClock.advanceTimeBy(1_500)
            // A slow drag, so the list stops where the finger does.
            compose.onRoot().performTouchInput {
                val from = Offset(centerX, centerY + 500f)
                swipe(from, from - Offset(0f, scrollPx), durationMillis = 1_200)
            }
        }
        capture(name, doc)
    }

    /** First launch: the sheet, and its buttons set into it (never glass on glass). */
    @Test
    fun onboarding() {
        val now = 1_758_621_600L
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = "geo:1")
        val state = HomeUiState(loaded = true, pages = emptyList(), settings = AppSettings(effects = EffectsQuality.Balanced))
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
        capture("onboarding", doc = false)
    }

    /** Settings over the day sky and at night: frosted sections, controls set into them. */
    @Test
    fun settingsDay() = settings(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "settings-day")

    @Test
    fun settingsNight() = settings(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "settings-night")

    private fun settings(scenario: SampleForecast.Scenario, now: Long, name: String) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now, placeId = "geo:1")
        val settings = AppSettings(effects = EffectsQuality.Balanced)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)) }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        SettingsScreen(settings, {}, {}, {}, {})
                    }
                }
            }
        }
        capture(name, doc = false)
    }

    /**
     * Places, each card tinted with its own city's sky: the tint keeps to the card's rounded
     * corners (it once drew a full rectangle whose square corners poked out past the glass).
     */
    @Test
    fun places() {
        val now = 1_758_621_600L
        fun place(id: String, name: String, region: String, scenario: SampleForecast.Scenario, lat: Double, lon: Double, offset: Int) =
            Place(id, name, lat, lon, region = region) to SampleForecast.create(scenario, nowEpochSeconds = now, latitude = lat, longitude = lon, utcOffsetSeconds = offset * 3600, placeId = id)
        val entries = listOf(
            place("msk", "Москва", "Москва", SampleForecast.Scenario.RainyAfternoon, 55.76, 37.62, 3),
            place("rix", "Рига", "Рига", SampleForecast.Scenario.RainyAfternoon, 56.95, 24.11, 3),
            place("ams", "Амстердам", "Северная Голландия", SampleForecast.Scenario.SunnyMild, 52.37, 4.9, 2),
            place("nyc", "Нью-Йорк", "Нью-Йорк", SampleForecast.Scenario.ClearNight, 40.71, -74.0, -4),
            place("jnb", "Йоханнесбург", "Гаутенг", SampleForecast.Scenario.SunnyMild, -26.2, 28.05, 2),
        )
        val state = PlacesUiState(
            followDevice = true,
            places = entries.map { it.first },
            forecasts = entries.associate { (p, f) -> p.id to f },
        )
        val settings = AppSettings(effects = EffectsQuality.Balanced)
        val forecast = entries.first().second
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)) }
            CompositionLocalProvider(LocalSky provides sky) {
                RosaEnvironment(settings, sky.palette) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        PlacesScreen(state, {}, {}, {}, {}, {}, { _, _ -> }, now = now)
                    }
                }
            }
        }
        capture("places", doc = false)
    }

    /** Scrolled: the cards fade into the sky under the floating bar (scroll edge effect). */
    @Test
    fun homeScrolled() = home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-scrolled", doc = false, scrollPx = 820f)

    @Test
    fun homeScrolledNight() = home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "home-scrolled-night", doc = false, scrollPx = 820f)

    /** 17:30 in Moscow, light rain on the pane. */
    @Test
    fun homeRainy() = home(SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, "home-rainy", forecastAgeSeconds = 2_400)

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

    @Test
    fun homeLight() = home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-mode-light", doc = false, appearance = Appearance.Light)

    @Test
    fun homeEveningMode() = home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-mode-evening", doc = false, appearance = Appearance.Evening)

    @Test
    fun homeDarkMode() = home(SampleForecast.Scenario.RainyAfternoon, 1_758_628_800L, "home-mode-dark", doc = false, appearance = Appearance.Dark)

    @Test
    fun appearancePicker() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(SampleForecast.create(SampleForecast.Scenario.SunnyMild).momentAt(1_758_628_800L)) }
            RosaEnvironment(AppSettings(), sky.palette) {
                SkyBackdrop(sky.params, EffectsQuality.Balanced, transitionMillis = 0) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, contentPadding = PaddingValues(18.dp)) {
                            AppearancePicker(Appearance.Evening) {}
                        }
                    }
                }
            }
        }
        capture("settings-appearance", doc = false)
    }

    /** Every UI icon at 16, 22 and 28 dp, in light ink on night and dark ink on day. */
    @Test
    fun iconSheet() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column {
                listOf(Color(0xFF141A3A) to Color(0xFFFFFBF5), Color(0xFFDDE7F7) to Color(0xFF1B2030)).forEach { (background, ink) ->
                    Column(Modifier.fillMaxWidth().background(background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(16.dp, 22.dp, 28.dp).forEach { size ->
                            RosaIcon.entries.chunked(7).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    row.forEach { RosaIconView(it, ink, size = size) }
                                }
                            }
                        }
                    }
                }
            }
        }
        capture("icons", doc = false)
    }

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
