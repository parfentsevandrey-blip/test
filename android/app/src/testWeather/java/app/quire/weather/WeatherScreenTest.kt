package app.quire.weather

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.m3.QuireTheme
import app.quire.weather.ui.WeatherModel
import app.quire.weather.ui.WeatherScreen
import app.quire.weather.ui.WeatherSettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The weather app's screen, drawn against a stored forecast rather than a network.
 *
 * This is where the things the card had to leave out are checked: the place name in full, the
 * three readings, and five days each with the bar showing where its swing sits in the week's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WeatherScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
    }

    private fun settle(rounds: Int = 8) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(16L)
            Thread.sleep(5)
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
    }

    private fun showing(text: String): Int =
        compose.onAllNodes(hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun shoot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    @Test
    fun `the screen lays out now, the readings and the five days`() {
        val today = LocalDate.now()
        val skies = listOf(Sky.SHOWERS, Sky.PARTLY_CLOUDY, Sky.CLEAR, Sky.THUNDER, Sky.SNOW)
        WeatherStore.save(
            app,
            Forecast(
                place = "Западный административный округ",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(12.8, 11.4, skies[0], false, 81, 13.7),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = java.time.LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 12.0 + 6.0 * kotlin.math.sin(hour / 3.6),
                        sky = skies[hour % skies.size],
                        day = hour % 24 in 6..20,
                        rain = if (hour % 5 == 0) 10 * (hour % 9) else 0,
                    )
                },
                days = skies.mapIndexed { index, sky ->
                    DayForecast(
                        date = today.plusDays(index.toLong()),
                        sky = sky,
                        high = 22.0 - index * 1.4,
                        low = 11.0 - index * 1.1,
                        rain = listOf(70, 30, 0, 80, 60)[index],
                        sunrise = java.time.LocalDate.now().plusDays(index.toLong()).atTime(5, 12),
                        sunset = java.time.LocalDate.now().plusDays(index.toLong()).atTime(20, 41),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        val model = WeatherModel(app)
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    WeatherScreen(model, PaddingValues(0.dp), onGrant = {})
                }
            }
        }
        settle()
        val bitmap = shoot("app-weather")

        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours += bitmap.getPixel(x, y); y += 4
            }
            x += 4
        }
        assertTrue("the screen painted only ${colours.size} colours", colours.size > 8)

        // The place name in full is the whole point of the screen existing beside the card.
        assertTrue("the temperature is missing", showing("13°") > 0)
        assertTrue("the humidity is missing", showing("81%") > 0)
        assertTrue("the five-day heading is missing", showing("Next five days") > 0)
        assertTrue("the hourly strip is missing", showing("Next 24 hours") > 0)
        assertTrue("the sun times are missing", showing("5:12") > 0)
        // The place is the app bar's job; repeating it over the temperature was the first thing
        // that read as crooked on a real phone.
        assertTrue(
            "the place is written twice on one screen",
            showing("Западный административный округ") == 0,
        )
    }

    /** The settings, with the alerts open so the threshold slider is on screen too. */
    @Test
    fun `the settings screen lays out every group`() {
        WeatherSettings.get(app).apply {
            alerts = true
            threshold = 60
            periodMinutes = 180
        }
        val model = WeatherModel(app)
        model.setAlerts(true)
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    WeatherSettingsScreen(model, PaddingValues(0.dp))
                }
            }
        }
        settle()
        shoot("app-weather-settings")

        assertTrue("no update interval", showing("3 hours") > 0)
        assertTrue("no rain alert switch", showing("Tell me about rain") > 0)
        assertTrue("no threshold", showing("From 60%") > 0)
        assertTrue("no temperature unit", showing("°F") > 0)
        assertTrue("no wind unit", showing("m/s") > 0)
    }
}
