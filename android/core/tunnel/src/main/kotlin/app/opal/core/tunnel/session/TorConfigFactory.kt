package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.tor.SocksFlag
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.Torrc
import app.opal.core.model.tor.torrc

/** Builds the Tor configuration. Anonymity-relevant defaults are never touched for speed. */
internal object TorConfigFactory {

    /** Options changed at runtime when bridges change (race, transport switch, custom bridges). */
    val BRIDGE_OPTIONS =
        listOf(TorOption.UseBridges, TorOption.ClientTransportPlugin, TorOption.Bridge)

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
        bridges(bridges, transportPorts)
        reducedConnectionPadding(settings.dataSaver)
        // GeoIP and ExitNodes are applied after bootstrap (see TorSession.applyRuntimeOptions).
    }

    fun bridges(bridges: List<BridgeLine>, transportPorts: Map<String, Int>): Torrc = torrc {
        bridges(bridges, transportPorts)
    }
}
