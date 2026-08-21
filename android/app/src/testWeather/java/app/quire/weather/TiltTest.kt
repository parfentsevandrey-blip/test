package app.quire.weather

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.quire.weather.ui.TiltFilter
import app.quire.weather.ui.fallTouch
import app.quire.weather.ui.snowGap
import app.quire.weather.ui.struck
import app.quire.weather.ui.tapGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand's arithmetic: what the accelerometer becomes before the camera sees it, and the
 * rhythms the sky lands in. All pure functions, tested as such — the sensor plumbing and
 * the pixels have their own tests, and neither should have to prove the maths too.
 */
class TiltTest {

    @Test
    fun `any way of holding the phone becomes zero`() {
        val filter = TiltFilter()
        // A phone lying oddly on a couch: constant, arbitrary gravity.
        var tilt = filter.step(3.2f, 5.1f)
        repeat(400) { tilt = filter.step(3.2f, 5.1f) }
        assertEquals("a resting pose did not settle to zero", 0f, tilt.x, 0.02f)
        assertEquals(0f, tilt.y, 0.02f)
    }

    @Test
    fun `a lean answers at once and lets go slowly`() {
        val filter = TiltFilter()
        repeat(200) { filter.step(0f, 9.8f) }
        // The hand tips the phone: the fast average jumps, the resting pose lags behind.
        var tilt = filter.step(2.6f, 9.8f)
        repeat(6) { tilt = filter.step(2.6f, 9.8f) }
        assertTrue("a sharp lean read as nothing (${tilt.x})", tilt.x > 0.4f)

        // Held there, the lean slowly becomes the new resting pose.
        repeat(600) { tilt = filter.step(2.6f, 9.8f) }
        assertEquals("a held lean never became the resting pose", 0f, tilt.x, 0.05f)
    }

    @Test
    fun `the tilt is clamped to its box`() {
        val filter = TiltFilter()
        repeat(200) { filter.step(0f, 9.8f) }
        val tilt = filter.step(60f, 9.8f)
        assertTrue("an impossible reading escaped the clamp", tilt.x in -1f..1f)
    }

    @Test
    fun `hard rain taps faster than a drizzle, and never keeps a beat`() {
        fun mean(intensity: Float): Double =
            (0 until 40).map { tapGap(it, intensity) }.average()
        assertTrue(
            "a downpour does not tap faster than a drizzle",
            mean(1f) < mean(0.15f) * 0.55,
        )
        // Irregular: forty gaps of hard rain are not forty of the same number.
        val gaps = (0 until 40).map { tapGap(it, 1f) }.toSet()
        assertTrue("the rain keeps a metronome's beat (${gaps.size} distinct gaps)", gaps.size > 20)
        assertTrue("a tap came faster than the floor", (0 until 200).all { tapGap(it, 1f) >= 100L })
    }

    /** Snow's metre is seconds where rain's is fractions of one: it lands, it does not hit. */
    @Test
    fun `snow falls on the hand far more slowly than rain`() {
        val rain = (0 until 40).map { tapGap(it, 0.5f) }.average()
        val snow = (0 until 40).map { snowGap(it, 0.5f) }.average()
        assertTrue("snow taps like rain ($snow ms vs $rain ms)", snow > rain * 3)
        assertTrue(
            "a flake landed faster than the floor",
            (0 until 200).all { snowGap(it, 1f) >= 400L },
        )
    }

    /** Sleet is taps with the odd harder knock — the ice — and the same ones every time. */
    @Test
    fun `sleet knocks the odd pellet among the taps`() {
        val touches = (0 until 60).map { fallTouch(Sky.SLEET, it) }
        val knocks = touches.count { it == HapticFeedbackType.SegmentTick }
        val taps = touches.count { it == HapticFeedbackType.SegmentFrequentTick }
        assertTrue("sleet never knocked", knocks > 0)
        assertTrue("sleet was all knocks ($knocks of ${touches.size})", taps > knocks)
        assertEquals(
            "the same sleet knocked a different rhythm",
            touches,
            (0 until 60).map { fallTouch(Sky.SLEET, it) },
        )
        // And the neighbours keep their own touch: snow soft, rain light.
        assertEquals(HapticFeedbackType.TextHandleMove, fallTouch(Sky.SNOW, 7))
        assertEquals(HapticFeedbackType.SegmentFrequentTick, fallTouch(Sky.RAIN, 7))
    }

    /** The lap knows when it crossed a strike, including over the seam of the loop. */
    @Test
    fun `a strike is a crossing of the lap, not a schedule of its own`() {
        assertTrue("the first strike was missed", struck(0.10f, 0.30f))
        assertTrue("the second strike was missed", struck(0.60f, 0.70f))
        assertTrue("a strike fired between the strikes", !struck(0.30f, 0.60f))
        assertTrue("the seam hid a strike", struck(0.95f, 0.25f))
        assertTrue("the tail of the lap struck from nothing", !struck(0.70f, 0.95f))
        assertTrue("a clock standing still struck", !struck(0.40f, 0.40f))
    }
}
