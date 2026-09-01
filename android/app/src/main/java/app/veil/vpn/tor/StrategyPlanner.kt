package app.veil.vpn.tor

import android.content.Context
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.BridgeRepository
import app.veil.vpn.data.EndpointCooldown
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.data.StrategyMemory
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.DtlsProfile
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.net.MoatClient
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.ProbeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One rung of the escalation ladder: a transport plus the bridges to try with it. */
data class Attempt(
    val transport: Transport,
    val bridges: List<BridgeLine>,
    val why: String,
    /** Snowflake only: rendezvous with the broker through an AMP cache. */
    val ampRendezvous: Boolean = false,
) {
    val label: String get() = if (ampRendezvous) "${transport.label} (AMP)" else transport.label
}

/**
 * Decides, in order, what to try.
 *
 * The ordering problem is the whole product. Trying the rungs in a fixed order
 * wastes minutes on a network where the answer was knowable in seconds, and
 * asking the user to choose only works for users who already know what obfs4
 * is. So four inputs are combined, strongest first:
 *
 *  1. What worked on *this* network before. Nothing beats a confirmed answer.
 *  2. What the Tor Project currently recommends for this country. This is the
 *     same data Tor Browser's "Connect Assist" uses, and it reflects blocking
 *     events days before an individual user could work them out.
 *  3. Live measurements of the network in front of us.
 *  4. A static fallback ladder, cheapest first, for when nothing else is known.
 *
 * The result always ends with Snowflake, because its data path is WebRTC over
 * UDP and therefore meets none of the TCP handshake heuristics at all, and a
 * variant of it that rendezvouses through an AMP cache is appended as a true
 * last resort.
 */
class StrategyPlanner(
    private val context: Context,
    private val bridges: BridgeRepository,
    private val memory: StrategyMemory,
    private val moat: MoatClient,
    private val cooldown: EndpointCooldown,
) {

    suspend fun plan(
        network: NetworkContext,
        probe: ProbeReport,
        probeRanking: List<Pair<Transport, Float>>,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): List<Attempt> = withContext(Dispatchers.Default) {
        val discouraged = memory.discouraged(network.fingerprint)
        val remembered = memory.preferredFor(network.fingerprint)
        val recommended = countryRecommendation(network.countryIso)

        val ordered = LinkedHashMap<Transport, String>()

        remembered?.let { ordered[it] = "worked on this network before" }
        recommended.forEach { transport ->
            ordered.putIfAbsent(transport, "recommended for ${network.countryIso?.uppercase()}")
        }
        probeRanking
            .filter { it.second >= 0.5f }
            .forEach { (transport, score) ->
                ordered.putIfAbsent(transport, "probe score ${"%.2f".format(score)}")
            }
        // Everything else still gets a turn, cheapest first, so a wrong guess
        // upstream never removes a working route from the ladder.
        Transport.entries.forEach { transport ->
            ordered.putIfAbsent(transport, "fallback")
        }

        // A network that accepts connections and then blackholes them has
        // already told us that anything terminating on a plain host is a waste
        // of a rung, whatever the rest of the evidence said.
        val demoted = if (probe.freezeSuspected) {
            setOf(Transport.DIRECT, Transport.OBFS4) + discouraged
        } else {
            discouraged
        }

        val attempts = ordered
            .toList()
            .sortedBy { (transport, _) -> if (transport in demoted) 1 else 0 }
            .mapNotNull { (transport, why) ->
                buildAttempt(transport, why, tlsProfile, dtlsProfile)
            }
            .toMutableList()

        // Snowflake over an AMP cache: slow, but it survives a censor that has
        // blocked the fronted broker request itself.
        attempts.firstOrNull { it.transport == Transport.SNOWFLAKE }?.let { snowflake ->
            attempts += snowflake.copy(
                why = "last resort: broker reached through an AMP cache",
                ampRendezvous = true,
            )
        }

        VeilLog.i("planner", attempts.joinToString(" -> ") { it.label })
        attempts
    }

    /** The ladder for a user who picked a transport by hand. */
    fun manualPlan(
        transport: Transport,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): List<Attempt> =
        listOfNotNull(buildAttempt(transport, "chosen by you", tlsProfile, dtlsProfile))

    private fun buildAttempt(
        transport: Transport,
        why: String,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): Attempt? {
        if (transport == Transport.DIRECT) return Attempt(transport, emptyList(), why)

        val candidates = bridges.forTransport(transport, bridgeBudget(transport) * 3)
            .filterNot { it.hasRoutableAddress && cooldown.isCoolingDown(it.host) }
            .take(bridgeBudget(transport))

        if (candidates.isEmpty()) {
            VeilLog.d("planner", "skipping ${transport.torName}: no usable bridges right now")
            return null
        }
        return Attempt(transport, candidates.map { shape(it, tlsProfile, dtlsProfile) }, why)
    }

    /**
     * Pins the Client Hello each transport presents.
     *
     * Bridge lines are published with whatever fingerprint was current when
     * they were written, and several of the built-in ones still ask for a
     * randomised hello. Randomisation defeats an exact-hash blocklist but is
     * itself anomalous to anything scoring plausibility, so the profile becomes
     * a local decision rather than an inherited one.
     */
    private fun shape(
        bridge: BridgeLine,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): BridgeLine = when (bridge.transportEnum) {
        Transport.MEEK, Transport.WEBTUNNEL ->
            bridge.withParams(mapOf("utls" to tlsProfile.lyrebirdName))

        Transport.SNOWFLAKE -> bridge.withParams(
            mapOf(
                "utls-imitate" to tlsProfile.snowflakeName,
                // Snowflake's data path is DTLS, which carries a fingerprint of
                // its own that the TLS setting above does not touch.
                "covertdtls-config" to dtlsProfile.argument,
            ),
        )

        // obfs4 is not TLS at all: there is no Client Hello to shape.
        else -> bridge
    }

    /**
     * How many bridges to hand tor for one attempt.
     *
     * More bridges is not better. tor opens a connection to each, and a fan of
     * near-simultaneous handshakes is one of the three things current DPI is
     * reported to look for. The broker-based transports need exactly one line,
     * because the line is configuration rather than an address.
     */
    private fun bridgeBudget(transport: Transport): Int = when (transport) {
        Transport.MEEK, Transport.SNOWFLAKE -> 1
        Transport.OBFS4, Transport.WEBTUNNEL -> 3
        Transport.DIRECT -> 0
    }

    /**
     * Asks the bridge API what works in this country, falling back to a snapshot
     * of the same data that ships with the app.
     */
    private suspend fun countryRecommendation(countryIso: String?): List<Transport> {
        if (countryIso.isNullOrBlank()) return emptyList()

        val live = runCatching { moat.settingsFor(countryIso) }.getOrNull()
        if (!live.isNullOrEmpty()) {
            return live.mapNotNull { Transport.fromTorName(it.transport) }
        }

        return runCatching {
            val json = context.assets.open(MAP_ASSET).bufferedReader().use { it.readText() }
            moat.parseMap(json, countryIso).mapNotNull { Transport.fromTorName(it.transport) }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val MAP_ASSET = "circumvention_map.json"

        /**
         * How long a rung gets before we move on.
         *
         * The numbers come from how each transport actually behaves: obfs4
         * either connects quickly or not at all, whereas Snowflake has to find
         * a volunteer proxy first and routinely needs half a minute before the
         * first byte moves. Cutting Snowflake off at obfs4's budget is the
         * classic way to conclude, wrongly, that nothing works.
         */
        fun budgetMillis(transport: Transport): Long = when (transport) {
            Transport.DIRECT -> 45_000
            Transport.OBFS4 -> 60_000
            Transport.WEBTUNNEL -> 75_000
            Transport.MEEK -> 90_000
            Transport.SNOWFLAKE -> 120_000
        }

        /** Abort early if bootstrap has not moved at all for this long. */
        const val STALL_MILLIS = 35_000L
    }
}

/** Convenience: the probe, wired to the bridges we actually plan to use. */
suspend fun NetworkProbe.runFor(
    bridges: BridgeRepository,
    onProgress: (Int, Int, String) -> Unit,
): ProbeReport = run(bridges.probeTargets(Transport.OBFS4), onProgress)
