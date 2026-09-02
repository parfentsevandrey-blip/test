package app.veil.vpn.core

import app.veil.vpn.VeilApp
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.NatBehaviour
import app.veil.vpn.net.NetworkProbe
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
 * A diagnostic that actually tries to connect through each transport.
 *
 * The point is the distinction an earlier, shallow version missed. "obfs4 is
 * listening" and "the bridge service answered" say only that things *started* —
 * not that a single byte can leave the phone through them. The question worth
 * answering when nothing works is which transport can carry Tor to a relay and
 * which cannot, and why: refused, frozen mid-handshake, or stuck at a
 * particular bootstrap percentage.
 *
 * So this starts tor once with every plugin declared and its network off — the
 * exact mechanism the app connects with — then, for each transport that has
 * bridges, points tor at it, turns the network on, and watches it bootstrap
 * against a time budget. It records how far each got, how long it took, and
 * what tor said when it failed. That is a real end-to-end test of every route,
 * run to a verdict, rather than a list of listeners.
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

    suspend fun run(app: VeilApp, progress: Progress): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val out = StringBuilder()
            fun line(text: String) {
                out.appendLine(text)
                VeilLog.i("selftest", text)
            }

            // The routes that will actually be bootstrap-tested decide the step
            // count, so the percentage means something.
            val order = listOf(
                Transport.DIRECT, Transport.OBFS4, Transport.WEBTUNNEL,
                Transport.MEEK, Transport.CONJURE, Transport.SNOWFLAKE,
            )
            val preamble = 4
            val total = preamble + order.size
            var step = 0
            fun stage(label: String) {
                progress.update(step, total, label)
                step += 1
            }

            line("=== Veil deep diagnostic ===")
            line("version 0.5.8")

            val tunnelLive = runCatching { app.tor.isRunning }.getOrDefault(false)
            if (tunnelLive) {
                line("A tunnel is already connected. Disconnect first: this test")
                line("drives the same Tor and would fight the live connection.")
                progress.update(total, total, "Done")
                return@withContext out.toString()
            }

            // --- Preamble: the cheap facts, so a deep failure has context ----
            stage("Network")
            val network = runCatching { NetworkContext.inspect(app) }.getOrNull()
            if (network == null || !network.isOnline) {
                line("network: OFFLINE — nothing else can be tested")
                progress.update(total, total, "Done")
                return@withContext out.toString()
            }
            line("network: ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})")
            line("resolvers: ${network.dnsServers.joinToString().ifEmpty { "none reported" }}")

            stage("Transports")
            val ports = runCatching { app.pt.startAll() }.getOrElse {
                line("transports: NONE STARTED — ${it.message}")
                emptyMap()
            }
            order.filter { it.isPluggable }.forEach { t ->
                line("transport ${t.torName}: ${ports[t]?.let { "up on 127.0.0.1:$it" } ?: "DID NOT START"}")
            }
            line("lyrebird ${app.pt.lyrebirdVersion}, snowflake ${app.pt.snowflakeVersion}")

            stage("Bridge service")
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

            stage("Network probe")
            val report = runCatching {
                withTimeoutOrNull(45_000) { NetworkProbe().run(app.bridges.probeTargets()) }
            }.getOrNull()
            if (report == null) {
                line("probe: did not finish")
            } else {
                report.results.forEach { line("probe ${it.verdict}: ${it.millis} ms") }
                line("nat: ${report.natBehaviour}")
            }

            // --- The real test: bootstrap Tor through each route -------------
            val reserved = runCatching { LoopbackPorts.reserve(2) }.getOrDefault(emptyList())
            if (reserved.size < 2) {
                line("tor: could not reserve local ports; cannot run the route tests")
                progress.update(total, total, "Done")
                return@withContext out.toString()
            }
            val session = Torrc.Session(ports, reserved[0], reserved[1], opening = null)
            val torUp = withTimeoutOrNull(60_000) {
                runCatching {
                    app.tor.start(Torrc.build(session), Torrc.minimal(session), reserved[0], reserved[1])
                }.getOrDefault(false)
            } ?: false
            if (!torUp) {
                line("tor: DID NOT START — ${app.tor.lastError.value ?: "no reason"}")
                progress.update(total, total, "Done")
                return@withContext out.toString()
            }

            line("")
            line("--- route tests (each tries to reach a relay) ---")
            val current = app.settings.settings.first()
            val tls = TlsProfile.resolve(current.tlsProfile, app.settings.installSeed())
            val dtls = current.dtlsProfile

            val outcomes = mutableListOf<Outcome>()
            for (transport in order) {
                stage("Testing ${transport.torName}")
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

            runCatching { app.tor.stop() }
            runCatching { app.pt.stopAll() }

            line("")
            line("--- verdict ---")
            val winner = outcomes.firstOrNull { it.connected }
            when {
                winner != null ->
                    line("WORKS: ${winner.transport.torName} reached a relay in ${winner.millis / 1000}s. Use it.")
                report?.natBehaviour == NatBehaviour.SYMMETRIC ->
                    line("Nothing connected. This network's NAT is symmetric, so Snowflake is out; WebTunnel is the one to get working here.")
                outcomes.all { it.reachedPercent < 15 } ->
                    line("Nothing connected, and nothing got past the first handshake — the bridges are being blocked. Add fresh bridges in the Bridges screen.")
                else ->
                    line("Nothing completed. The routes reached a relay but could not finish; try again, or add fresh bridges.")
            }
            line("=== end ===")
            progress.update(total, total, "Done")
            out.toString()
        }
    }

    /**
     * Points tor at one route, turns the network on, and watches it bootstrap
     * to a verdict, then turns the network off again so the next route starts
     * clean.
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
                tor.setNetworkEnabled(false)
                return Outcome(transport, 100, true, System.currentTimeMillis() - started, b.summary.ifEmpty { "connected" })
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
