package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.tor.CircuitStatus
import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorEventParser
import app.opal.core.model.tunnel.CircuitHop
import app.opal.core.model.tunnel.CircuitInfo
import app.opal.core.model.tunnel.HopRole
import app.opal.core.tunnel.tor.TorEngine
import app.opal.core.tunnel.util.LogBuffer

/**
 * Describes the circuit currently used by the user's traffic — bridge → middle → exit with
 * countries and the exit address — from Tor's own data (circuit-status, consensus, GeoIP), never
 * from external "what is my IP" services.
 */
internal class CircuitInspector(private val engine: TorEngine, private val log: LogBuffer? = null) {

    suspend fun inspect(
        bridges: List<BridgeLine>,
        preferredCircuitId: String?,
        geoIpLoaded: Boolean,
    ): CircuitInfo? {
        val status = engine.getInfo("circuit-status")["circuit-status"].orEmpty()
        val built =
            status
                .lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { TorEventParser.parse("CIRC", it) as? TorEvent.Circuit }
                .filter { it.status == CircuitStatus.BUILT }
                .toList()
        val circuits =
            built
                .filter { it.path.size >= 2 }
                .filter { it.purpose == null || it.purpose in USER_TRAFFIC_PURPOSES }
                .filterNot { "ONEHOP_TUNNEL" in it.buildFlags || "IS_INTERNAL" in it.buildFlags }
        val circuit =
            circuits.firstOrNull { it.id == preferredCircuitId }
                ?: circuits.firstOrNull()
                ?: run {
                    // Purposes and counts only: no relays, no addresses.
                    val purposes =
                        built
                            .groupingBy { it.purpose ?: "?" }
                            .eachCount()
                            .entries
                            .joinToString {
                                "${it.key}×${it.value}"
                            }
                    log?.d(TAG, "No circuit for traffic yet; built: ${purposes.ifEmpty { "none" }}")
                    return null
                }
        val hops =
            circuit.path.mapIndexed { index, relay ->
                val last = index == circuit.path.lastIndex
                if (index == 0) {
                    val bridge = bridges.firstOrNull { it.fingerprint == relay.fingerprint }
                    // Fronted transports use placeholder addresses (192.0.2.0/24): no country for
                    // those.
                    val real = bridge?.takeIf {
                        it.transport != TransportKind.Snowflake &&
                            it.transport != TransportKind.Meek
                    }
                    CircuitHop(
                        role = if (bridges.isEmpty()) HopRole.Guard else HopRole.Bridge,
                        nickname = relay.nickname,
                        country = if (geoIpLoaded) real?.host?.let { country(it) } else null,
                    )
                } else {
                    val address = relayAddress(relay.fingerprint)
                    CircuitHop(
                        role = if (last) HopRole.Exit else HopRole.Middle,
                        nickname = relay.nickname,
                        country = if (geoIpLoaded) address?.let { country(it) } else null,
                        address = if (last) address else null,
                    )
                }
            }
        return CircuitInfo(hops)
    }

    /**
     * IPv4 address of a relay from its consensus entry (`r nick id digest date time IP ORPort
     * DirPort`).
     */
    private suspend fun relayAddress(fingerprint: String): String? {
        val entry =
            runCatching { engine.getInfo("ns/id/$fingerprint")["ns/id/$fingerprint"] }.getOrNull()
                ?: return null
        val r = entry.lineSequence().firstOrNull { it.startsWith("r ") } ?: return null
        return r.split(' ').getOrNull(6)
    }

    private suspend fun country(address: String): String? = runCatching {
        engine.getInfo("ip-to-country/$address")["ip-to-country/$address"]
    }
        .getOrNull()
        ?.lowercase()
        ?.takeIf { it.length == 2 && it != "??" }

    private companion object {
        const val TAG = "circuit"

        /**
         * Circuits that carry the user's streams. Tor 0.4.8+ puts general streams on a linked
         * conflux set first (`circuit_get_best`), plain general circuits only when none fits, so
         * the stream's circuit is usually `CONFLUX_LINKED`. Legs still being linked
         * (`CONFLUX_UNLINKED`) carry nothing yet.
         */
        val USER_TRAFFIC_PURPOSES = setOf("GENERAL", "CONFLUX_LINKED")
    }
}
