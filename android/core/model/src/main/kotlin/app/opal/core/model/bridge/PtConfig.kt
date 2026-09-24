package app.opal.core.model.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Built-in bridges as published by Tor Browser in
 * `tor-browser-build/projects/tor-expert-bundle/pt_config.json`, bundled verbatim as an asset.
 */
data class BuiltinBridges(val byTransport: Map<TransportKind, List<BridgeLine>>) {

    operator fun get(kind: TransportKind): List<BridgeLine> = byTransport[kind].orEmpty()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses pt_config.json; unknown transports (e.g. conjure) and bad lines are skipped. */
        fun parsePtConfig(text: String): BuiltinBridges {
            val root = json.parseToJsonElement(text) as JsonObject
            val bridges = root["bridges"] as? JsonObject ?: return BuiltinBridges(emptyMap())
            return fromMap(
                bridges.mapValues { (_, v) ->
                    (v as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                }
            )
        }

        /** Same shape as the Settings API `/builtin` response: transport name → lines. */
        fun fromMap(map: Map<String, List<String>>): BuiltinBridges {
            val result = LinkedHashMap<TransportKind, MutableList<BridgeLine>>()
            for ((_, lines) in map) {
                for (raw in lines) {
                    val line = BridgeLine.parseOrNull(raw) ?: continue
                    result.getOrPut(line.transport) { mutableListOf() } += line
                }
            }
            return BuiltinBridges(result)
        }
    }
}
