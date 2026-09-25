package app.opal.core.model.settings

import app.opal.core.model.bridge.TransportKind
import kotlinx.serialization.Serializable

/** User settings, shared by the UI and `:tunnel` processes (multi-process DataStore). */
@Serializable
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    /** Snowflake by default, as in Tor Browser; Auto (race + Settings API) is opt-in. */
    val connectionMode: ConnectionMode = ConnectionMode.Snowflake,
    /** Raw bridge lines entered by the user (validated before saving). */
    val customBridges: List<String> = emptyList(),
    /** ISO 3166-1 alpha-2, lowercase; null = let Tor choose. */
    val exitCountry: String? = null,
    /** Keep Tor connected between sessions; connecting only raises the VPN interface. */
    val hotStandby: Boolean = false,
    /** Periodically refresh the Tor directory while the VPN is off (Wi-Fi or charging). */
    val backgroundDirectoryRefresh: Boolean = true,
    /** Start bootstrapping as soon as the app is opened; stop after a minute if unused. */
    val prepareOnOpen: Boolean = true,
    /** ReducedConnectionPadding — less traffic, slightly weaker padding defence. */
    val dataSaver: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
    val simplifiedGraphics: Boolean = false,
    val splitTunnel: SplitTunnelSettings = SplitTunnelSettings(),
)

@Serializable
enum class ConnectionMode(val transport: TransportKind?) {
    /**
     * Race transports (Snowflake, WebTunnel, obfs4, meek, Settings API bridges); start with the
     * last winner for the current network type. Opt-in: for networks where Snowflake is blocked.
     */
    Auto(null),
    /** Default: only the built-in Snowflake bridges, like Tor Browser. */
    Snowflake(TransportKind.Snowflake),
    WebTunnel(TransportKind.WebTunnel),
    Obfs4(TransportKind.Obfs4),
    Meek(TransportKind.Meek),
    /** Only the user's own bridges. */
    Custom(null),
}

@Serializable
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * App language. Not part of [AppSettings]: on Android 13+ the system owns it (per-app language,
 * also editable in system settings); below that it lives in a tiny file read before the first frame
 * (`AppLocaleStore` in :core:data).
 */
@Serializable
enum class AppLanguage(val tag: String?) {
    System(null),
    Russian("ru"),
    English("en"),
}

@Serializable
enum class SplitTunnelMode {
    /** Everything through Tor except [SplitTunnelSettings.excluded]. */
    AllExcept,
    /** Only [SplitTunnelSettings.included] go through Tor. */
    OnlySelected,
}

@Serializable
data class SplitTunnelSettings(
    val mode: SplitTunnelMode = SplitTunnelMode.AllExcept,
    val excluded: Set<String> = emptySet(),
    val included: Set<String> = emptySet(),
    val showSystemApps: Boolean = false,
)
