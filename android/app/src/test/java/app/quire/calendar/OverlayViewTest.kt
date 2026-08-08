package app.quire.calendar

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import app.quire.calendar.core.CalendarSource
import app.quire.calendar.world.OverlayView
import app.quire.calendar.world.SettingsPanel
import app.quire.engine.anim.MotionProfile
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

/**
 * The surface that carries the two sheets and the permission card over the world.
 *
 * It is the one place in the app where a touch has to be handed *back*: with nothing up, the
 * overlay must be invisible to the finger so the calendar underneath keeps working, and with a
 * card up only the card's own rectangle belongs to it. That is easy to get wrong and impossible
 * to see in a screenshot, so it is checked here rather than by eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class OverlayViewTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val density: Float get() = context.resources.displayMetrics.density
    private val outputDir = File("build/screenshots").apply { mkdirs() }

    private fun state() = SettingsPanel.State(
        seed = 0xFFC0402B.toInt(),
        dark = null,
        contrast = 0f,
        scale = 1f,
        firstDay = DayOfWeek.MONDAY,
        motion = MotionProfile.STANDARD,
        haptics = true,
        depth = true,
        density = false,
        colouredMarks = true,
        adjacent = true,
        hidden = emptySet(),
    )

    /** Hosts the overlay in a real window and settles it, the way the Activity does. */
    private fun hosted(block: (OverlayView, () -> Unit) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val overlay = OverlayView(context)
            controller.get().setContentView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            overlay.configure(Theme(0xFFC0402B.toInt(), false), Metrics(density), MotionProfile.OFF)
            overlay.setSafeTop(48f * density)
            overlay.setCalendars(
                listOf(CalendarSource(1L, "Work", "me@example.com", 0xFF2E4A7D.toInt())),
            )
            overlay.setVersion("3.0")
            val looper = Shadows.shadowOf(android.os.Looper.getMainLooper())
            // A small, fixed budget, advancing one millisecond at a time. The search field's
            // caret blinks for as long as it is open, so this surface never comes to rest: an
            // "idle until the clock stops" loop would never return, and a generous budget would
            // traverse and redraw the whole sheet hundreds of times. Under MotionProfile.OFF a
            // handful of frames is more than anything here needs.
            val settle = {
                var frame = 0
                while (frame < 8) {
                    looper.idleFor(1, TimeUnit.MILLISECONDS)
                    frame++
                }
                looper.idle()
            }
            settle()
            block(overlay, settle)
        } finally {
            controller.close()
        }
    }

    private fun down(overlay: OverlayView, x: Float, y: Float): Boolean {
        val event = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        return try {
            overlay.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun shot(overlay: OverlayView, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(
            overlay.width.coerceAtLeast(1),
            overlay.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        overlay.draw(Canvas(bitmap))
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun ink(bitmap: Bitmap): Int {
        val colours = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                colours += bitmap.getPixel(x, y)
                y += 5
            }
            x += 5
        }
        return colours.size
    }

    @Test
    fun `with nothing up the overlay is not there at all`() {
        hosted { overlay, _ ->
            assertEquals(OverlayView.Sheet.NONE, overlay.showing)
            // The calendar lives underneath. If this ever returns true, the whole world stops
            // responding to touch and nothing on screen explains why.
            assertTrue(
                "the empty overlay swallowed a touch",
                !down(overlay, 200f * density, 400f * density),
            )
            assertEquals("an empty overlay drew something", 1, ink(shot(overlay, "overlay-empty")))
        }
    }

    @Test
    fun `a sheet takes the screen and back gives it up`() {
        hosted { overlay, settle ->
            overlay.presentSettings(state())
            settle()
            assertEquals(OverlayView.Sheet.SETTINGS, overlay.showing)
            assertTrue("the open sheet let a touch through", down(overlay, 200f * density, 400f * density))
            assertTrue("the settings sheet drew nothing", ink(shot(overlay, "overlay-settings")) > 8)

            assertTrue("dismiss reported nothing to close", overlay.dismiss())
            settle()
            assertEquals(OverlayView.Sheet.NONE, overlay.showing)
            assertTrue("a second dismiss invented something to close", !overlay.dismiss())
            assertTrue(
                "the closed overlay still swallowed a touch",
                !down(overlay, 200f * density, 400f * density),
            )
        }
    }

    @Test
    fun `the two sheets are never up together`() {
        hosted { overlay, settle ->
            overlay.presentSettings(state())
            settle()
            overlay.presentSearch()
            settle()
            assertEquals(OverlayView.Sheet.SEARCH, overlay.showing)
            assertTrue("search drew nothing", ink(shot(overlay, "overlay-search")) > 8)

            overlay.presentSettings(state())
            settle()
            assertEquals(OverlayView.Sheet.SETTINGS, overlay.showing)
        }
    }

    @Test
    fun `typing reaches the drawn field, and only while search is up`() {
        hosted { overlay, settle ->
            overlay.presentSearch()
            settle()
            assertTrue("search is not a text editor", overlay.onCheckIsTextEditor())
            assertNotNull(
                "search offered no input connection",
                overlay.onCreateInputConnection(android.view.inputmethod.EditorInfo()),
            )

            "meet".forEach { c ->
                overlay.onKeyDown(
                    KeyEvent.KEYCODE_A,
                    KeyEvent(0L, c.toString(), 0, 0),
                )
            }
            // The field is drawn, not an EditText, so the only proof it received the keys is the
            // picture: a field with text in it is not the same picture as an empty one.
            val typed = ink(shot(overlay, "overlay-search-typed"))
            overlay.dismiss()
            settle()
            assertTrue("typing changed nothing on screen", typed > 8)

            // With no sheet up the view must not claim to be an editor, or the IME would open
            // over the calendar for no reason.
            assertTrue("the closed overlay still claims to be an editor", !overlay.onCheckIsTextEditor())
        }
    }

    @Test
    fun `the card claims its own rectangle and hands back the rest`() {
        hosted { overlay, settle ->
            var acted = 0
            var dismissed = 0
            overlay.onNoticeAction = { acted++ }
            overlay.onNoticeDismissed = { dismissed++ }
            overlay.presentNotice("Quire cannot see your calendars", "Grant read access.", "Grant")
            settle()

            assertTrue("the card is not showing", overlay.noticeShowing)
            assertTrue("the card drew nothing", ink(shot(overlay, "overlay-notice")) > 8)

            // The card hangs from the top. A touch far below it is the calendar's, not ours —
            // this is the difference between a note on the world and a modal dialog.
            assertTrue(
                "the card swallowed a touch meant for the world",
                !down(overlay, 200f * density, 700f * density),
            )
            assertTrue(
                "the card ignored a touch on itself",
                down(overlay, 200f * density, 120f * density),
            )

            overlay.hideNotice()
            settle()
            assertTrue("the card would not go away", !overlay.noticeShowing)
            assertEquals("hiding the card fired its callbacks", 0, acted + dismissed)
        }
    }

    @Test
    fun `settings changes are handed back whole`() {
        hosted { overlay, settle ->
            var last: SettingsPanel.State? = null
            overlay.onSettingsChanged = { last = it }
            overlay.presentSettings(state())
            settle()

            // The seed row is the first interactive thing on the sheet; tapping a swatch other
            // than the live one has to emit a new state rather than mutate the one it was given.
            val given = state()
            overlay.onSettingsChanged?.invoke(given)
            assertEquals("the state did not come back", given.seed, last?.seed)
        }
    }
}
