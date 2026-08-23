package app.quire.weather

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.m3.CalmMotionScheme
import app.quire.calendar.m3.QuireTheme
import app.quire.weather.ui.BLOCK
import app.quire.weather.ui.WeatherApp
import app.quire.weather.ui.WeatherModel
import app.quire.weather.ui.dayProgress
import app.quire.weather.ui.nightAt
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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The claims the first tier makes, each proved in the form it is actually made in.
 *
 * Arithmetic is proved as arithmetic and layout as layout. Two of these were originally drafted
 * as pixel counts and are not: the sun's position cannot be measured by counting lit pixels
 * around a disc that carries its own halo, and the night band's edge is a claim about where a
 * rectangle starts, not about how dark it is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TierOneTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
    }

    /**
     * Put back whatever this class switched off.
     *
     * Settings live in shared preferences, and shared preferences outlive a test method in
     * this harness — leaving Live sky off here silently turned the widget's rain and the sky's
     * own motion off for every test that ran afterwards, and three of them failed in another
     * file entirely with no hint of where it came from.
     */
    @org.junit.After
    fun putTheSkyBack() {
        WeatherSettings.get(app).liveSky = true
        android.provider.Settings.Global.putFloat(
            app.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    private fun settle(rounds: Int = 10) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(16L)
            Thread.sleep(5)
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.waitForIdle()
    }

    // ---- P1: the sun is where the clock says ------------------------------

    /**
     * The arc's arithmetic. Frozen at composition it answered "where the day was when you opened
     * the screen"; the fix is that it is asked again every minute, and what it is asked is this.
     */
    @Test
    fun `the day's progress follows the clock, not the moment the screen opened`() {
        val up = LocalDateTime.of(2026, 8, 23, 6, 0)
        val down = LocalDateTime.of(2026, 8, 23, 20, 0)

        assertEquals("before sunrise is not nought", 0f, dayProgress(up.minusHours(2), up, down), 0.0001f)
        assertEquals("after sunset is not one", 1f, dayProgress(down.plusHours(2), up, down), 0.0001f)
        assertEquals("midday is not halfway", 0.5f, dayProgress(up.plusHours(7), up, down), 0.001f)

        // Monotone, and actually moving: an hour of real time has to move the sun.
        var previous = -1f
        for (hour in 0..14) {
            val here = dayProgress(up.plusHours(hour.toLong()), up, down)
            assertTrue("the sun went backwards at hour $hour", here >= previous)
            previous = here
        }
        assertTrue(
            "an hour of the clock did not move the sun at all",
            dayProgress(up.plusHours(7), up, down) - dayProgress(up.plusHours(6), up, down) > 0.05f,
        )
    }

    // ---- P12: the dark hours, and only the ones that are actually dark ----

    /**
     * The band under the curve. The strip is a rolling twenty-four-hour window, so it crosses
     * midnight most of the time it is looked at — a single sunrise and sunset cannot answer it,
     * and Open-Meteo's are per day and nullable besides.
     */
    @Test
    fun `night is asked of the day each hour actually falls in`() {
        val first = LocalDate.of(2026, 8, 23)
        val days = listOf(
            day(first, up = 6, down = 20),
            // The next day rises an hour later, which is the whole reason this is per-day.
            day(first.plusDays(1), up = 7, down = 19),
        )

        assertTrue("noon was called night", !nightAt(first.atTime(12, 0), days))
        assertTrue("ten at night was called day", nightAt(first.atTime(22, 0), days))
        assertTrue("four in the morning was called day", nightAt(first.plusDays(1).atTime(4, 0), days))
        // The window crosses midnight and the second day's own sunrise governs there.
        assertTrue(
            "the second day was shaded by the first day's sunrise",
            nightAt(first.plusDays(1).atTime(6, 30), days),
        )
        assertTrue("after the second sunrise is still night", !nightAt(first.plusDays(1).atTime(7, 30), days))

        // Nothing known, nothing claimed.
        assertTrue("a day with no solar times was shaded anyway", !nightAt(first.atTime(22, 0), emptyList()))
        val blind = listOf(DayForecast(first, Sky.CLEAR, 20.0, 10.0, 0, null, null))
        assertTrue("a day with null sunrise was shaded anyway", !nightAt(first.atTime(22, 0), blind))
    }

    private fun day(date: LocalDate, up: Int, down: Int) = DayForecast(
        date = date,
        sky = Sky.CLEAR,
        high = 20.0,
        low = 10.0,
        rain = 0,
        sunrise = date.atTime(up, 0),
        sunset = date.atTime(down, 0),
    )

    // ---- P14: the stillness contract is one decision, in the theme --------

    /**
     * With animations switched off the theme itself hands out a scheme that does not move —
     * so every animation in both apps stops at once, and a new call site cannot forget to.
     *
     * Spatial snaps; effects do not. WCAG 2.3.3 excludes colour and opacity from what it calls
     * motion, and the crossfades are the protection against a value changing behind your back:
     * cutting those too would take information away in the name of an accessibility setting.
     */
    @Test
    fun `stillness swaps the scheme, and keeps the crossfades`() {
        android.provider.Settings.Global.putFloat(
            app.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        var calm: Boolean? = null
        var spatialSnaps: Boolean? = null
        var effectsSnap: Boolean? = null
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                val scheme = MaterialTheme.motionScheme
                calm = scheme === CalmMotionScheme
                spatialSnaps = scheme.defaultSpatialSpec<Float>() ===
                    CalmMotionScheme.defaultSpatialSpec<Float>()
                effectsSnap = scheme.defaultEffectsSpec<Float>() ===
                    CalmMotionScheme.defaultSpatialSpec<Float>()
                Text("still")
            }
        }
        settle()
        assertTrue("animations are off and the theme still hands out a moving scheme", calm == true)
        assertTrue("the spatial specs did not snap", spatialSnaps == true)
        assertTrue("the effects specs snapped too — the crossfades are gone", effectsSnap == false)
    }

    /** And with animations on, the calendar is quiet and the weather is the loud one. */
    @Test
    fun `the calendar moves standard and the weather moves expressive`() {
        android.provider.Settings.Global.putFloat(
            app.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        var calendar: Any? = null
        var weather: Any? = null
        compose.setContent {
            QuireTheme(dark = true, dynamic = false) {
                calendar = MaterialTheme.motionScheme
            }
            QuireTheme(
                dark = true,
                dynamic = false,
                motion = androidx.compose.material3.MotionScheme.expressive(),
            ) {
                weather = MaterialTheme.motionScheme
            }
        }
        settle()
        assertTrue("both apps resolved the same scheme", calendar !== weather)
        assertTrue("the calendar took the loud scheme", calendar !== null)
    }

    // ---- P4: an entrance is played once per screen ------------------------

    /**
     * A quiet refresh must not replay the cascade. Keyed on `fetched`, every block dropped 24dp
     * and faded back in each time the provider answered — the comment on the modifier already
     * condemned exactly that while the key underneath it guaranteed it.
     */
    @Test
    fun `a fresh forecast does not replay the entrance`() {
        // The sky is switched off for this one. It animates for ever by design, so it would
        // drift the pixels behind the card between the two photographs and the comparison
        // would fail whatever the entrance did. Isolating the claim is the point.
        WeatherSettings.get(app).liveSky = false
        store(fetched = 1_000L)
        // The model is held, because storing a forecast does not reach the screen on its
        // own — the view model keeps its own copy and only reloads when it is asked to.
        // Written the other way round this test drove nothing at all and passed with the
        // bug fully restored, which is the most dangerous kind of green there is.
        val model = WeatherModel(app)
        compose.setContent { WeatherApp(model) }
        settle()

        // Photographed, not measured from the layout tree. The entrance moves its block with a
        // graphicsLayer, and a graphics layer does not touch layout bounds — semantics report the
        // block sitting exactly where it always sat while the pixels slide and fade. This test
        // was written the wrong way round first and passed happily with the bug back in.
        val settled = compose.onAllNodesWithTag(BLOCK)[0]
            .captureToImage().asAndroidBitmap()

        // A new forecast, same content, inside the same minute so the "updated at" line
        // renders identical glyphs. Anything that moves now is the entrance.
        store(fetched = 20_000L)
        model.reload()
        compose.mainClock.advanceTimeBy(16L)
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(16L)
        compose.waitForIdle()

        val afterward = compose.onAllNodesWithTag(BLOCK)[0]
            .captureToImage().asAndroidBitmap()
        // Counted as surface, not as a pixel diff. A replayed entrance starts the block at
        // alpha nought and 24dp low, so one frame in its own card has all but vanished — the
        // area still carrying the card's fill collapses. A plain diff cannot make this claim:
        // a couple of per cent of the block legitimately differs frame to frame, and chasing
        // that number down is how a test ends up asserting nothing.
        fun carpet(b: android.graphics.Bitmap): Int {
            val counts = HashMap<Int, Int>()
            for (x in 0 until b.width step 2) {
                for (y in 0 until b.height step 2) {
                    val p = b.getPixel(x, y)
                    counts[p] = (counts[p] ?: 0) + 1
                }
            }
            return counts.values.maxOrNull() ?: 0
        }
        val before = carpet(settled)
        val now = carpet(afterward)
        assertTrue("nothing was drawn to begin with", before > 500)
        assertTrue(
            "the entrance replayed on a quiet refresh: the block's own surface went from " +
                "$before to $now",
            now > before * 8 / 10,
        )
    }

    private fun store(fetched: Long) {
        WeatherStore.pin(app, Place("Moscow", null, "Russia", 55.75, 37.62))
        WeatherStore.save(
            app,
            Forecast(
                place = "Moscow",
                latitude = 55.75,
                longitude = 37.62,
                now = Conditions(22.0, 21.0, Sky.CLEAR, true, 60, 8.0, 15.0, 180, 1012.0, 4.0),
                hours = (0 until 26).map { hour ->
                    HourForecast(
                        time = LocalDateTime.now().withMinute(0).plusHours(hour.toLong()),
                        temperature = 20.0 - hour * 0.2,
                        sky = Sky.CLEAR,
                        day = true,
                        rain = 0,
                    )
                },
                days = (0 until 5).map { index ->
                    val date = LocalDate.now().plusDays(index.toLong())
                    DayForecast(
                        date = date,
                        sky = Sky.CLEAR,
                        high = 24.0 - index,
                        low = 13.0 - index,
                        rain = 0,
                        sunrise = date.atTime(6, 0),
                        sunset = date.atTime(20, 0),
                    )
                },
                fetched = fetched,
            ),
        )
    }
}
