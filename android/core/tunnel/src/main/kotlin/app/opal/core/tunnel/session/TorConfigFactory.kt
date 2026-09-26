package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.tor.SocksFlag
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.Torrc
import app.opal.core.model.tor.TorrcBuilder
import app.opal.core.model.tor.torrc

/** Builds the Tor configuration. Anonymity-relevant defaults are never touched for speed. */
internal object TorConfigFactory {

    /**
     * Options changed at runtime when bridges change (race, transport switch, custom bridges).
     * Listed options missing from the new configuration go back to Tor's defaults.
     */
    val BRIDGE_OPTIONS =
        listOf(
            TorOption.UseBridges,
            TorOption.ClientTransportPlugin,
            TorOption.Bridge,
            TorOption.CircuitStreamTimeout,
        )

    /**
     * Stream timeout while only Snowflake carries the connection. When a volunteer proxy goes away,
     * the Snowflake client needs about 20 s to notice and up to ~45 s until the next proxy carries
     * data; meanwhile nothing moves on any circuit. With Tor's 10 s every stream waiting then
     * retires its circuit (and the circuit's conflux set) for new streams, so after the swap Tor
     * first builds new circuits over the slow first hop instead of using the ones that work again.
     * 30 s rides out most swaps; Tor's manual suggests values like 60 for slow networks.
     */
    const val SNOWFLAKE_STREAM_TIMEOUT_S = 30

    /**
     * [socksPort] is fixed for the lifetime of this Tor process (a free loopback port picked by the
     * session), not `auto`: `DisableNetwork 1` closes the SOCKS listener and `0` reopens it, and an
     * `auto` listener would come back on a different port than the one hev uses.
     */
    fun startup(
        bridges: List<BridgeLine>,
        transportPorts: Map<String, Int>,
        settings: AppSettings,
        socksPort: Int,
        networkUp: Boolean = true,
    ): Torrc = torrc {
        socksPort(port = socksPort, flags = setOf(SocksFlag.IPv6Traffic))
        // Without a network Tor would burn through its bridge list; it is enabled on reconnect.
        disableNetwork(!networkUp)
        noExtraListeners()
        clientOnly()
        dormantCanceledByStartup()
        bridgesWithTimeouts(bridges, transportPorts)
        reducedConnectionPadding(settings.dataSaver)
        // GeoIP and ExitNodes are applied after bootstrap (see TorSession.applyRuntimeOptions).
    }

    /** For [BRIDGE_OPTIONS]. */
    fun bridges(bridges: List<BridgeLine>, transportPorts: Map<String, Int>): Torrc = torrc {
        bridgesWithTimeouts(bridges, transportPorts)
    }

    private fun TorrcBuilder.bridgesWithTimeouts(
        bridges: List<BridgeLine>,
        transportPorts: Map<String, Int>,
    ) {
        bridges(bridges, transportPorts)
        circuitStreamTimeout(SNOWFLAKE_STREAM_TIMEOUT_S.takeIf { bridges.isSnowflakeOnly() })
    }
}
