package app.opal.core.tunnel.session

import app.opal.core.model.tor.CircuitStatus
import app.opal.core.model.tor.StreamStatus
import app.opal.core.model.tor.TorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchdogTest {
    private var time = 1_000_000L
    private val dog = Watchdog(now = { time }).also { it.arm() }

    private fun stream(id: String, status: StreamStatus, reason: String? = null) =
        TorEvent.Stream(id, status, "5", "example.com:443", reason, null)

    private fun circuit(status: CircuitStatus, reason: String? = null) =
        TorEvent.Circuit("7", status, emptyList(), "GENERAL", emptySet(), reason, null)

    @Test
    fun `healthy traffic is not a stall`() {
        repeat(30) {
            time += 1_000
            dog.onEvent(stream("s$it", StreamStatus.NEW))
            dog.onEvent(TorEvent.Bandwidth(4_000, 500))
            dog.onEvent(stream("s$it", StreamStatus.SUCCEEDED))
        }
        assertNull(dog.evaluate())
    }

    @Test
    fun `waiting streams with no bytes read is a freeze`() {
        dog.onEvent(TorEvent.Bandwidth(100_000, 1_000))
        dog.onEvent(stream("1", StreamStatus.SENTCONNECT))
        time += 19_000
        assertNull(dog.evaluate())
        time += 2_000
        assertEquals(Watchdog.Stall.Frozen, dog.evaluate())
    }

    @Test
    fun `idle connection without demand is not a freeze`() {
        time += 120_000
        assertNull(dog.evaluate())
    }

    @Test
    fun `repeated circuit failures without success`() {
        time += 61_000
        repeat(4) {
            time += 1_000
            dog.onEvent(circuit(CircuitStatus.FAILED, "TIMEOUT"))
        }
        assertEquals(Watchdog.Stall.CircuitsFailing, dog.evaluate())
    }

    @Test
    fun `stream timeouts without success`() {
        time += 31_000
        repeat(5) {
            time += 500
            dog.onEvent(stream("t$it", StreamStatus.FAILED, "TIMEOUT"))
        }
        assertEquals(Watchdog.Stall.StreamsTimingOut, dog.evaluate())
    }

    @Test
    fun `old failures fall out of the window`() {
        repeat(5) { dog.onEvent(circuit(CircuitStatus.FAILED, "TIMEOUT")) }
        time += 120_000
        assertNull(dog.evaluate())
    }
}
