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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.m3.QuireTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import app.quire.weather.ui.BLOCK
import app.quire.weather.ui.Days
import app.quire.weather.ui.Puff
import app.quire.weather.ui.RainPulse
import app.quire.weather.ui.SunArc
import app.quire.weather.ui.LiveSky
import app.quire.weather.ui.puffField
import app.quire.weather.ui.rememberTilt
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

        // What the first screenful must hold. The hero grew and the readings became dials, so
        // the sun and the five days start below this viewport now — they are asserted in their
        // own tests, against their own compositions, where a lazy list cannot hide them.
        assertTrue("the temperature is missing", showing("13°") > 0)
        assertTrue("the humidity is missing", showing("81%") > 0)
        assertTrue("the hourly strip is missing", showing("Next 24 hours") > 0)
        // The place is the app bar's job; repeating it over the temperature was the first thing
        // that read as crooked on a real phone.
        assertTrue(
            "the place is written twice on one screen",
            showing("Западный административный округ") == 0,
        )

        // Every block on the screen starts on the same left edge. This is the fault the screen was
        // rebuilt for: headings at one inset and the cards under them at another, all the way
        // down. The headings are checked where they are laid out, and the cards in pixels — a
        // card has no text of its own to ask, so its fill is found by walking in from the margin.
        // Over the headings that made it into the viewport: the page is taller than a screen now
        // and a LazyColumn does not compose what it cannot show.
        val headings = listOf("Next 24 hours", "Sunrise and sunset", "Next five days")
            .flatMap { text ->
                compose.onAllNodes(hasText(text))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    // A lazy list composes a little past the viewport, and a node it has built
                    // but not placed reports empty bounds at the origin — that is bookkeeping,
                    // not a heading hanging on the left edge.
                    .filter { it.boundsInRoot.width > 0f }
                    .map { text to it.boundsInRoot.left }
            }
        assertTrue("no headings are on screen at all", headings.isNotEmpty())
        assertTrue(
            "the headings start at different x: $headings",
            headings.maxOf { it.second } - headings.minOf { it.second } < 0.5f,
        )

        // And the four blocks under them. They say where they are rather than being measured out
        // of the picture: there is weather falling in front of the page now, and at some heights
        // the wash lifts the page to within a hair of a card's own colour.
        val blocks = compose.onAllNodesWithTag(BLOCK).fetchSemanticsNodes()
            .map { it.boundsInRoot.left }
        // The hero grew tall enough that the five-day card starts below this viewport, and a
        // LazyColumn does not compose what it cannot show — so the check runs over every block
        // that is composed rather than naming a count that depends on the hero's height.
        assertTrue("only ${blocks.size} blocks are tagged", blocks.size >= 3)
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
     * A day opens to the rest of itself.
     *
     * The row gives the day's swing; the tap gives its sunrise, its sunset and the word for its
     * sky. Before the tap those times exist once on the screen — on the sun card — and after it
     * they exist twice, which is the whole assertion: the detail is real text a reader can find,
     * not a drawing.
     */
    @Test
    fun `a day opens to its sunrise and sunset`() {
        val today = LocalDate.now()
        WeatherStore.pin(app, Place("Moscow", null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = "Moscow",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(15.0, 14.0, Sky.PARTLY_CLOUDY, true, 60, 10.0, 16.0, 90, 1010.0, 4.0),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = java.time.LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 15.0,
                        sky = Sky.PARTLY_CLOUDY,
                        day = true,
                        rain = 0,
                    )
                },
                days = (0 until 5).map { index ->
                    DayForecast(
                        date = today.plusDays(index.toLong()),
                        sky = Sky.PARTLY_CLOUDY,
                        high = 20.0 - index,
                        low = 10.0 - index,
                        rain = 0,
                        sunrise = today.plusDays(index.toLong()).atTime(5, 12),
                        sunset = today.plusDays(index.toLong()).atTime(20, 41),
                    )
                },
                fetched = System.currentTimeMillis(),
            ),
        )

        // The card on its own rather than the whole screen: scrolling a paused-clock test is a
        // fight with the scroll animation, and the card is the thing under test.
        val model = WeatherModel(app)
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Days(model.forecast!!, model.settings)
                }
            }
        }
        settle()

        assertTrue("a sunrise is on show before any tap", showing("5:12") == 0)
        compose.onNodeWithTag("day-1").performClick()
        settle()
        assertTrue("opening a day did not surface its sunrise", showing("5:12") == 1)
        assertTrue("opening a day did not surface its sunset", showing("8:41") == 1)
        shoot("app-weather-day-open")

        // And the accordion holds one note: opening another day closes the first.
        compose.onNodeWithTag("day-3").performClick()
        settle()
        assertTrue("two days are open at once", showing("5:12") == 1)

        // A dash in a column of percentages reads as a stray minus sign. The column keeps its
        // width on a dry day and writes nothing in it.
        assertTrue("a dash is written for a dry day", showing("—") == 0)
    }

    /** The sun card on its own: the times, and the exact daylight the drawing can only gesture at. */
    @Test
    fun `the sun card says the times and the daylight`() {
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    SunArc(
                        sunrise = LocalDate.now().atTime(5, 12),
                        sunset = LocalDate.now().atTime(20, 41),
                    )
                }
            }
        }
        settle()
        shoot("app-weather-sun")

        assertTrue("the sunrise is missing", showing("5:12") > 0)
        assertTrue("the sunset is missing", showing("8:41") > 0)
        assertTrue("the daylight line is missing", showing("of daylight") > 0)
    }

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

    /**
     * The sky knows the time and the gauge.
     *
     * Six tiles, each a claim: a morning sun sits east and an evening one west (found by the
     * brightest pixel, which is the sun's own centre); a full moon puts more light in its patch
     * of sky than a new one, over an identical star field; and a sheet of rain draws visibly
     * more than a sprinkle, because the drop count now rides the actual millimetres.
     */
    @Test
    fun `the sun crosses, the moon waxes, and the rain falls as hard as it falls`() {
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        LiveSky(
                            Sky.CLEAR, day = true, daylight = 0.15f,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("morning"),
                        )
                        LiveSky(
                            Sky.CLEAR, day = true, daylight = 0.85f,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("evening"),
                        )
                        LiveSky(
                            Sky.CLEAR, day = false, night = 0.5f, moonPhase = 0.5f,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("full"),
                        )
                        LiveSky(
                            Sky.CLEAR, day = false, night = 0.5f, moonPhase = 0.02f,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("new"),
                        )
                        LiveSky(
                            Sky.RAIN, day = true, rainMm = 0.15,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("sprinkle"),
                        )
                        LiveSky(
                            Sky.RAIN, day = true, rainMm = 2.5,
                            modifier = Modifier.fillMaxSize().weight(1f).testTag("sheet"),
                        )
                    }
                }
            }
        }
        settle()
        val sheet = shoot("weather-sky-moments")
        val density = compose.density.density

        fun band(tag: String): Pair<Int, Int> {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return (bounds.top.value * density).toInt() + 2 to
                (bounds.bottom.value * density).toInt() - 2
        }

        fun luma(pixel: Int): Int = (
            android.graphics.Color.red(pixel) * 299 +
                android.graphics.Color.green(pixel) * 587 +
                android.graphics.Color.blue(pixel) * 114
            ) / 1000

        // Where the light in a tile is, on average: the sun is a broad soft glow, so its centre
        // is the centroid of everything brighter than the tile's own empty corner — a single
        // brightest pixel is a coin toss between quantised neighbours, but a centroid of a few
        // thousand of them is geography.
        fun litCentroidX(tag: String): Int {
            val (top, bottom) = band(tag)
            val ground = luma(sheet.getPixel(sheet.width / 20, bottom - 4))
            var sum = 0L
            var count = 0
            for (y in top until bottom step 2) {
                for (x in 0 until sheet.width step 2) {
                    if (luma(sheet.getPixel(x, y)) > ground + 4) { sum += x; count++ }
                }
            }
            assertTrue("$tag drew no sun at all", count > 50)
            return (sum / count).toInt()
        }
        assertTrue("the morning sun is not in the east", litCentroidX("morning") < sheet.width * 45 / 100)
        assertTrue("the evening sun is not in the west", litCentroidX("evening") > sheet.width * 55 / 100)

        // The moon sits half way along its arc; count what shines in its patch of sky. The star
        // field is identical between the two tiles, so the difference is the moon itself.
        fun moonlight(tag: String): Int {
            val (top, bottom) = band(tag)
            val height = bottom - top
            val cx = sheet.width / 2
            val cy = top + height * 15 / 100
            val reach = (16f * density).toInt()
            val ground = luma(sheet.getPixel(sheet.width / 20, bottom - 4))
            var lit = 0
            for (y in (cy - reach).coerceAtLeast(top) until (cy + reach).coerceAtMost(bottom)) {
                for (x in cx - reach until cx + reach) {
                    // Well above the halo, which both phases wear alike: only the disc itself is
                    // this bright, and the disc is the thing the phase decides.
                    if (luma(sheet.getPixel(x, y)) > ground + 40) lit++
                }
            }
            return lit
        }
        assertTrue(
            "a full moon (${moonlight("full")}) does not outshine a new one (${moonlight("new")})",
            moonlight("full") > moonlight("new") + 400,
        )

        // Rain by the millimetre: the sheet draws well over what the sprinkle does.
        fun raining(tag: String): Int {
            val (top, bottom) = band(tag)
            val ground = luma(sheet.getPixel(sheet.width / 20, bottom - 4))
            var lit = 0
            for (y in top until bottom step 2) {
                for (x in 0 until sheet.width step 2) {
                    if (luma(sheet.getPixel(x, y)) > ground + 6) lit++
                }
            }
            return lit
        }
        assertTrue(
            "hard rain (${raining("sheet")}) is not visibly harder than a sprinkle (${raining("sprinkle")})",
            raining("sheet") > raining("sprinkle") * 3 / 2,
        )
    }

    /**
     * The clouds are truly three-dimensional, measured with a ruler.
     *
     * The same puff at two depths must differ in drawn area by better than the linear ratio —
     * perspective scales both axes, so depth 2 against depth 5 is a factor of 2.5 in radius and
     * six in area, and a flat field would give exactly one. Then the weather's own field: an
     * overcast sky must out-ink a partly cloudy one, and the golden hour must actually reach
     * the clouds — two fields identical but for the glow must not draw the same picture.
     */
    @Test
    fun `the clouds have depth, weight, and catch the sunset`() {
        val puff = { z: Float -> listOf(Puff(0f, 0.8f, z, 0.5f)) }
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val ink = MaterialTheme.colorScheme.onSurface
                    val gold = MaterialTheme.colorScheme.tertiary
                    // Every tile clipped, so each band of the ruler measures only its own sky.
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("near"),
                        ) { puffField(puff(2f), ink, gold, 0f) }
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("far"),
                        ) { puffField(puff(5f), ink, gold, 0f) }
                        LiveSky(
                            Sky.PARTLY_CLOUDY, day = false,
                            modifier = Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("partly"),
                        )
                        LiveSky(
                            Sky.OVERCAST, day = false, glow = 0f,
                            modifier = Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("calm"),
                        )
                        LiveSky(
                            Sky.OVERCAST, day = false, glow = 1f,
                            modifier = Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("golden"),
                        )
                        LiveSky(
                            Sky.OVERCAST, day = false,
                            modifier = Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("overcast"),
                        )
                    }
                }
            }
        }
        settle()
        val sheet = shoot("weather-clouds-3d")
        val density = compose.density.density

        fun band(tag: String): Pair<Int, Int> {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return (bounds.top.value * density).toInt() + 2 to
                (bounds.bottom.value * density).toInt() - 2
        }

        fun luma(pixel: Int): Int = (
            android.graphics.Color.red(pixel) * 299 +
                android.graphics.Color.green(pixel) * 587 +
                android.graphics.Color.blue(pixel) * 114
            ) / 1000

        fun ink(tag: String): Int {
            val (top, bottom) = band(tag)
            val ground = luma(sheet.getPixel(4, bottom - 4))
            var lit = 0
            for (y in top until bottom step 2) {
                for (x in 0 until sheet.width step 2) {
                    if (luma(sheet.getPixel(x, y)) > ground + 4) lit++
                }
            }
            return lit
        }

        val near = ink("near")
        val far = ink("far")
        assertTrue("the far puff drew nothing at all ($far)", far > 150)
        assertTrue(
            "one puff at depth 2 ($near) against depth 5 ($far) shows no perspective",
            near > far * 3,
        )

        assertTrue(
            "an overcast sky (${ink("overcast")}) does not out-ink a partly cloudy one (${ink("partly")})",
            ink("overcast") > ink("partly") * 5 / 4,
        )

        // Same field, same clock, only the glow differs: the sunset must reach the pixels.
        val (calmTop, calmBottom) = band("calm")
        val (goldTop, _) = band("golden")
        var tinted = 0
        for (y in calmTop until calmBottom step 2) {
            for (x in 0 until sheet.width step 2) {
                if (sheet.getPixel(x, y) != sheet.getPixel(x, y - calmTop + goldTop)) tinted++
            }
        }
        assertTrue("the golden hour never reached the clouds", tinted > 500)
    }

    /**
     * The hand moves the camera, and the light has a side.
     *
     * Four tiles: the same overcast night twice, once with the phone level and once tipped —
     * the clouds' light must shift sideways, because the tilt is a camera offset and not a
     * sticker slide. Then one puff lit from the left and the same puff lit from the right —
     * the bright side must actually change sides.
     */
    @Test
    fun `tilting the phone moves the sky, and the light has a side`() {
        val lone = listOf(Puff(0f, 0.8f, 3f, 0.5f))
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val ink = MaterialTheme.colorScheme.onSurface
                    val gold = MaterialTheme.colorScheme.tertiary
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("level"),
                        ) { puffField(lone, ink, gold, 0f, camX = 0f) }
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("tipped"),
                        ) { puffField(lone, ink, gold, 0f, camX = 0.45f) }
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("litleft"),
                        ) { puffField(lone, ink, gold, 0f, lightX = size.width * 0.05f) }
                        androidx.compose.foundation.Canvas(
                            Modifier.fillMaxSize().weight(1f).clipToBounds().testTag("litright"),
                        ) { puffField(lone, ink, gold, 0f, lightX = size.width * 0.95f) }
                    }
                }
            }
        }
        settle()
        val sheet = shoot("weather-tilt-light")
        val density = compose.density.density

        fun band(tag: String): Pair<Int, Int> {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return (bounds.top.value * density).toInt() + 2 to
                (bounds.bottom.value * density).toInt() - 2
        }

        fun luma(pixel: Int): Int = (
            android.graphics.Color.red(pixel) * 299 +
                android.graphics.Color.green(pixel) * 587 +
                android.graphics.Color.blue(pixel) * 114
            ) / 1000

        fun centroidX(tag: String): Int {
            val (top, bottom) = band(tag)
            val ground = luma(sheet.getPixel(4, bottom - 4))
            var sum = 0L
            var count = 0
            for (y in top until bottom step 2) {
                for (x in 0 until sheet.width step 2) {
                    if (luma(sheet.getPixel(x, y)) > ground + 4) { sum += x; count++ }
                }
            }
            assertTrue("$tag drew nothing", count > 50)
            return (sum / count).toInt()
        }

        // Tilted right, the camera moves right and the world slides left — by the projection's
        // own amount for this depth, give or take the clipped rim.
        val slid = centroidX("level") - centroidX("tipped")
        assertTrue("the tilt moved the cloud by only $slid px", slid > 40)

        // The bright side follows the light. Weigh each half of the puff's tile by luma-above-
        // ground: the lit-from-the-left picture must lean left of the lit-from-the-right one.
        assertTrue(
            "the light has no side",
            centroidX("litleft") < centroidX("litright") - 6,
        )
    }

    /** The sensor reaches the camera: an injected accelerometer event moves the tilt state. */
    @Test
    fun `the accelerometer reaches the sky`() {
        val manager = app.getSystemService(android.hardware.SensorManager::class.java)
        shadowOf(manager).addSensor(
            org.robolectric.shadows.ShadowSensor.newInstance(
                android.hardware.Sensor.TYPE_ACCELEROMETER,
            ),
        )
        var seen = androidx.compose.ui.geometry.Offset.Zero
        compose.setContent {
            val tilt by rememberTilt(enabled = true)
            seen = tilt
        }
        compose.waitForIdle()

        // A real SensorEvent with a sized values array; the platform hides the constructor and
        // this Robolectric's no-argument factory leaves the array null, so it is built the
        // blunt way. A settled grip first, then a sharp lean: the state must answer the lean.
        fun event(ax: Float, ay: Float): android.hardware.SensorEvent {
            val ctor = android.hardware.SensorEvent::class.java
                .getDeclaredConstructor(Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            return ctor.newInstance(3).also {
                it.values[0] = ax
                it.values[1] = ay
            }
        }
        repeat(60) { shadowOf(manager).sendSensorEventToListeners(event(0f, 9.8f)) }
        repeat(5) { shadowOf(manager).sendSensorEventToListeners(event(2.6f, 9.8f)) }
        repeat(4) {
            compose.mainClock.advanceTimeBy(16L)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("the lean never reached the tilt state (${seen.x})", seen.x > 0.3f)
    }

    /**
     * The rain taps the hand in step with itself: a downpour patters, a drizzle only now and
     * then, and dry weather never. Counted on the paused clock through the same frame pacing
     * that also silences the taps whenever the screen stops getting frames — background,
     * battery saver, animations off.
     */
    @Test
    fun `the rain taps the hand as hard as it falls`() {
        var hard = 0
        var light = 0
        var dry = 0
        compose.setContent {
            RainPulse(active = true, intensity = 1f, tap = { hard++ })
            RainPulse(active = true, intensity = 0.1f, tap = { light++ })
            RainPulse(active = false, intensity = 1f, tap = { dry++ })
        }
        compose.waitForIdle()
        repeat(100) {
            compose.mainClock.advanceTimeBy(160L)
            shadowOf(Looper.getMainLooper()).idle()
        }

        assertTrue("no taps in a downpour", hard > 20)
        assertTrue("a drizzle taps like a downpour ($light vs $hard)", light < hard / 2)
        assertTrue("dry weather tapped $dry times", dry == 0)
    }

    /**
     * Battery saver is "animations off" said by the battery: the sky stands still, in the same
     * scattered pose the accessibility setting freezes it in, and not one pixel moves.
     */
    @Test
    fun `battery saver stills the sky`() {
        val power = app.getSystemService(android.os.PowerManager::class.java)
        shadowOf(power).setIsPowerSaveMode(true)
        try {
            compose.setContent {
                QuireTheme(dark = true, dynamic = false) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        LiveSky(Sky.SHOWERS, day = true, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            settle()
            val first = shoot("weather-saver")
            compose.mainClock.advanceTimeBy(3_000L)
            val second = shoot("weather-saver-later")

            var moved = 0
            for (y in 0 until first.height step 3) {
                for (x in 0 until first.width step 3) {
                    if (first.getPixel(x, y) != second.getPixel(x, y)) moved++
                }
            }
            assertTrue("the sky kept moving on battery saver ($moved pixels)", moved == 0)
        } finally {
            shadowOf(power).setIsPowerSaveMode(false)
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
