package app.veil.vpn.core

import app.veil.vpn.R
import app.veil.vpn.VeilApp
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.NatBehaviour
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.SocksProxy
import app.veil.vpn.tor.Bootstrap
import app.veil.vpn.tor.Torrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A diagnostic that connects, and then measures what it connected to.
 *
 * It answers two questions, in order, and the second one only exists because
 * the first is not enough. Which route can carry Tor out of this network — a
 * real end-to-end attempt through each transport, run to a verdict, not a list
 * of listeners. And then: what is the thing it built actually like? A tunnel
 * that reaches 100% and takes eight seconds to return a byte is a tunnel the
 * user will describe as broken, and no bootstrap percentage will ever say so.
 *
 * So once a route connects, the tunnel is left up and driven: connections are
 * opened through tor's own SOCKS port and timed, a body of known size is pulled
 * through it, names are resolved through tor's DNS port, and the same small
 * request is repeated on a cadence to see whether the link holds still. Those
 * are the numbers behind "slow" and "unstable", and they are the ones worth
 * sending to someone who can fix it.
 *
 * It is single-flight and never runs while a real tunnel is up: it drives the
 * shared tor and transport controllers, and two of anything touching those at
 * once takes the process down.
 */
object SelfTest {

    fun interface Progress {
        fun update(done: Int, total: Int, label: String)
    }

    /** One route's verdict. */
    private data class Outcome(
        val transport: Transport,
        val reachedPercent: Int,
        val connected: Boolean,
        val millis: Long,
        val detail: String,
    )

    private val mutex = Mutex()
    val isRunning: Boolean get() = mutex.isLocked

    /** Per-transport bootstrap budgets for the test. Snowflake needs the most. */
    private fun budgetMillis(transport: Transport): Long = when (transport) {
        Transport.DIRECT -> 20_000
        Transport.OBFS4 -> 30_000
        Transport.WEBTUNNEL -> 45_000
        Transport.MEEK -> 45_000
        Transport.CONJURE -> 55_000
        Transport.SNOWFLAKE -> 60_000
    }

    /** No forward progress for this long means the route is not going to move. */
    private const val STALL_MILLIS = 18_000L

    /** How many times the steady-state probe is repeated, and how far apart. */
    private const val STEADY_SAMPLES = 6
    private const val STEADY_SPACING_MILLIS = 4_000L

    suspend fun run(app: VeilApp, progress: Progress): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val out = StringBuilder()
            fun line(text: String) {
                out.appendLine(text)
                VeilLog.i("selftest", text)
            }

            val order = listOf(
                Transport.DIRECT, Transport.OBFS4, Transport.WEBTUNNEL,
                Transport.MEEK, Transport.CONJURE, Transport.SNOWFLAKE,
            )
            // Five before the routes, four after them, so the percentage
            // reflects the work rather than the number of headings.
            val preamble = 5
            val postamble = 4
            val total = preamble + order.size + postamble
            var step = 0
            fun stage(label: String) {
                progress.update(step, total, label)
                step += 1
            }
            fun finish() = progress.update(total, total, app.getString(R.string.diag_stage_done))

            line("=== Veil deep diagnostic ===")
            line("version 0.5.9")

            val tunnelLive = runCatching { app.tor.isRunning }.getOrDefault(false)
            if (tunnelLive) {
                line("A tunnel is already connected. Disconnect first: this test")
                line("drives the same Tor and would fight the live connection.")
                finish()
                return@withContext out.toString()
            }

            // --- 1. The link itself ------------------------------------------
            stage(app.getString(R.string.diag_stage_network))
            val network = runCatching { NetworkContext.inspect(app) }.getOrNull()
            if (network == null || !network.isOnline) {
                line("network: OFFLINE — nothing else can be tested")
                finish()
                return@withContext out.toString()
            }
            line("network: ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})")
            line("resolvers: ${network.dnsServers.joinToString().ifEmpty { "none reported" }}")

            val facts = TrafficAnalysis.linkFacts(app)
            if (facts != null) {
                line(
                    "link: mtu ${if (facts.mtu > 0) facts.mtu.toString() else "unknown"}, " +
                        (if (facts.hasIpv4) "IPv4" else "no IPv4") + "/" +
                        (if (facts.hasIpv6) "IPv6" else "no IPv6") +
                        ", ${if (facts.metered) "metered" else "unmetered"}",
                )
                line(
                    "link speed as reported: ${facts.downstreamKbps} kbps down, " +
                        "${facts.upstreamKbps} kbps up",
                )
                if (!facts.validated) {
                    line("link: NOT VALIDATED by the system — a captive portal would look like this")
                }
                if (facts.captivePortalSuspected) {
                    line("link: the system thinks there is a captive portal in front of you")
                }
                if (facts.mtu in 1..1399) {
                    line("link: MTU ${facts.mtu} is small; large TLS handshakes can fail on their own here")
                }
            }

            // --- 2. The clock ------------------------------------------------
            stage(app.getString(R.string.diag_stage_clock))
            val skew = TrafficAnalysis.clockSkewSeconds()
            line(
                "clock: " + when {
                    skew == null -> "could not be checked (the plain request did not get through)"
                    kotlin.math.abs(skew) < 30 -> "within ${kotlin.math.abs(skew)}s of the server"
                    else -> "OFF BY ${skew}s — tor rejects consensus documents when the clock is this far out"
                },
            )

            // --- 3. Transports ------------------------------------------------
            stage(app.getString(R.string.diag_stage_transports))
            val ports = runCatching { app.pt.startAll() }.getOrElse {
                line("transports: NONE STARTED — ${it.message}")
                emptyMap()
            }
            order.filter { it.isPluggable }.forEach { t ->
                line("transport ${t.torName}: ${ports[t]?.let { "up on 127.0.0.1:$it" } ?: "DID NOT START"}")
            }
            line("lyrebird ${app.pt.lyrebirdVersion}, snowflake ${app.pt.snowflakeVersion}")

            // --- 4. The bridge service ----------------------------------------
            stage(app.getString(R.string.diag_stage_bridges))
            runCatching { app.bridges.load() }
            runCatching { app.memory.load() }
            val country = network.countryIso
            val fetched = if (country.isNullOrBlank()) null else
                withTimeoutOrNull(45_000) { runCatching { app.bridges.refreshCountry(country) }.getOrNull() }
            line(
                "bridge service: " + when {
                    country.isNullOrBlank() -> "country unknown, not asked"
                    fetched == null -> "NO ANSWER within 45s (fronting may be blocked)"
                    fetched > 0 -> "answered, $fetched fresh line(s)"
                    else -> "answered but returned nothing usable"
                },
            )
            order.filter { it.isPluggable }.forEach { t ->
                line("bridges ${t.torName}: ${app.bridges.forTransport(t, 99).size}")
            }

            // --- 5. Reachability and NAT --------------------------------------
            stage(app.getString(R.string.diag_stage_probe))
            val report = runCatching {
                withTimeoutOrNull(45_000) { NetworkProbe().run(app.bridges.probeTargets()) }
            }.getOrNull()
            if (report == null) {
                line("probe: did not finish")
            } else {
                report.results.forEach { line("probe ${it.verdict}: ${it.millis} ms") }
                line("nat: ${report.natBehaviour}")
                if (report.natBehaviour == NatBehaviour.SYMMETRIC) {
                    line("nat: symmetric — Snowflake needs a proxy able to work around this and often cannot")
                }
            }

            // --- The route tests ----------------------------------------------
            val reserved = runCatching { LoopbackPorts.reserve(2) }.getOrDefault(emptyList())
            if (reserved.size < 2) {
                line("tor: could not reserve local ports; cannot run the route tests")
                finish()
                return@withContext out.toString()
            }
            val socksPort = reserved[0]
            val dnsPort = reserved[1]
            val session = Torrc.Session(ports, socksPort, dnsPort, opening = null)
            val torUp = withTimeoutOrNull(60_000) {
                runCatching {
                    app.tor.start(Torrc.build(session), Torrc.minimal(session), socksPort, dnsPort)
                }.getOrDefault(false)
            } ?: false
            if (!torUp) {
                line("tor: DID NOT START — ${app.tor.lastError.value ?: "no reason"}")
                finish()
                return@withContext out.toString()
            }

            line("")
            line("--- route tests (each tries to reach a relay) ---")
            val current = app.settings.settings.first()
            val tls = TlsProfile.resolve(current.tlsProfile, app.settings.installSeed())
            val dtls = current.dtlsProfile

            val outcomes = mutableListOf<Outcome>()
            var winner: Outcome? = null
            for (transport in order) {
                stage(app.getString(R.string.diag_stage_testing, transport.label))
                if (transport != Transport.DIRECT && ports[transport] == null) {
                    line("${transport.torName}: skipped, transport did not start")
                    continue
                }
                val attempt = app.planner.diagnosticAttempt(transport, tls, dtls)
                val bridges = attempt?.bridges ?: emptyList()
                if (transport != Transport.DIRECT && bridges.isEmpty()) {
                    line("${transport.torName}: skipped, no bridges to try")
                    continue
                }
                val outcome = testRoute(app, transport, bridges)
                outcomes += outcome
                line(
                    "${transport.torName}: ${if (outcome.connected) "CONNECTED" else "failed"} " +
                        "at ${outcome.reachedPercent}% in ${outcome.millis / 1000}s — ${outcome.detail}",
                )
                if (outcome.connected) {
                    winner = outcome
                    // The diagnostic just proved this route works here. Record
                    // it the same way a real connect would, so the next connect
                    // starts with it instead of rediscovering it — which is the
                    // difference between the diagnostic being a report and it
                    // being a fix.
                    runCatching {
                        app.memory.recordSuccess(network.fingerprint, transport, outcome.millis)
                    }
                    line("(remembered ${transport.torName} for this network; the next connect will try it first)")
                    break
                }
            }

            // --- What the connection is actually like -------------------------
            var steady: TrafficAnalysis.Series? = null
            var throughput: TrafficAnalysis.Throughput? = null
            if (winner != null) {
                val socks = SocksProxy("127.0.0.1", socksPort)

                stage(app.getString(R.string.diag_stage_exit))
                line("")
                line("--- traffic analysis over ${winner.transport.torName} ---")
                line("circuit: ${app.tor.describeCircuit() ?: "not reported"}")
                line("leaving through Tor: ${TrafficAnalysis.exitCheck(socks)}")

                stage(app.getString(R.string.diag_stage_dns))
                listOf("torproject.org", "wikipedia.org").forEach { name ->
                    val (millis, note) = TrafficAnalysis.resolveThroughTor(dnsPort, name)
                    line(
                        "dns $name: " + if (millis < 0) "FAILED — $note" else "${millis} ms, $note",
                    )
                }

                stage(app.getString(R.string.diag_stage_latency))
                val latency = TrafficAnalysis.series(
                    label = "warm-up",
                    socks = socks,
                    host = TrafficAnalysis.LATENCY_HOST,
                    path = TrafficAnalysis.LATENCY_PATH,
                    count = 3,
                    spacingMillis = 500,
                )
                line(
                    "first requests: connect ${latency.medianConnect} ms, " +
                        "first byte ${latency.medianTtfb} ms" +
                        (latency.firstProblem?.let { ", $it" } ?: ""),
                )

                stage(app.getString(R.string.diag_stage_throughput))
                val measured = TrafficAnalysis.throughput(socks)
                throughput = measured
                line(
                    "throughput: " + if (measured.ok) {
                        "${measured.bytes / 1024} KB in ${measured.millis} ms = " +
                            "${measured.kbytesPerSecond} KB/s"
                    } else {
                        "could not be measured — ${measured.note}"
                    },
                )

                stage(app.getString(R.string.diag_stage_steady))
                val watched = TrafficAnalysis.series(
                    label = "steady",
                    socks = socks,
                    host = TrafficAnalysis.LATENCY_HOST,
                    path = TrafficAnalysis.LATENCY_PATH,
                    count = STEADY_SAMPLES,
                    spacingMillis = STEADY_SPACING_MILLIS,
                )
                steady = watched
                line(
                    "over ${STEADY_SAMPLES} requests ${STEADY_SPACING_MILLIS / 1000}s apart: " +
                        "median ${watched.medianTtfb} ms, " +
                        "range ${watched.minTtfb}–${watched.maxTtfb} ms, " +
                        "movement ±${watched.jitter} ms, " +
                        "${watched.failures} failed",
                )
                watched.firstProblem?.let { line("first failure said: $it") }
                line("bytes moved: ${app.tor.describeTraffic() ?: "not reported"}")
            } else {
                // Keep the progress bar honest: the stages that would have
                // measured the connection are not going to run.
                repeat(postamble) { stage(app.getString(R.string.diag_stage_skipped)) }
            }

            runCatching { app.tor.stop() }
            runCatching { app.pt.stopAll() }

            line("")
            line("--- verdict ---")
            when {
                winner != null && steady != null && throughput != null ->
                    line(
                        "${winner.transport.torName} reached a relay in ${winner.millis / 1000}s. " +
                            TrafficAnalysis.verdict(steady, throughput),
                    )
                winner != null ->
                    line("${winner.transport.torName} connected, but the traffic test did not complete.")
                report?.natBehaviour == NatBehaviour.SYMMETRIC ->
                    line("Nothing connected. This network's NAT is symmetric, so Snowflake is out; WebTunnel is the one to get working here.")
                outcomes.isNotEmpty() && outcomes.all { it.reachedPercent < 15 } ->
                    line("Nothing connected, and nothing got past the first handshake — the bridges are being blocked. Add fresh bridges in the Bridges screen.")
                outcomes.isEmpty() ->
                    line("Nothing could even be tried: no transport started, or no bridges were available.")
                else ->
                    line("Nothing completed. The routes reached a relay but could not finish; try again, or add fresh bridges.")
            }
            if (winner != null && steady != null && steady.failures > 0) {
                line(
                    "Requests failing on a connected tunnel is the signature of a hop that keeps " +
                        "going away — over Snowflake, a volunteer proxy closing.",
                )
            }
            line("=== end ===")
            finish()
            out.toString()
        }
    }

    /**
     * Points tor at one route, turns the network on, and watches it bootstrap
     * to a verdict.
     *
     * A failed route leaves the network off so the next one starts clean. A
     * route that connects leaves it on: the traffic analysis that follows needs
     * the tunnel it just built.
     */
    private suspend fun testRoute(
        app: VeilApp,
        transport: Transport,
        bridges: List<BridgeLine>,
    ): Outcome {
        val tor = app.tor
        tor.resetBootstrap()
        if (!tor.applyRoute(transport, bridges)) {
            return Outcome(transport, 0, false, 0, "tor rejected the route: ${tor.lastError.value ?: "?"}")
        }
        if (!tor.setNetworkEnabled(true)) {
            return Outcome(transport, 0, false, 0, "could not enable the network")
        }
        tor.awaitListeners()

        val started = System.currentTimeMillis()
        val deadline = started + budgetMillis(transport)
        var maxPercent = 0
        var lastMoveAt = started
        var detail = "no progress"
        while (System.currentTimeMillis() < deadline) {
            val b = tor.refreshBootstrap()
            if (b.percent > maxPercent) {
                maxPercent = b.percent
                lastMoveAt = System.currentTimeMillis()
            }
            if (b.isDone) {
                return Outcome(
                    transport, 100, true, System.currentTimeMillis() - started,
                    b.summary.ifEmpty { "connected" },
                )
            }
            val quiet = System.currentTimeMillis() - lastMoveAt
            if (b.isHopeless && quiet > 8_000) {
                detail = failureDetail(b, tor.lastError.value)
                break
            }
            if (quiet > STALL_MILLIS) {
                detail = "stalled at $maxPercent% — ${failureDetail(b, tor.lastError.value)}"
                break
            }
            delay(500)
        }
        if (System.currentTimeMillis() >= deadline && maxPercent < 100) {
            detail = "timed out at $maxPercent% — ${failureDetail(tor.bootstrap.value, tor.lastError.value)}"
        }
        tor.setNetworkEnabled(false)
        return Outcome(transport, maxPercent, false, System.currentTimeMillis() - started, detail)
    }

    /** The most useful thing tor said about why a route did not complete. */
    private fun failureDetail(b: Bootstrap, lastError: String?): String {
        val warning = b.lastWarning?.substringAfterLast("): ")?.take(80)
        return when {
            !warning.isNullOrBlank() -> warning
            !lastError.isNullOrBlank() -> lastError.take(80)
            b.summary.isNotBlank() -> "stuck: ${b.summary}"
            else -> "no detail from tor"
        }
    }
}
