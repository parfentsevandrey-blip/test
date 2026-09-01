package app.veil.vpn.net

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Paces outbound TLS handshakes so the app never looks like a burst.
 *
 * Russian DPI as documented in mid-2026 does not decide on any single feature.
 * The reported rule is a conjunction, and the third term is the one a client
 * can actually control: more than three TLS handshakes to the same server name
 * within a few hundred milliseconds is treated as a proxy opening a fan of
 * connections, and the answer is a blackhole of roughly two minutes. Worse,
 * changing fingerprint while that penalty is running is reported to extend it
 * to ten minutes for everything going to that host.
 *
 * So: at most two handshakes to one host at a time, and at least 450 ms between
 * the starts of any two. The numbers sit deliberately on the safe side of the
 * reported 3-connection / 350-400 ms threshold, which is a working hypothesis
 * from traffic observation rather than a published constant.
 *
 * This governs the app's own requests — probes and bridge-API calls. tor's own
 * connections are not routed through here; what protects those is that a
 * bridged client opens one or two connections to one guard and multiplexes
 * every circuit over them, which is the shape this heuristic is looking for the
 * absence of.
 */
object HandshakeGovernor {

    /** Comfortably under the reported 3-handshake trigger. */
    private const val MAX_CONCURRENT_PER_HOST = 2

    /** Comfortably over the reported 350-400 ms window. */
    private const val MIN_SPACING_MILLIS = 450L

    private val slots = ConcurrentHashMap<String, Semaphore>()
    private val spacing = ConcurrentHashMap<String, Mutex>()
    private val lastStart = ConcurrentHashMap<String, Long>()

    /**
     * Runs [block] once it is safe to start a handshake to [host]. Blocking
     * here is the point: the delay is cheaper than a two-minute freeze.
     */
    suspend fun <T> withSlot(host: String, block: suspend () -> T): T {
        val semaphore = slots.getOrPut(host) { Semaphore(MAX_CONCURRENT_PER_HOST) }
        semaphore.acquire()
        try {
            spacing.getOrPut(host) { Mutex() }.withLock {
                val since = System.currentTimeMillis() - (lastStart[host] ?: 0L)
                if (since < MIN_SPACING_MILLIS) delay(MIN_SPACING_MILLIS - since)
                lastStart[host] = System.currentTimeMillis()
            }
            return block()
        } finally {
            semaphore.release()
        }
    }

    /** Forgets pacing state, so a new session does not inherit old timings. */
    fun reset() {
        slots.clear()
        spacing.clear()
        lastStart.clear()
    }
}
