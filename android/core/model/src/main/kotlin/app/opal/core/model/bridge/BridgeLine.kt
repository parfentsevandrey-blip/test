package app.opal.core.model.bridge

import kotlinx.serialization.Serializable

/** Pluggable transports we can run (via IPtProxy) plus plain (vanilla) bridges. */
@Serializable
enum class TransportKind(val ptName: String?) {
    Snowflake("snowflake"),
    WebTunnel("webtunnel"),
    Obfs4("obfs4"),
    Meek("meek_lite"),
    Vanilla(null);

    companion object {
        fun fromPtName(name: String?): TransportKind? =
            when (name?.lowercase()) {
                null,
                "" -> Vanilla
                "snowflake" -> Snowflake
                "webtunnel" -> WebTunnel
                "obfs4" -> Obfs4
                "meek_lite",
                "meek" -> Meek
                else -> null
            }
    }
}

/**
 * A parsed `Bridge` line: `[transport] address:port [fingerprint] [key=value ...]`.
 *
 * [raw] is the normalized line as passed to Tor (without the `Bridge ` prefix).
 */
@Serializable
data class BridgeLine(
    val transport: TransportKind,
    val host: String,
    val port: Int,
    val fingerprint: String?,
    val args: Map<String, String>,
) {
    /** Address as Tor prints it in ORCONN events, e.g. `192.0.2.3:80` or `[2001:db8::1]:443`. */
    val address: String
        get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    /** Normalized line for torrc / SETCONF (without the `Bridge` keyword). */
    val raw: String
        get() = buildString {
            transport.ptName?.let { append(it).append(' ') }
            append(address)
            fingerprint?.let { append(' ').append(it) }
            for ((k, v) in args) append(' ').append(k).append('=').append(v)
        }

    /**
     * A stable identity for statistics: fingerprint when present, otherwise transport + address (+
     * url for fronted transports, where the address is a placeholder like 192.0.2.x).
     */
    val id: String
        get() = fingerprint ?: listOfNotNull(transport.name, address, args["url"]).joinToString("|")

    override fun toString(): String = raw

    sealed interface ParseResult {
        data class Ok(val line: BridgeLine) : ParseResult

        data class Invalid(val reason: Reason, val input: String) : ParseResult
    }

    enum class Reason {
        Empty,
        UnknownTransport,
        BadAddress,
        BadPort,
        BadFingerprint,
        BadArgument,
        MissingRequiredArgument,
    }

    companion object {
        private val FINGERPRINT = Regex("^[0-9A-Fa-f]{40}$")
        private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private val IPV6 = Regex("^[0-9A-Fa-f:.]+$")

        private val required: Map<TransportKind, List<List<String>>> =
            mapOf(
                // Each inner list is an alternative set of required keys.
                TransportKind.Obfs4 to listOf(listOf("cert", "iat-mode")),
                TransportKind.WebTunnel to listOf(listOf("url")),
                TransportKind.Meek to listOf(listOf("url"), listOf("targets")),
                TransportKind.Snowflake to listOf(emptyList()),
            )

        fun parse(input: String): ParseResult {
            val text = input.trim().removePrefix("Bridge ").removePrefix("bridge ").trim()
            if (text.isEmpty()) return ParseResult.Invalid(Reason.Empty, input)
            val tokens = text.split(Regex("\\s+"))
            var idx = 0
            val first = tokens[0]
            val transport: TransportKind
            if (looksLikeAddress(first)) {
                transport = TransportKind.Vanilla
            } else {
                transport =
                    TransportKind.fromPtName(first)?.takeIf { it != TransportKind.Vanilla }
                        ?: return ParseResult.Invalid(Reason.UnknownTransport, input)
                idx = 1
            }
            val addrToken =
                tokens.getOrNull(idx) ?: return ParseResult.Invalid(Reason.BadAddress, input)
            idx++
            val (host, port) =
                splitAddress(addrToken) ?: return ParseResult.Invalid(Reason.BadAddress, input)
            if (port !in 1..65535) return ParseResult.Invalid(Reason.BadPort, input)
            var fingerprint: String? = null
            val next = tokens.getOrNull(idx)
            if (next != null && !next.contains('=')) {
                if (!FINGERPRINT.matches(next))
                    return ParseResult.Invalid(Reason.BadFingerprint, input)
                fingerprint = next.uppercase()
                idx++
            }
            val args = LinkedHashMap<String, String>()
            while (idx < tokens.size) {
                val t = tokens[idx++]
                val eq = t.indexOf('=')
                if (eq <= 0) return ParseResult.Invalid(Reason.BadArgument, input)
                args[t.substring(0, eq)] = t.substring(eq + 1)
            }
            val alternatives = required[transport]
            if (alternatives != null && alternatives.none { set -> set.all { it in args } }) {
                return ParseResult.Invalid(Reason.MissingRequiredArgument, input)
            }
            return ParseResult.Ok(BridgeLine(transport, host, port, fingerprint, args))
        }

        fun parseOrNull(input: String): BridgeLine? = (parse(input) as? ParseResult.Ok)?.line

        /**
         * Extracts every bridge line from free text: one per line, JSON/Python lists from
         * bridges.torproject.org QR codes (`['obfs4 …', 'obfs4 …']`), `Bridge ` prefixes, etc.
         */
        fun extractAll(text: String): List<BridgeLine> {
            val candidates = CANDIDATE.findAll(text).map { it.value.trim() }
            return candidates.mapNotNull(::parseOrNull).distinctBy { it.raw }.toList()
        }

        private val CANDIDATE =
            Regex(
                "(?:(?:obfs4|webtunnel|snowflake|meek_lite|meek)\\s+)?" +
                    "(?:\\d{1,3}(?:\\.\\d{1,3}){3}|\\[[0-9A-Fa-f:.]+\\]):\\d{1,5}" +
                    "(?:\\s+[0-9A-Fa-f]{40})?" +
                    "(?:\\s+[A-Za-z0-9_-]+=[^\\s'\",\\]]+(?:,[^\\s'\",\\]]+)*)*"
            )

        private fun looksLikeAddress(token: String): Boolean = splitAddress(token) != null

        private fun splitAddress(token: String): Pair<String, Int>? {
            if (token.startsWith("[")) {
                val end = token.indexOf(']')
                if (end < 0 || end + 1 >= token.length || token[end + 1] != ':') return null
                val host = token.substring(1, end)
                if (!IPV6.matches(host) || !host.contains(':')) return null
                val port = token.substring(end + 2).toIntOrNull() ?: return null
                return host to port
            }
            val colon = token.lastIndexOf(':')
            if (colon <= 0) return null
            val host = token.substring(0, colon)
            val m = IPV4.matchEntire(host) ?: return null
            if (m.groupValues.drop(1).any { it.toInt() > 255 }) return null
            val port = token.substring(colon + 1).toIntOrNull() ?: return null
            return host to port
        }
    }
}
