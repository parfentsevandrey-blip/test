package app.opal.core.tunnel.bridges

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.CircumventionCache
import app.opal.core.model.settings.TunnelMemory

/**
 * All bridge candidates we know, per transport, best first:
 * - built-in lines (bundled pt_config.json, or the fresher `/builtin` answer when cached);
 * - lines from the Settings API for the user's country (private `bridgedb` ones first);
 * - the user's own lines (Custom mode only). Within a transport, bridges are ordered by their
 *   success statistics.
 *
 * Snowflake is the exception: its lines are one coherent set and are never mixed (see [snowflake]).
 */
internal class BridgeCatalog(
    private val bundled: BuiltinBridges,
    /**
     * Snowflake lines the Settings API serves per country (snowflake_regional.json), e.g. other
     * domain fronts, a STUN list without servers blocked there and an AMP cache fallback for "ru".
     */
    private val regionalSnowflake: Map<String, List<BridgeLine>> = emptyMap(),
    /** Lowercase ISO 3166-1 country the device is in, when known (see TunnelRuntime). */
    private val country: () -> String? = { null },
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

    /**
     * The Snowflake set to use, as a whole: Settings API lines for the user's country, else the
     * bundled set for the country, else the built-in lines. Sets are not mixed: they reuse the same
     * placeholder addresses (192.0.2.3/4) with different arguments, and Tor keeps only the last
     * bridge per address.
     */
    fun snowflake(memory: TunnelMemory): List<BridgeLine> {
        val country = country()
        val api =
            memory.circumvention
                ?.takeIf { it.isFor(country) }
                ?.settings
                .orEmpty()
                .filter { it.type == TransportKind.Snowflake.ptName }
                .flatMap { set -> set.lines.mapNotNull(BridgeLine::parseOrNull) }
                .filter { it.transport == TransportKind.Snowflake }
        val lines =
            api.ifEmpty { country?.let { regionalSnowflake[it] }.orEmpty() }
                .ifEmpty { builtin(memory)[TransportKind.Snowflake] }
                .distinctBy { it.raw }
                .take(SNOWFLAKE_CAP)
        return if (breakSnowflake()) lines.map(::sabotaged) else lines
    }

    /**
     * Whether a cached Settings API answer holds the Snowflake set for [country]. An answer without
     * a country is the `/defaults` fallback (the server could not tell the country), whose
     * Snowflake lines are the built-in ones, and an answer for another country is stale after
     * travelling: neither may replace the regional set.
     */
    private fun CircumventionCache.isFor(country: String?): Boolean {
        val answered = this.country ?: return false
        return country == null || answered.equals(country, ignoreCase = true)
    }

    /**
     * The user's lines. A Snowflake line without any rendezvous method (url, ampcache or sqsqueue)
     * gets the missing broker, fronts, STUN and uTLS arguments of the current Snowflake set. This
     * is done per line on purpose: IPtProxy's global Snowflake defaults would also be added to
     * every other line lacking them (for example `fronts=` to AMP cache lines that use `front=`).
     */
    fun custom(settings: AppSettings, memory: TunnelMemory): List<BridgeLine> {
        val reference = snowflake(memory).firstOrNull { "url" in it.args && "ampcache" !in it.args }
        return settings.customBridges
            .mapNotNull(BridgeLine::parseOrNull)
            .map { line -> if (reference != null) completed(line, reference) else line }
            .distinctBy { it.raw }
    }

    private fun completed(line: BridgeLine, reference: BridgeLine): BridgeLine {
        if (line.transport != TransportKind.Snowflake) return line
        if (RENDEZVOUS_KEYS.any { it in line.args }) return line
        val extra = reference.args.filterKeys { it in COMPLETION_KEYS && it !in line.args }
        return line.copy(args = line.args + extra)
    }

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
            if (kind == TransportKind.Snowflake) {
                snowflake(memory).takeIf { it.isNotEmpty() }?.let { result[kind] = it }
                continue
            }
            val lines =
                (privateLines.filter { it.transport == kind } +
                        apiBuiltin.filter { it.transport == kind } +
                        builtin[kind])
                    .distinctBy { it.raw }
            if (lines.isEmpty()) continue
            result[kind] = rank(lines).take(CAPS[kind] ?: DEFAULT_CAP)
        }
        return result
    }

    private companion object {
        /** RFC 2606 `.invalid`: guaranteed never to resolve or be served by the CDN front. */
        const val BROKEN_BROKER = "https://broker.invalid/"
        const val DEFAULT_CAP = 4

        /** Whole sets: 2 built-in lines, or 4 for "ru" (2 domain-fronted + 2 AMP cache). */
        const val SNOWFLAKE_CAP = 4
        val RENDEZVOUS_KEYS = setOf("url", "ampcache", "sqsqueue")
        val COMPLETION_KEYS = setOf("url", "fronts", "front", "ice", "utls-imitate")
        val CAPS =
            mapOf(
                TransportKind.Obfs4 to 8,
                TransportKind.WebTunnel to 4,
                TransportKind.Meek to 1,
                TransportKind.Vanilla to 4,
            )
    }
}
