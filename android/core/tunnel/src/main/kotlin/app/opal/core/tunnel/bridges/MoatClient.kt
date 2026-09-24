package app.opal.core.tunnel.bridges

import app.opal.core.model.moat.DefaultsRequest
import app.opal.core.model.moat.MoatApi
import app.opal.core.model.moat.SettingsRequest
import app.opal.core.model.moat.SettingsResponse
import app.opal.core.tunnel.pt.Transports
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Circumvention Settings API client. Before Tor is up requests are domain-fronted exactly like Tor
 * Browser (meek_lite with `targets=`), otherwise they would receive decoy bridges; once Tor is up
 * they go through Tor's SOCKS port on an isolated circuit (and then the country must be sent
 * explicitly, as the server only sees the exit).
 */
internal class MoatClient(private val transports: Transports) {

    sealed interface Route {
        /** Direct connection, domain-fronted through the CDN (server sees the user's IP). */
        data object DomainFronted : Route

        data class ViaTor(val socksPort: Int) : Route
    }

    class MoatException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun settings(route: Route, country: String?): SettingsResponse =
        post(
                route,
                "settings",
                MoatApi.json.encodeToString(
                    SettingsRequest.serializer(),
                    SettingsRequest(country = country),
                ),
            )
            .let { decode(it) }

    suspend fun defaults(route: Route): SettingsResponse =
        post(
                route,
                "defaults",
                MoatApi.json.encodeToString(DefaultsRequest.serializer(), DefaultsRequest()),
            )
            .let {
                decode(it)
            }

    /** `/builtin`: transport name → current public built-in bridge lines. */
    suspend fun builtin(route: Route): Map<String, List<String>> {
        val body =
            post(
                route,
                "builtin",
                MoatApi.json.encodeToString(DefaultsRequest.serializer(), DefaultsRequest()),
            )
        val root =
            try {
                MoatApi.json.parseToJsonElement(body) as? JsonObject
            } catch (e: SerializationException) {
                throw MoatException("Bad /builtin response", e)
            } ?: throw MoatException("Bad /builtin response")
        return root
            .filterValues { it is JsonArray }
            .mapValues { (_, v) -> (v as JsonArray).map { it.jsonPrimitive.content } }
    }

    private fun decode(body: String): SettingsResponse =
        try {
            MoatApi.json.decodeFromString(SettingsResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw MoatException("Bad settings response", e)
        } catch (e: IllegalArgumentException) {
            throw MoatException("Bad settings response", e)
        }

    private suspend fun post(route: Route, endpoint: String, json: String): String =
        withTimeout(REQUEST_TIMEOUT_MS) {
            runInterruptible(Dispatchers.IO) {
                val http =
                    when (route) {
                        Route.DomainFronted ->
                            HttpsOverSocks(
                                proxyPort = transports.ensure(MEEK),
                                // pt-spec: arguments in the username, a single NUL as password.
                                username = "targets=" + Socks5.escapePtArg(MoatApi.MEEK_TARGETS),
                                password = "\u0000",
                            )
                        // Distinct credentials → Tor puts these requests on their own circuit.
                        is Route.ViaTor ->
                            HttpsOverSocks(route.socksPort, "opal-moat", "circumvention")
                    }
                val response =
                    http.post(
                        MoatApi.HOST,
                        MoatApi.BASE_PATH + endpoint,
                        MoatApi.CONTENT_TYPE,
                        json,
                    )
                if (response.status != 200)
                    throw MoatException("HTTP ${response.status} from $endpoint")
                response.body
            }
        }

    private companion object {
        const val MEEK = "meek_lite"
        const val REQUEST_TIMEOUT_MS = 90_000L
    }
}
