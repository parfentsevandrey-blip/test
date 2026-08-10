package app.quire.weather

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.m3.QuireTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import app.quire.weather.ui.BLOCK
import app.quire.weather.ui.LiveSky
import app.quire.weather.ui.glassEdge
import app.quire.weather.ui.WeatherApp
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
        // Stated rather than assumed. The settings object outlives a single test method, so a test
        // that turns the glass off would otherwise decide what the next one renders.
        WeatherSettings.get(app).glassEdges = true
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
        // A named place, so the screen is the one somebody who has finished setting it up sees:
        // no permission card at the top pushing the five days off the bottom of the shot.
        WeatherStore.pin(
            app,
            Place("Западный административный округ", null, "Россия", 55.75, 37.62),
        )
        WeatherStore.save(
            app,
            Forecast(
                place = "Западный административный округ",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(12.8, 11.4, skies[0], false, 81, 13.7, 27.4, 210, 1004.0, 6.2),
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
        // The arc says roughly; this says exactly, and it fills the one part of that card the
        // drawing leaves empty.
        assertTrue("the daylight left is missing", showing("of daylight left") > 0)
        // A dash in a column of percentages reads as a stray minus sign. The column keeps its
        // width on a dry day and writes nothing in it.
        assertTrue("a dash is written for a dry day", showing("—") == 0)

        // Every block on the screen starts on the same left edge. This is the fault the screen was
        // rebuilt for: headings at one inset and the cards under them at another, all the way
        // down. The headings are checked where they are laid out, and the cards in pixels — a
        // card has no text of its own to ask, so its fill is found by walking in from the margin.
        val headings = listOf("Next 24 hours", "Sunrise and sunset", "Next five days")
            .map { it to compose.onNodeWithText(it).getUnclippedBoundsInRoot().left.value }
        assertTrue(
            "the headings start at different x: $headings",
            headings.maxOf { it.second } - headings.minOf { it.second } < 0.5f,
        )

        // And the four blocks under them. They say where they are rather than being measured out
        // of the picture: there is weather falling in front of the page now, and at some heights
        // the wash lifts the page to within a hair of a card's own colour.
        val blocks = compose.onAllNodesWithTag(BLOCK).fetchSemanticsNodes()
            .map { it.boundsInRoot.left }
        assertTrue("only ${blocks.size} blocks are tagged", blocks.size == 4)
        assertTrue(
            "the blocks start at different x: $blocks",
            blocks.max() - blocks.min() < 0.5f,
        )

    }

    /** How far down the window the weather is drawn, in points. */
    private val SKY_BAND = 320f

    /**
     * The whole app, bar and all, because the fault this catches only exists where the two meet.
     *
     * The sky behind the page used to start under the app bar, and the bar was opaque over it, so
     * the screen carried one hard horizontal line across its full width with a square corner at
     * each end — the only edge on a page made entirely of rounded cards. The bar is transparent
     * over the forecast now and the wash runs from the top of the window.
     *
     * Asserted as smoothness rather than as a colour: walking down the left margin, no two
     * neighbouring rows of a gradient differ by much. A step is exactly what a hard edge is.
     */
    @Test
    fun `the sky runs behind the app bar with no edge in it`() {
        WeatherStore.pin(app, Place("Moscow", null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = "Moscow",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(22.0, 22.0, Sky.MOSTLY_CLEAR, true, 48, 5.0, 11.0, 315, 1012.0, 5.0),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = java.time.LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 22.0 + 2.0 * kotlin.math.sin(hour / 3.6),
                        sky = Sky.MOSTLY_CLEAR,
                        day = true,
                        rain = 0,
                    )
                },
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = LocalDate.now().plusDays(index.toLong()),
                        sky = Sky.MOSTLY_CLEAR,
                        high = 24.0 - index,
                        low = 13.0 - index,
                        rain = 0,
                        sunrise = LocalDate.now().plusDays(index.toLong()).atTime(4, 51),
                        sunset = LocalDate.now().plusDays(index.toLong()).atTime(20, 17),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        compose.setContent { WeatherApp() }
        settle()
        val bitmap = shoot("app-weather-bar")

        // Down the margin, through the bar and out the other side of the wash.
        var worst = 0
        var worstAt = 0
        for (y in 1 until (bitmap.height * 0.45f).toInt()) {
            val step = distance(bitmap.getPixel(2, y), bitmap.getPixel(2, y - 1))
            if (step > worst) {
                worst = step
                worstAt = y
            }
        }
        assertTrue("the sky steps by $worst at row $worstAt — there is an edge in it", worst <= 8)
    }

    private fun distance(a: Int, b: Int): Int {
        var sum = 0
        for (shift in intArrayOf(16, 8, 0)) {
            sum += kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        }
        return sum
    }

    /**
     * The sky moves, and only the sky.
     *
     * An app that draws a raincloud and then sits perfectly still is a diagram of the weather.
     * Two frames three seconds apart have to differ where the weather is, and be identical
     * everywhere else — a page that shifts under a moving sky is a page that has been dragged
     * along with it.
     */
    @Test
    fun `the weather behind the page is moving`() {
        val today = LocalDate.now()
        WeatherStore.pin(app, Place("Moscow", null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = "Moscow",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(4.0, 2.0, Sky.SHOWERS, true, 88, 21.0, 33.0, 180, 998.0, 1.0),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = java.time.LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 4.0 + kotlin.math.sin(hour / 3.6),
                        sky = Sky.SHOWERS,
                        day = true,
                        rain = 80,
                    )
                },
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = today.plusDays(index.toLong()),
                        sky = Sky.SHOWERS,
                        high = 8.0 - index,
                        low = 2.0 - index,
                        rain = 80,
                        sunrise = today.plusDays(index.toLong()).atTime(5, 12),
                        sunset = today.plusDays(index.toLong()).atTime(20, 41),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        // The cards' own rims shimmer, which is the point of them and would also be movement below
        // the skyline. Turned off here so this test keeps asking exactly what it was written to
        // ask: whether the page itself is being dragged along by the sky.
        WeatherSettings.get(app).glassEdges = false

        val model = WeatherModel(app)
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    WeatherScreen(model, PaddingValues(0.dp), onGrant = {})
                }
            }
        }
        settle()
        val first = shoot("app-weather-rain")

        // Far enough for a drop to have crossed a third of the band, and past the end of the
        // one-off animations that play on arrival — those are settled by now, so anything that
        // moves between these two frames is the sky itself.
        compose.mainClock.advanceTimeBy(3_000L)
        val second = shoot("app-weather-rain-later")

        var movedAbove = 0
        var movedBelow = 0
        val skyline = (SKY_BAND * compose.density.density).toInt()
        for (y in 0 until first.height step 3) {
            for (x in 0 until first.width step 3) {
                if (first.getPixel(x, y) == second.getPixel(x, y)) continue
                if (y < skyline) movedAbove++ else movedBelow++
            }
        }
        assertTrue("nothing moved in the sky at all", movedAbove > 200)
        assertTrue("the page moved under the sky ($movedBelow pixels)", movedBelow == 0)
    }

    /**
     * The rim of a card, and only the rim.
     *
     * The glass is meant to live on the edge a card already has: light travelling round it and a
     * few sparks riding it. That is two claims, and both are worth holding on to. It has to move —
     * a still rim is a border — and it has to stay on the edge, because anything that wanders into
     * the middle of a card is drawing on top of the reading somebody opened the app for.
     */
    @Test
    fun `the light on a card rim moves, and stays on the rim`() {
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .glassEdge(CardDefaults.shape)
                                .testTag("glass"),
                        ) {}
                    }
                }
            }
        }
        settle()
        val first = shoot("weather-glass")
        // Far enough round the lap for the travelling band to have left where it was, and not so
        // far that a spark has come back to the same place.
        compose.mainClock.advanceTimeBy(1_800L)
        val second = shoot("weather-glass-later")

        val bounds = compose.onNodeWithTag("glass").getUnclippedBoundsInRoot()
        val scale = compose.density.density
        val left = (bounds.left.value * scale).toInt()
        val top = (bounds.top.value * scale).toInt()
        val right = (bounds.right.value * scale).toInt()
        val bottom = (bounds.bottom.value * scale).toInt()
        // Two bands: within [RIM] of the card's edge, and everything further in than that.
        val rim = (RIM * scale).toInt()

        var onTheRim = 0
        var inTheMiddle = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (first.getPixel(x, y) == second.getPixel(x, y)) continue
                val inset = minOf(x - left, right - x, y - top, bottom - y)
                if (inset in -rim..rim) onTheRim++ else inTheMiddle++
            }
        }
        assertTrue("nothing on the rim moved at all", onTheRim > 200)
        assertTrue("the glass moved $inTheMiddle pixels off its own edge", inTheMiddle == 0)
    }

    /** How far from a card's edge the glass is allowed to reach, in points. */
    private val RIM = 8f

    /**
     * Every sky, drawn on its own.
     *
     * The screen can only show one at a time, and the branch that has never been looked at is the
     * branch that draws nothing. Each one gets a tile on a sheet, and each tile has to carry
     * enough ink to be a picture rather than an empty rectangle.
     */
    @Test
    fun `every sky paints something of its own`() {
        val skies = listOf(
            Sky.CLEAR to true,
            Sky.CLEAR to false,
            Sky.OVERCAST to true,
            Sky.FOG to true,
            Sky.DRIZZLE to true,
            Sky.RAIN to true,
            Sky.SHOWERS to true,
            Sky.THUNDER to true,
            Sky.SLEET to true,
            Sky.SNOW to true,
        )
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        skies.forEach { (sky, day) ->
                            LiveSky(
                                sky = sky,
                                day = day,
                                windKmh = 28.0,
                                windFrom = 270,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .testTag("sky-${sky.name}-$day"),
                            )
                        }
                    }
                }
            }
        }
        settle()
        // A quarter of the way in: past the flash and the shooting star, which both happen later
        // in the lap and would otherwise never be drawn in a picture taken at the start of one.
        compose.mainClock.advanceTimeBy(5_600L)
        val sheet = shoot("weather-skies")

        skies.forEach { (sky, day) ->
            val bounds = compose.onNodeWithTag("sky-${sky.name}-$day").getUnclippedBoundsInRoot()
            val top = (bounds.top.value * compose.density.density).toInt() + 2
            val bottom = (bounds.bottom.value * compose.density.density).toInt() - 2
            var lit = 0
            for (y in top until bottom step 2) {
                for (x in 0 until sheet.width step 2) {
                    if (sheet.getPixel(x, y) != sheet.getPixel(2, top)) lit++
                }
            }
            assertTrue("${sky.name} (day=$day) drew nothing at all", lit > 300)
        }
    }

    /** The same screen in daylight, because the dark one is only half of what ships. */
    @Test
    fun `the screen lays out in the light theme too`() {
        val today = LocalDate.now()
        WeatherStore.pin(app, Place("Moscow", null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = "Moscow",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(21.4, 21.4, Sky.CLEAR, true, 44, 9.0, 18.0, 45, 1018.0, 7.4),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = java.time.LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 18.0 + 5.0 * kotlin.math.sin(hour / 3.6),
                        sky = Sky.CLEAR,
                        day = hour % 24 in 6..20,
                        rain = 0,
                    )
                },
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = today.plusDays(index.toLong()),
                        sky = Sky.CLEAR,
                        high = 24.0 - index,
                        low = 13.0 - index,
                        rain = 0,
                        sunrise = today.plusDays(index.toLong()).atTime(5, 12),
                        sunset = today.plusDays(index.toLong()).atTime(20, 41),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        val model = WeatherModel(app)
        compose.setContent {
            QuireTheme(dark = false, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    WeatherScreen(model, PaddingValues(0.dp), onGrant = {})
                }
            }
        }
        settle()
        shoot("app-weather-light")

        assertTrue("the temperature is missing", showing("21°") > 0)
        assertTrue("the humidity is missing", showing("44%") > 0)
        // Clear all week, so no day row writes a chance of rain and no hour column writes one
        // either — the two rain figures on this screen are the reading card's 0% and nothing else.
        assertTrue("a chance of rain appeared on a dry week", showing("0%") == 1)
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
