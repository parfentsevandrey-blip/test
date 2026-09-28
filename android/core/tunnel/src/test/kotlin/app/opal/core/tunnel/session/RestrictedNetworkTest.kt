package app.opal.core.tunnel.session

import app.opal.core.tunnel.session.RestrictedNetwork.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedNetworkTest {
    private val guard = RestrictedNetwork()
    private var time = 1_000_000L
    private var progressAt = time

    /** Ticks once a second for [seconds]; returns the actions that were not [Action.None]. */
    private fun run(seconds: Int, validated: Boolean = false): List<Pair<Long, Action>> =
        (1..seconds).mapNotNull {
            time += 1_000
            guard
                .tick(time, validated, progressAt)
                .takeIf { it != Action.None }
                ?.let {
                    (time - START) / 1_000 to it
                }
        }

    @Test
    fun `a validated network is never restricted`() {
        assertEquals(emptyList<Pair<Long, Action>>(), run(30 * 60, validated = true))
        assertFalse(guard.active)
    }

    @Test
    fun `no progress without internet - hint, pause, then sparse tries`() {
        val actions = run(15 * 60)
        assertEquals(
            listOf(
                45L to Action.ShowHint,
                180L to Action.Pause,
                480L to Action.Resume,
                570L to Action.Pause,
                870L to Action.Resume,
            ),
            actions,
        )
        assertTrue(guard.active)
    }

    @Test
    fun `progress during a try keeps Tor running`() {
        run(8 * 60) // paused at 180 s, trying again at 480 s
        progressAt = time + 10_000
        val actions = run(120)
        // No pause at the end of the try: Tor gets somewhere, whatever Android says.
        assertEquals(emptyList<Pair<Long, Action>>(), actions)
        assertFalse(guard.paused)
    }

    @Test
    fun `the network working again ends it at once`() {
        run(60)
        assertEquals(listOf(61L to Action.Recovered), run(1, validated = true))
        assertFalse(guard.active)
        progressAt = time
        run(5 * 60)
        assertTrue(guard.paused)
        assertEquals(listOf(362L to Action.RecoveredFromPause), run(1, validated = true))
    }

    @Test
    fun `reset reports whether Tor was paused`() {
        run(60)
        assertFalse(guard.reset())
        progressAt = time
        run(4 * 60)
        assertTrue(guard.reset())
        assertFalse(guard.active)
    }

    private companion object {
        const val START = 1_000_000L
    }
}
