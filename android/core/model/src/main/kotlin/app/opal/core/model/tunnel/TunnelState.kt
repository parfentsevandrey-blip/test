package app.opal.core.model.tunnel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Public state of the tunnel as the user sees it. The VPN interface is up in every state except
 * [Off], [Standby], [Stopping] (being torn down) and [Error].
 */
@Serializable
sealed interface TunnelState {

    /** VPN is off. Tor may still be warming up in the background (see [TunnelSnapshot.warm]). */
    @Serializable @SerialName("off") data object Off : TunnelState

    /** VPN is up and holding traffic (nothing leaks) while Tor bootstraps. */
    @Serializable
    @SerialName("connecting")
    data class Connecting(val attempt: Int = 1) : TunnelState

    @Serializable @SerialName("connected") data object Connected : TunnelState

    /** VPN is up, Tor lost connectivity and is recovering. */
    @Serializable
    @SerialName("reconnecting")
    data class Reconnecting(val reason: ReconnectReason) : TunnelState

    /** VPN is up, but the device has no usable network. */
    @Serializable @SerialName("no_network") data object WaitingForNetwork : TunnelState

    /**
     * Every transport and every bridge (built-in, Settings API, custom) failed. Typical for mobile
     * networks restricted to allow-lists; the UI explains what to try.
     */
    @Serializable @SerialName("blocked") data object Blocked : TunnelState

    /** Hot standby: Tor stays connected, VPN is down. Connecting takes well under a second. */
    @Serializable @SerialName("standby") data object Standby : TunnelState

    @Serializable @SerialName("stopping") data object Stopping : TunnelState

    @Serializable @SerialName("error") data class Error(val error: TunnelError) : TunnelState
}

@Serializable
enum class ReconnectReason {
    NetworkChanged,
    Stalled,
    CircuitLost,
    TransportSwitch,
    Restart,
}

@Serializable
enum class TunnelError {
    /** The user has not granted (or withdrew) VPN consent. */
    VpnPermissionMissing,
    /** Another VPN app took over, or the system revoked our VPN. */
    VpnRevoked,
    /** The VPN interface could not be created (e.g. restricted by a work profile policy). */
    VpnEstablishFailed,
    TorStartFailed,
    NativeLibraryMissing,
}

/** True while our VPN interface should be up (traffic goes into the tunnel). */
val TunnelState.isTunnelActive: Boolean
    get() =
        when (this) {
            is TunnelState.Connecting,
            TunnelState.Connected,
            is TunnelState.Reconnecting,
            TunnelState.WaitingForNetwork,
            TunnelState.Blocked -> true
            TunnelState.Off,
            TunnelState.Standby,
            TunnelState.Stopping,
            is TunnelState.Error -> false
        }
