package app.quire.weather

import app.quire.weather.ui.TiltFilter
import app.quire.weather.ui.tapGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand's arithmetic: what the accelerometer becomes before the camera sees it, and the
 * rhythm the rain taps in. Both are pure functions, tested as such — the sensor plumbing and
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
}
