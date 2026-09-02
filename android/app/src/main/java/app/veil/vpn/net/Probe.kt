package app.veil.vpn.net

import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.EndpointCooldown
import androidx.annotation.StringRes
import app.veil.vpn.R
import app.veil.vpn.model.Localised
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

/**
 * How the network in front of us translates outgoing UDP.
 *
 * Only two answers change what the app does. A mapping that does not depend on
 * where the packet is going is what WebRTC needs, and Snowflake works normally
 * behind it. One that does — symmetric, and the normal behaviour of the
 * carrier-grade NAT a mobile subscriber sits behind — leaves Snowflake able to
 * use only the minority of volunteer proxies that are themselves unrestricted,
 * which in practice means it often never finds one.
 */
enum class NatBehaviour {
    /** Same public address whoever we send to. Snowflake is fine here. */
    ENDPOINT_INDEPENDENT,

    /** A new port per destination. Snowflake will struggle; TCP routes will not. */
    SYMMETRIC,

    /** No UDP at all: Snowflake cannot work, and nothing else needs it. */
    NO_UDP,

    /** Not enough answers to say. Treated as "no reason to demote anything". */
    UNKNOWN,
}

/** One measurement, in terms the diagnostics screen can print verbatim. */
data class ProbeResult(
    val name: Localised,
    val ok: Boolean,
    val detail: Localised,
    val millis: Long,
    val verdict: PathVerdict = if (ok) PathVerdict.OPEN else PathVerdict.UNKNOWN,
    /** Time to complete the TCP handshake, or -1 if it never did. */
    val connectMillis: Long = -1,
    /** Time to complete the TLS handshake, or -1 if it never did. */
    val handshakeMillis: Long = -1,
    /** Only set by the UDP measurement. */
    val natBehaviour: NatBehaviour = NatBehaviour.UNKNOWN,
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
    /**
     * What the network does to outgoing UDP mappings. This is the difference
     * between Snowflake connecting in seconds and not connecting at all, and it
     * is the usual reason it behaves one way on Wi-Fi and another on mobile.
     */
    val natBehaviour: NatBehaviour = NatBehaviour.UNKNOWN,
    val completedAtMillis: Long = 0,
) {
    val hasRun: Boolean get() = completedAtMillis > 0

    /** A one-line verdict for the home screen. */
    @get:StringRes
    val summaryRes: Int get() = when {
        !hasRun -> R.string.probe_summary_not_run
        freezeSuspected && cdnReachable -> R.string.probe_summary_frozen_cdn
        freezeSuspected -> R.string.probe_summary_frozen
        directTorReachable && !sniFilteringSuspected -> R.string.probe_summary_open
        natBehaviour == NatBehaviour.SYMMETRIC && publishedBridgesReachable ->
            R.string.probe_summary_symmetric
        publishedBridgesReachable -> R.string.probe_summary_bridges_ok
        cdnReachable -> R.string.probe_summary_cdn_ok
        udpUsable -> R.string.probe_summary_udp_ok
        else -> R.string.probe_summary_severe
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


        /** A name a censor has a reason to filter. */
        const val CENSORED_CANARY = "www.torproject.org"

        /** A name a censor has no reason to filter, as a control. */
        const val NEUTRAL_CANARY = "cdn.jsdelivr.net"



    }

    suspend fun run(
        obfs4Bridges: List<Pair<String, Int>>,
        onProgress: (done: Int, total: Int, noteRes: Int) -> Unit = { _, _, _ -> },
    ): ProbeReport = coroutineScope {
        val total = 6
        var done = 0
        fun step(@StringRes noteRes: Int) {
            done += 1
            onProgress(done, total, noteRes)
        }

        onProgress(0, total, R.string.probe_step_start)

        // Started together, but every TLS handshake inside them is paced.
        val directName = Localised(R.string.probe_name_directory)
        val bridgeName = Localised(R.string.probe_name_bridges)
        val cdnName = Localised(R.string.probe_name_cdn)
        val directDeferred = async { tcpAny(directName, DIRECTORY_AUTHORITIES) }
        val bridgeDeferred = async { tcpAny(bridgeName, obfs4Bridges) }
        val cdnDeferred = async { tcpAny(cdnName, CDN_HOSTS) }
        val censoredDeferred = async { tlsCanary(CENSORED_CANARY) }
        val neutralDeferred = async { tlsCanary(NEUTRAL_CANARY) }
        val udpDeferred = async { stunReachable() }

        val direct = directDeferred.await().also { step(R.string.probe_step_directory) }
        val bridges = bridgeDeferred.await().also { step(R.string.probe_step_bridges) }
        val cdn = cdnDeferred.await().also { step(R.string.probe_step_cdn) }
        val censored = censoredDeferred.await().also { step(R.string.probe_step_tls_name) }
        val neutral = neutralDeferred.await().also { step(R.string.probe_step_tls_control) }
        val udp = udpDeferred.await().also { step(R.string.probe_step_udp) }

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
            natBehaviour = udp.natBehaviour,
            completedAtMillis = System.currentTimeMillis(),
        )
        VeilLog.i(
            "probe",
            "direct=${direct.ok} bridges=${bridges.ok} cdn=${cdn.ok} " +
                "udp=${udp.ok} nat=${udp.natBehaviour}",
        )
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
            // Nothing here needs UDP, so a carrier NAT that rules Snowflake out
            // makes this the strongest remaining candidate rather than merely
            // an equal one.
            report.natBehaviour == NatBehaviour.SYMMETRIC && report.cdnReachable -> 0.85f
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

        scores[Transport.CONJURE] = when {
            !report.hasRun -> 0.55f
            // Nothing about Conjure depends on UDP, and it has no endpoint that
            // could be on a blocklist — so the two things that take the other
            // routes out, a carrier NAT and a blocked bridge address, leave it
            // standing. That makes it the strongest candidate on exactly the
            // network where everything else has already failed.
            report.natBehaviour == NatBehaviour.SYMMETRIC -> 0.8f
            report.freezeSuspected && report.cdnReachable -> 0.75f
            // Registration is a fronted request, so it needs a CDN to be
            // reachable even though the data path does not go near one.
            report.cdnReachable -> 0.6f
            else -> 0.3f
        }

        scores[Transport.SNOWFLAKE] = when {
            !report.hasRun -> 0.55f
            // A network that hands out a new public port per destination is the
            // one thing WebRTC cannot work around. Snowflake is then limited to
            // the minority of volunteer proxies that are themselves
            // unrestricted, so it is worth trying — but only after the routes
            // that do not care about NAT at all. This is why the same phone
            // connects instantly on Wi-Fi and waits for ever on mobile data.
            report.natBehaviour == NatBehaviour.SYMMETRIC -> 0.25f
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

    private suspend fun tcpAny(name: Localised, endpoints: List<Pair<String, Int>>): ProbeResult {
        if (endpoints.isEmpty()) {
            return ProbeResult(name, false, Localised(R.string.probe_detail_nothing), 0)
        }
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
                Localised(R.string.probe_detail_none_answered, sample.size)
            } else {
                Localised(
                    R.string.probe_detail_some_answered,
                    reachable.size,
                    sample.size,
                    reachable.first(),
                )
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
    private suspend fun tlsCanary(host: String): ProbeResult =
        HandshakeGovernor.withSlot(host) {
            withContext(Dispatchers.IO) {
                val name = Localised(R.string.probe_name_tls, host)
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
                            detail = Localised(
                                R.string.probe_detail_handshake_ok,
                                it.orEmpty(),
                                connectMillis,
                            ),
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

    private fun describe(
        verdict: PathVerdict,
        connectMillis: Long,
        error: Throwable,
    ): Localised = when (verdict) {
        PathVerdict.TCP_BLOCKED ->
            Localised(R.string.probe_detail_no_tcp, error.message.orEmpty().take(90))
        PathVerdict.TLS_FROZEN ->
            Localised(R.string.probe_detail_frozen, connectMillis)
        PathVerdict.TLS_RESET ->
            Localised(R.string.probe_detail_reset, connectMillis, error.message.orEmpty().take(70))
        else -> Localised(R.string.probe_detail_other, error.message.orEmpty().take(110))
    }

    /**
     * Asks two STUN servers what address they see us coming from.
     *
     * This is more than a UDP liveness check, and the difference is the whole
     * reason Snowflake behaves so differently on Wi-Fi and on a mobile network.
     * Snowflake's data path is WebRTC, and WebRTC needs the NAT in front of the
     * client to reuse the same public port for different destinations. Home
     * routers do; carrier-grade NAT, which nearly every mobile network puts its
     * subscribers behind, frequently does not — it allocates a fresh port per
     * destination, which is called symmetric or, in Snowflake's own vocabulary,
     * "restricted".
     *
     * A restricted client can only be matched with an unrestricted volunteer
     * proxy, and most volunteers are themselves behind home routers with the
     * same limitation. So the broker has far fewer proxies it can offer, and
     * the experience is a Snowflake that connects in seconds on Wi-Fi and never
     * connects at all on mobile data.
     *
     * Two servers are enough to tell the two apart: the same mapped address
     * from both means the mapping does not depend on where we are sending, and
     * a different one means it does. Knowing which it is turns "Snowflake
     * doesn't work here" into "try WebTunnel first here", which the ladder can
     * act on before the user has waited two minutes to find out.
     */
    private suspend fun stunReachable(): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        // The survey asks every server at once on one socket and keeps what
        // answers; see [StunSurvey] for why that, and not one server after
        // another. The probe needs two answers to compare; the connect path
        // needs all of them, so the survey is the one that does the asking.
        val survey = StunSurvey.run(networkFingerprint = "probe", protector = protector)
        val seen = survey.answers.map { it.host to it.mapped }

        val elapsed = System.currentTimeMillis() - started
        val name = Localised(R.string.probe_name_udp)

        when {
            seen.isEmpty() -> ProbeResult(
                name = name,
                ok = false,
                detail = Localised(R.string.probe_detail_stun_none),
                millis = elapsed,
                verdict = PathVerdict.TCP_BLOCKED,
                natBehaviour = NatBehaviour.NO_UDP,
            )

            // Only one server answered, so there is nothing to compare against.
            // UDP works; whether the mapping is stable is unknown, and guessing
            // "restricted" would demote Snowflake on networks where it is fine.
            seen.size == 1 -> ProbeResult(
                name = name,
                ok = true,
                detail = Localised(R.string.probe_detail_stun_ok, seen[0].first),
                millis = elapsed,
                verdict = PathVerdict.OPEN,
                natBehaviour = NatBehaviour.UNKNOWN,
            )

            seen[0].second == seen[1].second -> ProbeResult(
                name = name,
                ok = true,
                detail = Localised(R.string.probe_detail_nat_open, seen[0].second),
                millis = elapsed,
                verdict = PathVerdict.OPEN,
                natBehaviour = NatBehaviour.ENDPOINT_INDEPENDENT,
            )

            else -> ProbeResult(
                name = name,
                ok = true,
                detail = Localised(
                    R.string.probe_detail_nat_symmetric,
                    seen[0].second,
                    seen[1].second,
                ),
                millis = elapsed,
                verdict = PathVerdict.OPEN,
                natBehaviour = NatBehaviour.SYMMETRIC,
            )
        }
    }

    /**
     * Sends one STUN binding request and returns the address the server saw, or
     * null if it did not answer with one.
     *
     * Only XOR-MAPPED-ADDRESS is read. The older MAPPED-ADDRESS attribute exists
     * for compatibility, but it is exactly the attribute middleboxes were known
     * to rewrite — which is why the XORed one was specified in the first place,
     * and why trusting it here would defeat the measurement.
     */
    /**
     * Sends one binding request and returns its transaction id, which is how
     * the reply is matched back to the server that sent it. Replies arrive in
     * whatever order the network delivers them, and on one socket there is
     * nothing else to tell them apart by.
     */
}
