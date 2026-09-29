package app.opal.core.tunnel.session

import app.opal.core.tunnel.session.RestrictedNetwork.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedNetworkTest {
    private val hint = RestrictedNetwork()
    private var time = START
    private var progressAt = time

    /** Ticks once a second for [seconds]; returns the changes with their second. */
    private fun run(seconds: Int, validated: Boolean = false): List<Pair<Long, Change>> =
        (1..seconds).mapNotNull {
            time += 1_000
            hint.tick(time, validated, progressAt)?.let { (time - START) / 1_000 to it }
        }

    @Test
    fun `a validated network never gets the hint`() {
        assertEquals(emptyList<Pair<Long, Change>>(), run(30 * 60, validated = true))
        assertFalse(hint.shown)
    }

    /** Only a hint: nothing else happens however long it lasts (1.0.5 paused Tor here). */
    @Test
    fun `no progress without internet shows the hint once`() {
        assertEquals(listOf(60L to Change.Show), run(30 * 60))
        assertTrue(hint.shown)
    }

    @Test
    fun `progress or a validated network takes it down`() {
        run(61)
        progressAt = time
        assertEquals(listOf(62L to Change.Hide), run(1))
        assertEquals(listOf(121L to Change.Show), run(60))
        assertEquals(listOf(123L to Change.Hide), run(1, validated = true))
    }

    @Test
    fun `reset reports whether the hint was shown`() {
        assertFalse(hint.reset())
        run(61)
        assertTrue(hint.reset())
        assertFalse(hint.shown)
        // The clock is the caller's: with no progress since, the next tick shows it again.
        assertEquals(listOf(62L to Change.Show), run(1))
    }

    private companion object {
        const val START = 1_000_000L
    }
}
