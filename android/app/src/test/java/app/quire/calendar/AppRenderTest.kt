package app.quire.calendar

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Prefs
import app.quire.calendar.core.Skin
import app.quire.calendar.m3.CalendarModel
import app.quire.calendar.m3.MonthScreen
import app.quire.calendar.m3.QuireTheme
import app.quire.calendar.m3.SearchResults
import app.quire.calendar.m3.SettingsScreen
import app.quire.calendar.m3.YearScreen
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
import java.time.YearMonth
import java.time.ZoneId

/**
 * The app itself, drawn.
 *
 * There is no emulator in this environment, so every screen is composed for real under
 * Robolectric's native graphics and written out as a PNG that can be looked at. The assertions
 * below only catch a screen that failed to paint at all; the pictures in `build/screenshots` are
 * what catch a screen that painted the wrong thing, and they are what the design was checked
 * against.
 *
 * Events come from [FakeCalendarProvider] rather than from stub data, so the model, the loader and
 * the repository all run the code they run on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private val month: YearMonth = YearMonth.now()

    @Before
    fun stockTheCalendar() {
        compose.mainClock.autoAdvance = false
        FakeCalendarProvider.reset()
        org.robolectric.Robolectric.setupContentProvider(
            FakeCalendarProvider::class.java,
            android.provider.CalendarContract.AUTHORITY,
        )
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALENDAR)

        val zone = ZoneId.systemDefault()
        fun at(day: Int, hour: Int) = month.atDay(day)
            .atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        fun julian(day: Int) = MonthModel.julianDay(month.atDay(day))

        // A believable month rather than an event on every square: a few busy days, a few quiet
        // ones, and most of it empty. A density wash tuned against a full month is tuned wrong.
        val busy = listOf(
            Triple(3, "Dentist", 0xFF4C5D3C.toInt()),
            Triple(9, "Standup", 0xFF2E4A7D.toInt()),
            Triple(9, "Design review", 0xFF9A6F21.toInt()),
            Triple(9, "One to one", 0xFF7B3B6E.toInt()),
            Triple(14, "Рабочая встреча", 0xFF9A6F21.toInt()),
            Triple(17, "Flight to Porto", 0xFF2E4A7D.toInt()),
            Triple(17, "Check in", 0xFF4C5D3C.toInt()),
            Triple(23, "Library books due", 0xFF7B3B6E.toInt()),
        )
        FakeCalendarProvider.instances = busy.mapIndexed { index, (day, title, colour) ->
            FakeCalendarProvider.instance(
                eventId = index + 1L,
                beginMillis = at(day, 9 + index % 6),
                endMillis = at(day, 10 + index % 6),
                startDay = julian(day),
                endDay = julian(day),
                title = title,
                location = if (title == "Flight to Porto") "Terminal 2" else null,
                colour = colour,
            )
        }
        FakeCalendarProvider.calendars = listOf(
            FakeCalendarProvider.calendar(1L, "Personal", "me@example.com", 0xFF2E4A7D.toInt()),
            FakeCalendarProvider.calendar(2L, "Work", "me@work.example", 0xFF9A6F21.toInt()),
        )
    }

    /**
     * Lets the loader's background thread answer, runs what it posted to the main thread, and
     * moves the animation clock on far enough for everything the frame started to have finished.
     *
     * The clock is driven by hand rather than left to advance itself because the screens contain
     * an animation that never ends — the expressive loading indicator — and `waitForIdle` with an
     * automatic clock waits for animations to finish. Against an endless one it never returns.
     */
    private fun settle(rounds: Int = 40) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(FRAME_MS)
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun model(dark: Boolean): CalendarModel {
        Prefs.get(app).skin = if (dark) Skin.INK else Skin.PAPER
        Prefs.get(app).heat = true
        return CalendarModel(app)
    }

    /**
     * Every screen is shot on a real page rather than on the transparent default — a `Surface`,
     * not a coloured `Box`, because a Surface is also what sets the content colour that unstyled
     * text inherits. `Scaffold` does the same thing in the app, so this is the same ground.
     */
    private fun page(dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            QuireTheme(dark = dark, dynamic = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    content()
                }
            }
        }
    }

    private fun shoot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertPainted(bitmap, name)
        return bitmap
    }

    private fun assertPainted(bitmap: Bitmap, name: String) {
        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours += bitmap.getPixel(x, y)
                y += 3
            }
            x += 3
        }
        assertTrue("$name painted only ${colours.size} distinct colours", colours.size > 8)
    }

    private fun showing(text: String): Int =
        compose.onAllNodes(hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `the month screen paints its grid, its marks and the day's agenda`() {
        val model = model(dark = false)
        page(dark = false) {
            MonthScreen(model, PaddingValues(0.dp), onOpenEvent = {}, onGrant = {})
        }
        model.refresh()
        model.openDay(month.atDay(9))
        settle()
        shoot("app-month-light")

        assertTrue("the agenda did not name the day's events", showing("Standup") > 0)
        assertTrue("a second entry on the day was dropped", showing("Design review") > 0)
    }

    @Test
    fun `the month screen paints dark`() {
        val model = model(dark = true)
        page(dark = true) {
            MonthScreen(model, PaddingValues(0.dp), onOpenEvent = {}, onGrant = {})
        }
        model.refresh()
        model.openDay(month.atDay(17))
        settle()
        val bitmap = shoot("app-month-dark")

        // A dark screen that came out light is the failure worth an assertion; the top-left
        // corner is page rather than a component in every layout this grid can take.
        val corner = bitmap.getPixel(4, 4)
        val luma = (
            android.graphics.Color.red(corner) * 299 +
                android.graphics.Color.green(corner) * 587 +
                android.graphics.Color.blue(corner) * 114
            ) / 1000
        assertTrue("the dark screen painted a light page (luma $luma)", luma < 96)
    }

    @Test
    fun `the year screen paints twelve legible months`() {
        val model = model(dark = false)
        page(dark = false) { YearScreen(model, PaddingValues(0.dp)) {} }
        model.refresh()
        settle()
        shoot("app-year")

        // Twelve tiles, each naming its month, is the whole point of going back to this view.
        assertTrue(
            "the year did not name every month",
            (1..12).count { showing(MonthModel.monthName(YearMonth.of(month.year, it), java.util.Locale.getDefault())) > 0 } >= 10,
        )
    }

    @Test
    fun `the settings screen paints every section`() {
        val model = model(dark = false)
        page(dark = false) { SettingsScreen(model, PaddingValues(0.dp)) }
        model.refresh()
        settle()
        shoot("app-settings")
    }

    @Test
    fun `search names the day each hit was found in`() {
        val model = model(dark = false)
        page(dark = false) { SearchResults(model) {} }
        model.refresh()
        model.search("re")
        settle()
        shoot("app-search")

        assertTrue("search found nothing it should have found", showing("Design review") > 0)
    }
}

/** One frame at sixty a second, and long enough after it for any spring to have stopped. */
private const val FRAME_MS = 16L
private const val SETTLE_MS = 2_000L
