package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.tor.OrConnStatus
import app.opal.core.model.tor.RelayRef
import app.opal.core.model.tor.TorEvent

/**
 * Figures out which configured bridge actually carries traffic, from ORCONN events: a connection is
 * LAUNCHED to `address:port` and later reported CONNECTED as `$FINGERPRINT~nick` with the same ID.
 * That maps fingerprints to addresses even for bridge lines without a fingerprint (meek).
 */
internal class BridgeUsageTracker {
    private val targetById = HashMap<String, String>()
    private val addressByFingerprint = HashMap<String, String>()
    private val connected = ArrayList<String>()
    private val failed = LinkedHashSet<String>()

    fun onOrConn(event: TorEvent.OrConn) {
        val id = event.connectionId
        val target = event.target
        if (target.startsWith("$")) {
            val fp = RelayRef.parse(target).fingerprint
            id?.let { targetById[it] }
                ?.takeIf { !it.startsWith("$") }
                ?.let { addressByFingerprint[fp] = it }
        }
        if (id != null && !target.startsWith("$")) targetById[id] = target
        when (event.status) {
            OrConnStatus.CONNECTED -> connected += target
            OrConnStatus.FAILED -> failed += (id?.let { targetById[it] } ?: target)
            else -> Unit
        }
    }

    /** The bridge used as the first hop by the circuit whose guard is [guardFingerprint]. */
    fun bridgeFor(guardFingerprint: String?, bridges: List<BridgeLine>): BridgeLine? {
        if (guardFingerprint != null) {
            bridges
                .firstOrNull { it.fingerprint == guardFingerprint }
                ?.let {
                    return it
                }
            addressByFingerprint[guardFingerprint]?.let { address ->
                bridges
                    .firstOrNull { it.address == address }
                    ?.let {
                        return it
                    }
            }
        }
        for (target in connected) {
            match(target, bridges)?.let {
                return it
            }
        }
        return null
    }

    /** Bridges that failed to connect during this session (for statistics). */
    fun failedBridges(bridges: List<BridgeLine>): List<BridgeLine> =
        failed.mapNotNull { match(it, bridges) }.distinct()

    fun reset() {
        targetById.clear()
        addressByFingerprint.clear()
        connected.clear()
        failed.clear()
    }

    private fun match(target: String, bridges: List<BridgeLine>): BridgeLine? =
        if (target.startsWith("$")) {
            val fp = RelayRef.parse(target).fingerprint
            bridges.firstOrNull { it.fingerprint == fp }
                ?: addressByFingerprint[fp]?.let { a -> bridges.firstOrNull { it.address == a } }
        } else {
            bridges.firstOrNull { it.address == target }
        }
}
