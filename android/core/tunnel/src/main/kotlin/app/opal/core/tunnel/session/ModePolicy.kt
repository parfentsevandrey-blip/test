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
     * which replaces a dead proxy by itself.
     */
    val watchdogMayReconnect: Boolean,
) {
    companion object {
        private val SNOWFLAKE =
            ModePolicy(race = false, settingsApiBeforeConnect = false, watchdogMayReconnect = false)

        /**
         * The policy for [mode] with the bridges Tor currently uses. Snowflake lines are left alone
         * whichever mode brought them (the user's own Snowflake lines, or Snowflake winning the
         * Auto race): tearing them down helps no more there than in Snowflake mode.
         */
        fun of(mode: ConnectionMode, active: List<BridgeLine>): ModePolicy {
            val base = of(mode)
            val snowflakeOnly =
                active.isNotEmpty() && active.all { it.transport == TransportKind.Snowflake }
            return if (snowflakeOnly) base.copy(watchdogMayReconnect = false) else base
        }

        fun of(mode: ConnectionMode): ModePolicy =
            when (mode) {
                ConnectionMode.Snowflake -> SNOWFLAKE
                ConnectionMode.Auto ->
                    ModePolicy(
                        race = true,
                        settingsApiBeforeConnect = true,
                        watchdogMayReconnect = true,
                    )
                // Public obfs4 bridges are blocked first; the API hands out private ones. WebTunnel
                // bridges exist only in the API.
                ConnectionMode.Obfs4,
                ConnectionMode.WebTunnel ->
                    ModePolicy(
                        race = false,
                        settingsApiBeforeConnect = true,
                        watchdogMayReconnect = true,
                    )
                ConnectionMode.Meek,
                ConnectionMode.Custom ->
                    ModePolicy(
                        race = false,
                        settingsApiBeforeConnect = false,
                        watchdogMayReconnect = true,
                    )
            }
    }
}
