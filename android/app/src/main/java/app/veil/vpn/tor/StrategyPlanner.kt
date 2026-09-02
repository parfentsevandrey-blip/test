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
    /** Conjure only: register with the station over DNS rather than HTTPS. */
    val dnsRendezvous: Boolean = false,
) {
    val label: String
        get() = when {
            ampRendezvous -> "${transport.label} (AMP)"
            dnsRendezvous -> "${transport.label} (DNS)"
            else -> transport.label
        }
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

    /**
     * Rewrites a Snowflake line's `front` as `fronts`, which is the form that
     * wins.
     *
     * Both spellings mean the same thing to the transport, but not with the
     * same authority: it reads `fronts` if there is one and only falls back to
     * `front`. That matters here because the app supplies a default set of
     * fronts for lines that name none, and the transport controller fills those
     * in per connection — so a line carrying only the singular `front` has the
     * app's default quietly layered on top of it and loses its own.
     *
     * The lines this bites are exactly the ones where it does most damage: the
     * Tor Project's AMP-cache Snowflake lines say `front=www.google.com`,
     * because the request goes to Google's cache. Overriding that with a CDN77
     * front sends an AMP request to a host that has never heard of it, and the
     * rendezvous fails in a way that reads as "no proxies available".
     */
    private fun promoteFront(bridge: BridgeLine): BridgeLine {
        val singular = bridge.params["front"]?.takeIf { it.isNotBlank() } ?: return bridge
        if (!bridge.params["fronts"].isNullOrBlank()) return bridge.withoutParams("front")
        return bridge.withoutParams("front").withParams(mapOf("fronts" to singular))
    }

    /**
     * Everything for the one method the user chose, and nothing else.
     *
     * This is not a ladder with a preferred rung on top: it is the chosen
     * method or nothing. Nothing is measured first either — the probe exists to
     * decide between methods, and there is no decision left to make, so the
     * seconds it costs buy nothing. A connect goes straight at what was asked
     * for.
     *
     * That is a real trade and worth naming. Falling back would connect more
     * often; being told plainly that the chosen method did not work is what
     * lets someone pick a different one on purpose, instead of watching the app
     * quietly do something else and never learning which method their network
     * actually allows.
     *
     * The one thing that does produce more than a single entry is a method with
     * more than one way of starting: Snowflake can ask the broker through a
     * fronted request or through an AMP cache, and Conjure can register with
     * its station over a fronted request or over DNS. Those are the same
     * method, so both ways are tried.
     */
    suspend fun pinnedPlan(
        transport: Transport,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
        available: Set<Transport>,
    ): List<Attempt> = withContext(Dispatchers.IO) {
        if (transport != Transport.DIRECT && transport !in available) {
            VeilLog.e("planner", "${transport.torName} has no listener; nothing to try")
            return@withContext emptyList()
        }
        val why = context.getString(R.string.route_why_pinned)
        val fronted = buildAttempt(transport, why, tlsProfile, dtlsProfile)
        val alternate = when (transport) {
            Transport.SNOWFLAKE -> ampSnowflakeAttempt(tlsProfile, dtlsProfile)
            Transport.CONJURE -> dnsConjureAttempt()
            else -> null
        }

        // Both ways of starting the method, cheapest-to-block last.
        //
        // The first move of both Snowflake and Conjure is an ordinary fronted
        // HTTPS request — to a broker, or to a registration station — and it is
        // the only part of either that a censor can get at. On the networks
        // this app is built for that request is the thing that is blocked:
        // measured on a Russian mobile network, the fronted Snowflake
        // rendezvous returns nothing at all while the same bridge reached
        // through Google's AMP cache connects in seconds. Fronting through a
        // CDN is only as strong as the list of names it borrows, and those
        // names are enumerable; cdn.ampproject.org shares an edge with
        // google.com, which is not. So the alternative goes first.
        //
        // Not hardcoded to that, though. Whichever of the two last failed is
        // demoted by the same failure counter that already orders bridges, so
        // a network where the reverse is true corrects itself after one
        // connect instead of paying for this preference every time.
        val attempts = listOfNotNull(alternate, fronted)
            .sortedBy { bridges.failureCount(it.bridges) }
        VeilLog.i("planner", attempts.joinToString(" -> ") { it.label }.ifEmpty { "nothing to try" })
        attempts
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
            // As is Conjure registered over DNS.
            .filterNot { transport == Transport.CONJURE && it.params["registrar"] == "dns" }
            .take(bridgeBudget(transport))

        if (candidates.isEmpty()) {
            // Worth saying out loud rather than at debug level. A transport
            // with no bridges is not tried at all, and on a network where the
            // bridge service is unreachable that is how the route most likely
            // to work there disappears without anyone noticing.
            VeilLog.w("planner", "skipping ${transport.torName}: no bridges for it")
            return null
        }
        return Attempt(transport, candidates.map { shape(it, tlsProfile, dtlsProfile) }, why)
    }

    /**
     * Snowflake asking the broker a different way.
     *
     * Everything Snowflake is famous for — no fixed address, a proxy that is
     * someone's browser tab — begins after a rendezvous, and the rendezvous is
     * an ordinary HTTPS request to an ordinary CDN. It is the one part of
     * Snowflake a censor can reach, and blocking it stops Snowflake dead in a
     * way that looks from the phone like there being no proxies in the world.
     *
     * So the same bridge is offered a second time, asking through Google's AMP
     * cache instead: a different company, a different edge, a different request
     * shape, blocked or not blocked independently of the first.
     *
     * The Tor Project publishes ready-made AMP lines for some countries and
     * those are used as they are. Where it does not — which includes every
     * network the country list has nothing to say about — one is made here from
     * a plain line, and the three parameters are replaced together. That last
     * part is the whole difficulty: the broker mirror, the front and the cache
     * only work as a set, and filling in a cache while leaving a fronted line's
     * own broker and front in place produces a request that arrives nowhere.
     */
    private fun ampSnowflakeAttempt(
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): Attempt? {
        val all = bridges.forTransport(Transport.SNOWFLAKE, limit = 8)
        val published = all.filter { it.params.containsKey("ampcache") }
        val line = published.firstOrNull()
            ?: all.firstOrNull { !it.params.containsKey("ampcache") }
                // All three at once. The fronted line names the CDN77 broker
                // mirror and a DataPacket front, and either of those left
                // behind would send an AMP request to a host that has never
                // heard of it. `front` is dropped rather than overwritten
                // because the plural is the one the transport reads.
                ?.withoutParams("front")
                ?.withParams(
                    mapOf(
                        "url" to AMP_BROKER,
                        "ampcache" to AMP_CACHE,
                        "fronts" to AMP_FRONT,
                    ),
                )
                // And under its own placeholder address, so that tor holds it
                // as a second bridge rather than as a duplicate of the one it
                // was made from. Without this the two ways cannot be offered at
                // the same time: tor keys bridges on address and port and would
                // keep exactly one of them.
                ?.withPlaceholderOffset(AMP_ADDRESS_OFFSET)
        if (line == null) return null
        // Shaped and length-checked like any other rung. The second part is not
        // optional: the AMP settings make an already long Snowflake line longer,
        // and tor rejects an over-length `Bridge` outright — taking `UseBridges`
        // and the whole rung down with it.
        return Attempt(
            transport = Transport.SNOWFLAKE,
            bridges = listOf(shape(line, tlsProfile, dtlsProfile)),
            why = context.getString(R.string.route_why_snowflake_amp),
            ampRendezvous = true,
        )
    }

    /** The Conjure line that registers over DNS rather than a fronted request. */
    private fun dnsConjureAttempt(): Attempt? {
        val overDns = bridges.forTransport(Transport.CONJURE, limit = 6)
            .filter { it.params["registrar"] == "dns" }
        if (overDns.isEmpty()) return null
        return Attempt(
            transport = Transport.CONJURE,
            bridges = overDns.take(1),
            why = context.getString(R.string.route_why_conjure_dns),
            ampRendezvous = false,
            dnsRendezvous = true,
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

            // Conjure's registration is a fronted HTTPS request, and it reads
            // the same short table of Client Hello names Snowflake does.
            Transport.CONJURE ->
                bridge.withParams(mapOf("utls-imitate" to tlsProfile.snowflakeName))

            Transport.SNOWFLAKE -> promoteFront(bridge).withParams(
                buildMap {
                    put("utls-imitate", tlsProfile.snowflakeName)
                    // Snowflake's data path is DTLS, which carries a
                    // fingerprint of its own that the TLS setting above does
                    // not touch.
                    put("covertdtls-config", dtlsProfile.argument)
                    // The STUN list is left exactly as published. It was
                    // shortened here once, to save the seconds WebRTC spends
                    // gathering candidates from servers that will not answer —
                    // an optimisation made without measuring anything, and a
                    // bad one. Keeping the first three of the published list
                    // means keeping the three furthest from a user in Russia
                    // and discarding the German ones and the Russian one, which
                    // are the ones most likely to answer. Snowflake needs STUN
                    // to learn its own NAT and to gather candidates at all, so
                    // that is not a slower Snowflake, it is no Snowflake.
                    //
                    // Redundancy in that list is the point of it. Whatever it
                    // costs in connect time is the price of the transport
                    // working on a hostile network.
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

    /**
     * How many bridges to hand tor for one attempt.
     *
     * More bridges is not better. tor opens a connection to each, and a fan of
     * near-simultaneous handshakes is one of the three things current DPI is
     * reported to look for. The broker-based transports need exactly one line,
     * because the line is configuration rather than an address.
     */
    private fun bridgeBudget(transport: Transport): Int = when (transport) {
        // For these the line is configuration rather than an address, so a
        // second one buys nothing and costs a parallel handshake.
        Transport.MEEK, Transport.SNOWFLAKE, Transport.CONJURE -> 1
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

    /**
     * Asks the bridge service again, slowly, for the bridges the first attempt
     * did not wait for.
     *
     * This is where WebTunnel comes from. Its bridges are not in anybody's
     * built-in list — they are handed out per request, which is what makes them
     * worth having and also what makes them absent when the service cannot be
     * reached. On a censored network the request only succeeds through a
     * fronted transport, and that takes far longer than a connect should wait,
     * so the fast path above gives up on it.
     *
     * Giving up was the mistake. The answer is not to wait longer before
     * starting, but to keep asking while the connection attempt runs and add
     * what comes back to it — which is possible now that routes can be added to
     * a running tor rather than only chosen before it starts.
     */
    suspend fun fetchLateBridges(
        countryIso: String?,
        tlsProfile: TlsProfile,
        dtlsProfile: DtlsProfile,
    ): Map<Transport, List<BridgeLine>> = withContext(Dispatchers.IO) {
        if (countryIso.isNullOrBlank()) return@withContext emptyMap()
        val settings = withTimeoutOrNull(LATE_RECOMMENDATION_MILLIS) {
            runCatching { moat.settingsFor(countryIso) }.getOrNull()
        }.orEmpty()
        if (settings.isEmpty()) {
            VeilLog.w("planner", "bridge service did not answer; running on what we shipped")
            return@withContext emptyMap()
        }
        keepBridges(settings)

        settings.mapNotNull { setting ->
            val transport = Transport.fromTorName(setting.transport) ?: return@mapNotNull null
            val lines = setting.bridges
                .filterNot { it.hasRoutableAddress && cooldown.isCoolingDown(it.host) }
                .take(bridgeBudget(transport))
                .map { shape(it, tlsProfile, dtlsProfile) }
            if (lines.isEmpty()) null else transport to lines
        }.toMap()
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
         * The AMP rendezvous, as the Tor Project publishes it.
         *
         * These three belong together. The cache fetches the broker's own
         * origin rather than its CDN77 mirror — there is nothing to mirror when
         * something else is doing the fetching — and the request to the cache
         * is fronted through www.google.com, which shares an edge with
         * cdn.ampproject.org.
         */
        const val AMP_BROKER = "https://snowflake-broker.torproject.net/"
        const val AMP_CACHE = "https://cdn.ampproject.org/"
        const val AMP_FRONT = "www.google.com"

        /**
         * How far the AMP line's placeholder address is moved from the fronted
         * one's. Two, matching the Tor Project's own settings, where the
         * fronted pair is 192.0.2.3/.4 and the AMP pair is .5/.6.
         */
        const val AMP_ADDRESS_OFFSET = 2

        /**
         * How long the live country lookup may delay the first attempt. The
         * offline snapshot already gave us an answer; this only improves it.
         */
        const val LIVE_RECOMMENDATION_MILLIS = 12_000L

        /**
         * How long the same question may take when nobody is waiting on it.
         * On a censored network the answer only arrives through a fronted
         * request, which is slow but is also the only one that arrives.
         */
        const val LATE_RECOMMENDATION_MILLIS = 90_000L

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
            // Registration with the station, then a connection to the phantom.
            // Under load the first of those alone can take most of a minute.
            Transport.CONJURE -> 110_000
            Transport.SNOWFLAKE -> 100_000
        }

        /**
         * How long a route may go without its bootstrap percentage moving
         * before it is given up on — which depends entirely on how far it got.
         *
         * A flat timer is wrong, and measurably so. Below about three quarters
         * tor is still trying to establish a link to the bridge, and a route
         * that is going to fail fails here; twenty-five seconds of silence is
         * a dead route and waiting longer costs the user a minute to learn
         * nothing. Past that point the link is up and what remains is
         * directory material and extending a circuit through it — over a path
         * that runs through a CDN or a volunteer's browser, in steps the
         * percentage only reports coarsely. Silence there is what a slow link
         * looks like, not a broken one.
         *
         * Measured, not guessed. On one Russian mobile network meek reached
         * 95% and Conjure-over-DNS reached 95%, and both were killed by an
         * eighteen-second timer for not producing a number — one step short of
         * a working tunnel, on the two routes that had got furthest.
         */
        fun stallMillis(percent: Int): Long = when {
            percent >= 90 -> 60_000
            percent >= 75 -> 45_000
            else -> 25_000
        }

        /** Bootstrap is this far along when the link to the bridge is working. */
        const val LATE_BOOTSTRAP_PERCENT = 75
    }
}

/** Convenience: the probe, wired to the bridges we actually plan to use. */
suspend fun NetworkProbe.runFor(
    bridges: BridgeRepository,
    onProgress: (done: Int, total: Int, noteRes: Int) -> Unit,
): ProbeReport = run(bridges.probeTargets(Transport.OBFS4), onProgress)
