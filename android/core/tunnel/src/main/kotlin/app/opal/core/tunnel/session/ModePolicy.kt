package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.ConnectionMode

/**
 * What the session may do on its own in each connection mode.
 *
 * Snowflake (the default) is left alone, exactly like in Tor Browser: Tor and the Snowflake client
 * retry by themselves, and every teardown (DisableNetwork, bridge switch, restart) only throws away
 * a proxy search that may be seconds from succeeding. Racing and switching transports happen only
 * in Auto, which the user chooses explicitly.
 */
internal data class ModePolicy(
    /** Race several transports in one Tor and keep the winner; give up rounds and retry. */
    val race: Boolean,
    /**
     * May ask the Circumvention Settings API *before* Tor is connected. That request goes directly
     * (domain-fronted), so the Tor Project server sees the user's IP: only where it is needed.
     */
    val settingsApiBeforeConnect: Boolean,
    /**
     * On a detected freeze the watchdog may tear connections down, switch bridges and restart Tor.
     * Useful against DPI freezing TCP sessions (obfs4, WebTunnel, meek); harmful for Snowflake,
     * which replaces a dead proxy by itself. Without it the watchdog only renews a Snowflake
     * session that has been silent far longer than a proxy swap takes (see TorSession).
     */
    val watchdogEscalates: Boolean,
) {
    companion object {
        private val SNOWFLAKE =
            ModePolicy(race = false, settingsApiBeforeConnect = false, watchdogEscalates = false)

        /**
         * The policy for [mode] with the bridges Tor currently uses. Snowflake and dnstt lines are
         * left alone whichever mode brought them (the user's own lines, or Snowflake winning the
         * Auto race): tearing them down helps no more there than in Snowflake mode.
         */
        fun of(mode: ConnectionMode, active: List<BridgeLine>): ModePolicy {
            val base = of(mode)
            return if (active.isSessionOnly()) base.copy(watchdogEscalates = false) else base
        }

        fun of(mode: ConnectionMode): ModePolicy =
            when (mode) {
                ConnectionMode.Snowflake -> SNOWFLAKE
                ConnectionMode.Auto ->
                    ModePolicy(
                        race = true,
                        settingsApiBeforeConnect = true,
                        watchdogEscalates = true,
                    )
                // Public obfs4 bridges are blocked first; the API hands out private ones. WebTunnel
                // bridges exist only in the API.
                ConnectionMode.Obfs4,
                ConnectionMode.WebTunnel ->
                    ModePolicy(
                        race = false,
                        settingsApiBeforeConnect = true,
                        watchdogEscalates = true,
                    )
                ConnectionMode.Meek,
                ConnectionMode.Custom ->
                    ModePolicy(
                        race = false,
                        settingsApiBeforeConnect = false,
                        watchdogEscalates = true,
                    )
            }
    }
}

/**
 * Transports that carry Tor inside a session of their own (Turbo Tunnel: KCP and smux) and recover
 * a lost path by themselves: Snowflake replaces a silent proxy, dnstt keeps polling its resolver.
 * Their first hop is slow, and closing Tor's connection only throws their session away.
 */
private val SESSION_TRANSPORTS = setOf(TransportKind.Snowflake, TransportKind.Dnstt)

/** Tor would use only Snowflake and/or dnstt. */
internal fun List<BridgeLine>.isSessionOnly(): Boolean =
    isNotEmpty() && all { it.transport in SESSION_TRANSPORTS }

/** The Snowflake server drops a client's session after 4 minutes without it (smux keepalive). */
internal const val SNOWFLAKE_SESSION_EXPIRY_MS = 4 * 60_000L

/** dnstt client and server close a session after 2 minutes without traffic (`idleTimeout`). */
internal const val DNSTT_SESSION_EXPIRY_MS = 2 * 60_000L

/**
 * How long without the phone the servers keep the session of these (session) transports: a gap this
 * long ends it for all of them.
 */
internal fun List<BridgeLine>.sessionExpiryMillis(): Long =
    maxOfOrNull {
        if (it.transport == TransportKind.Dnstt) DNSTT_SESSION_EXPIRY_MS
        else SNOWFLAKE_SESSION_EXPIRY_MS
    } ?: SNOWFLAKE_SESSION_EXPIRY_MS
