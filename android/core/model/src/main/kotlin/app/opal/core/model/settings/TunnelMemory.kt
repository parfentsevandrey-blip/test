package app.opal.core.model.settings

import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.tunnel.NetworkKind
import kotlinx.serialization.Serializable

/**
 * What the tunnel learned from previous connections. Written only by the `:tunnel` process, kept
 * separately from [AppSettings] so user edits and background bookkeeping never race.
 */
@Serializable
data class TunnelMemory(
    /** Transport that won the last race per network type ("start with the last winner"). */
    val winners: Map<NetworkKind, TransportKind> = emptyMap(),
    /** Bridge (by [app.opal.core.model.bridge.BridgeLine.id]) that carried the last session. */
    val lastWorkingBridge: Map<NetworkKind, String> = emptyMap(),
    val circumvention: CircumventionCache? = null,
    val bridgeStats: Map<String, BridgeStat> = emptyMap(),
    /** Country reported by the Settings API (used when asking again through Tor). */
    val detectedCountry: String? = null,
    /** Wall-clock millis of the last completed bootstrap (warm-cache bookkeeping). */
    val lastBootstrapAt: Long? = null,
    val lastDirectoryRefreshAt: Long? = null,
    /**
     * The user wants the VPN on. Decides what a system restart of the service (START_STICKY, boot
     * with always-on) should do: reconnect, or stay off.
     */
    val vpnWanted: Boolean = false,
)

/** Response of the Circumvention Settings API, cached with the time it was fetched. */
@Serializable
data class CircumventionCache(
    val fetchedAt: Long,
    val country: String? = null,
    /** Ordered by usefulness for the country, as returned by `/settings` (or `/defaults`). */
    val settings: List<BridgeSet> = emptyList(),
    /** `/builtin`: the current public built-in bridges (fresher than the bundled pt_config). */
    val builtin: Map<String, List<String>> = emptyMap(),
    /** Last failed attempt, to back off between retries. */
    val lastFailureAt: Long? = null,
)

@Serializable
data class BridgeSet(
    val type: String,
    /** `builtin` or `bridgedb` (private bridges handed out for this client). */
    val source: String,
    val lines: List<String>,
)

@Serializable
data class BridgeStat(
    val successes: Int = 0,
    val failures: Int = 0,
    val lastSuccessAt: Long? = null,
    val lastFailureAt: Long? = null,
) {
    /** Laplace-smoothed success ratio used for ordering candidates. */
    val score: Double
        get() = (successes + 1.0) / (successes + failures + 2.0)
}
