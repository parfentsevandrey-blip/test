package app.opal.core.tunnel.bridges

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.TunnelMemory

/**
 * All bridge candidates we know, per transport, best first:
 * - built-in lines (bundled pt_config.json, or the fresher `/builtin` answer when cached);
 * - lines from the Settings API for the user's country (private `bridgedb` ones first);
 * - the user's own lines (Custom mode only). Within a transport, bridges are ordered by their
 *   success statistics.
 */
internal class BridgeCatalog(
    private val bundled: BuiltinBridges,
    /**
     * Test hook, debug builds only (see TunnelRuntime): points every Snowflake line at a broker
     * that cannot exist, to check that the race wins through another transport.
     */
    private val breakSnowflake: () -> Boolean = { false },
) {

    fun builtin(memory: TunnelMemory): BuiltinBridges {
        val fresh = memory.circumvention?.builtin.orEmpty()
        val merged =
            if (fresh.isEmpty()) {
                bundled
            } else {
                // Keep bundled transports the API did not mention (e.g. meek if absent from
                // /builtin).
                BuiltinBridges(bundled.byTransport + BuiltinBridges.fromMap(fresh).byTransport)
            }
        if (!breakSnowflake()) return merged
        return BuiltinBridges(merged.byTransport.mapValues { (_, lines) -> lines.map(::sabotaged) })
    }

    private fun sabotaged(line: BridgeLine): BridgeLine =
        if (line.transport == TransportKind.Snowflake) {
            line.copy(args = line.args + ("url" to BROKEN_BROKER))
        } else {
            line
        }

    fun custom(settings: AppSettings): List<BridgeLine> =
        settings.customBridges.mapNotNull(BridgeLine::parseOrNull).distinctBy { it.raw }

    fun candidates(memory: TunnelMemory): Map<TransportKind, List<BridgeLine>> {
        val builtin = builtin(memory)
        val api = memory.circumvention?.settings.orEmpty()
        val privateLines =
            api.filter { it.source != "builtin" }
                .flatMap { set -> set.lines.mapNotNull(BridgeLine::parseOrNull) }
        val apiBuiltin =
            api.filter { it.source == "builtin" }
                .flatMap { set -> set.lines.mapNotNull(BridgeLine::parseOrNull) }

        fun rank(lines: List<BridgeLine>): List<BridgeLine> =
            lines
                .withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<BridgeLine>> {
                            memory.bridgeStats[it.value.id]?.score ?: 0.5
                        }
                        .thenBy { it.index }
                )
                .map { it.value }

        val result = LinkedHashMap<TransportKind, List<BridgeLine>>()
        for (kind in TransportKind.entries) {
            val lines =
                (privateLines.filter { it.transport == kind } +
                        apiBuiltin.filter { it.transport == kind } +
                        builtin[kind])
                    .distinctBy { it.raw }
            if (lines.isEmpty()) continue
            val usable = if (breakSnowflake()) lines.map(::sabotaged) else lines
            val cap = CAPS[kind] ?: DEFAULT_CAP
            // Snowflake lines are all needed (different bridges behind the same broker).
            result[kind] =
                if (kind == TransportKind.Snowflake) usable.take(cap) else rank(usable).take(cap)
        }
        return result
    }

    private companion object {
        /** RFC 2606 `.invalid`: guaranteed never to resolve or be served by the CDN front. */
        const val BROKEN_BROKER = "https://broker.invalid/"
        const val DEFAULT_CAP = 4
        val CAPS =
            mapOf(
                TransportKind.Snowflake to 2,
                TransportKind.Obfs4 to 8,
                TransportKind.WebTunnel to 4,
                TransportKind.Meek to 1,
                TransportKind.Vanilla to 4,
            )
    }
}
