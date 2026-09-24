package app.opal.core.model.tunnel

import app.opal.core.model.bridge.TransportKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the UI needs to render the tunnel, pushed from the `:tunnel` process over AIDL as
 * JSON. Bandwidth samples travel separately ([TrafficSample]) because they change every second.
 */
@Serializable
data class TunnelSnapshot(
    val state: TunnelState = TunnelState.Off,
    val bootstrap: BootstrapInfo? = null,
    /** Transport carrying the connection right now (null while undecided). */
    val transport: TransportKind? = null,
    /** Transports raced in parallel right now (Auto mode), empty when not racing. */
    val racing: List<TransportKind> = emptyList(),
    val circuit: CircuitInfo? = null,
    /** Wall-clock millis when the current session became connected. */
    val connectedSince: Long? = null,
    /** Wall-clock millis until which "New identity" is rate-limited by Tor. */
    val newIdentityReadyAt: Long? = null,
    val warm: WarmState = WarmState.Cold,
    val network: NetworkKind? = null,
    /** The last bootstrap/transport problem worth showing (localized in UI). */
    val problem: TunnelProblem? = null,
    val exitCountry: String? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

        fun fromJson(value: String): TunnelSnapshot = json.decodeFromString(serializer(), value)
    }
}

@Serializable data class BootstrapInfo(val progress: Int, val phase: BootstrapPhase)

@Serializable
enum class HopRole {
    Bridge,
    Guard,
    Middle,
    Exit,
}

@Serializable
data class CircuitHop(
    val role: HopRole,
    val nickname: String? = null,
    /** ISO country code, lowercase, from Tor's GeoIP (`GETINFO ip-to-country`). */
    val country: String? = null,
    /** Shown for the exit only: the address websites see. */
    val address: String? = null,
)

@Serializable data class CircuitInfo(val hops: List<CircuitHop>)

/** Warm-cache / standby status of Tor while the VPN is off. */
@Serializable
enum class WarmState {
    Cold,
    /** Tor is bootstrapping ahead of time (app opened, background refresh, hot standby). */
    Warming,
    /** Tor is connected and connecting the VPN will be instant. */
    Ready,
}

@Serializable
enum class NetworkKind {
    Wifi,
    Cellular,
    Ethernet,
    Other,
}

@Serializable
enum class TunnelProblem {
    /** Bootstrap reports that bridges/relays cannot be reached. */
    CannotReachBridges,
    /** Traffic stopped although connections stay open (DPI freeze). */
    ConnectionFrozen,
    /** Snowflake broker/proxies unreachable. */
    SnowflakeUnavailable,
    /** Clock skew detected by Tor. */
    ClockSkew,
    /** The Settings API could not be reached; using built-in and cached bridges. */
    SettingsApiUnreachable,
}

@Serializable
data class TrafficSample(
    /** Bytes per second read (download) in the last second. */
    val read: Long,
    /** Bytes per second written (upload) in the last second. */
    val written: Long,
    val totalRead: Long,
    val totalWritten: Long,
)
