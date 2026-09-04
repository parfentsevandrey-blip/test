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

        /**
         * Where a Snowflake attempt spends its time, step by step.
         *
         * "offer" carries how long ICE gathering took (the wait on STUN
         * servers), "rendezvous" how long the broker took to find a proxy, and
         * "connected" how long until the data channel opened — all in
         * milliseconds from the attempt's start. These are the numbers that
         * decide what to optimise next; before they were logged, every
         * judgement about a slow Snowflake was a guess.
         */
        override fun phase(name: String, phase: String, detail: String) {
            val text = when (phase) {
                "offer" -> "$name: offer ready, ICE gathering took ${detail}ms"
                "rendezvous" -> "$name: broker answered at +${detail}ms"
                // Which of the two ways of asking won this time: "amp in
                // 1200ms" or "fronted in 900ms". Over a few connects this is
                // the map of what this network blocks.
                "rendezvous-via" -> "$name: broker reached via $detail"
                "connected" -> "$name: data channel open at +${detail}ms"
                else -> "$name: $phase $detail"
            }
            if (phase == "failed") VeilLog.w("pt", text) else VeilLog.i("pt", text)
        }
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

    /**
     * Hands Snowflake the NAT behaviour the app measured, so its first request
     * to the broker says what this network is instead of guessing. See the
     * transport module for why the guess costs a failed first attempt behind
     * a restricted NAT.
     */
    fun setSnowflakeNat(behaviour: app.veil.vpn.net.NatBehaviour) {
        if (!transports.ready()) return
        val natType = when (behaviour) {
            app.veil.vpn.net.NatBehaviour.ENDPOINT_INDEPENDENT -> "unrestricted"
            app.veil.vpn.net.NatBehaviour.SYMMETRIC -> "restricted"
            else -> ""
        }
        transports.setSnowflakeNATType(natType)
        if (natType.isNotEmpty()) VeilLog.d("pt", "snowflake told the NAT is $natType")
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
        // The other way of reaching the broker, raced inside the transport
        // against the way each bridge line names. The line the app uses names
        // the AMP cache, so in practice this pairs it with the fronted broker
        // above; a fronted line from anywhere else gets this AMP triple as its
        // partner instead.
        transports.configureSnowflakeRace(
            StrategyPlanner.AMP_BROKER,
            StrategyPlanner.AMP_CACHE,
            StrategyPlanner.AMP_FRONT,
            SNOWFLAKE_RACE_STAGGER_MILLIS,
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

        /**
         * How many WebRTC peers Snowflake keeps on hand.
         *
         * This is not bandwidth. Only one proxy carries traffic at a time — a
         * Tor circuit is a single stream and the client says so in as many
         * words — so the rest are a standby pool of already-negotiated, idle
         * data channels. What the pool buys is the recovery time when the
         * proxy in use disappears, which on Snowflake is constant: proxies are
         * volunteers' browser tabs, and a tab closes without warning. With one
         * peer that is a full stop — offer, broker rendezvous, ICE, a new data
         * channel — and it is exactly the freeze a Snowflake user learns to
         * expect. With a standby ready, the session moves onto it and the
         * pause is not long enough to notice.
         *
         * Three is the number the mechanism actually supports, and it is worth
         * writing down why rather than picking a bigger one and hoping. An idle
         * peer closes itself after twenty seconds without a message, and the
         * client collects at most one replacement every ten, so the pool
         * settles at roughly one active peer and two standbys no matter how
         * much room it is given. A larger figure does not deepen the pool; it
         * only removes the ceiling that stops the client rendezvousing
         * continuously — more requests to the broker, more of a commons that
         * other people are also queuing for, and no more resilience.
         */
        const val SNOWFLAKE_PEERS = 3L

        /**
         * How long the second way of reaching the broker waits before it
         * starts, when the first has not answered.
         *
         * Measured on the network this was built against, the way that works
         * answers in one to two seconds; three is enough that it usually gets
         * to answer alone, and short enough that a blocked first way costs
         * three seconds rather than a timeout. A successful second way also
         * goes first next time.
         */
        const val SNOWFLAKE_RACE_STAGGER_MILLIS = 3_000L

        /** How many censored users this device will carry at once. */
        const val SNOWFLAKE_PROXY_CAPACITY = 2L
    }
}
