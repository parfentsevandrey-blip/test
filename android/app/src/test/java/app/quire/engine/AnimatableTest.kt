package app.quire.engine

import app.quire.engine.anim.Decay
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.Timeline
import app.quire.engine.anim.Track
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.anim.smoothstep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The integrators everything else in the app rests on.
 *
 * These are plain JVM tests with no Android in them, which is the point: the whole reason this
 * app carries its own animation engine is that the platform's is switched off by a system
 * setting, so the one thing that must never depend on a device is the maths.
 */
class AnimatableTest {

    private fun settle(spring: Spring, seconds: Float = 6f, step: Float = 1f / 60f): Int {
        var frames = 0
        var elapsed = 0f
        while (elapsed < seconds && spring.advance(step)) {
            elapsed += step
            frames++
        }
        return frames
    }

    @Test
    fun `every profile reaches its target and then stops asking for frames`() {
        MotionProfile.entries.forEach { profile ->
            val spring = Spring(0f).apply {
                profile(profile)
                target = 1f
            }
            settle(spring)
            assertEquals(profile.name, 1f, spring.value, 0.001f)
            assertTrue("${profile.name} never came to rest", spring.atRest)
            assertTrue("${profile.name} kept a velocity", abs(spring.velocity) < 0.01f)
        }
    }

    @Test
    fun `off arrives at once and the live profiles take real time`() {
        assertTrue(MotionProfile.OFF.instant)
        assertEquals(0f, MotionProfile.OFF.staggerSeconds, 0f)

        val off = Spring(0f).apply {
            profile(MotionProfile.OFF)
            target = 1f
        }
        // "Off" is allowed to be a very stiff spring rather than a literal assignment, but it
        // has to be over within a couple of frames or it is not off.
        assertTrue("Off took ${settle(off)} frames", settle(off) <= 3)

        MotionProfile.entries.filter { !it.instant }.forEach { profile ->
            assertTrue("${profile.name} has no stagger", profile.staggerSeconds > 0f)
            val spring = Spring(0f).apply {
                profile(profile)
                target = 1f
            }
            assertTrue("${profile.name} arrived instantly", settle(spring) > 6)
        }
    }

    @Test
    fun `damping decides whether a spring overshoots`() {
        val calm = Spring(0f).apply {
            profile(MotionProfile.CALM)
            target = 1f
        }
        var calmPeak = 0f
        repeat(400) {
            calm.advance(1f / 60f)
            calmPeak = maxOf(calmPeak, calm.value)
        }
        assertTrue("calm peaked at $calmPeak", calmPeak <= 1.005f)

        val playful = Spring(0f).apply {
            profile(MotionProfile.PLAYFUL)
            target = 1f
        }
        var playfulPeak = 0f
        repeat(400) {
            playful.advance(1f / 60f)
            playfulPeak = maxOf(playfulPeak, playful.value)
        }
        assertTrue("playful peaked at $playfulPeak", playfulPeak > 1.03f)
    }

    @Test
    fun `a stiff spring survives a dropped frame instead of diverging`() {
        val spring = Spring(0f).apply {
            profile(MotionProfile.PLAYFUL)
            target = 1f
        }
        // Half a second delivered as one step. An unsubstepped integrator at this stiffness goes
        // to infinity here; the 4 ms substep is what absorbs it.
        repeat(20) { spring.advance(0.5f) }
        assertTrue("value ${spring.value} is not finite", spring.value.isFinite())
        assertEquals(1f, spring.value, 0.02f)
    }

    @Test
    fun `retargeting mid-flight keeps the velocity it already had`() {
        val spring = Spring(0f).apply {
            profile(MotionProfile.STANDARD)
            target = 1f
        }
        repeat(8) { spring.advance(1f / 60f) }
        val carried = spring.velocity
        assertTrue("the spring should be moving by now", carried > 0.1f)

        // This is the whole reason the app uses springs rather than durations: a flick that
        // changes its mind mid-flight has to continue, not restart from zero.
        spring.target = 0.4f
        assertEquals("velocity did not survive the new target", carried, spring.velocity, 0.0001f)
        settle(spring)
        assertEquals(0.4f, spring.value, 0.001f)
    }

    @Test
    fun `snapping puts a spring exactly where it was told, with no motion left`() {
        val spring = Spring(0f).apply {
            profile(MotionProfile.STANDARD)
            target = 1f
        }
        repeat(10) { spring.advance(1f / 60f) }
        spring.snapTo(0.25f)
        assertEquals(0.25f, spring.value, 0f)
        assertEquals(0f, spring.velocity, 0f)
        assertTrue("a snapped spring is not at rest", spring.atRest)
    }

    @Test
    fun `a decay slows to a stop and honours its bounds`() {
        val fling = Decay(0f).apply { velocity = 4000f }
        var elapsed = 0f
        while (elapsed < 10f && fling.advance(1f / 60f)) elapsed += 1f / 60f
        assertTrue("a free fling never stopped", elapsed < 10f)
        assertTrue("a free fling did not travel", fling.value > 100f)

        val bounded = Decay(0f).apply {
            min = 0f
            max = 50f
            velocity = 4000f
        }
        elapsed = 0f
        while (elapsed < 10f && bounded.advance(1f / 60f)) elapsed += 1f / 60f
        assertTrue("a bounded fling escaped to ${bounded.value}", bounded.value <= 50.001f)
        assertTrue("a bounded fling went backwards", bounded.value >= -0.001f)
    }

    @Test
    fun `a track walks its keyframes and holds the ends`() {
        val track = Track(0f to 0f, 0.5f to 10f, 1f to 4f)
        assertEquals(1f, track.duration, 0.0001f)

        track.seek(0f)
        assertEquals(0f, track.value, 0.0001f)
        track.seek(0.25f)
        assertEquals("halfway into the first leg", 5f, track.value, 0.0001f)
        track.seek(0.5f)
        assertEquals(10f, track.value, 0.0001f)
        track.seek(0.75f)
        assertEquals("halfway into the second leg", 7f, track.value, 0.0001f)

        // Past the end it holds the last key rather than wrapping or running off.
        track.seek(9f)
        assertEquals(4f, track.value, 0.0001f)
        track.seek(-9f)
        assertEquals(0f, track.value, 0.0001f)
    }

    @Test
    fun `a timeline is finished only once its last delayed member is`() {
        val early = Spring(0f).apply {
            profile(MotionProfile.STANDARD)
            target = 1f
        }
        val late = Spring(0f).apply {
            profile(MotionProfile.STANDARD)
            target = 1f
        }
        val timeline = Timeline().add(early).add(late, delaySeconds = 0.5f)

        var elapsed = 0f
        var running = true
        while (elapsed < 8f && running) {
            running = timeline.advance(1f / 60f)
            elapsed += 1f / 60f
            if (elapsed < 0.4f) {
                assertEquals("the delayed member started early", 0f, late.value, 0.0001f)
            }
        }
        assertTrue("the timeline never finished", elapsed < 8f)
        assertEquals(1f, early.value, 0.001f)
        assertEquals(1f, late.value, 0.001f)
    }

    @Test
    fun `the interpolation helpers stay in range`() {
        assertEquals(5f, lerp(0f, 10f, 0.5f), 0.0001f)
        assertEquals(0f, smoothstep(2f, 4f, 1f), 0.0001f)
        assertEquals(1f, smoothstep(2f, 4f, 9f), 0.0001f)
        assertEquals(0.5f, smoothstep(0f, 1f, 0.5f), 0.0001f)
        assertEquals(3f, clamp(9f, 1f, 3f), 0.0001f)
        assertEquals(1f, clamp(-9f, 1f, 3f), 0.0001f)

        // A zero-width window is a real case — a stagger with no gap in it — and must pick a
        // side rather than divide by zero.
        assertTrue(smoothstep(2f, 2f, 3f).isFinite())
        assertTrue(smoothstep(2f, 2f, 1f).isFinite())
    }
}
