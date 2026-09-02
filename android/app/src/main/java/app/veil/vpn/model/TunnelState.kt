package app.veil.vpn.model

import androidx.annotation.StringRes

/** Everything the UI needs to know about the tunnel, and nothing more. */
sealed interface TunnelState {

    data object Idle : TunnelState

    /** Measuring the network before committing to a transport. */
    data class Probing(@StringRes val noteRes: Int, val done: Int, val total: Int) : TunnelState

    data class Starting(
        val transport: Transport,
        val attempt: Int,
        val ladderSize: Int,
    ) : TunnelState

    data class Bootstrapping(
        val transport: Transport,
        val percent: Int,
        val summary: String,
        val attempt: Int,
        val ladderSize: Int,
    ) : TunnelState

    data class Connected(
        val transport: Transport,
        val connectedAtMillis: Long,
        val socksPort: Int,
    ) : TunnelState

    /**
     * Connecting to a volunteer VPN server from the VPN Gate list.
     *
     * Deliberately not folded into the states above. Those all carry a
     * [Transport], which is a way of *disguising a connection to Tor*, and VPN
     * Gate is not one — it is a different network with a different threat model,
     * where an operator we do not know can see the traffic. Giving it its own
     * states means the interface cannot accidentally describe it as a Tor route,
     * and every place that shows what is carrying the traffic has to say which
     * of the two it is.
     */
    data class VpnGateStarting(
        val server: String,
        val country: String,
        val attempt: Int,
        val total: Int,
    ) : TunnelState

    data class VpnGateConnected(
        val server: String,
        val country: String,
        val connectedAtMillis: Long,
        val socksPort: Int,
    ) : TunnelState

    /** A rung failed; moving to the next one without dropping the VPN. */
    data class Escalating(
        val from: Transport,
        val to: Transport,
        val reason: String,
    ) : TunnelState

    data object Stopping : TunnelState

    data class Failed(val reason: String, val tried: List<Transport>) : TunnelState

    val isBusy: Boolean
        get() = this is Probing || this is Starting || this is Bootstrapping ||
            this is Escalating || this is Stopping || this is VpnGateStarting

    val isLive: Boolean
        get() = this is Connected || this is VpnGateConnected

    /** When the tunnel started carrying traffic, whatever is carrying it. */
    val liveSinceMillis: Long?
        get() = when (this) {
            is Connected -> connectedAtMillis
            is VpnGateConnected -> connectedAtMillis
            else -> null
        }

    val activeTransport: Transport?
        get() = when (this) {
            is Starting -> transport
            is Bootstrapping -> transport
            is Connected -> transport
            is Escalating -> to
            else -> null
        }
}

/** Live traffic counters, mirrored from the native tunnel. */
data class TunnelStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val tcpOpen: Long = 0,
    val dnsQueries: Long = 0,
    val dnsErrors: Long = 0,
    val blockedUdp: Long = 0,
    val dialErrors: Long = 0,
)
