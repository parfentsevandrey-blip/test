package app.opal.core.model.tor

import app.opal.core.model.bridge.BridgeLine

@DslMarker annotation class TorrcDsl

/** One configuration entry. Options that accept several values appear several times. */
data class TorrcEntry(val option: TorOption, val value: String)

/** Tor options this app sets. Keeping them in one enum makes typos impossible. */
enum class TorOption(val key: String, val isList: Boolean = false) {
    SocksPort("SocksPort", isList = true),
    ControlPort("ControlPort"),
    HttpTunnelPort("HTTPTunnelPort"),
    ClientOnly("ClientOnly"),
    UseBridges("UseBridges"),
    Bridge("Bridge", isList = true),
    ClientTransportPlugin("ClientTransportPlugin", isList = true),
    GeoIpFile("GeoIPFile"),
    GeoIpv6File("GeoIPv6File"),
    ExitNodes("ExitNodes"),
    StrictNodes("StrictNodes"),
    ReducedConnectionPadding("ReducedConnectionPadding"),
    DisableNetwork("DisableNetwork"),
    Log("Log", isList = true),
    SafeLogging("SafeLogging"),
    AvoidDiskWrites("AvoidDiskWrites"),
    DormantCanceledByStartup("DormantCanceledByStartup"),
}

/** SOCKS listener flags that do not weaken Tor's default stream isolation. */
enum class SocksFlag(val token: String) {
    IPv6Traffic("IPv6Traffic"),
    PreferIPv6("PreferIPv6"),
}

/**
 * A complete, validated Tor configuration. Rendered either as a torrc file (initial start) or as
 * `SETCONF` arguments (live reconfiguration without restarting Tor).
 */
class Torrc internal constructor(val entries: List<TorrcEntry>) {

    fun values(option: TorOption): List<String> =
        entries.filter { it.option == option }.map { it.value }

    fun value(option: TorOption): String? = entries.lastOrNull { it.option == option }?.value

    /** torrc file syntax: `Key value` per line. */
    fun render(): String = buildString {
        for (e in entries) append(e.option.key).append(' ').append(e.value).append('\n')
    }

    /**
     * Arguments for `SETCONF` covering [options]. Options that are listed but have no entries are
     * emitted without a value, which resets them to Tor's default (e.g. removes all bridges).
     */
    fun toSetConf(options: Collection<TorOption>): String =
        options.distinct().joinToString(" ") { option ->
            val values = values(option)
            if (values.isEmpty()) option.key
            else values.joinToString(" ") { "${option.key}=${ControlArgs.quote(it)}" }
        }

    override fun equals(other: Any?): Boolean = other is Torrc && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = render()
}

@TorrcDsl
class TorrcBuilder internal constructor() {
    private val entries = mutableListOf<TorrcEntry>()

    private fun set(option: TorOption, value: String) {
        require('\n' !in value && '\r' !in value) { "Newlines are not allowed in ${option.key}" }
        if (!option.isList) entries.removeAll { it.option == option }
        entries += TorrcEntry(option, value)
    }

    private fun flag(value: Boolean) = if (value) "1" else "0"

    /** SOCKS listener on loopback. `port = null` lets Tor pick a free port ("auto"). */
    fun socksPort(port: Int? = null, flags: Set<SocksFlag> = emptySet()) {
        require(port == null || port in 1..65535) { "Bad SOCKS port $port" }
        val spec = "127.0.0.1:" + (port?.toString() ?: "auto")
        set(TorOption.SocksPort, (listOf(spec) + flags.map { it.token }).joinToString(" "))
    }

    /** Explicitly disable network-reachable control and HTTP tunnel listeners. */
    fun noExtraListeners() {
        set(TorOption.ControlPort, "0")
        set(TorOption.HttpTunnelPort, "0")
    }

    fun clientOnly() = set(TorOption.ClientOnly, "1")

    fun disableNetwork(disabled: Boolean) = set(TorOption.DisableNetwork, flag(disabled))

    /** Bridges + the local proxy port for each pluggable transport they need. */
    fun bridges(lines: List<BridgeLine>, transportPorts: Map<String, Int>) {
        set(TorOption.UseBridges, flag(lines.isNotEmpty()))
        val needed = lines.mapNotNull { it.transport.ptName }.toSortedSet()
        for (pt in needed) {
            val port = requireNotNull(transportPorts[pt]) { "No local port for transport $pt" }
            require(port in 1..65535) { "Bad port $port for $pt" }
            set(TorOption.ClientTransportPlugin, "$pt socks5 127.0.0.1:$port")
        }
        for (line in lines) set(TorOption.Bridge, line.raw)
    }

    fun geoIp(v4Path: String, v6Path: String) {
        set(TorOption.GeoIpFile, v4Path)
        set(TorOption.GeoIpv6File, v6Path)
    }

    /**
     * Preferred exit country (ISO 3166-1 alpha-2). `StrictNodes 0` keeps Tor working if no suitable
     * exit is available instead of failing closed.
     */
    fun exitCountry(countryCode: String?) {
        if (countryCode == null) return
        require(countryCode.length == 2 && countryCode.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            "Bad country code $countryCode"
        }
        set(TorOption.ExitNodes, "{${countryCode.lowercase()}}")
        set(TorOption.StrictNodes, "0")
    }

    /** Only when the user enables "Экономия трафика": trades a bit of padding for data. */
    fun reducedConnectionPadding(enabled: Boolean) {
        if (enabled) set(TorOption.ReducedConnectionPadding, "1")
    }

    fun log(level: String, destination: String) = set(TorOption.Log, "$level $destination")

    /** Whether starting Tor wakes it from dormant mode (default 0 would keep it asleep). */
    fun dormantCanceledByStartup() = set(TorOption.DormantCanceledByStartup, "1")

    internal fun build() = Torrc(entries.toList())
}

fun torrc(block: TorrcBuilder.() -> Unit): Torrc = TorrcBuilder().apply(block).build()
