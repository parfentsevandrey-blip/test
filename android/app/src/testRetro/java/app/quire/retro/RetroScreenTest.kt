package app.quire.retro

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.quire.weather.Conditions
import app.quire.weather.DayForecast
import app.quire.weather.Forecast
import app.quire.weather.Place
import app.quire.weather.Sky
import app.quire.weather.WeatherStore
import app.quire.weather.ui.WeatherModel
import org.junit.Assert.assertEquals
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
 * The 1995 window, drawn against a stored forecast.
 *
 * The claims are the era's: a teal desktop behind a grey window, a navy title bar, and the
 * forecast legible inside it. What is *not* here is any assertion about Material — because
 * there is no Material in this build to assert about, which is the point of it being its own
 * source set rather than a theme switch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RetroScreenTest {

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

    private fun shoot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    @Test
    fun `the desktop is teal, the window is grey, and the forecast is in it`() {
        val today = LocalDate.now()
        WeatherStore.pin(app, Place("Redmond", null, "United States", 47.67, -122.12))
        WeatherStore.save(
            app,
            Forecast(
                place = "Redmond",
                latitude = 47.67,
                longitude = -122.12,
                now = Conditions(12.4, 10.6, Sky.SHOWERS, true, 82, 14.0, 27.0, 210, 1004.0, 3.0),
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = today.plusDays(index.toLong()),
                        sky = Sky.SHOWERS,
                        high = 22.0 - index,
                        low = 11.0 - index,
                        rain = 40,
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        compose.setContent { Desktop95(WeatherModel(app)) }
        settle()
        val shot = shoot("retro-app")

        // The desktop shows in the margin the window leaves round itself.
        val desktop = shot.getPixel(2, shot.height / 2)
        assertEquals(
            "the desktop is not teal but " + Integer.toHexString(desktop),
            0xFF008080.toInt(),
            desktop,
        )

        // The title bar, a little way in from the window's own edge: navy.
        val density = compose.density.density
        val title = shot.getPixel((30 * density).toInt(), (14 * density).toInt())
        assertTrue(
            "the title bar is not navy but " + Integer.toHexString(title),
            android.graphics.Color.blue(title) > 110 && android.graphics.Color.red(title) < 90,
        )

        listOf("Weather - Redmond", "Showers", "12°", "Humidity", "Ready").forEach { text ->
            assertTrue(
                "the window is missing \"$text\"",
                compose.onAllNodes(hasText(text, substring = true))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
            )
        }
    }

    /** Before the first fetch the window says so the way a 1995 dialog would, with an OK. */
    @Test
    fun `an unfetched window asks rather than pretending`() {
        WeatherStore.clear(app)
        compose.setContent { Desktop95(WeatherModel(app)) }
        settle()
        shoot("retro-app-empty")

        val asked = compose.onAllNodes(hasText("weather", substring = true, ignoreCase = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("an unfetched window said nothing at all", asked.isNotEmpty())
        assertTrue(
            "an unfetched window claimed a temperature",
            compose.onAllNodes(hasText("0°", substring = true))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
        )
    }
}
