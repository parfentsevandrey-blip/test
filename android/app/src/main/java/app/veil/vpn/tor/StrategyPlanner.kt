package app.veil.vpn.tor

import android.content.Context
import app.veil.vpn.R
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.BridgeRepository
import app.veil.vpn.data.EndpointCooldown
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.data.StrategyMemory
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.DtlsProfile
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.net.CircumventionSetting
import app.veil.vpn.net.MoatClient
import app.veil.vpn.net.NatBehaviour
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.ProbeReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        /** Transports that actually have a listener tor can be pointed at. */
        available: Set<Transport>,
    ): List<Attempt> = withContext(Dispatchers.Default) {
        val discouraged = memory.discouraged(network.fingerprint)
        val remembered = memory.preferredFor(network.fingerprint)
        val recommended = applyCountryRecommendation(network.countryIso)

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
        val frozen = if (probe.freezeSuspected) {
            setOf(Transport.DIRECT, Transport.OBFS4)
        } else {
            emptySet()
        }
        // And a network that gives every destination a different public port —
        // which is what a mobile carrier's NAT normally does — has told us that
        // WebRTC will spend a long time failing. Snowflake stays on the ladder,
        // because a client behind such a NAT can still be matched with an
        // unrestricted volunteer, but it goes after the routes that do not care.
        val natBound = when (probe.natBehaviour) {
            NatBehaviour.SYMMETRIC, NatBehaviour.NO_UDP -> setOf(Transport.SNOWFLAKE)
            else -> emptySet()
        }
        val demoted = discouraged + frozen + natBound

        val attempts = ordered
            .map { (transport, why) ->
                if (transport in natBound) transport to context.getString(R.string.route_why_nat)
                else transport to why
            }
            // A bridge whose plugin never started would make tor stall on a
            // route it has no way to take.
            .filter { (transport, _) -> transport == Transport.DIRECT || transport in available }
            .sortedBy { (transport, _) -> if (transport in demoted) 1 else 0 }
            .mapNotNull { (transport, why) ->
                buildAttempt(transport, why, tlsProfile, dtlsProfile)
            }
            .toMutableList()

        // Snowflake over an AMP cache: slower, but it survives a censor that
        // has blocked the fronted broker request itself. It is a different set
        // of bridge lines rather than a different transport, so it costs
        // nothing but a `SETCONF`.
        if (Transport.SNOWFLAKE in available) ampSnowflakeAttempt()?.let { attempts += it }

        VeilLog.i("planner", attempts.joinToString(" -> ") { it.label })
        attempts
    }

    /** The ladder for a user who picked a transport by hand. */
    fun manualPlan(
        transport: Transport,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
        available: Set<Transport>,
    ): List<Attempt> {
        if (transport != Transport.DIRECT && transport !in available) {
            VeilLog.e("planner", "${transport.torName} has no listener; nothing to try")
            return emptyList()
        }
        return listOfNotNull(buildAttempt(transport, "chosen by you", tlsProfile, dtlsProfile))
    }

    private fun buildAttempt(
        transport: Transport,
        why: String,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): Attempt? {
        if (transport == Transport.DIRECT) return Attempt(transport, emptyList(), why)

        val candidates = bridges.forTransport(transport, bridgeBudget(transport) * 3)
            .filterNot { it.hasRoutableAddress && cooldown.isCoolingDown(it.host) }
            // Snowflake's AMP variant is a separate rung; keep it out of this one.
            .filterNot { transport == Transport.SNOWFLAKE && it.params.containsKey("ampcache") }
            .take(bridgeBudget(transport))

        if (candidates.isEmpty()) {
            VeilLog.d("planner", "skipping ${transport.torName}: no usable bridges right now")
            return null
        }
        return Attempt(transport, candidates.map { shape(it, tlsProfile, dtlsProfile) }, why)
    }

    /** The Snowflake lines that rendezvous through an AMP cache, if we have any. */
    private fun ampSnowflakeAttempt(): Attempt? {
        val amp = bridges.forTransport(Transport.SNOWFLAKE, limit = 6)
            .filter { it.params.containsKey("ampcache") }
        if (amp.isEmpty()) return null
        return Attempt(
            transport = Transport.SNOWFLAKE,
            bridges = amp.take(1),
            why = "last resort: broker reached through an AMP cache",
            ampRendezvous = true,
        )
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
    ): BridgeLine {
        val shaped = when (bridge.transportEnum) {
            Transport.MEEK, Transport.WEBTUNNEL ->
                bridge.withParams(mapOf("utls" to tlsProfile.lyrebirdName))

            Transport.SNOWFLAKE -> bridge.withParams(
                buildMap {
                    put("utls-imitate", tlsProfile.snowflakeName)
                    // Snowflake's data path is DTLS, which carries a
                    // fingerprint of its own that the TLS setting above does
                    // not touch.
                    put("covertdtls-config", dtlsProfile.argument)
                    // The published line names eight STUN servers, and WebRTC
                    // gathers candidates from all of them before it can offer
                    // anything to the broker — so every slow or dead one in
                    // that list is added to how long the user waits before
                    // Snowflake even starts looking for a proxy. Three is
                    // enough to determine the NAT behaviour Snowflake needs.
                    // They are still the Tor Project's own servers, kept in
                    // their order: that list is vetted for the mapping
                    // behaviour Snowflake depends on, so it is shortened rather
                    // than replaced.
                    shortenIce(bridge)?.let { put("ice", it) }
                },
            )

            // obfs4 is not TLS at all: there is no Client Hello to shape.
            else -> bridge
        }
        // Adding to a bridge line is not free. Everything past the fingerprint
        // is handed to the transport through the SOCKS5 authentication fields,
        // which hold 510 bytes between them, and tor rejects the whole line —
        // and with it the rung — rather than truncating.
        val fitted = shaped.withinSocksArgLimit()
        if (fitted.raw != shaped.raw) {
            VeilLog.w("planner", "trimmed ${bridge.transport} arguments to fit tor's SOCKS limit")
        }
        return fitted
    }

    /** The first few STUN servers from a Snowflake line, or null to leave it be. */
    private fun shortenIce(bridge: BridgeLine): String? {
        val servers = bridge.params["ice"]?.split(',')?.filter { it.isNotBlank() } ?: return null
        if (servers.size <= ICE_SERVERS) return null
        return servers.take(ICE_SERVERS).joinToString(",")
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
     * Asks the bridge API what works in this country — and keeps the bridges it
     * hands back, not just the names of the transports.
     *
     * Discarding those was a real bug rather than an omission. For a censored
     * country the API answers with working bridge lines, including ones for
     * transports the app ships none of, and including broker fronts far fresher
     * than anything compiled in. Keeping only the transport names meant the app
     * would decide that WebTunnel was the right answer and then skip it for
     * want of a single bridge it had just been given.
     *
     * The offline snapshot is consulted first because it is instant, and the
     * live call refines it. A connect should not wait on the network to find
     * out how to reach the network.
     */
    private suspend fun applyCountryRecommendation(countryIso: String?): List<Transport> {
        if (countryIso.isNullOrBlank()) return emptyList()

        val offline = runCatching {
            val json = context.assets.open(MAP_ASSET).bufferedReader().use { it.readText() }
            moat.parseMap(json, countryIso)
        }.getOrDefault(emptyList())
        keepBridges(offline)

        val live = withTimeoutOrNull(LIVE_RECOMMENDATION_MILLIS) {
            runCatching { moat.settingsFor(countryIso) }.getOrNull()
        }
        val chosen = if (!live.isNullOrEmpty()) live else offline
        keepBridges(chosen)

        return chosen.mapNotNull { Transport.fromTorName(it.transport) }
    }

    private fun keepBridges(settings: List<CircumventionSetting>) {
        if (settings.isEmpty()) return
        val byTransport = settings.mapNotNull { setting ->
            val transport = Transport.fromTorName(setting.transport) ?: return@mapNotNull null
            if (setting.bridges.isEmpty()) null else transport to setting.bridges
        }.toMap()
        bridges.setRecommended(byTransport)
    }

    companion object {
        const val MAP_ASSET = "circumvention_map.json"

        /**
         * How long the live country lookup may delay the first attempt. The
         * offline snapshot already gave us an answer; this only improves it.
         */
        const val LIVE_RECOMMENDATION_MILLIS = 6_000L

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
            Transport.DIRECT -> 30_000
            Transport.OBFS4 -> 45_000
            Transport.WEBTUNNEL -> 60_000
            Transport.MEEK -> 75_000
            Transport.SNOWFLAKE -> 100_000
        }

        /**
         * Abort early if bootstrap has not moved at all for this long.
         *
         * Tighter than it used to be, because switching route no longer costs a
         * restart: giving up on a dead rung after twenty-five seconds and
         * moving to the next one is now cheaper than waiting another ten.
         */
        const val STALL_MILLIS = 25_000L

        /** How many STUN servers Snowflake is asked to gather from. */
        const val ICE_SERVERS = 3
    }
}

/** Convenience: the probe, wired to the bridges we actually plan to use. */
suspend fun NetworkProbe.runFor(
    bridges: BridgeRepository,
    onProgress: (done: Int, total: Int, noteRes: Int) -> Unit,
): ProbeReport = run(bridges.probeTargets(Transport.OBFS4), onProgress)
