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

    /** Bridges whose address is a placeholder are reached via a broker instead. */
    val hasRoutableAddress: Boolean
        get() = !host.startsWith("192.0.2.") && !host.startsWith("198.51.100.") && host != "0.0.0.0"

    fun torrcLine(): String = "Bridge $raw"

    companion object {
        private val FINGERPRINT = Regex("^[A-Fa-f0-9]{40}$")

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
