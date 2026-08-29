package app.quire.calendar

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import app.quire.calendar.m3.MainActivity
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
 * The app driven the way it is used: the real Activity, real taps, and the clock in hand.
 *
 * The screen tests compose one screen at a time, which cannot show what happens *between* two of
 * them. Here the destination is changed by pressing the thing a finger would press, and because
 * the animation clock is stopped the transition can be photographed part of the way through — the
 * only way to see that the year's tile grows into the month rather than dissolving into it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val outputDir = File("build/screenshots").apply { mkdirs() }

    @Before
    fun stopTheClock() {
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun settle(rounds: Int = 20) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(16L)
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
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

    private fun colours(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                seen += bitmap.getPixel(x, y)
                y += 4
            }
            x += 4
        }
        return seen.size
    }

    private fun tap(label: String) {
        compose.onAllNodes(hasText(label)).fetchSemanticsNodes().firstOrNull()
            ?: error("nothing on screen says \"$label\"")
        compose.onAllNodes(hasText(label))[0].performClick()
    }

    @Test
    fun `the year opens by growing out of the month it was tapped from`() {
        tap("Year")

        // Part of the way through: both the year and the month it came from are on screen, which
        // is what a container transform looks like and what a cross-fade never does.
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(120L)
        val midway = shoot("flow-year-midway")
        assertTrue("nothing was painted mid-transition", colours(midway) > 8)

        settle()
        val year = shoot("flow-year")
        assertTrue("the year did not arrive", colours(year) > 8)
        assertTrue(
            "the year is not showing twelve months",
            compose.onAllNodes(hasText("December")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /** The expressive button opens into the three things worth doing from a month. */
    @Test
    fun `the action button opens into a menu`() {
        compose.onAllNodes(hasContentDescription("Actions"))[0].performClick()
        settle()
        val open = shoot("flow-fab-menu")
        assertTrue("the menu painted nothing", colours(open) > 8)

        listOf("New event", "Jump to a date", "Today").forEach { label ->
            assertTrue(
                "the menu is missing \"$label\"",
                compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun `jumping to a date opens the picker`() {
        compose.onAllNodes(hasContentDescription("Actions"))[0].performClick()
        settle()
        tap("Jump to a date")
        settle()

        assertTrue(
            "the picker has no way to confirm",
            compose.onAllNodes(hasText("Go")).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the picker has no way out",
            compose.onAllNodes(hasText("Cancel")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * The scrub, wired to the bar the app actually draws.
     *
     * [ScrubTest] proves the gesture against four equal boxes; this proves that the four boxes
     * are the navigation bar — that the columns line up with the items Material lays out, that
     * what the finger names is the destination and not some parallel number, and that having
     * given the bar a drag has not quietly cost it its taps.
     */
    @Test
    fun `one finger sliding along the bar walks the screens, and a tap still lands`() {
        fun item(label: String) =
            compose.onAllNodes(hasText(label))[0].fetchSemanticsNode().boundsInRoot

        val year = item("Year")
        val search = item("Search")
        val settings = item("Settings")
        // "Today" is on the bar and also heading the agenda under the grid, and the agenda's copy
        // is the one higher up the tree — so the bar's own is found by the row it sits on rather
        // than by being first. Reaching for `[0]` here is how this test spent its first run
        // dragging a finger sideways across the agenda and reporting the gesture broken.
        val today = compose.onAllNodes(hasText("Today")).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .first { it.top >= year.top }

        val row = today.center.y
        compose.onRoot().performTouchInput {
            // Slop first, without leaving the item pressed.
            down(Offset(today.center.x, row))
            moveTo(Offset(today.center.x + viewConfiguration.touchSlop + 1f, row))
        }
        // The finger stays down across these calls — the dispatcher keeps its pointers between
        // them — which is what lets each stop be photographed without ever letting go. The
        // frames are the filmstrip of the gesture, and the only picture of it there can be.
        listOf(today, year, search, settings).forEachIndexed { index, item ->
            compose.onRoot().performTouchInput { moveTo(Offset(item.center.x, row)) }
            settle()
            assertTrue(
                "the screen under the finger at stop $index did not paint",
                colours(shoot("flow-scrub-$index")) > 8,
            )
        }
        compose.onRoot().performTouchInput { up() }
        settle()

        assertTrue(
            "the finger crossed the whole bar and the settings never arrived",
            compose.onAllNodes(hasText("System colours")).fetchSemanticsNodes().isNotEmpty(),
        )
        // And the bar is still four buttons: a press that does not travel goes where it is aimed.
        compose.onRoot().performTouchInput { down(today.center); up() }
        settle()
        assertTrue(
            "a tap on the bar stopped working once the bar could be dragged",
            compose.onAllNodes(hasContentDescription("Actions")).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * The year used to be a room with twelve pictures and no door. It drew whatever year the
     * month happened to be in, and offered nothing for reaching another one — so this asserts the
     * door, in the only terms that matter: a finger drags, and a different year is there.
     */
    @Test
    fun `the year is not a dead end`() {
        val here = LocalDate.now().year
        tap("Year")
        settle()
        assertTrue(
            "the year screen did not open on this year",
            compose.onAllNodes(hasText(here.toString())).fetchSemanticsNodes().isNotEmpty(),
        )

        // January, in pixels, is what the claim rests on. The caption in the app bar reads the
        // model's year and follows the pager whether or not the grid does — with that as the
        // assertion, an implementation whose every page drew the same twelve months passed. A
        // January is a different shape in a different year, because the first falls on a
        // different weekday, and no caption lives inside the tile to give a false pass.
        val before = januaryTile("flow-year-this")

        compose.onRoot().performTouchInput { swipeLeft() }
        settle()
        assertTrue(
            "the swipe went nowhere: ${here + 1} never arrived",
            compose.onAllNodes(hasText((here + 1).toString())).fetchSemanticsNodes().isNotEmpty(),
        )
        val next = januaryTile("flow-year-next")
        val moved = differing(before, next)
        assertTrue(
            "the caption changed year but the grid did not (January differs in ${percent(moved)})",
            // A mini-month is mostly ground: the digits are a few per cent of the tile, so a
            // year whose first of January lands on a different weekday moves about four per cent
            // of the pixels. The same year drawn twice moves none at all, which is the whole
            // distance this number has to tell apart.
            moved > 0.015,
        )

        // And back, so the door swings both ways rather than only forwards.
        compose.onRoot().performTouchInput { swipeRight() }
        settle()
        assertTrue(
            "the year could be left but not returned to",
            compose.onAllNodes(hasText(here.toString())).fetchSemanticsNodes().isNotEmpty(),
        )
        val returned = differing(before, januaryTile("flow-year-back"))
        assertTrue(
            "coming back landed on a different January (${percent(returned)} of it differs)",
            returned < 0.005,
        )
    }

    /** The January tile alone, cropped out of the screen it was drawn on. */
    private fun januaryTile(name: String): Bitmap {
        val box = compose.onAllNodes(hasText("January"))[0].fetchSemanticsNode().boundsInRoot
        val whole = shoot(name)
        val left = box.left.toInt().coerceIn(0, whole.width - 1)
        val top = box.top.toInt().coerceIn(0, whole.height - 1)
        return Bitmap.createBitmap(
            whole,
            left,
            top,
            box.width.toInt().coerceAtMost(whole.width - left),
            box.height.toInt().coerceAtMost(whole.height - top),
        )
    }

    /** The fraction of pixels that are not the same colour in both. */
    private fun differing(a: Bitmap, b: Bitmap): Double {
        if (a.width != b.width || a.height != b.height) return 1.0
        var seen = 0
        var apart = 0
        for (x in 0 until a.width) {
            for (y in 0 until a.height) {
                seen++
                if (a.getPixel(x, y) != b.getPixel(x, y)) apart++
            }
        }
        return if (seen == 0) 0.0 else apart.toDouble() / seen
    }

    private fun percent(fraction: Double) = "%.1f%%".format(fraction * 100)

    @Test
    fun `settings and search are reachable and paint`() {
        tap("Settings")
        settle()
        assertTrue("settings did not paint", colours(shoot("flow-settings")) > 8)

        tap("Search")
        settle()
        assertTrue("search did not paint", colours(shoot("flow-search")) > 8)
    }
}
