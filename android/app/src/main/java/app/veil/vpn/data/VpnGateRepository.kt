package app.veil.vpn.data

import android.content.Context
import android.util.Base64
import app.veil.vpn.core.VeilLog
import app.veil.vpn.net.SimpleHttp
import app.veil.vpn.net.SocksProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * One volunteer VPN server from the VPN Gate list.
 *
 * The configuration is kept exactly as the project publishes it, base64 and
 * all, because it is the thing the OpenVPN client has to be handed and any
 * rewriting of it is a chance to be wrong about a directive we did not think
 * to support.
 */
data class VpnGateServer(
    val host: String,
    val ip: String,
    val country: String,
    val port: Int,
    val score: Long,
    val ping: Int,
    val configBase64: String,
) {
    val endpoint: String get() = "$ip:$port"

    /** Servers the project runs itself, rather than a volunteer's machine. */
    val isOfficial: Boolean get() = host.startsWith("public-vpn")

    fun config(): String =
        String(Base64.decode(configBase64, Base64.DEFAULT), Charsets.UTF_8)
}

/**
 * The VPN Gate server list.
 *
 * VPN Gate is an academic experiment at the University of Tsukuba: volunteers
 * run OpenVPN servers and the project publishes the whole list, addresses and
 * ready-made configurations included, to anyone who asks. There is no account
 * and no payment, which is the entire reason it is here — it is one of the very
 * few things that can work the moment the app is installed.
 *
 * What it is not is private. The volunteer operating the server sees every
 * destination, and the project asks operators to keep connection logs. Where
 * Tor is chosen for what a censor and an observer cannot learn, this is chosen
 * for getting out at all, and the app has to say which is which.
 *
 * Three sources, in order of trust. A snapshot shipped in the APK, so the app
 * works with no network yet. Whatever was last fetched, cached on disk. And the
 * live list, which is the only one that is current — volunteers come and go
 * daily — but whose own address is blocked in the places this matters, so it is
 * fetched through the meek transport when a direct request fails.
 */
class VpnGateRepository(private val context: Context) {

    private val cacheFile = File(context.filesDir, "vpngate-servers.json")

    private val _servers = MutableStateFlow<List<VpnGateServer>>(emptyList())
    val servers: StateFlow<List<VpnGateServer>> = _servers.asStateFlow()

    private val _lastRefresh = MutableStateFlow(0L)
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    /** Endpoints that have failed since one of them last worked. */
    private val failures = mutableMapOf<String, Int>()

    suspend fun load() = withContext(Dispatchers.IO) {
        val bundled = runCatching { readList(context.assets.open(ASSET).bufferedReader().readText()) }
            .getOrElse {
                VeilLog.w("vpngate", "no bundled list: $it")
                emptyList()
            }
        val cached = runCatching {
            if (cacheFile.exists()) readList(cacheFile.readText()) else emptyList()
        }.getOrDefault(emptyList())
        // Cached first: it is newer by definition, and a bundled entry that is
        // still live simply appears once.
        _servers.value = (cached + bundled).distinctBy { it.endpoint }
        VeilLog.i("vpngate", "${_servers.value.size} server(s) known")
    }

    /**
     * The order to try servers in.
     *
     * The project's own machines go first. They are the long-lived ones — the
     * same dozen addresses for years — which makes them both the most likely to
     * be up and the most likely to be on a blocklist. Volunteers are the
     * opposite: unknown to a censor, and gone by Thursday. So the reliable ones
     * are tried first and the obscure ones are what is left when they fail,
     * which is the right way round for a list that is also a fallback.
     *
     * Within each group: fewest recent failures, then the project's own score,
     * which folds together uptime and throughput.
     */
    fun ranked(limit: Int = 6): List<VpnGateServer> =
        _servers.value
            .sortedWith(
                compareBy<VpnGateServer> { failures[it.endpoint] ?: 0 }
                    .thenByDescending { it.isOfficial }
                    .thenByDescending { it.score },
            )
            .take(limit)

    fun recordFailure(server: VpnGateServer) {
        failures[server.endpoint] = (failures[server.endpoint] ?: 0) + 1
    }

    fun recordSuccess(server: VpnGateServer) {
        failures.remove(server.endpoint)
    }

    /**
     * Fetches the live list, directly if that works and through meek if not.
     *
     * The API is plain HTTPS to a single well-known name, which is exactly the
     * shape a censor blocks first, so the fronted path is not a fallback for
     * unusual cases — on the networks this app exists for it is the normal one.
     */
    suspend fun refresh(meekPort: Int?): Int = withContext(Dispatchers.IO) {
        val text = fetch(null) ?: meekPort?.let { port ->
            VeilLog.i("vpngate", "direct list fetch failed; trying through meek")
            fetch(SocksProxy("127.0.0.1", port))
        }
        if (text == null) {
            VeilLog.w("vpngate", "could not fetch the server list")
            return@withContext 0
        }
        val parsed = parseCsv(text)
        if (parsed.isEmpty()) {
            VeilLog.w("vpngate", "the list came back with no usable servers")
            return@withContext 0
        }
        runCatching { cacheFile.writeText(writeList(parsed)) }
            .onFailure { VeilLog.w("vpngate", "could not cache the list: $it") }
        _lastRefresh.value = System.currentTimeMillis()
        load()
        parsed.size
    }

    private suspend fun fetch(proxy: SocksProxy?): String? = runCatching {
        val response = SimpleHttp.get(API, proxy = proxy, timeoutMillis = 45_000)
        if (!response.isSuccess) null else response.text()
    }.getOrNull()

    /**
     * Parses the published CSV.
     *
     * Only TCP servers are kept. OpenVPN over UDP is the faster of the two and
     * the one to prefer anywhere else, but it is also trivially identified and
     * dropped by the equipment this has to pass, and a route that cannot
     * connect is not faster.
     */
    private fun parseCsv(text: String): List<VpnGateServer> {
        val lines = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .map { it.removePrefix("#") }
            .toList()
        if (lines.size < 2) return emptyList()
        val header = lines.first().split(',').map { it.trim() }
        val index = header.withIndex().associate { (i, name) -> name to i }

        fun column(row: List<String>, name: String): String =
            index[name]?.let { row.getOrNull(it) }?.trim().orEmpty()

        return lines.drop(1).mapNotNull { line ->
            val row = line.split(',')
            val configBase64 = column(row, "OpenVPN_ConfigData_Base64")
            if (configBase64.isBlank()) return@mapNotNull null
            val config = runCatching {
                String(Base64.decode(configBase64, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            val (proto, port) = remoteOf(config)
            if (proto != "tcp" || port <= 0) return@mapNotNull null
            VpnGateServer(
                host = column(row, "HostName"),
                ip = column(row, "IP"),
                country = column(row, "CountryShort").uppercase(),
                port = port,
                score = column(row, "Score").toLongOrNull() ?: 0,
                ping = column(row, "Ping").toIntOrNull() ?: 0,
                configBase64 = configBase64,
            )
        }
    }

    /** The protocol and port a configuration actually connects to. */
    private fun remoteOf(config: String): Pair<String, Int> {
        var proto = ""
        var port = 0
        config.lineSequence().forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            when {
                fields.size >= 2 && fields[0] == "proto" -> proto = fields[1].lowercase()
                fields.size >= 3 && fields[0] == "remote" -> port = fields[2].toIntOrNull() ?: 0
            }
        }
        return proto to port
    }

    private fun readList(json: String): List<VpnGateServer> {
        val root = JSONObject(json)
        val array = root.optJSONArray("servers") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val entry = array.optJSONObject(i) ?: return@mapNotNull null
            val configBase64 = entry.optString("config")
            if (configBase64.isBlank()) return@mapNotNull null
            VpnGateServer(
                host = entry.optString("host"),
                ip = entry.optString("ip"),
                country = entry.optString("country").uppercase(),
                port = entry.optInt("port"),
                score = entry.optLong("score"),
                ping = entry.optInt("ping"),
                configBase64 = configBase64,
            )
        }
    }

    private fun writeList(servers: List<VpnGateServer>): String {
        val array = JSONArray()
        // Bounded on purpose. The full list is well over a megabyte of embedded
        // certificates, and nothing here will ever try the two hundredth entry.
        servers.take(CACHE_LIMIT).forEach { server ->
            array.put(
                JSONObject()
                    .put("host", server.host)
                    .put("ip", server.ip)
                    .put("country", server.country)
                    .put("port", server.port)
                    .put("score", server.score)
                    .put("ping", server.ping)
                    .put("config", server.configBase64),
            )
        }
        return JSONObject()
            .put("updated", System.currentTimeMillis())
            .put("servers", array)
            .toString()
    }

    private companion object {
        const val ASSET = "vpngate_servers.json"
        const val API = "https://www.vpngate.net/api/iphone/"
        const val CACHE_LIMIT = 40
    }
}
