package app.quire.calendar

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
