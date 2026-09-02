package app.veil.vpn.model

/**
 * One `Bridge` line from a torrc, parsed far enough to be shown, deduplicated
 * and reachability-probed. The raw text is kept verbatim because tor is the
 * authority on what the parameters mean.
 */
data class BridgeLine(
    val transport: String,
    val host: String,
    val port: Int,
    val fingerprint: String?,
    val params: Map<String, String>,
    val raw: String,
) {
    val transportEnum: Transport? get() = Transport.fromTorName(transport)

    /**
     * Bridges whose address is a placeholder are reached some other way — a
     * broker, a station, or a URL on the line — and the address is only an
     * identity.
     *
     * The IPv6 documentation prefix belongs here too: WebTunnel bridges are
     * published as `[2001:db8:...]:443` with the real endpoint in `url=`.
     * Treating that as a real address means probing it, timing it out, and
     * putting it on cooldown for failing to answer something it was never
     * going to answer.
     */
    val hasRoutableAddress: Boolean
        get() = !host.startsWith("192.0.2.") &&
            !host.startsWith("198.51.100.") &&
            !host.startsWith("203.0.113.") &&
            !host.lowercase().startsWith("2001:db8:") &&
            host != "0.0.0.0"

    fun torrcLine(): String = "Bridge $raw"

    /**
     * Returns the same bridge with parameters replaced or added, rebuilding the
     * raw line so tor sees the change.
     *
     * Used to pin the TLS Client Hello a transport imitates: the bridge lines
     * the Tor Project publishes carry whatever profile was current when they
     * were written, and that is a decision worth taking locally rather than
     * inheriting.
     */
    fun withParams(overrides: Map<String, String>): BridgeLine {
        if (overrides.isEmpty()) return this
        val merged = LinkedHashMap(params)
        overrides.forEach { (key, value) -> if (value.isNotEmpty()) merged[key] = value }
        return rebuiltWith(merged)
    }

    /**
     * Drops optional parameters until tor will accept the line at all.
     *
     * The SOCKS5 authentication fields are one byte of length each, so the
     * arguments cannot exceed 2 x 255 bytes, and tor enforces that when it
     * parses the line: over the limit, `Bridge` is rejected outright. Because
     * every bridge for one attempt is set in a single `SETCONF`, one over-long
     * line takes the whole rung down with it — including `UseBridges` — and the
     * result is a route that fails instantly and looks like censorship.
     *
     * Snowflake lines are the ones that get close. A published line with nine
     * STUN servers is already around 435 bytes before this app adds a TLS and a
     * DTLS preference to it, so the parameters are dropped in order of how
     * little they cost: the STUN list first, because the transport carries its
     * own, then the fingerprint preferences, then all but two rendezvous
     * fronts. What is never touched is what identifies the bridge or tells the
     * transport where the broker is.
     */
    fun withinSocksArgLimit(limit: Int = MAX_SOCKS_ARGS): BridgeLine {
        if (argsLength(params) <= limit) return this
        val trimmed = LinkedHashMap(params)

        for (key in DROPPABLE) {
            if (argsLength(trimmed) <= limit) break
            trimmed.remove(key)
        }
        if (argsLength(trimmed) > limit) {
            trimmed["fronts"]?.split(',')?.take(2)?.joinToString(",")?.let {
                trimmed["fronts"] = it
            }
        }
        return rebuiltWith(trimmed)
    }

    private fun rebuiltWith(fields: Map<String, String>): BridgeLine {
        val rebuilt = buildString {
            append(transport)
            append(' ')
            append(if (host.contains(':')) "[$host]" else host)
            append(':')
            append(port)
            fingerprint?.let { append(' ').append(it) }
            fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
        }
        return copy(params = fields, raw = rebuilt)
    }

    companion object {
        private val FINGERPRINT = Regex("^[A-Fa-f0-9]{40}$")

        /**
         * Two SOCKS5 authentication fields of 255 bytes each. A little is left
         * spare because tor escapes `;` and `\\` before measuring.
         */
        const val MAX_SOCKS_ARGS = 500

        /** Least costly to lose, first. */
        private val DROPPABLE =
            listOf("ice", "covertdtls-config", "utls-imitate", "utls")

        /**
         * The size of what tor will hand the transport: every `key=value`
         * joined with a semicolon, which is the form the SOCKS5 authentication
         * fields carry.
         */
        private fun argsLength(fields: Map<String, String>): Int {
            if (fields.isEmpty()) return 0
            return fields.entries.sumOf { it.key.length + 1 + it.value.length } +
                (fields.size - 1)
        }

        /**
         * Accepts the forms tor accepts: an optional transport name, an
         * address, an optional fingerprint, then `key=value` parameters.
         */
        fun parse(line: String): BridgeLine? {
            val text = line.trim().removePrefix("Bridge ").trim()
            if (text.isEmpty() || text.startsWith("#")) return null
            val tokens = text.split(Regex("\\s+"))
            if (tokens.isEmpty()) return null

            var index = 0
            val transport = if (!tokens[0].contains(':') && !tokens[0].contains('=')) {
                tokens[index++]
            } else {
                Transport.DIRECT.torName
            }
            if (index >= tokens.size) return null

            val endpoint = tokens[index++]
            val separator = endpoint.lastIndexOf(':')
            if (separator <= 0) return null
            val host = endpoint.substring(0, separator).trim('[', ']')
            val port = endpoint.substring(separator + 1).toIntOrNull() ?: return null

            var fingerprint: String? = null
            if (index < tokens.size && FINGERPRINT.matches(tokens[index])) {
                fingerprint = tokens[index++].uppercase()
            }

            val params = buildMap {
                while (index < tokens.size) {
                    val token = tokens[index++]
                    val eq = token.indexOf('=')
                    if (eq > 0) put(token.substring(0, eq), token.substring(eq + 1))
                }
            }
            return BridgeLine(transport, host, port, fingerprint, params, text)
        }

        fun parseAll(text: String): List<BridgeLine> =
            text.lineSequence().mapNotNull { parse(it) }.distinctBy { it.raw }.toList()
    }
}
