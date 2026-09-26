package app.opal.core.designsystem.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltFilterTest {

    private fun TiltFilter.feed(x: Float, y: Float, times: Int = 60) =
        repeat(times) { onGravity(x, y) }

    @Test
    fun `upright keeps the designed light`() {
        val filter = TiltFilter()
        filter.feed(0f, 9.81f)
        assertEquals(DEFAULT_LIGHT_ANGLE, filter.angle, 0.01f)
    }

    @Test
    fun `turning the phone turns the light the other way`() {
        val filter = TiltFilter()
        // Landscape, left edge down: "up" points along +x in device axes.
        filter.feed(9.81f, 0f)
        assertEquals(DEFAULT_LIGHT_ANGLE - 90f, filter.angle, 1.5f)
    }

    @Test
    fun `a single reading only nudges the light`() {
        val filter = TiltFilter()
        filter.onGravity(9.81f, 0f)
        assertTrue(filter.angle < DEFAULT_LIGHT_ANGLE)
        assertTrue(filter.angle > DEFAULT_LIGHT_ANGLE - 45f)
    }

    @Test
    fun `lying flat carries no direction`() {
        val filter = TiltFilter()
        filter.feed(9.81f, 0f)
        val tilted = filter.angle
        // Face up on a table: gravity is along z, almost nothing within the screen plane.
        repeat(60) { assertFalse(filter.onGravity(0.4f, -0.3f)) }
        assertEquals(tilted, filter.angle, 0f)
    }
}
