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
    /**
     * Silence that outlasts Snowflake's replacement of a silent proxy (about 20 s to notice, up to
     * ~45 s until the next proxy carries data), see [deadSession].
     */
    private val deadAfterMillis: Long = 60_000,
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
    private var lastReportAt = 0L
    private var armedAt = 0L

    private var lastTickAt = 0L

    /**
     * Called every [tickMillis]. A tick that comes much later means the process was not running
     * (Doze, a suspended device) or the clock jumped: that time says nothing about the connection,
     * so observation starts over. Returns false then.
     */
    fun onTick(tickMillis: Long): Boolean {
        val t = now()
        val asleep = lastTickAt != 0L && t - lastTickAt > tickMillis + ASLEEP_SLACK_MILLIS
        lastTickAt = t
        if (asleep) arm()
        return !asleep
    }

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
            is TorEvent.Bandwidth -> {
                // Tor reports every second while its main loop runs. A long gap means Tor itself
                // was busy (loading GeoIP or its cache on a slow device): that time says nothing
                // about the network, so observation starts over.
                if (lastReportAt != 0L && t - lastReportAt > TOR_BUSY_MILLIS) arm()
                lastReportAt = t
                if (event.read > 0) lastReadAt = t
            }
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

    /**
     * A stall during which Tor read nothing at all for [deadAfterMillis]. A bridge pads an idle
     * connection that carries user traffic (Tor's defaults: every 1.5–9.5 s, 9–14 s with reduced
     * padding), and Snowflake has long replaced a silent proxy by then: the session behind the
     * proxies is gone. Only while Tor keeps reporting: silence from a busy Tor is not the
     * network's.
     */
    fun deadSession(): Stall? =
        evaluate()?.takeIf { torReporting() && silentMillis() >= deadAfterMillis }

    /** Milliseconds since Tor last read a byte from the network (counted from [arm] at most). */
    fun silentMillis(): Long = now() - lastReadAt

    private fun torReporting(): Boolean =
        lastReportAt != 0L && now() - lastReportAt <= TOR_BUSY_MILLIS

    private fun prune(times: ArrayDeque<Long>, t: Long) {
        while (times.isNotEmpty() && t - times.first() > windowMillis) times.removeFirst()
    }
}

/** A tick this much later than due means the process was asleep (see [Watchdog.onTick]). */
private const val ASLEEP_SLACK_MILLIS = 30_000L

/** No bandwidth report from Tor for this long: Tor's main loop is busy (see [Watchdog.onEvent]). */
private const val TOR_BUSY_MILLIS = 10_000L
