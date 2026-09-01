package app.veil.vpn.data

import android.content.Context
import app.veil.vpn.core.VeilLog
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.Transport
import app.veil.vpn.net.MoatClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the app knows about how to reach the Tor network.
 *
 * Three sources, in increasing order of freshness:
 *
 *  1. A snapshot of the Tor Project's built-in bridges, shipped as an asset so
 *     the very first connect needs no network of its own.
 *  2. The same list re-fetched from the bridge API, cached on disk. Built-in
 *     bridges are public and therefore the first thing a censor enumerates, so
 *     a stale snapshot is a real failure mode.
 *  3. Bridges the user obtained themselves, by solving a CAPTCHA in the app or
 *     by pasting lines from Telegram, email or a friend.
 *
 * Everything is deduplicated by the raw line, and a bridge that failed recently
 * is sorted last rather than dropped, because "failed" often means "the network
 * was down", not "this bridge is blocked".
 */
class BridgeRepository(
    private val context: Context,
    private val moat: MoatClient,
) {
    private val cacheFile = File(context.filesDir, "bridges-cache.json")
    private val customFile = File(context.filesDir, "bridges-custom.txt")
    private val failureFile = File(context.filesDir, "bridge-failures.json")

    private val _bridges = MutableStateFlow<Map<Transport, List<BridgeLine>>>(emptyMap())
    val bridges: StateFlow<Map<Transport, List<BridgeLine>>> = _bridges.asStateFlow()

    private val _lastRefresh = MutableStateFlow(0L)
    val lastRefresh: StateFlow<Long> = _lastRefresh.asStateFlow()

    private var failures: MutableMap<String, Int> = mutableMapOf()

    /**
     * Bridges the Tor Project currently recommends for this country.
     *
     * Kept separate from the stored lists and tried first. These are the
     * freshest thing available — the built-in set is public and therefore the
     * first to be enumerated, and the broker fronts baked into it go stale
     * exactly when they matter most — so they are held only for the session and
     * never allowed to age on disk.
     */
    @Volatile
    private var recommended: Map<Transport, List<BridgeLine>> = emptyMap()

    suspend fun load() = withContext(Dispatchers.IO) {
        failures = readFailures()
        val fromCache = runCatching { readGrouped(cacheFile.readText()) }.getOrNull()
        val fromAsset = runCatching {
            readGrouped(context.assets.open(BUILTIN_ASSET).bufferedReader().use { it.readText() })
        }.getOrDefault(emptyMap())

        val merged = merge(fromAsset, fromCache ?: emptyMap(), customBridges())
        _bridges.value = merged
        _lastRefresh.value = if (cacheFile.exists()) cacheFile.lastModified() else 0
        VeilLog.i(
            "bridges",
            "loaded " + merged.entries.joinToString { "${it.key.torName}=${it.value.size}" },
        )
    }

    /** Re-fetches the public bridge list. Safe to call on every connect. */
    suspend fun refreshFromMoat(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val fetched = moat.builtinBridges()
            if (fetched.isEmpty()) error("bridge API returned nothing")
            cacheFile.writeText(writeGrouped(fetched))
            _lastRefresh.value = System.currentTimeMillis()
            load()
            fetched.values.sumOf { it.size }
        }.onFailure { VeilLog.w("bridges", "refresh failed: $it") }
    }

    /** Adds bridges the user obtained out of band. */
    suspend fun addCustom(text: String): Int = withContext(Dispatchers.IO) {
        val parsed = BridgeLine.parseAll(text)
        if (parsed.isEmpty()) return@withContext 0
        val existing = customBridges().values.flatten().map { it.raw }.toSet()
        val fresh = parsed.filter { it.raw !in existing }
        if (fresh.isNotEmpty()) {
            customFile.appendText(fresh.joinToString("\n", postfix = "\n") { it.raw })
            load()
        }
        fresh.size
    }

    suspend fun replaceCustom(text: String) = withContext(Dispatchers.IO) {
        customFile.writeText(text.trim() + "\n")
        load()
    }

    suspend fun customText(): String = withContext(Dispatchers.IO) {
        runCatching { customFile.readText() }.getOrDefault("")
    }

    private fun customBridges(): Map<Transport, List<BridgeLine>> {
        val text = runCatching { customFile.readText() }.getOrNull() ?: return emptyMap()
        return BridgeLine.parseAll(text).groupBy { it.transportEnum ?: Transport.OBFS4 }
    }

    /** Replaces what the bridge API says is currently right for this country. */
    fun setRecommended(byTransport: Map<Transport, List<BridgeLine>>) {
        if (byTransport.isEmpty()) return
        recommended = byTransport
        VeilLog.i(
            "bridges",
            "recommended for this country: " +
                byTransport.entries.joinToString { "${it.key.torName}=${it.value.size}" },
        )
    }

    /** Bridges for one transport, best candidates first. */
    fun forTransport(transport: Transport, limit: Int = 6): List<BridgeLine> {
        val preferred = recommended[transport].orEmpty()
        val known = _bridges.value[transport].orEmpty()
        return (preferred + known)
            .distinctBy { it.raw }
            .sortedBy { failures[it.raw] ?: 0 }
            .take(limit)
    }

    /** True when the bridge API has given us something for this transport. */
    fun hasRecommended(transport: Transport): Boolean =
        recommended[transport].orEmpty().isNotEmpty()

    /** Address and port pairs for the reachability probe. */
    fun probeTargets(transport: Transport = Transport.OBFS4): List<Pair<String, Int>> =
        forTransport(transport, limit = 6)
            .filter { it.hasRoutableAddress }
            .map { it.host to it.port }

    fun recordFailure(bridges: List<BridgeLine>) {
        bridges.forEach { failures[it.raw] = (failures[it.raw] ?: 0) + 1 }
        writeFailures()
    }

    fun recordSuccess(bridges: List<BridgeLine>) {
        bridges.forEach { failures.remove(it.raw) }
        writeFailures()
    }

    private fun merge(vararg sources: Map<Transport, List<BridgeLine>>): Map<Transport, List<BridgeLine>> {
        val result = linkedMapOf<Transport, MutableList<BridgeLine>>()
        val seen = mutableSetOf<String>()
        sources.reversed().forEach { source ->
            source.forEach { (transport, lines) ->
                val bucket = result.getOrPut(transport) { mutableListOf() }
                lines.forEach { line -> if (seen.add(line.raw)) bucket.add(line) }
            }
        }
        return result
    }

    private fun readGrouped(json: String): Map<Transport, List<BridgeLine>> {
        val root = JSONObject(json)
        val result = linkedMapOf<Transport, List<BridgeLine>>()
        for (key in root.keys()) {
            val transport = Transport.fromTorName(normalise(key)) ?: continue
            val array = root.optJSONArray(key) ?: continue
            val lines = (0 until array.length()).mapNotNull { BridgeLine.parse(array.getString(it)) }
            if (lines.isNotEmpty()) result[transport] = lines
        }
        return result
    }

    private fun writeGrouped(grouped: Map<String, List<BridgeLine>>): String {
        val root = JSONObject()
        grouped.forEach { (key, lines) ->
            root.put(key, JSONArray(lines.map { it.raw }))
        }
        return root.toString()
    }

    private fun normalise(key: String) = when (key.lowercase()) {
        "meek", "meek-azure", "meek_azure" -> "meek_lite"
        else -> key.lowercase()
    }

    private fun readFailures(): MutableMap<String, Int> = runCatching {
        val root = JSONObject(failureFile.readText())
        val map = mutableMapOf<String, Int>()
        for (key in root.keys()) map[key] = root.optInt(key)
        map
    }.getOrDefault(mutableMapOf())

    private fun writeFailures() = runCatching {
        val root = JSONObject()
        failures.forEach { (key, value) -> root.put(key, value) }
        failureFile.writeText(root.toString())
    }.onFailure { VeilLog.w("bridges", "could not persist failure counts: $it") }

    private companion object {
        const val BUILTIN_ASSET = "builtin_bridges.json"
    }
}
