package app.veil.vpn.net

import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.EndpointCooldown
import app.veil.vpn.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Keeps probe traffic off the tunnel it is measuring.
 *
 * Once the VPN is up, every socket this process opens would otherwise be routed
 * back into our own TUN, so a measurement would only ever tell us about the
 * tunnel. VpnService.protect pins a socket to the real network instead.
 */
interface SocketProtector {
    fun protect(socket: Socket)
    fun protect(socket: DatagramSocket)

    object None : SocketProtector {
        override fun protect(socket: Socket) = Unit
        override fun protect(socket: DatagramSocket) = Unit
    }
}

/**
 * What happened when we tried to reach a host, in the terms that distinguish
 * the ways a connection can be stopped.
 *
 * The distinction that matters is the last two. A reset is a censor saying no;
 * a freeze is a censor saying nothing, which looks to an application exactly
 * like a slow network and is what current Russian DPI does — it lets the TCP
 * handshake finish, reads the Client Hello, and then simply stops forwarding.
 */
enum class PathVerdict {
    OPEN,

    /** The TCP handshake never completed: address or port level blocking. */
    TCP_BLOCKED,

    /** TCP came up and the TLS handshake was torn down: filtering by name. */
    TLS_RESET,

    /**
     * TCP came up, the Client Hello went out, and nothing came back. The
     * signature of a blackhole penalty rather than an outage.
     */
    TLS_FROZEN,

    UNKNOWN,
}

/** One measurement, in terms the diagnostics screen can print verbatim. */
data class ProbeResult(
    val name: String,
    val ok: Boolean,
    val detail: String,
    val millis: Long,
    val verdict: PathVerdict = if (ok) PathVerdict.OPEN else PathVerdict.UNKNOWN,
    /** Time to complete the TCP handshake, or -1 if it never did. */
    val connectMillis: Long = -1,
    /** Time to complete the TLS handshake, or -1 if it never did. */
    val handshakeMillis: Long = -1,
)

/**
 * What the network in front of us looks like.
 *
 * The point is not to name the censor but to answer the questions that change
 * what we do next: can we open TCP to Tor at all, is TLS being stopped and in
 * which of the two ways, do the big CDNs still work, and is UDP alive. Those
 * answers are what order the escalation ladder.
 */
data class ProbeReport(
    val results: List<ProbeResult> = emptyList(),
    val directTorReachable: Boolean = false,
    val publishedBridgesReachable: Boolean = false,
    val sniFilteringSuspected: Boolean = false,
    /**
     * A TLS connection was accepted and then blackholed. This is the signature
     * of the penalty-based blocking deployed in Russia through 2026, and it
     * changes strategy: retrying the same endpoint is worthless for the length
     * of the penalty, and switching fingerprint against it is reported to make
     * the penalty longer.
     */
    val freezeSuspected: Boolean = false,
    val cdnReachable: Boolean = false,
    val udpUsable: Boolean = false,
    val completedAtMillis: Long = 0,
) {
    val hasRun: Boolean get() = completedAtMillis > 0

    /** A one-line verdict for the home screen. */
    fun summary(): String = when {
        !hasRun -> "Network not measured yet"
        freezeSuspected && cdnReachable ->
            "Connections are being accepted and then blackholed; CDN paths still work"
        freezeSuspected -> "Connections are being accepted and then blackholed"
        directTorReachable && !sniFilteringSuspected -> "Network looks open"
        publishedBridgesReachable -> "Tor is filtered; bridges still reachable"
        cdnReachable -> "Bridges blocked; large CDNs still reachable"
        udpUsable -> "Heavy filtering; peer-to-peer rendezvous still possible"
        else -> "Severe filtering on every path we tested"
    }
}

/**
 * Active measurements against fixed, publicly documented endpoints.
 *
 * Everything here is a plain connectivity test to infrastructure the app would
 * contact anyway. Nothing is sent about the user, and no third-party analytics
 * endpoint is involved. Handshakes are paced through [HandshakeGovernor] so the
 * measurement itself never trips the burst heuristic it is measuring.
 */
class NetworkProbe(
    private val protector: SocketProtector = SocketProtector.None,
    private val cooldown: EndpointCooldown? = null,
) {

    private companion object {
        /**
         * Tor's directory authorities, as compiled into tor. Reaching one over
         * TCP says the plain protocol is at least routable.
         */
        val DIRECTORY_AUTHORITIES = listOf(
            "128.31.0.39" to 9131,
            "86.59.21.38" to 80,
            "45.66.33.45" to 80,
            "131.188.40.189" to 80,
            "193.23.244.244" to 80,
            "171.25.193.9" to 443,
        )

        /**
         * Hosts that must work for meek and Snowflake rendezvous to work. These
         * are large CDNs, which censors are reluctant to block wholesale
         * because of what else lives behind them.
         */
        val CDN_HOSTS = listOf(
            "www.phpmyadmin.net" to 443,
            "app.datapacket.com" to 443,
            "cdn.jsdelivr.net" to 443,
        )

        /** STUN servers Snowflake uses to discover its own address. */
        val STUN_SERVERS = listOf(
            "stun.voipgate.com" to 3478,
            "stun.hot-chilli.net" to 3478,
            "stun.m-online.net" to 3478,
        )

        /** A name a censor has a reason to filter. */
        const val CENSORED_CANARY = "www.torproject.org"

        /** A name a censor has no reason to filter, as a control. */
        const val NEUTRAL_CANARY = "cdn.jsdelivr.net"
    }

    suspend fun run(
        obfs4Bridges: List<Pair<String, Int>>,
        onProgress: (done: Int, total: Int, note: String) -> Unit = { _, _, _ -> },
    ): ProbeReport = coroutineScope {
        val total = 6
        var done = 0
        fun step(note: String) {
            done += 1
            onProgress(done, total, note)
        }

        onProgress(0, total, "Reaching for the Tor directory")

        // Started together, but every TLS handshake inside them is paced.
        val directDeferred = async { tcpAny("Tor directory authorities", DIRECTORY_AUTHORITIES) }
        val bridgeDeferred = async { tcpAny("Published obfs4 bridges", obfs4Bridges) }
        val cdnDeferred = async { tcpAny("Large CDNs", CDN_HOSTS) }
        val censoredDeferred = async { tlsCanary("TLS to $CENSORED_CANARY", CENSORED_CANARY) }
        val neutralDeferred = async { tlsCanary("TLS to $NEUTRAL_CANARY", NEUTRAL_CANARY) }
        val udpDeferred = async { stunReachable() }

        val direct = directDeferred.await().also { step("Tor directory") }
        val bridges = bridgeDeferred.await().also { step("Bridges") }
        val cdn = cdnDeferred.await().also { step("CDNs") }
        val censored = censoredDeferred.await().also { step("TLS by name") }
        val neutral = neutralDeferred.await().also { step("TLS control") }
        val udp = udpDeferred.await().also { step("UDP") }

        val frozen = censored.verdict == PathVerdict.TLS_FROZEN ||
            neutral.verdict == PathVerdict.TLS_FROZEN

        val report = ProbeReport(
            results = listOf(direct, bridges, cdn, censored, neutral, udp),
            directTorReachable = direct.ok,
            publishedBridgesReachable = bridges.ok,
            // TCP opens but TLS to a censored name does not complete, while the
            // control name does: filtering keyed on the server name.
            sniFilteringSuspected = !censored.ok && neutral.ok,
            freezeSuspected = frozen,
            cdnReachable = cdn.ok,
            udpUsable = udp.ok,
            completedAtMillis = System.currentTimeMillis(),
        )
        VeilLog.i("probe", report.summary())
        report
    }

    /**
     * Ranks the ladder from the measurements. Higher scores are tried first.
     *
     * The weighting follows what the measurements imply about each of the three
     * things current DPI is reported to check together: where the far end is,
     * what the Client Hello looks like, and how many handshakes arrive at once.
     * A transport that terminates on a large CDN sidesteps the first outright,
     * which is why the fronted rungs gain rather than lose when a freeze is
     * detected.
     */
    fun rank(report: ProbeReport): List<Pair<Transport, Float>> {
        val scores = linkedMapOf<Transport, Float>()

        scores[Transport.DIRECT] = when {
            !report.hasRun -> 0.5f
            report.freezeSuspected -> 0.02f
            report.directTorReachable && !report.sniFilteringSuspected -> 0.95f
            report.directTorReachable -> 0.35f
            else -> 0.05f
        }

        scores[Transport.OBFS4] = when {
            !report.hasRun -> 0.7f
            // obfs4 terminates on a bridge in a hosting provider, which is the
            // kind of destination the first signal is looking for, and its
            // traffic shape is the one published TLS-in-TLS work detects best.
            report.freezeSuspected -> 0.2f
            report.publishedBridgesReachable -> 0.8f
            else -> 0.15f
        }

        scores[Transport.WEBTUNNEL] = when {
            !report.hasRun -> 0.6f
            // A real website on a real host, reached as ordinary HTTPS: the
            // closest thing to indistinguishable that a bridge can be.
            report.freezeSuspected && report.cdnReachable -> 0.7f
            report.cdnReachable && !report.sniFilteringSuspected -> 0.75f
            report.cdnReachable -> 0.5f
            else -> 0.2f
        }

        scores[Transport.MEEK] = when {
            !report.hasRun -> 0.5f
            // Fronting puts a CDN's name in the SNI and the real destination
            // inside the encrypted request, so neither name filtering nor
            // destination reputation applies.
            report.freezeSuspected && report.cdnReachable -> 0.8f
            report.cdnReachable -> 0.65f
            else -> 0.15f
        }

        scores[Transport.SNOWFLAKE] = when {
            !report.hasRun -> 0.55f
            // The data path is WebRTC over UDP, so none of the TCP handshake
            // heuristics apply to it at all. When TLS is being frozen, that is
            // worth more than its latency costs.
            report.freezeSuspected && report.udpUsable -> 0.85f
            report.udpUsable && report.cdnReachable -> 0.75f
            report.udpUsable -> 0.6f
            // Rendezvous can still go through an AMP cache without STUN, but
            // the data path itself needs UDP, so this is a long shot.
            else -> 0.2f
        }

        return scores.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private suspend fun tcpAny(name: String, endpoints: List<Pair<String, Int>>): ProbeResult {
        if (endpoints.isEmpty()) return ProbeResult(name, false, "nothing to test", 0)
        val started = System.currentTimeMillis()
        val sample = endpoints.take(4)
        val reachable = coroutineScope {
            sample.map { (host, port) ->
                async { if (tcpConnects(host, port)) "$host:$port" else null }
            }.awaitAll().filterNotNull()
        }
        val elapsed = System.currentTimeMillis() - started
        return ProbeResult(
            name = name,
            ok = reachable.isNotEmpty(),
            detail = if (reachable.isEmpty()) {
                "none of ${sample.size} answered"
            } else {
                "${reachable.size}/${sample.size} answered, first ${reachable.first()}"
            },
            millis = elapsed,
            verdict = if (reachable.isEmpty()) PathVerdict.TCP_BLOCKED else PathVerdict.OPEN,
        )
    }

    private suspend fun tcpConnects(host: String, port: Int, timeoutMillis: Int = 6_000): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    protector.protect(socket)
                    socket.connect(InetSocketAddress(host, port), timeoutMillis)
                    socket.isConnected
                }
            }.getOrDefault(false)
        }

    /**
     * Completes a full TLS handshake and reports how it ended.
     *
     * This is the same measurement as timing curl's `time_connect` against its
     * `time_appconnect`: a TCP connection that opens quickly and then stalls
     * during the handshake is filtering, and a handshake that is torn down is a
     * different kind of filtering with a different answer.
     */
    private suspend fun tlsCanary(name: String, host: String): ProbeResult =
        HandshakeGovernor.withSlot(host) {
            withContext(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                var connectMillis = -1L
                val outcome = runCatching {
                    Socket().use { raw ->
                        protector.protect(raw)
                        raw.connect(InetSocketAddress(host, 443), 8_000)
                        connectMillis = System.currentTimeMillis() - started
                        // Generous, so a slow-but-working network is not
                        // mistaken for a blackhole.
                        raw.soTimeout = 12_000
                        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                            .createSocket(raw, host, 443, false) as SSLSocket
                        ssl.sslParameters = ssl.sslParameters.apply {
                            endpointIdentificationAlgorithm = "HTTPS"
                        }
                        ssl.startHandshake()
                        ssl.session.protocol
                    }
                }
                val elapsed = System.currentTimeMillis() - started

                outcome.fold(
                    onSuccess = {
                        ProbeResult(
                            name = name,
                            ok = true,
                            detail = "handshake ok ($it), tcp ${connectMillis}ms",
                            millis = elapsed,
                            verdict = PathVerdict.OPEN,
                            connectMillis = connectMillis,
                            handshakeMillis = elapsed - connectMillis,
                        )
                    },
                    onFailure = { error ->
                        val verdict = classify(connectMillis, error)
                        if (verdict == PathVerdict.TLS_FROZEN) {
                            cooldown?.markFrozen(host)
                        }
                        ProbeResult(
                            name = name,
                            ok = false,
                            detail = describe(verdict, connectMillis, error),
                            millis = elapsed,
                            verdict = verdict,
                            connectMillis = connectMillis,
                        )
                    },
                )
            }
        }

    private fun classify(connectMillis: Long, error: Throwable): PathVerdict = when {
        connectMillis < 0 -> PathVerdict.TCP_BLOCKED
        error is SocketTimeoutException -> PathVerdict.TLS_FROZEN
        error.message?.contains("reset", ignoreCase = true) == true -> PathVerdict.TLS_RESET
        error.message?.contains("closed", ignoreCase = true) == true -> PathVerdict.TLS_RESET
        error is IOException -> PathVerdict.TLS_RESET
        else -> PathVerdict.UNKNOWN
    }

    private fun describe(verdict: PathVerdict, connectMillis: Long, error: Throwable): String =
        when (verdict) {
            PathVerdict.TCP_BLOCKED -> "no TCP connection: ${error.message.orEmpty().take(90)}"
            PathVerdict.TLS_FROZEN ->
                "TCP up in ${connectMillis}ms, then nothing came back — blackholed"
            PathVerdict.TLS_RESET ->
                "TCP up in ${connectMillis}ms, handshake torn down: ${error.message.orEmpty().take(70)}"
            else -> error.message.orEmpty().take(110)
        }

    /** A real STUN binding request: the cheapest honest test that UDP survives. */
    private suspend fun stunReachable(): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        for ((host, port) in STUN_SERVERS) {
            val ok = withTimeoutOrNull(5_000) {
                runCatching {
                    DatagramSocket().use { socket ->
                        protector.protect(socket)
                        socket.soTimeout = 4_000
                        val request = ByteArray(20)
                        request[0] = 0x00; request[1] = 0x01 // Binding request
                        request[2] = 0x00; request[3] = 0x00 // Length 0
                        // Magic cookie 0x2112A442
                        request[4] = 0x21; request[5] = 0x12
                        request[6] = 0xA4.toByte(); request[7] = 0x42
                        val transactionId = ByteArray(12)
                        SecureRandom().nextBytes(transactionId)
                        transactionId.copyInto(request, 8)

                        val address = InetAddress.getByName(host)
                        socket.send(DatagramPacket(request, request.size, address, port))
                        val reply = DatagramPacket(ByteArray(256), 256)
                        socket.receive(reply)
                        reply.length >= 20 && reply.data[0].toInt() == 0x01
                    }
                }.getOrDefault(false)
            } ?: false
            if (ok) {
                return@withContext ProbeResult(
                    name = "UDP / STUN",
                    ok = true,
                    detail = "$host answered",
                    millis = System.currentTimeMillis() - started,
                    verdict = PathVerdict.OPEN,
                )
            }
        }
        ProbeResult(
            name = "UDP / STUN",
            ok = false,
            detail = "no STUN server answered",
            millis = System.currentTimeMillis() - started,
            verdict = PathVerdict.TCP_BLOCKED,
        )
    }
}
