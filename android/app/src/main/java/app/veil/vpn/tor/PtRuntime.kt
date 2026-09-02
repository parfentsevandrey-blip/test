package app.veil.vpn.tor

import android.content.Context
import app.veil.tun.veiltun.SnowflakeProxyEvents
import app.veil.tun.veiltun.TransportEvents
import app.veil.tun.veiltun.Transports
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.core.VeilLog
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
     * Brings up every transport that has a listener, once, and reports the
     * ports tor should be pointed at.
     *
     * All of them are started together on purpose. Each is only a local SOCKS
     * listener until a bridge line names it, so an unused one costs nothing —
     * and having them all up front is what lets tor be configured once and
     * switched between routes over its control port instead of restarted.
     */
    fun startAll(): Map<Transport, Int> {
        check(transports.ready()) { "transport controller failed to initialise" }
        configureSnowflakeDefaults()

        val ports = linkedMapOf<Transport, Int>()
        for (transport in Transport.entries) {
            if (!transport.isPluggable) continue
            val name = transport.torName
            val started = runCatching {
                if (name !in running) {
                    transports.start(name)
                    running += name
                }
                transports.port(name).toInt()
            }.getOrElse {
                VeilLog.w("pt", "$name would not start: ${it.message}")
                0
            }
            if (started > 0) {
                ports[transport] = started
                VeilLog.i("pt", "$name listening on 127.0.0.1:$started")
            }
        }
        if (ports.isEmpty()) VeilLog.e("pt", "no transport could be started")
        return ports
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

    /**
     * Fallback rendezvous settings for Snowflake.
     *
     * These only fill gaps: anything the bridge line carries wins, and the
     * lines the Tor Project hands out per country carry fresher broker fronts
     * than anything that could be compiled in here.
     */
    private fun configureSnowflakeDefaults() {
        transports.configureSnowflake(
            DEFAULT_ICE,
            DEFAULT_BROKER,
            DEFAULT_FRONTS,
            "",
            "",
            "",
            SNOWFLAKE_PEERS,
        )
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
        /**
         * Only used when a bridge line names none of its own.
         *
         * These are the Tor Project's current published set, verbatim and in
         * their order. Substituting a shorter or a "better" list is a mistake
         * this app has already made once: Snowflake uses STUN both to gather
         * candidates and to work out what the NAT in front of it does, and that
         * list is chosen for the mapping behaviour it needs — not for latency.
         */
        const val DEFAULT_ICE =
            "stun:stun.antisip.com:3478,stun:stun.epygi.com:3478," +
                "stun:stun.uls.co.za:3478,stun:stun.telnyx.com:3478," +
                "stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478," +
                "stun:stun.m-online.net:3478"

        /** More peers means a faster but noisier connection. */
        const val SNOWFLAKE_PEERS = 3L

        /** How many censored users this device will carry at once. */
        const val SNOWFLAKE_PROXY_CAPACITY = 2L
    }
}
