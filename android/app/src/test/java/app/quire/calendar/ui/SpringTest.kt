package app.quire.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringTest {

    private fun run(spring: Spring, seconds: Float = 4f, step: Float = 1f / 60f): Int {
        var frames = 0
        var elapsed = 0f
        while (elapsed < seconds && spring.advance(step)) {
            elapsed += step
            frames++
        }
        return frames
    }

    @Test
    fun `every profile reaches its target and stops`() {
        MotionProfile.entries.forEach { profile ->
            val spring = Spring(0f, 0f).apply { this.profile(profile); target = 1f }
            run(spring)
            assertEquals(profile.name, 1f, spring.value, 0.001f)
            assertTrue(profile.name, spring.atRest)
            assertTrue(profile.name, abs(spring.velocity) < 0.01f)
        }
    }

    @Test
    fun `critical damping does not overshoot and springy damping does`() {
        val calm = Spring(0f, 0f).apply { profile(MotionProfile.CALM); target = 1f }
        var calmPeak = 0f
        repeat(300) { calm.advance(1f / 60f); calmPeak = maxOf(calmPeak, calm.value) }
        assertTrue("calm peaked at $calmPeak", calmPeak <= 1.005f)

        val playful = Spring(0f, 0f).apply { profile(MotionProfile.PLAYFUL); target = 1f }
        var playfulPeak = 0f
        repeat(300) { playful.advance(1f / 60f); playfulPeak = maxOf(playfulPeak, playful.value) }
        assertTrue("playful peaked at $playfulPeak", playfulPeak > 1.03f)
    }

    @Test
    fun `a stiff spring survives a dropped frame instead of diverging`() {
        val spring = Spring(0f, 0f).apply { profile(MotionProfile.PLAYFUL); target = 1f }
        // Half a second in one step: the substepping has to absorb this.
        repeat(20) { spring.advance(0.5f) }
        assertTrue("value ${spring.value}", spring.value.isFinite())
        assertEquals(1f, spring.value, 0.02f)
    }

    @Test
    fun `retargeting mid-flight keeps the velocity it already had`() {
        val spring = Spring(0f, 0f).apply { profile(MotionProfile.STANDARD); target = 1f }
        repeat(8) { spring.advance(1f / 60f) }
        val carried = spring.velocity
        assertTrue("should be moving", carried > 0.1f)
        spring.target = 0.4f
        assertEquals("velocity survives the new target", carried, spring.velocity, 0.0001f)
        run(spring)
        assertEquals(0.4f, spring.value, 0.001f)
    }

    @Test
    fun `off is instant by contract`() {
        assertTrue(MotionProfile.OFF.instant)
        assertEquals(0L, MotionProfile.OFF.staggerMillis)
        MotionProfile.entries.filter { !it.instant }.forEach {
            assertTrue(it.name, it.staggerMillis > 0L)
        }
    }

    @Test
    fun `interpolation helpers stay in range`() {
        assertEquals(5f, lerp(0f, 10f, 0.5f), 0.0001f)
        assertEquals(0f, smoothstep(2f, 4f, 1f), 0.0001f)
        assertEquals(1f, smoothstep(2f, 4f, 9f), 0.0001f)
        assertEquals(0.5f, smoothstep(0f, 1f, 0.5f), 0.0001f)
    }
}
