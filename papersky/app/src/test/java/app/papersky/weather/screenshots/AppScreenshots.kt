package app.papersky.weather.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import app.papersky.weather.Fixtures
import app.papersky.weather.TestApp
import app.papersky.weather.container
import app.papersky.weather.core.data.ForecastStore
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.SampleForecast
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.PaperskyChrome
import app.papersky.weather.ui.PaperskyRoot
import app.papersky.weather.ui.places.PlacesScreen
import app.papersky.weather.ui.places.PlacesViewModel
import app.papersky.weather.ui.settings.SettingsScreen
import app.papersky.weather.ui.settings.SettingsViewModel
import app.papersky.weather.ui.widgets.WidgetStudioScreen
import app.papersky.weather.ui.widgets.WidgetStudioViewModel
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.config.Grid
import app.papersky.weather.widget.config.WidgetEditor
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Espresso (used by the Compose test idling) doesn't run in Robolectric's API 37 sandbox yet.
@Config(sdk = [36], application = TestApp::class, qualifiers = "w411dp-h914dp-xxhdpi")
class AppScreenshots {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun seed(forecast: Forecast, settings: (UserSettings) -> UserSettings = { it }) = runBlocking {
        val c = app.container
        c.settings.update { settings(it.copy(motion = MotionLevel.Still, onboarded = true)) }
        c.places.updateDevice(Fixtures.moscow)
        c.places.add(Place(id = "om-1", name = "Санкт-Петербург", country = "Россия", latitude = 59.94, longitude = 30.31))
        c.weather.ensureLoaded()
        val store = c.weather.javaClass.getDeclaredField("store").apply { isAccessible = true }.get(c.weather) as ForecastStore
        store.put(forecast)
        store.put(forecast.copy(placeId = "om-1"))
    }

    private fun shoot(name: String, content: @Composable () -> Unit, scrollTo: Int? = null, previews: Int = 1) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.mainClock.advanceTimeBy(2_500)
        // Widget previews compose on a background dispatcher in real time.
        repeat(previews) {
            Thread.sleep(2_500)
            compose.mainClock.advanceTimeBy(500)
        }
        if (scrollTo != null) {
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(scrollTo)
            compose.mainClock.advanceTimeBy(1_500)
        }
        compose.onRoot().captureRoboImage(File(Shots.dir, "app_$name.png").path)
    }

    private fun scene(): SceneState {
        val f = app.container.weather.peek(Place.HERE)!!
        return SceneState.from(f.momentAt(System.currentTimeMillis() / 1000)!!, SceneState.seedFor(f.placeId), f)
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun homeRain() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        shoot("home_rain", { PaperskyRoot(app.container, MutableStateFlow(null)) {} })
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun homeRainScrolled() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        shoot("home_rain_scrolled", { PaperskyRoot(app.container, MutableStateFlow(null)) {} }, scrollTo = 2)
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun homeRainDetails() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        shoot("home_rain_details", { PaperskyRoot(app.container, MutableStateFlow(null)) {} }, scrollTo = 4)
    }

    @Test
    fun homeSunny() {
        Shots.assumeEnabled()
        seed(SampleForecast.build(System.currentTimeMillis()).copy(placeId = Place.HERE))
        shoot("home_sunny", { PaperskyRoot(app.container, MutableStateFlow(null)) {} })
    }

    /** The same forecast with the sun set three hours ago: the desk lamp is on. */
    private fun atNight(f: Forecast, nowSec: Long): Forecast {
        val today = f.daily.lastOrNull { it.date <= nowSec } ?: f.daily.first()
        val delta = (nowSec - 3 * 3600) - today.sunset
        return f.copy(
            current = f.current.copy(isDay = false),
            hourly = f.hourly.map { it.copy(isDay = false) },
            daily = f.daily.map { it.copy(sunrise = it.sunrise + delta, sunset = it.sunset + delta) },
        )
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun homeNight() {
        Shots.assumeEnabled()
        val now = System.currentTimeMillis()
        seed(atNight(SampleForecast.build(now).copy(placeId = Place.HERE), now / 1000))
        shoot("home_night", { PaperskyRoot(app.container, MutableStateFlow(null)) {} })
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun homeNightScrolled() {
        Shots.assumeEnabled()
        val now = System.currentTimeMillis()
        seed(atNight(SampleForecast.build(now).copy(placeId = Place.HERE), now / 1000))
        shoot("home_night_scrolled", { PaperskyRoot(app.container, MutableStateFlow(null)) {} }, scrollTo = 3)
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun widgets() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        val s = scene()
        val vm = WidgetStudioViewModel(app.container, app)
        shoot("widgets", {
            PaperskyChrome(UserSettings(motion = MotionLevel.Still), s) {
                WidgetStudioScreen(vm, s, MotionLevel.Still, village = true, onEdit = {}) {}
            }
        }, previews = 3)
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun places() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        val s = scene()
        val vm = PlacesViewModel(app.container)
        val units = runBlocking { app.container.settings.current() }.resolvedUnits()
        shoot("places", {
            PaperskyChrome(UserSettings(motion = MotionLevel.Still), s) {
                PlacesScreen(vm, s, units, MotionLevel.Still, village = true) {}
            }
        })
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun settings() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        val s = scene()
        val vm = SettingsViewModel(app.container)
        shoot("settings", { PaperskyChrome(UserSettings(motion = MotionLevel.Still), s) { SettingsScreen(vm, s) {} } })
    }

    @Test
    @Config(qualifiers = "+ru-rRU")
    fun editor() {
        Shots.assumeEnabled()
        seed(Fixtures.moscow(System.currentTimeMillis()))
        val s = scene()
        shoot("editor", {
            PaperskyChrome(UserSettings(motion = MotionLevel.Still), s) {
                WidgetEditor(WidgetConfig(), listOf(Fixtures.moscow), Grid.size(4, 2), "Мастерская виджета", "На экран", {}, {})
            }
        })
    }
}
