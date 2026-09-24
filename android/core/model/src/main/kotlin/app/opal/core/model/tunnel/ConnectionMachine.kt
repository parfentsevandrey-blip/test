package app.opal.core.model.tunnel

/**
 * Inputs to [ConnectionMachine]; produced by the tunnel controller from Tor/network/user events.
 */
sealed interface ConnectionEvent {
    /** Bring the VPN up (user tap, always-on, tile). */
    data object Connect : ConnectionEvent

    /** Take the VPN down. With [keepTor] Tor stays connected (hot standby). */
    data class Disconnect(val keepTor: Boolean) : ConnectionEvent

    /** Everything (VPN, Tor, transports) has been torn down. */
    data object Stopped : ConnectionEvent

    /** Tor finished bootstrapping and can build circuits. */
    data object TorReady : ConnectionEvent

    /** Tor lost its ability to build circuits (after having been ready). */
    data class TorNotReady(val reason: ReconnectReason) : ConnectionEvent

    data object NetworkLost : ConnectionEvent

    data object NetworkAvailable : ConnectionEvent

    /** Watchdog: connection alive but no progress/data (e.g. DPI freezing the TCP session). */
    data object Stalled : ConnectionEvent

    /** Every transport/bridge candidate failed within the attempt budget. */
    data object AllTransportsFailed : ConnectionEvent

    /** A new attempt started after [AllTransportsFailed] (backoff elapsed, network changed…). */
    data object Retry : ConnectionEvent

    data object Revoked : ConnectionEvent

    data class Fatal(val error: TunnelError) : ConnectionEvent
}

/** State of [ConnectionMachine]: the public [state] plus facts needed for correct transitions. */
data class MachineState(
    val state: TunnelState = TunnelState.Off,
    /** Tor has bootstrapped and circuits are available. */
    val torReady: Boolean = false,
    /**
     * Whether the current VPN session has been connected at least once (Reconnecting vs
     * Connecting).
     */
    val sessionConnected: Boolean = false,
)

/**
 * Pure reducer for the connection lifecycle. Side effects (starting Tor, establishing the VPN…)
 * live in the controller; this function only decides what the user-visible state is, so every
 * transition is unit-testable and impossible states (e.g. "Connected" with Tor not ready) are
 * rejected in one place.
 */
object ConnectionMachine {

    fun reduce(current: MachineState, event: ConnectionEvent): MachineState {
        val s = current.state
        return when (event) {
            ConnectionEvent.Connect ->
                when {
                    s.isTunnelActive -> current
                    s == TunnelState.Stopping -> current
                    current.torReady ->
                        current.copy(state = TunnelState.Connected, sessionConnected = true)
                    else -> current.copy(state = TunnelState.Connecting(), sessionConnected = false)
                }

            is ConnectionEvent.Disconnect ->
                when {
                    !s.isTunnelActive && s != TunnelState.Standby -> current
                    event.keepTor ->
                        current.copy(state = TunnelState.Standby, sessionConnected = false)
                    else -> current.copy(state = TunnelState.Stopping, sessionConnected = false)
                }

            ConnectionEvent.Stopped ->
                if (s is TunnelState.Error) current.copy(torReady = false)
                else MachineState(state = TunnelState.Off)

            ConnectionEvent.TorReady -> {
                val ready = current.copy(torReady = true)
                when (s) {
                    is TunnelState.Connecting,
                    is TunnelState.Reconnecting,
                    TunnelState.Blocked ->
                        ready.copy(state = TunnelState.Connected, sessionConnected = true)
                    else -> ready
                }
            }

            is ConnectionEvent.TorNotReady -> {
                val notReady = current.copy(torReady = false)
                when (s) {
                    TunnelState.Connected ->
                        notReady.copy(state = TunnelState.Reconnecting(event.reason))
                    else -> notReady
                }
            }

            ConnectionEvent.NetworkLost ->
                if (s.isTunnelActive) current.copy(state = TunnelState.WaitingForNetwork)
                else current

            ConnectionEvent.NetworkAvailable ->
                if (s == TunnelState.WaitingForNetwork)
                    current.copy(state = recovering(current, ReconnectReason.NetworkChanged))
                else current

            ConnectionEvent.Stalled ->
                if (s == TunnelState.Connected)
                    current.copy(state = TunnelState.Reconnecting(ReconnectReason.Stalled))
                else current

            ConnectionEvent.AllTransportsFailed ->
                when (s) {
                    is TunnelState.Connecting,
                    is TunnelState.Reconnecting ->
                        current.copy(state = TunnelState.Blocked, torReady = false)
                    else -> current
                }

            ConnectionEvent.Retry ->
                if (s == TunnelState.Blocked)
                    current.copy(state = recovering(current, ReconnectReason.TransportSwitch))
                else current

            ConnectionEvent.Revoked ->
                MachineState(state = TunnelState.Error(TunnelError.VpnRevoked))

            is ConnectionEvent.Fatal -> MachineState(state = TunnelState.Error(event.error))
        }
    }

    private fun recovering(current: MachineState, reason: ReconnectReason): TunnelState {
        val attempt = (current.state as? TunnelState.Connecting)?.attempt ?: 1
        return if (current.sessionConnected) TunnelState.Reconnecting(reason)
        else TunnelState.Connecting(attempt + 1)
    }
}
