package app.veil.vpn.tor

import android.content.Context
import app.veil.tun.veiltun.SnowflakeProxyEvents
import app.veil.tun.veiltun.TransportEvents
import app.veil.tun.veiltun.Transports
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.core.VeilLog
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.Transport
import java.io.File

/**
 * Owns the pluggable transport clients.
 *
 * The transports are the Tor Project's own lyrebird (obfs4, meek_lite,
 * webtunnel) and Snowflake, compiled into the same native library as the
 * tunnel. Running them as a library rather than as spawned executables matters
 * on Android, where executing a file from the app's data directory has been
 * unreliable since API 29.
 *
 * Each transport listens on a local SOCKS port, and tor is pointed at it with a
 * `ClientTransportPlugin ... socks5 127.0.0.1:port` line.
 */
class PtRuntime(context: Context) {

    private val stateDir: File = File(context.filesDir, "pt-state").apply { mkdirs() }

    private val events = object : TransportEvents {
        override fun connected(name: String) = VeilLog.i("pt", "$name connected")
        override fun failed(name: String, message: String) = VeilLog.w("pt", "$name: $message")
        override fun stopped(name: String, message: String) {
            if (message.isEmpty()) {
                VeilLog.d("pt", "$name stopped")
            } else {
                VeilLog.w("pt", "$name stopped: $message")
            }
        }
    }

    private val transports: Transports = Veiltun.newTransports(stateDir.absolutePath, events)

    private val running = mutableSetOf<String>()

    val lyrebirdVersion: String get() = runCatching { Veiltun.lyrebirdVersion() }.getOrDefault("?")
    val snowflakeVersion: String get() = runCatching { Veiltun.snowflakeVersion() }.getOrDefault("?")

    /**
     * Starts whatever the chosen transport needs and returns the local SOCKS
     * port tor should be pointed at, or null for a direct connection.
     *
     * @param bridges the bridge lines for this attempt. Snowflake takes its
     *   broker, fronts and ICE servers from them rather than from a config file.
     * @param ampRendezvous ask Snowflake to reach its broker through Google's
     *   AMP cache instead of a fronted request. Slower, but it survives where
     *   the fronted broker request does not.
     */
    fun start(
        transport: Transport,
        bridges: List<BridgeLine>,
        ampRendezvous: Boolean = false,
    ): Int? {
        if (transport == Transport.DIRECT) return null
        check(transports.ready()) { "transport controller failed to initialise" }

        if (transport == Transport.SNOWFLAKE) {
            configureSnowflake(bridges.firstOrNull(), ampRendezvous)
        }

        val name = transport.torName
        if (name !in running) {
            transports.start(name)
            running += name
        }
        val port = transports.port(name).toInt()
        check(port > 0) { "${transport.label} started but is not listening" }
        VeilLog.i("pt", "$name listening on 127.0.0.1:$port")
        return port
    }

    /** The meek port, started on demand, for fronted requests to the bridge API. */
    fun meekPortForFrontedRequests(): Int {
        check(transports.ready()) { "transport controller failed to initialise" }
        val name = Transport.MEEK.torName
        if (name !in running) {
            transports.start(name)
            running += name
        }
        return transports.port(name).toInt()
    }

    private fun configureSnowflake(bridge: BridgeLine?, ampRendezvous: Boolean) {
        val params = bridge?.params.orEmpty()
        transports.configureSnowflake(
            params["ice"] ?: DEFAULT_ICE,
            params["url"] ?: DEFAULT_BROKER,
            params["fronts"] ?: params["front"] ?: DEFAULT_FRONTS,
            if (ampRendezvous) params["ampcache"] ?: DEFAULT_AMP_CACHE else "",
            params["sqsqueue"].orEmpty(),
            params["sqscreds"].orEmpty(),
            SNOWFLAKE_PEERS,
        )
        VeilLog.i(
            "pt",
            "snowflake $snowflakeVersion configured for " +
                if (ampRendezvous) "AMP cache rendezvous" else "fronted rendezvous",
        )
    }

    /** Stops Snowflake only, so a retry can restart it with other rendezvous settings. */
    fun stopSnowflake() {
        val name = Transport.SNOWFLAKE.torName
        if (running.remove(name)) {
            runCatching { transports.stop(name) }
            VeilLog.i("pt", "snowflake stopped")
        }
    }

    fun stopAll() {
        if (running.isEmpty()) return
        running.clear()
        runCatching { transports.stopAll() }
        VeilLog.i("pt", "transports stopped")
    }

    // --- Giving back --------------------------------------------------------

    val isSnowflakeProxyRunning: Boolean
        get() = runCatching { Veiltun.isSnowflakeProxyRunning() }.getOrDefault(false)

    /**
     * Runs a Snowflake proxy on this device, so that someone behind a firewall
     * can use it as their WebRTC hop. This is the one thing an uncensored user
     * can do that directly helps a censored one.
     */
    fun startSnowflakeProxy(onClientConnected: () -> Unit) {
        if (isSnowflakeProxyRunning) return
        Veiltun.startSnowflakeProxy(
            SNOWFLAKE_PROXY_CAPACITY,
            object : SnowflakeProxyEvents {
                override fun clientConnected() = onClientConnected()
                override fun clientDisconnected(country: String) =
                    VeilLog.d("pt", "snowflake client from $country disconnected")

                override fun clientFailed() =
                    VeilLog.d("pt", "snowflake client rendezvous failed")
            },
        )
        VeilLog.i("pt", "snowflake proxy started")
    }

    fun stopSnowflakeProxy() {
        if (!isSnowflakeProxyRunning) return
        runCatching { Veiltun.stopSnowflakeProxy() }
        VeilLog.i("pt", "snowflake proxy stopped")
    }

    private companion object {
        const val DEFAULT_BROKER = "https://1098762253.rsc.cdn77.org/"
        const val DEFAULT_FRONTS = "app.datapacket.com,www.datapacket.com"
        const val DEFAULT_AMP_CACHE = "https://cdn.ampproject.org/"
        const val DEFAULT_ICE =
            "stun:stun.l.google.com:19302,stun:stun.voipgate.com:3478," +
                "stun:stun.hot-chilli.net:3478,stun:stun.m-online.net:3478"

        /** More peers means a faster but noisier connection. */
        const val SNOWFLAKE_PEERS = 3L

        /** How many censored users this device will carry at once. */
        const val SNOWFLAKE_PROXY_CAPACITY = 2L
    }
}
