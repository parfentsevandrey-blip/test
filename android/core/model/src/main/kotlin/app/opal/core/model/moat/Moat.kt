package app.opal.core.model.moat

import app.opal.core.model.settings.BridgeSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Circumvention Settings API (rdsys "moat" distributor, doc/moat.md).
 *
 * Requests go to `https://bridges.torproject.org/moat/circumvention/<endpoint>` with `Content-Type:
 * application/vnd.api+json`. Every response is HTTP 200; errors are in [errors].
 */
object MoatApi {
    const val HOST = "bridges.torproject.org"
    const val BASE_PATH = "/moat/circumvention/"
    const val CONTENT_TYPE = "application/vnd.api+json"

    /**
     * Domain-fronting route used by Tor Browser 16.0 for Moat
     * (`extensions.torlauncher.bridgedb_targets`): reflector URL | fronts separated by '+'. Passed
     * to meek_lite as the `targets` argument.
     */
    const val MEEK_TARGETS = "https://1723079976.rsc.cdn77.org|cdn.zk.mk+www.cdn77.com"

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Transports we can run; the API filters its answer by this list. */
    val SUPPORTED_TRANSPORTS = listOf("obfs4", "snowflake", "webtunnel", "meek")
}

@Serializable
data class SettingsRequest(
    val country: String? = null,
    val transports: List<String> = MoatApi.SUPPORTED_TRANSPORTS,
)

/** `/defaults` accepts only the transports list. */
@Serializable
data class DefaultsRequest(val transports: List<String> = MoatApi.SUPPORTED_TRANSPORTS)

@Serializable
data class SettingsResponse(
    val settings: List<Setting>? = null,
    val country: String? = null,
    val errors: List<MoatError>? = null,
)

@Serializable data class Setting(val bridges: SettingBridges)

@Serializable
data class SettingBridges(
    val type: String,
    val source: String,
    @SerialName("bridge_strings") val bridgeStrings: List<String>? = null,
)

@Serializable data class MoatError(val code: Int? = null, val detail: String? = null)

/** Converts a successful response into cacheable bridge sets (entries without lines dropped). */
fun SettingsResponse.toBridgeSets(): List<BridgeSet> =
    settings.orEmpty().mapNotNull { s ->
        val lines = s.bridges.bridgeStrings.orEmpty().filter { it.isNotBlank() }
        if (lines.isEmpty()) null else BridgeSet(s.bridges.type, s.bridges.source, lines)
    }
