package app.opal.core.tunnel.session

import app.opal.core.model.tor.CircuitStatus
import app.opal.core.model.tor.StreamStatus
import app.opal.core.model.tor.TorEvent

/**
 * Detects a connection that is alive but useless, using Tor's own events only (no probes to third
 * party services). The typical case is DPI "freezing" a TCP session to a bridge after its first
 * ~16–20 KB: nothing errors, data just stops. Signals:
 * - streams waiting for a connection while no byte has been read for [freezeMillis];
 * - circuits failing to build with none succeeding;
 * - streams timing out with none succeeding.
 */
internal class Watchdog(
    private val now: () -> Long,
    /** Long enough for Tor's own stream retries over a slow first hop (Snowflake, meek). */
    private val freezeMillis: Long = 45_000,
    private val windowMillis: Long = 60_000,
) {
    enum class Stall {
        Frozen,
        CircuitsFailing,
        StreamsTimingOut,
    }

    private val pendingStreams = HashMap<String, Long>()
    private val circuitFailures = ArrayDeque<Long>()
    private val streamTimeouts = ArrayDeque<Long>()
    private var lastCircuitBuiltAt = 0L
    private var lastStreamSucceededAt = 0L
    private var lastReadAt = 0L
    private var armedAt = 0L

    /** Starts (or restarts) observation; earlier events are forgotten. */
    fun arm() {
        pendingStreams.clear()
        circuitFailures.clear()
        streamTimeouts.clear()
        val t = now()
        armedAt = t
        lastReadAt = t
        lastCircuitBuiltAt = t
        lastStreamSucceededAt = t
    }

    fun onEvent(event: TorEvent) {
        val t = now()
        when (event) {
            is TorEvent.Bandwidth -> if (event.read > 0) lastReadAt = t
            is TorEvent.Stream ->
                when (event.status) {
                    StreamStatus.NEW,
                    StreamStatus.SENTCONNECT,
                    StreamStatus.SENTRESOLVE -> pendingStreams.putIfAbsent(event.id, t)
                    StreamStatus.SUCCEEDED -> {
                        pendingStreams.remove(event.id)
                        lastStreamSucceededAt = t
                    }
                    StreamStatus.FAILED -> {
                        pendingStreams.remove(event.id)
                        if (event.reason == "TIMEOUT" || event.remoteReason == "TIMEOUT")
                            streamTimeouts.addLast(t)
                    }
                    StreamStatus.CLOSED -> pendingStreams.remove(event.id)
                    else -> Unit
                }
            is TorEvent.Circuit ->
                when (event.status) {
                    CircuitStatus.BUILT -> lastCircuitBuiltAt = t
                    CircuitStatus.FAILED ->
                        if (event.reason != "FINISHED") circuitFailures.addLast(t)
                    else -> Unit
                }
            else -> Unit
        }
    }

    fun evaluate(): Stall? {
        val t = now()
        prune(circuitFailures, t)
        prune(streamTimeouts, t)
        val oldestPending = pendingStreams.values.minOrNull()
        return when {
            oldestPending != null &&
                t - oldestPending >= freezeMillis &&
                t - lastReadAt >= freezeMillis -> Stall.Frozen
            circuitFailures.size >= 4 && t - lastCircuitBuiltAt >= windowMillis ->
                Stall.CircuitsFailing
            streamTimeouts.size >= 5 && t - lastStreamSucceededAt >= windowMillis / 2 ->
                Stall.StreamsTimingOut
            else -> null
        }
    }

    private fun prune(times: ArrayDeque<Long>, t: Long) {
        while (times.isNotEmpty() && t - times.first() > windowMillis) times.removeFirst()
    }
}
