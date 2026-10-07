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
import app.rosa.weather.ui.common.RosaTab
import app.rosa.weather.ui.common.SkyController
import app.rosa.weather.ui.common.TabBarScaffold
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

    private fun capture(name: String, doc: Boolean): Bitmap {
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (doc) exportDocImage(bitmap, name, 540)
        return bitmap
    }

    /**
     * The tab bar reads as a layer of its own, not as part of the card under it: its glass stands
     * apart in tone from the band just above it, where whatever scrolls toward the bar has faded
     * into the sky. (It ran together with the dark glass of the cards at night in 2.6.0: 1.29:1.)
     */
    private fun assertBarStandsApart(bitmap: Bitmap, name: String) {
        val d = 3
        // The bar: 64 dp tall, 10 dp above the bottom (no system bar here).
        val top = bitmap.height - (10 + 64) * d
        fun lum(c: Int): Double {
            fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(android.graphics.Color.red(c)) + 0.7152 * ch(android.graphics.Color.green(c)) + 0.0722 * ch(android.graphics.Color.blue(c))
        }
        val xs = 40 * d until bitmap.width - 40 * d step 3
        val bar = xs.map { lum(bitmap.getPixel(it, top + 8 * d)) }.average()
        val above = (top - 30 * d until top - 6 * d step 3).flatMap { y -> xs.map { lum(bitmap.getPixel(it, y)) } }.average()
        val ratio = (maxOf(bar, above) + 0.05) / (minOf(bar, above) + 0.05)
        println("$name: tab bar %.3f, above it %.3f, %.2f:1".format(java.util.Locale.ROOT, bar, above, ratio))
        com.google.common.truth.Truth.assertWithMessage("$name: the tab bar against what lies just above it").that(ratio).isAtLeast(1.5)
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
        swipes: Int = 1,
    ): Bitmap {
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
                RosaEnvironment(state.settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Weather, {}) {
                            HomeScreen(state, {}, {}, {}, {}, {}, fixedNow = now)
                        }
                    }
                }
            }
        }
        if (scrollPx > 0f) {
            compose.mainClock.advanceTimeBy(1_500)
            // A slow drag, so the list stops where the finger does.
            repeat(swipes) {
                compose.onRoot().performTouchInput {
                    val from = Offset(centerX, centerY + 500f)
                    swipe(from, from - Offset(0f, scrollPx), durationMillis = 1_200)
                }
                compose.mainClock.advanceTimeBy(600)
            }
        }
        return capture(name, doc)
    }

    /**
     * A city's first moments on screen, frame by frame: the cards rise one after another, the
     * hourly line draws itself with each hour's dot popping in, the days' ranges grow down the
     * card. Six frames side by side to `build/screens/home-entrance.png`.
     */
    @Test
    fun homeEntrance() {
        val now = 1_758_621_600L
        val forecast = SampleForecast.create(SampleForecast.Scenario.SunnyMild, nowEpochSeconds = now, placeId = "geo:1")
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
                RosaEnvironment(state.settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Weather, {}) {
                            HomeScreen(state, {}, {}, {}, {}, {}, fixedNow = now)
                        }
                    }
                }
            }
        }
        val times = listOf(120L, 260L, 420L, 640L, 900L, 1_500L)
        var elapsed = 0L
        val frames = times.map { t ->
            compose.mainClock.advanceTimeBy(t - elapsed)
            elapsed = t
            compose.waitForIdle()
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
        val w = frames[0].width / 3
        val h = frames[0].height / 3
        val strip = Bitmap.createBitmap(w * frames.size + 8 * (frames.size - 1), h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(strip).apply {
            drawColor(android.graphics.Color.rgb(20, 20, 24))
            frames.forEachIndexed { i, frame ->
                drawBitmap(Bitmap.createScaledBitmap(frame, w, h, true), (i * (w + 8)).toFloat(), 0f, null)
            }
        }
        val out = File("build/screens").apply { mkdirs() }
        File(out, "home-entrance.png").outputStream().use { strip.compress(Bitmap.CompressFormat.PNG, 100, it) }
        exportDocImage(strip, "home-entrance", 1600)
    }

    /**
     * The glass alive on the real screen, frame by frame (10 a second, at 2×), for the README's
     * animations: rain landing on the cards and running down them; a finger on the 10-day card —
     * its light running round the rim, the lens springing back like a gel when it lets go.
     */
    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xhdpi")
    fun homeAliveRain() = aliveFrames("alive-rain", SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, age = 2_400, frames = 30)

    /** At night, where the light of a touch shows best: near the hourly card's edge, off its hours. */
    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xhdpi")
    fun homeAliveTouch() = aliveFrames("alive-touch", SampleForecast.Scenario.ClearNight, 1_758_664_800L, frames = 30, touchDp = Offset(382f, 372f))

    /** The screen arriving: cards rise into place and a wave of light washes down their glass. */
    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xhdpi")
    fun homeArrive() = aliveFrames("arrive", SampleForecast.Scenario.SunnyMild, 1_758_621_600L, frames = 28, settle = 0, stepMs = 80)

    /**
     * A finger drawing the hourly ribbon along: the lens stretches along the hours' way and the
     * hour under it comes alive; let go, the ribbon snaps to an hour and the lens springs back.
     */
    @Test
    @Config(qualifiers = "ru-w411dp-h891dp-xhdpi")
    fun homeScrub() = aliveFrames("scrub", SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, age = 2_400, frames = 22, stepMs = 50, scrub = true)

    private fun aliveFrames(
        name: String,
        scenario: SampleForecast.Scenario,
        now: Long,
        age: Long = 0,
        frames: Int,
        touchDp: Offset? = null,
        settle: Long = 2_000,
        stepMs: Long = 100,
        scrub: Boolean = false,
    ) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now - age, placeId = "geo:1")
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
                RosaEnvironment(state.settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Weather, {}) {
                            HomeScreen(state, {}, {}, {}, {}, {}, fixedNow = now)
                        }
                    }
                }
            }
        }
        // The cards have risen into place (unless their arrival is what is filmed).
        if (settle > 0) compose.mainClock.advanceTimeBy(settle)
        val density = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.density
        val dir = File("build/screens/$name").apply { deleteRecursively(); mkdirs() }
        // The hourly ribbon, under the finger: drawn along for the first frames, then let go.
        val ribbon = Offset(300f, 450f) * density
        repeat(frames) { i ->
            if (touchDp != null && i == 3) compose.onRoot().performTouchInput { down(touchDp * density) }
            if (touchDp != null && i == 16) compose.onRoot().performTouchInput { up() }
            if (scrub) {
                when (i) {
                    1 -> compose.onRoot().performTouchInput { down(ribbon) }
                    in 2..9 -> compose.onRoot().performTouchInput { moveBy(Offset(-26f * density, 0f)) }
                    10 -> compose.onRoot().performTouchInput { up() }
                }
            }
            compose.mainClock.advanceTimeBy(stepMs)
            compose.waitForIdle()
            val frame = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "%03d.png".format(i)).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
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
                RosaEnvironment(state.settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, state.settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Weather, {}) {
                            HomeScreen(state, {}, {}, {}, {}, {}, fixedNow = now)
                        }
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

    private fun settings(scenario: SampleForecast.Scenario, now: Long, name: String, appearance: Appearance = Appearance.Auto) {
        val forecast = SampleForecast.create(scenario, nowEpochSeconds = now, placeId = "geo:1")
        val settings = AppSettings(effects = EffectsQuality.Balanced, appearance = appearance)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(forecast.momentAt(now)).apply { applyAppearance(appearance) } }
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
                RosaEnvironment(settings, sky.palette, richGlass = true) {
                    SkyBackdrop(sky.params, settings.effects, stage = sky.stage, transitionMillis = 0) {
                        TabBarScaffold(RosaTab.Places, {}) {
                            PlacesScreen(state, {}, {}, {}, {}, { _, _ -> }, now = now)
                        }
                    }
                }
            }
        }
        capture("places", doc = false)
    }

    /** Scrolled: the cards fade into the sky under the floating bar (scroll edge effect). */
    @Test
    fun homeScrolled() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-scrolled", doc = false, scrollPx = 820f)
    }

    /** Further down: the details, each tile with its own instrument. */
    @Test
    fun homeDetails() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-details", doc = false, scrollPx = 1_500f, swipes = 2)
    }

    @Test
    fun homeScrolledNight() {
        home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "home-scrolled-night", doc = false, scrollPx = 820f)
    }

    /** 17:30 in Moscow, light rain on the pane. */
    @Test
    fun homeRainy() = assertBarStandsApart(home(SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, "home-rainy", forecastAgeSeconds = 2_400), "home-rainy")

    @Test
    fun homeNight() = assertBarStandsApart(home(SampleForecast.Scenario.ClearNight, 1_758_664_800L, "home-night"), "home-night")

    @Test
    fun homeSnow() {
        home(SampleForecast.Scenario.SnowyCold, 1_758_610_800L, "home-snow")
    }

    @Test
    fun homeSunny() = assertBarStandsApart(home(SampleForecast.Scenario.SunnyMild, 1_758_621_600L, "home-sunny"), "home-sunny")

    /** 09:13 in Moscow, mostly clear: the low eastern sun used to sit right behind the numerals. */
    @Test
    fun homeMorning() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-morning", doc = false)
    }

    /** Same morning at −12°: the widest numerals must still keep the sun clear of them. */
    @Test
    fun homeMorningFrost() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-morning-frost", shiftCelsius = -30.0, doc = false)
    }

    @Test
    fun homeLight() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-mode-light", doc = false, appearance = Appearance.Light)
    }

    @Test
    fun homeEveningMode() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-mode-evening", doc = false, appearance = Appearance.Evening)
    }

    /** The cozy mood: the blue hour outside a lamp-lit window — here on a clear morning. */
    @Test
    fun homeCozyMode() = assertBarStandsApart(
        home(SampleForecast.Scenario.SunnyMild, 1_758_607_980L, "home-mode-cozy", appearance = Appearance.Cozy),
        "home-mode-cozy",
    )

    /** Cozy in the rain: drops on the misted pane, the town's lights blurred behind them. */
    @Test
    fun homeCozyRain() = assertBarStandsApart(
        home(SampleForecast.Scenario.RainyAfternoon, 1_758_637_800L, "home-cozy-rain", forecastAgeSeconds = 2_400, appearance = Appearance.Cozy),
        "home-cozy-rain",
    )

    @Test
    fun homeCozySnow() {
        home(SampleForecast.Scenario.SnowyCold, 1_758_610_800L, "home-cozy-snow", appearance = Appearance.Cozy)
    }

    @Test
    fun settingsCozy() = settings(SampleForecast.Scenario.SnowyCold, 1_758_610_800L, "settings-cozy", Appearance.Cozy)

    @Test
    fun homeDarkMode() {
        home(SampleForecast.Scenario.RainyAfternoon, 1_758_628_800L, "home-mode-dark", doc = false, appearance = Appearance.Dark)
    }

    @Test
    fun appearancePicker() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val sky = remember { SkyController(SampleForecast.create(SampleForecast.Scenario.SunnyMild).momentAt(1_758_628_800L)) }
            RosaEnvironment(AppSettings(), sky.palette, richGlass = true) {
                SkyBackdrop(sky.params, EffectsQuality.Balanced, transitionMillis = 0) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, contentPadding = PaddingValues(18.dp)) {
                            AppearancePicker(Appearance.Cozy) {}
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
    fun homeEvening() {
        home(SampleForecast.Scenario.SunnyMild, 1_758_638_400L, "home-evening", doc = false)
    }
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
