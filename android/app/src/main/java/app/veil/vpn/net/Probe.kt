package app.veil.vpn.net

import app.veil.vpn.core.VeilLog
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

/** One measurement, in terms the diagnostics screen can print verbatim. */
data class ProbeResult(
    val name: String,
    val ok: Boolean,
    val detail: String,
    val millis: Long,
)

/**
 * What the network in front of us looks like. The point is not to name the
 * censor but to answer four practical questions: can we open TCP to Tor at all,
 * is TLS being torn down by name, do the big CDNs still work, and is UDP alive.
 * Those four answers are enough to order the escalation ladder well.
 */
data class ProbeReport(
    val results: List<ProbeResult> = emptyList(),
    val directTorReachable: Boolean = false,
    val publishedBridgesReachable: Boolean = false,
    val sniFilteringSuspected: Boolean = false,
    val cdnReachable: Boolean = false,
    val udpUsable: Boolean = false,
    val completedAtMillis: Long = 0,
) {
    val hasRun: Boolean get() = completedAtMillis > 0

    /** A one-line verdict for the home screen. */
    fun summary(): String = when {
        !hasRun -> "Network not measured yet"
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
 * endpoint is involved.
 */
class NetworkProbe(private val protector: SocketProtector = SocketProtector.None) {

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

        /** Hosts that must work for meek and Snowflake rendezvous to work. */
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

        const val TLS_CANARY = "www.torproject.org"
    }

    suspend fun run(
        obfs4Bridges: List<Pair<String, Int>>,
        onProgress: (done: Int, total: Int, note: String) -> Unit = { _, _, _ -> },
    ): ProbeReport = coroutineScope {
        val total = 5
        var done = 0
        fun step(note: String) {
            done += 1
            onProgress(done, total, note)
        }

        onProgress(0, total, "Reaching for the Tor directory")
        val directDeferred = async { tcpAny("Tor directory authorities", DIRECTORY_AUTHORITIES) }
        val bridgeDeferred = async { tcpAny("Published obfs4 bridges", obfs4Bridges) }
        val cdnDeferred = async { tcpAny("Large CDNs", CDN_HOSTS) }
        val sniDeferred = async { tlsCanary() }
        val udpDeferred = async { stunReachable() }

        val direct = directDeferred.await().also { step("Tor directory") }
        val bridges = bridgeDeferred.await().also { step("Bridges") }
        val cdn = cdnDeferred.await().also { step("CDNs") }
        val sni = sniDeferred.await().also { step("TLS by name") }
        val udp = udpDeferred.await().also { step("UDP") }

        val report = ProbeReport(
            results = listOf(direct, bridges, cdn, sni, udp),
            directTorReachable = direct.ok,
            publishedBridgesReachable = bridges.ok,
            // TCP opens but TLS to a well-known name does not complete: the
            // classic signature of filtering on the server name.
            sniFilteringSuspected = !sni.ok && cdn.ok,
            cdnReachable = cdn.ok,
            udpUsable = udp.ok,
            completedAtMillis = System.currentTimeMillis(),
        )
        VeilLog.i("probe", report.summary())
        report
    }

    /** Ranks the ladder from the measurements. Higher scores are tried first. */
    fun rank(report: ProbeReport): List<Pair<Transport, Float>> {
        val scores = linkedMapOf<Transport, Float>()

        scores[Transport.DIRECT] = when {
            !report.hasRun -> 0.5f
            report.directTorReachable && !report.sniFilteringSuspected -> 0.95f
            report.directTorReachable -> 0.35f
            else -> 0.05f
        }
        scores[Transport.OBFS4] = when {
            !report.hasRun -> 0.7f
            report.publishedBridgesReachable -> 0.8f
            else -> 0.15f
        }
        scores[Transport.WEBTUNNEL] = when {
            !report.hasRun -> 0.6f
            report.cdnReachable && !report.sniFilteringSuspected -> 0.7f
            report.cdnReachable -> 0.45f
            else -> 0.2f
        }
        scores[Transport.MEEK] = when {
            !report.hasRun -> 0.5f
            // Fronting hides the real name inside TLS, so SNI filtering does
            // not hurt it as long as the CDN itself answers.
            report.cdnReachable -> 0.65f
            else -> 0.15f
        }
        scores[Transport.SNOWFLAKE] = when {
            !report.hasRun -> 0.55f
            report.udpUsable && report.cdnReachable -> 0.75f
            report.udpUsable -> 0.6f
            // Snowflake can still rendezvous over an AMP cache without UDP to
            // STUN, but the data path itself needs UDP, so this is a long shot.
            else -> 0.2f
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private suspend fun tcpAny(name: String, endpoints: List<Pair<String, Int>>): ProbeResult {
        if (endpoints.isEmpty()) return ProbeResult(name, false, "nothing to test", 0)
        val started = System.currentTimeMillis()
        val reachable = coroutineScope {
            endpoints.take(6).map { (host, port) ->
                async { if (tcpConnects(host, port)) "$host:$port" else null }
            }.awaitAll().filterNotNull()
        }
        val elapsed = System.currentTimeMillis() - started
        return ProbeResult(
            name = name,
            ok = reachable.isNotEmpty(),
            detail = if (reachable.isEmpty()) {
                "none of ${endpoints.size} answered"
            } else {
                "${reachable.size}/${endpoints.take(6).size} answered, first ${reachable.first()}"
            },
            millis = elapsed,
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
     * Completes a TLS handshake to a well-known censored name. A TCP connection
     * that opens and then dies during the handshake is filtering, not an outage.
     */
    private suspend fun tlsCanary(): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val outcome = runCatching {
            Socket().use { raw ->
                protector.protect(raw)
                raw.connect(InetSocketAddress(TLS_CANARY, 443), 8_000)
                raw.soTimeout = 8_000
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, TLS_CANARY, 443, false) as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                ssl.startHandshake()
                ssl.session.protocol
            }
        }
        val elapsed = System.currentTimeMillis() - started
        outcome.fold(
            onSuccess = { ProbeResult("TLS to $TLS_CANARY", true, "handshake ok ($it)", elapsed) },
            onFailure = {
                val reason = (it as? IOException)?.message ?: it.toString()
                ProbeResult("TLS to $TLS_CANARY", false, reason.take(120), elapsed)
            },
        )
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
                        request[4] = 0x21; request[5] = 0x12; request[6] = 0xA4.toByte(); request[7] = 0x42
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
                    "UDP / STUN",
                    true,
                    "$host answered",
                    System.currentTimeMillis() - started,
                )
            }
        }
        ProbeResult("UDP / STUN", false, "no STUN server answered", System.currentTimeMillis() - started)
    }
}
