package app.veil.vpn.net

import app.veil.vpn.core.VeilLog
import app.veil.vpn.model.BridgeLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A CAPTCHA the bridge authority wants solved before it hands out addresses. */
data class MoatChallenge(
    val transport: String,
    val challenge: String,
    val imagePngBase64: String,
)

/**
 * Client for the Tor Project's bridge distribution API ("Moat").
 *
 * Two families of call matter here:
 *
 *  - The `circumvention` endpoints are the unauthenticated side. It returns the built-in
 *    bridges shipped with Tor Browser and, given a country code, the transports
 *    the Tor Project currently believes work there. This is what lets the app
 *    make a good first guess without asking the user for anything.
 *  - `fetch` and `check` are the CAPTCHA-gated side that hands out fresh, less
 *    widely known bridges when the built-in ones have been enumerated and
 *    blocked.
 *
 * Every call can be routed through a pluggable transport, because the API's own
 * domain is among the first things a censor blocks.
 */
class MoatClient(private val proxyProvider: () -> SocksProxy? = { null }) {

    private companion object {
        const val BASE = "https://bridges.torproject.org/moat"
        const val CONTENT_TYPE = "application/vnd.api+json"
        val HEADERS = mapOf("Content-Type" to CONTENT_TYPE, "Accept" to CONTENT_TYPE)
    }

    /** Bridges bundled with Tor Browser, keyed by transport name. */
    suspend fun builtinBridges(): Map<String, List<BridgeLine>> = withContext(Dispatchers.IO) {
        val response = call("$BASE/circumvention/builtin", "{}")
        val json = JSONObject(response)
        buildMap {
            for (key in json.keys()) {
                val array = json.optJSONArray(key) ?: continue
                val lines = (0 until array.length())
                    .mapNotNull { BridgeLine.parse(array.getString(it)) }
                if (lines.isNotEmpty()) put(normaliseKey(key), lines)
            }
        }
    }

    /**
     * What the Tor Project recommends for a country, most-likely first. An empty
     * result means "nothing special is known", which is itself useful: it says
     * a direct connection is probably fine.
     */
    suspend fun settingsFor(countryCode: String): List<CircumventionSetting> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("country", countryCode.lowercase())
                .put("transports", JSONArray(listOf("obfs4", "snowflake", "webtunnel", "meek")))
                .toString()
            parseSettings(call("$BASE/circumvention/settings", body))
        }

    /** The same recommendations for every country, cached offline as an asset. */
    fun parseMap(json: String, countryCode: String): List<CircumventionSetting> {
        val root = JSONObject(json)
        val country = root.optJSONObject(countryCode.lowercase()) ?: return emptyList()
        return parseSettingsArray(country.optJSONArray("settings"))
    }

    private fun parseSettings(raw: String): List<CircumventionSetting> {
        val root = JSONObject(raw)
        return parseSettingsArray(root.optJSONArray("settings"))
    }

    private fun parseSettingsArray(array: JSONArray?): List<CircumventionSetting> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val bridges = array.optJSONObject(index)?.optJSONObject("bridges") ?: return@mapNotNull null
            val type = bridges.optString("type").ifEmpty { return@mapNotNull null }
            val strings = bridges.optJSONArray("bridge_strings")
            val lines = (0 until (strings?.length() ?: 0))
                .mapNotNull { BridgeLine.parse(strings!!.getString(it)) }
            CircumventionSetting(normaliseKey(type), lines, bridges.optString("source"))
        }
    }

    /** Asks for a CAPTCHA so we can request bridges the censor has not seen. */
    suspend fun requestChallenge(transport: String = "obfs4"): MoatChallenge =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("version", "0.1.0")
                        .put("type", "client-transports")
                        .put("supported", JSONArray(listOf(transport))),
                ),
            ).toString()

            val data = firstDataObject(call("$BASE/fetch", body))
                ?: error("Moat returned no challenge")
            MoatChallenge(
                transport = data.optString("transport", transport),
                challenge = data.getString("challenge"),
                imagePngBase64 = data.getString("image"),
            )
        }

    /** Redeems a solved CAPTCHA for bridge lines. */
    suspend fun solveChallenge(challenge: MoatChallenge, solution: String): List<BridgeLine> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put(
                "data",
                JSONArray().put(
                    JSONObject()
                        .put("id", "2")
                        .put("version", "0.1.0")
                        .put("type", "moat-solution")
                        .put("transport", challenge.transport)
                        .put("challenge", challenge.challenge)
                        .put("solution", solution.trim())
                        .put("qrcode", "false"),
                ),
            ).toString()

            val data = firstDataObject(call("$BASE/check", body))
                ?: error("Moat rejected the solution")
            val array = data.optJSONArray("bridges") ?: return@withContext emptyList()
            (0 until array.length()).mapNotNull { BridgeLine.parse(array.getString(it)) }
        }

    private fun firstDataObject(raw: String): JSONObject? {
        val root = JSONObject(raw)
        root.optJSONArray("errors")?.let { errors ->
            if (errors.length() > 0) {
                val detail = errors.optJSONObject(0)?.optString("detail").orEmpty()
                error("Moat: ${detail.ifEmpty { "request rejected" }}")
            }
        }
        return root.optJSONArray("data")?.optJSONObject(0)
    }

    /**
     * Tries the API directly first: on an uncensored network that is one round
     * trip. Only if that fails does it pay the cost of a fronted request.
     */
    private fun call(url: String, body: String): String {
        val payload = body.toByteArray(Charsets.UTF_8)
        val direct = runCatching { SimpleHttp.post(url, payload, HEADERS) }
        direct.getOrNull()?.takeIf { it.isSuccess }?.let { return it.text() }

        val proxy = proxyProvider()
        if (proxy == null) {
            direct.exceptionOrNull()?.let { throw it }
            error("Moat: HTTP ${direct.getOrNull()?.code} and no fronted route available")
        }

        VeilLog.i("moat", "direct request failed, retrying through a fronted transport")
        val fronted = SimpleHttp.post(url, payload, HEADERS, proxy)
        if (!fronted.isSuccess) error("Moat: HTTP ${fronted.code} over the fronted route")
        return fronted.text()
    }

    /** The API says "meek" where torrc says "meek_lite". */
    private fun normaliseKey(key: String): String = when (key.lowercase()) {
        "meek", "meek-azure", "meek_azure" -> "meek_lite"
        else -> key.lowercase()
    }
}

data class CircumventionSetting(
    val transport: String,
    val bridges: List<BridgeLine>,
    val source: String,
)
