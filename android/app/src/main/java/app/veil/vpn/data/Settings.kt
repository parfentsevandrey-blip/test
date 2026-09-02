package app.veil.vpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.veil.vpn.model.DtlsProfile
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("veil.settings")

/**
 * Which network carries the traffic.
 *
 * Two quite different things, and the difference is not a detail of transport.
 * Tor hides who is talking to whom from everyone, including the relays; VPN
 * Gate carries the traffic through one volunteer's machine, which can see all
 * of it. Presenting them as two entries in the same list is honest only if the
 * app says which is which wherever the choice appears.
 */
enum class Engine { TOR, VPN_GATE }

enum class AppRoutingMode {
    /** Every app goes through the tunnel. */
    ALL,

    /** Only the chosen apps are tunnelled; everything else uses the plain network. */
    ONLY_SELECTED,

    /** Everything is tunnelled except the chosen apps. */
    EXCEPT_SELECTED,
}

/**
 * How names are resolved. Every option keeps DNS inside the tunnel; they differ
 * in how the query is framed once it is there.
 */
/** Domains that commonly refuse connections arriving from a Tor exit. */
const val DEFAULT_BYPASS_SUFFIXES = ".ru,.\u0440\u0444,.su,.by,.kz"

enum class DnsMode(val nativeMode: String) {
    /** tor's own DNSPort. Resolution happens at the exit relay. */
    TOR_DNS_PORT("udp"),

    /** DNS over TCP to a public resolver, carried by the tunnel. */
    TCP_THROUGH_TUNNEL("tcp"),

    /** RFC 8484 DoH to a public resolver, carried by the tunnel. */
    DOH_THROUGH_TUNNEL("doh"),
}

enum class IsolationMode(val nativeMode: String) {
    /** One circuit for everything: fastest, and links all your activity. */
    SHARED("none"),

    /** A circuit per destination address, like Tor Browser's first-party isolation. */
    PER_DESTINATION("host"),

    /** A fresh circuit per connection: slowest, hardest to correlate. */
    PER_CONNECTION("conn"),
}

data class VeilSettings(
    val engine: Engine = Engine.TOR,
    val manualTransport: Transport = Transport.SNOWFLAKE,
    val blockUdp: Boolean = true,
    val dnsMode: DnsMode = DnsMode.TOR_DNS_PORT,
    val dohEndpoint: String = DEFAULT_DOH,
    val tcpDnsResolver: String = DEFAULT_TCP_DNS,
    val isolation: IsolationMode = IsolationMode.PER_DESTINATION,
    val killSwitch: Boolean = true,
    val autoStartOnBoot: Boolean = false,
    val runSnowflakeProxy: Boolean = false,
    val appRoutingMode: AppRoutingMode = AppRoutingMode.ALL,
    val selectedApps: Set<String> = emptySet(),
    val customBridges: String = "",
    /** Which Client Hello the fronted transports imitate. */
    val tlsProfile: TlsProfile = TlsProfile.Default,
    /** How Snowflake shapes its DTLS Client Hello. */
    val dtlsProfile: DtlsProfile = DtlsProfile.Default,
    /** Route names ending in these suffixes around the tunnel. Empty is off. */
    val bypassSuffixes: String = "",
) {
    companion object {
        /** Quad9 filters nothing and keeps no logs; reached only through the tunnel. */
        const val DEFAULT_DOH = "https://dns.quad9.net/dns-query"
        const val DEFAULT_TCP_DNS = "9.9.9.9:53"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENGINE = stringPreferencesKey("engine")
        val MANUAL_TRANSPORT = stringPreferencesKey("manual_transport")
        val BLOCK_UDP = booleanPreferencesKey("block_udp")
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val DOH_ENDPOINT = stringPreferencesKey("doh_endpoint")
        val TCP_DNS = stringPreferencesKey("tcp_dns")
        val ISOLATION = stringPreferencesKey("isolation")
        val KILL_SWITCH = booleanPreferencesKey("kill_switch")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val SNOWFLAKE_PROXY = booleanPreferencesKey("snowflake_proxy")
        val APP_MODE = stringPreferencesKey("app_mode")
        val APP_SET = stringSetPreferencesKey("app_set")
        val CUSTOM_BRIDGES = stringPreferencesKey("custom_bridges")
        val TLS_PROFILE = stringPreferencesKey("tls_profile")
        val DTLS_PROFILE = stringPreferencesKey("dtls_profile")
        val BYPASS_SUFFIXES = stringPreferencesKey("bypass_suffixes")
        val INSTALL_SEED = intPreferencesKey("install_seed")
    }

    val settings: Flow<VeilSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): VeilSettings = snapshot()

    private suspend fun snapshot(): VeilSettings {
        var result = VeilSettings()
        context.dataStore.edit { result = it.toSettings() }
        return result
    }

    private fun Preferences.toSettings() = VeilSettings(
        engine = enumOf(this[Keys.ENGINE], Engine.TOR),
        manualTransport = enumOf(this[Keys.MANUAL_TRANSPORT], Transport.SNOWFLAKE),
        blockUdp = this[Keys.BLOCK_UDP] ?: true,
        dnsMode = enumOf(this[Keys.DNS_MODE], DnsMode.TOR_DNS_PORT),
        dohEndpoint = this[Keys.DOH_ENDPOINT] ?: VeilSettings.DEFAULT_DOH,
        tcpDnsResolver = this[Keys.TCP_DNS] ?: VeilSettings.DEFAULT_TCP_DNS,
        isolation = enumOf(this[Keys.ISOLATION], IsolationMode.PER_DESTINATION),
        killSwitch = this[Keys.KILL_SWITCH] ?: true,
        autoStartOnBoot = this[Keys.AUTO_START] ?: false,
        runSnowflakeProxy = this[Keys.SNOWFLAKE_PROXY] ?: false,
        appRoutingMode = enumOf(this[Keys.APP_MODE], AppRoutingMode.ALL),
        selectedApps = this[Keys.APP_SET] ?: emptySet(),
        customBridges = this[Keys.CUSTOM_BRIDGES] ?: "",
        tlsProfile = enumOf(this[Keys.TLS_PROFILE], TlsProfile.Default),
        dtlsProfile = enumOf(this[Keys.DTLS_PROFILE], DtlsProfile.Default),
        bypassSuffixes = this[Keys.BYPASS_SUFFIXES] ?: "",
    )

    suspend fun setEngine(engine: Engine) = put(Keys.ENGINE, engine.name)
    suspend fun setManualTransport(transport: Transport) = put(Keys.MANUAL_TRANSPORT, transport.name)
    suspend fun setBlockUdp(value: Boolean) = put(Keys.BLOCK_UDP, value)
    suspend fun setDnsMode(mode: DnsMode) = put(Keys.DNS_MODE, mode.name)
    suspend fun setIsolation(mode: IsolationMode) = put(Keys.ISOLATION, mode.name)
    suspend fun setKillSwitch(value: Boolean) = put(Keys.KILL_SWITCH, value)
    suspend fun setAutoStartOnBoot(value: Boolean) = put(Keys.AUTO_START, value)
    suspend fun setRunSnowflakeProxy(value: Boolean) = put(Keys.SNOWFLAKE_PROXY, value)
    suspend fun setAppRoutingMode(mode: AppRoutingMode) = put(Keys.APP_MODE, mode.name)
    suspend fun setCustomBridges(text: String) = put(Keys.CUSTOM_BRIDGES, text)
    suspend fun setTlsProfile(profile: TlsProfile) = put(Keys.TLS_PROFILE, profile.name)
    suspend fun setDtlsProfile(profile: DtlsProfile) = put(Keys.DTLS_PROFILE, profile.name)
    suspend fun setBypassSuffixes(value: String) = put(Keys.BYPASS_SUFFIXES, value.trim())

    /**
     * A number that is stable for this installation and meaningless anywhere
     * else. Used only to pick a TLS profile, so that every copy of the app does
     * not present the same fingerprint to the same front.
     */
    suspend fun installSeed(): Int {
        var seed = 0
        context.dataStore.edit { prefs ->
            seed = prefs[Keys.INSTALL_SEED] ?: java.security.SecureRandom().nextInt().also {
                prefs[Keys.INSTALL_SEED] = it
            }
        }
        return seed
    }

    suspend fun toggleApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.APP_SET] ?: emptySet()
            prefs[Keys.APP_SET] = if (packageName in current) current - packageName else current + packageName
        }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
