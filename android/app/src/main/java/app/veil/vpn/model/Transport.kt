package app.veil.vpn.model

import androidx.annotation.StringRes
import app.veil.vpn.R

/**
 * A way of reaching the Tor network.
 *
 * The ordering of the enum is the ordering of the default escalation ladder:
 * cheapest and fastest first, most evasive and most expensive last. Nothing
 * here needs a server address from the user — every entry either talks to the
 * Tor directory authorities that are compiled into tor itself, or to bridges
 * that the app fetches on its own.
 */
enum class Transport(
    /** Name tor uses in `ClientTransportPlugin` and `Bridge` lines. */
    val torName: String,
    /**
     * The name to show a person. Four of the five are product names that stay
     * as they are in every language; only "direct" is a word rather than a
     * name, which is why this is a resource and not a constant.
     */
    @StringRes val labelRes: Int,
    /** The same name for logs, which are read in English or not at all. */
    val label: String,
    /** Whether the transport needs `UseBridges 1` plus bridge lines. */
    val needsBridges: Boolean,
    /** Rough round-trip cost, used to rank equally-likely candidates. */
    val latencyClass: LatencyClass,
) {
    /** Plain connections to Tor guards. Fast, but trivially recognisable. */
    DIRECT("direct", R.string.transport_direct, "Direct", false, LatencyClass.LOW),

    /**
     * Lyrebird's obfs4: a polymorphic stream with no fixed byte patterns.
     * Defeats protocol fingerprinting but not IP blocklists, which is why
     * bridge addresses have to be rotated.
     */
    OBFS4("obfs4", R.string.transport_obfs4, "obfs4", true, LatencyClass.LOW),

    /**
     * WebTunnel wraps the stream in ordinary HTTPS to a host that also serves a
     * real website, so a probe that connects sees a plausible site. Currently
     * the hardest transport for an active prober to distinguish.
     */
    WEBTUNNEL("webtunnel", R.string.transport_webtunnel, "WebTunnel", true, LatencyClass.MEDIUM),

    /**
     * meek_lite fronts the connection through a large CDN: the TLS SNI names a
     * popular domain, the real destination is in an encrypted header. Blocking
     * it means blocking the CDN.
     */
    MEEK("meek_lite", R.string.transport_meek, "meek", true, LatencyClass.HIGH),

    /**
     * Snowflake rendezvouses through a broker and then hops via short-lived
     * WebRTC proxies run by volunteers' browsers. There is no stable address to
     * block, which is what makes it the last rung of the ladder.
     */
    SNOWFLAKE("snowflake", R.string.transport_snowflake, "Snowflake", true, LatencyClass.HIGH);

    val isPluggable: Boolean get() = this != DIRECT

    companion object {
        fun fromTorName(name: String): Transport? =
            entries.firstOrNull { it.torName.equals(name, ignoreCase = true) }
    }
}

enum class LatencyClass { LOW, MEDIUM, HIGH }

/** How a transport is expected to behave on the network in front of us. */
data class TransportVerdict(
    val transport: Transport,
    /** 0.0 hopeless, 1.0 looks clear. */
    val score: Float,
    val reason: String,
)
