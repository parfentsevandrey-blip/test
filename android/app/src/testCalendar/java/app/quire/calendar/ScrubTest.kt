package app.quire.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.quire.calendar.m3.columnAt
import app.quire.calendar.m3.scrubbable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One finger, laid on a row of four and never lifted.
 *
 * The bar this proves is a synthetic one rather than the app's, and deliberately: the claims here
 * are about the gesture — where the threshold is, what it costs a tap, which way it runs in a
 * mirrored layout — and each of them wants a bar whose columns are exactly a quarter and whose
 * children do nothing but report being pressed. That the real navigation bar is actually wired to
 * this is a different claim, and [AppFlowTest] makes it with the real Activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScrubTest {

    @get:Rule
    val compose = createComposeRule()

    /** Every touch the gesture asks for, in order. */
    private class Hand : HapticFeedback {
        val felt = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            felt += hapticFeedbackType
        }
    }

    private val hand = Hand()
    private val tapped = mutableListOf<Int>()
    private var landed by mutableStateOf(0)

    private fun bar(enabled: Boolean = true, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(
                LocalHapticFeedback provides hand,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .testTag(BAR)
                        .scrubbable(
                            enabled = enabled,
                            count = 4,
                            landedOn = { landed },
                            onScrub = { landed = it },
                        ),
                ) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { tapped += index },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun ticks() = hand.felt.count { it == HapticFeedbackType.SegmentTick }

    // ---- the arithmetic ------------------------------------------------

    @Test
    fun `a column is a quarter of the width, and the ends hold`() {
        assertEquals(0, columnAt(x = 10f, width = 400f, count = 4, rtl = false))
        assertEquals(1, columnAt(x = 150f, width = 400f, count = 4, rtl = false))
        assertEquals(3, columnAt(x = 399f, width = 400f, count = 4, rtl = false))
        // The boundary belongs to the column it opens.
        assertEquals(1, columnAt(x = 100f, width = 400f, count = 4, rtl = false))
        assertEquals(0, columnAt(x = 99.9f, width = 400f, count = 4, rtl = false))
    }

    @Test
    fun `a finger dragged off either end stays on the end item`() {
        assertEquals(0, columnAt(x = -260f, width = 400f, count = 4, rtl = false))
        // Truncation towards zero is what makes this worth asserting: a small negative x is
        // -0.4 of a column, which becomes 0 rather than -1 all by itself, and a large one is
        // not. Both have to answer the same.
        assertEquals(0, columnAt(x = -30f, width = 400f, count = 4, rtl = false))
        assertEquals(3, columnAt(x = 4000f, width = 400f, count = 4, rtl = false))
    }

    @Test
    fun `a mirrored layout counts the other way`() {
        assertEquals(3, columnAt(x = 10f, width = 400f, count = 4, rtl = true))
        assertEquals(0, columnAt(x = 390f, width = 400f, count = 4, rtl = true))
        assertEquals(2, columnAt(x = 150f, width = 400f, count = 4, rtl = true))
    }

    @Test
    fun `a bar with no width names no column instead of dividing by it`() {
        assertEquals(0, columnAt(x = 10f, width = 0f, count = 4, rtl = false))
        assertEquals(0, columnAt(x = 10f, width = 400f, count = 0, rtl = false))
    }

    // ---- the gesture ---------------------------------------------------

    @Test
    fun `one finger crossing the bar names every item on the way`() {
        bar()
        compose.onNodeWithTag(BAR).performTouchInput {
            val y = height / 2f
            val column = width / 4f
            down(Offset(column * 0.5f, y))
            // The slop is crossed without leaving the first column, so the threshold itself is
            // not what moves the screen — the travel after it is.
            moveTo(Offset(column * 0.5f + viewConfiguration.touchSlop + 1f, y))
            moveTo(Offset(column * 1.5f, y))
            moveTo(Offset(column * 2.5f, y))
            moveTo(Offset(column * 3.5f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the finger did not carry to the last item", 3, landed)
        assertEquals("one tick per boundary crossed, and no more", 3, ticks())
        assertEquals(
            "the item the scrub started on also fired its own click",
            emptyList<Int>(),
            tapped,
        )
    }

    @Test
    fun `and back again, without lifting`() {
        bar()
        compose.onNodeWithTag(BAR).performTouchInput {
            val y = height / 2f
            val column = width / 4f
            down(Offset(column * 0.5f, y))
            moveTo(Offset(column * 3.5f, y))
            moveTo(Offset(column * 1.5f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the finger could go out but not come back", 1, landed)
    }

    /**
     * The threshold, from below. No finger holds still, so a tap that wanders a few pixels
     * sideways is the ordinary case and not the edge one: it must still be a tap, land on the
     * item it was aimed at, and ask for none of the scrub's touches.
     */
    @Test
    fun `a tap that wanders a little is still a tap`() {
        bar()
        compose.onNodeWithTag(BAR).performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveBy(Offset(viewConfiguration.touchSlop - 2f, 0f))
            up()
        }
        compose.waitForIdle()

        assertEquals("the tap did not reach the item it was aimed at", listOf(1), tapped)
        assertEquals("a tap moved the screen", 0, landed)
        assertEquals("a tap asked for a scrub's tick", 0, ticks())
    }

    @Test
    fun `switched off, the bar is four buttons again`() {
        bar(enabled = false)
        compose.onNodeWithTag(BAR).performTouchInput {
            val y = height / 2f
            val column = width / 4f
            down(Offset(column * 0.5f, y))
            moveTo(Offset(column * 1.5f, y))
            moveTo(Offset(column * 3.5f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the setting is off and the bar scrubbed anyway", 0, landed)
        assertEquals("the setting is off and the bar buzzed anyway", 0, ticks())
    }

    @Test
    fun `a finger going down the bar is not taken for one going along it`() {
        bar()
        compose.onNodeWithTag(BAR).performTouchInput {
            val x = width * 0.125f
            down(Offset(x, height / 2f))
            moveBy(Offset(0f, viewConfiguration.touchSlop * 3f))
            up()
        }
        compose.waitForIdle()

        assertEquals("a vertical drag was read as a scrub", 0, landed)
        assertEquals("a vertical drag asked for a tick", 0, ticks())
    }

    @Test
    fun `a mirrored bar scrubs towards the left`() {
        bar(rtl = true)
        compose.onNodeWithTag(BAR).performTouchInput {
            val y = height / 2f
            val column = width / 4f
            // In a right-to-left layout the first item is drawn on the right, so this starts on
            // it and travels away — physically leftwards, logically forwards.
            down(Offset(column * 3.5f, y))
            moveTo(Offset(column * 3.5f - viewConfiguration.touchSlop - 1f, y))
            moveTo(Offset(column * 2.5f, y))
            moveTo(Offset(column * 1.5f, y))
            moveTo(Offset(column * 0.5f, y))
            up()
        }
        compose.waitForIdle()

        assertEquals("the mirrored bar ran backwards", 3, landed)
        assertEquals(3, ticks())
    }

    @Test
    fun `the touch is the one the rest of the app uses for a boundary`() {
        bar()
        compose.onNodeWithTag(BAR).performTouchInput {
            val y = height / 2f
            down(Offset(width * 0.125f, y))
            moveTo(Offset(width * 0.375f, y))
            up()
        }
        compose.waitForIdle()

        assertTrue("the scrub was silent", hand.felt.isNotEmpty())
        assertEquals(
            "a boundary crossed here does not feel like one crossed anywhere else",
            listOf(HapticFeedbackType.SegmentTick),
            hand.felt,
        )
    }

    private companion object {
        const val BAR = "bar"
    }
}
