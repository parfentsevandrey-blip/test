package app.veil.vpn.model

/** Everything the UI needs to know about the tunnel, and nothing more. */
sealed interface TunnelState {

    data object Idle : TunnelState

    /** Measuring the network before committing to a transport. */
    data class Probing(val note: String, val done: Int, val total: Int) : TunnelState

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
            this is Escalating || this is Stopping

    val isLive: Boolean
        get() = this is Connected

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
