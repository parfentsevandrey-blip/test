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
import app.veil.vpn.net.StunSurvey
import app.veil.vpn.tor.Bootstrap
import app.veil.vpn.tor.DirectorySeed
import app.veil.vpn.tor.StrategyPlanner
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

    /**
     * Extra time for a route that has its link to the bridge up.
     *
     * The budgets below are opening budgets: enough to tell a dead route from
     * a live one. A route that reached three quarters is neither, and cutting
     * it off there reports a working method as broken — which is what this
     * diagnostic did to meek and to Conjure-over-DNS on a Russian mobile
     * network, both at 95%, both one step from a tunnel.
     */
    private const val LATE_GRACE_MILLIS = 40_000L

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
            val postamble = 5
            val total = preamble + order.size + postamble
            var step = 0
            fun stage(label: String) {
                progress.update(step, total, label)
                step += 1
            }
            fun finish() = progress.update(total, total, app.getString(R.string.diag_stage_done))

            line("=== Veil deep diagnostic ===")
            line("version 0.14.1")

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

            // Whether the system is allowed to cut this app's network while the
            // screen is off. It is the most common reason a tunnel that worked
            // is dead after the phone has been in a pocket, and it is not
            // visible from anything else in this report.
            val power = app.getSystemService(android.os.PowerManager::class.java)
            val exempt = power?.isIgnoringBatteryOptimizations(app.packageName) == true
            line(
                "battery optimisation: " + if (exempt) {
                    "off for this app (the tunnel keeps its network with the screen off)"
                } else {
                    "ON — the system may cut the tunnel's network when the screen is off; turn it off in Settings"
                },
            )

            // The directory tor will start from. Present means it skips the
            // long part of a first bootstrap; the date says how stale the
            // consensus is, which decides whether it fetches a diff or a whole
            // new one.
            line(
                "directory cache: " + when {
                    !DirectorySeed.isPresent(app) -> "none — a first connect downloads the whole directory"
                    else -> "present, consensus from ${DirectorySeed.consensusAge(app) ?: "unknown date"} UTC"
                },
            )

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
            // Three outcomes, and they mean different things. Nothing back
            // inside the budget, or a request that failed, is the fronted call
            // being blocked — the usual case on the networks this is for, and
            // the reason the app is running on the bridges it shipped with. A
            // reply with nothing in it is a service that simply has no advice
            // for this country.
            val fetched = if (country.isNullOrBlank()) null else
                withTimeoutOrNull(45_000) {
                    runCatching { app.bridges.refreshCountry(country) }.getOrNull()
                }
            line(
                "bridge service: " + when {
                    country.isNullOrBlank() -> "country unknown, not asked"
                    fetched == null -> "NO ANSWER within 45s (the fronted request did not get through)"
                    fetched > 0 -> "answered, $fetched line(s) for ${country.uppercase()}"
                    else -> "answered, but has nothing for ${country.uppercase()}"
                },
            )
            order.filter { it.isPluggable }.forEach { t ->
                line("bridges ${t.torName}: ${app.bridges.forTransport(t, 99).size}")
            }
            val history = runCatching { app.memory.describe(network.fingerprint) }.getOrDefault(emptyList())
            line(
                "history on this network: " + if (history.isEmpty()) "nothing recorded yet" else
                    history.joinToString {
                        "${it.transport.torName} ${it.successes}/${it.successes + it.failures}"
                    },
            )

            // --- 5. Reachability and NAT --------------------------------------
            stage(app.getString(R.string.diag_stage_probe))
            // The STUN survey first, on its own line: which servers answer from
            // here and how fast is what decides how long Snowflake spends
            // gathering candidates before it will ask for a proxy, and the
            // list it is handed below is built from this answer.
            val survey = runCatching { StunSurvey.run(network.fingerprint) }.getOrNull()
            line(
                "stun: " + when {
                    survey == null -> "survey failed"
                    survey.answers.isEmpty() -> "NO server answered in ${StunSurvey.WINDOW_MILLIS}ms — Snowflake cannot gather candidates here"
                    else -> "${survey.answers.size}/${StunSurvey.PUBLISHED_SERVERS.size} answered — " +
                        survey.answers.joinToString { "${it.host} ${it.millis}ms" }
                },
            )
            val report = runCatching {
                withTimeoutOrNull(45_000) { NetworkProbe().run(app.bridges.probeTargets()) }
            }.getOrNull()
            if (report == null) {
                line("probe: did not finish")
            } else {
                report.results.forEach { line("probe ${it.verdict}: ${it.millis} ms") }
                line("nat: ${report.natBehaviour}")
                // What the measurements say each method's chances are here.
                // Nothing acts on this any more — the method is the user's
                // choice — but it is the number to compare against the route
                // results below, and a disagreement between them is itself
                // worth seeing.
                line(
                    "expected from the probe: " + NetworkProbe().rank(report)
                        .joinToString { (t, score) -> "${t.torName} ${"%.2f".format(score)}" },
                )
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
                // Exactly what a connect with this method chosen would try,
                // in the same order — including the second way of starting it
                // where there is one. Testing only the fronted rendezvous, as
                // this used to, can report a method dead on a network where its
                // other path connects in seconds, and then pick something else.
                val attempts = app.planner.pinnedPlan(
                    transport, tls, dtls, ports.keys,
                    iceServers = survey?.iceServers,
                )
                if (attempts.isEmpty()) {
                    line("${transport.torName}: skipped, no bridges to try")
                    continue
                }
                for (attempt in attempts) {
                    val outcome = testRoute(app, transport, attempt.bridges)
                    outcomes += outcome
                    line(
                        "${attempt.label}: ${if (outcome.connected) "CONNECTED" else "failed"} " +
                            "at ${outcome.reachedPercent}% in ${outcome.millis / 1000}s — ${outcome.detail}",
                    )
                    if (outcome.connected) {
                        winner = outcome
                        // The diagnostic just proved this method works here, and
                        // the app connects with whatever method is chosen. So it
                        // chooses it — which is the difference between the
                        // diagnostic being a report and it being a fix — and adds
                        // it to the record of what has worked on this network.
                        runCatching {
                            app.memory.recordSuccess(network.fingerprint, transport, outcome.millis)
                            app.settings.setManualTransport(transport)
                        }
                        line("(set as the chosen method; the next connect will use it)")
                        break
                    }
                }
                if (winner != null) break
            }

            // --- What the connection is actually like -------------------------
            var steady: TrafficAnalysis.Series? = null
            var throughput: TrafficAnalysis.Throughput? = null
            var exitVerdict: List<String> = emptyList()
            if (winner != null) {
                val socks = SocksProxy("127.0.0.1", socksPort)

                stage(app.getString(R.string.diag_stage_exit))
                line("")
                line("--- traffic analysis over ${winner.transport.torName} ---")
                line("circuit: ${app.tor.describeCircuit() ?: "not reported"}")
                line("leaving through Tor: ${TrafficAnalysis.exitCheck(socks)}")

                stage(app.getString(R.string.diag_stage_dns))
                // These come back in about a millisecond, and that number is
                // not a measurement of anything at the far end. The torrc sets
                // AutomapHostsOnResolve with a suffix of ".", so tor answers
                // every name from a local table of virtual addresses and only
                // resolves it for real at the exit, when a connection to that
                // address is made. So this checks that the DNS port answers at
                // all — which is worth checking, since nothing on the device
                // resolves anything if it does not — and the round-trip
                // numbers below are where real resolution is actually paid for.
                listOf("torproject.org", "wikipedia.org").forEach { name ->
                    val (millis, note) = TrafficAnalysis.resolveThroughTor(dnsPort, name)
                    line(
                        "dns $name: " + if (millis < 0) {
                            "FAILED — $note"
                        } else {
                            "answered in ${millis} ms, $note (virtual address; resolved at the exit)"
                        },
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

                // The user's own test, done the user's way: a Google search
                // and YouTube, through the exit, plus the sustained rate. A
                // tunnel can pass every measurement above and still be
                // useless for exactly these two sites, because Google treats
                // Tor exits as suspects and a shaped link is fast for the first
                // quarter megabyte. This is where "connected but nothing
                // works" gets a specific name.
                stage(app.getString(R.string.diag_stage_youtube))
                line("")
                line("--- google and youtube through the exit ---")
                val google = TrafficAnalysis.reach(
                    socks, TrafficAnalysis.GOOGLE_HOST, TrafficAnalysis.GOOGLE_SEARCH_PATH,
                )
                line("google search: ${google.describe()}")
                val youtube = TrafficAnalysis.reach(
                    socks, TrafficAnalysis.YOUTUBE_HOST, TrafficAnalysis.YOUTUBE_PATH,
                )
                line("youtube.com: ${youtube.describe()}")
                val image = TrafficAnalysis.download(
                    socks, TrafficAnalysis.YOUTUBE_IMAGE_HOST, TrafficAnalysis.YOUTUBE_IMAGE_PATH,
                )
                line(
                    "youtube image cdn: " + if (image.ok) {
                        "${image.bytes / 1024} KB in ${image.millis} ms = ${image.kbytesPerSecond} KB/s"
                    } else {
                        "FAILED — ${image.note}"
                    },
                )
                val runs = TrafficAnalysis.sustained(socks)
                line(
                    "sustained: " + runs.joinToString(", ") {
                        if (it.ok) "${it.bytes / 1024} KB @ ${it.kbytesPerSecond} KB/s" else "FAILED (${it.note})"
                    },
                )
                exitVerdict = TrafficAnalysis.exitVerdict(google, youtube, image, runs)

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
            exitVerdict.forEach { line(it) }
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
        var deadline = started + budgetMillis(transport)
        // Bounded, so one creeping route cannot hold the whole diagnostic.
        val ceiling = deadline + LATE_GRACE_MILLIS * 2
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
            if (quiet > StrategyPlanner.stallMillis(maxPercent)) {
                detail = "stalled at $maxPercent% — ${failureDetail(b, tor.lastError.value)}"
                break
            }
            if (maxPercent >= StrategyPlanner.LATE_BOOTSTRAP_PERCENT &&
                System.currentTimeMillis() > deadline - LATE_GRACE_MILLIS &&
                deadline < ceiling
            ) {
                deadline = minOf(System.currentTimeMillis() + LATE_GRACE_MILLIS, ceiling)
            }
            delay(500)
        }
        if (System.currentTimeMillis() >= deadline && maxPercent < 100) {
            detail = "timed out at $maxPercent% — ${failureDetail(tor.bootstrap.value, tor.lastError.value)}"
        }
        tor.setNetworkEnabled(false)
        return Outcome(transport, maxPercent, false, System.currentTimeMillis() - started, detail)
    }

    /**
     * Tor warnings that describe the process rather than this route.
     *
     * Tor's heartbeat reports totals since it started, so a line like "6
     * connections died in state handshaking (TLS)" can be emitted during a
     * Snowflake attempt while counting failures from the obfs4 one before it.
     * Printing it as Snowflake's reason for failing is worse than printing
     * nothing: it sends whoever reads the log looking at TLS on a transport
     * that does not use it.
     */
    private val CUMULATIVE_WARNINGS = listOf(
        "connections died in state",
        "Heartbeat:",
        "Since startup",
        "Average packaged cell",
        "circuit handshake",
    )

    /** The most useful thing tor said about why a route did not complete. */
    private fun failureDetail(b: Bootstrap, lastError: String?): String {
        val warning = b.lastWarning
            ?.takeIf { text -> CUMULATIVE_WARNINGS.none { text.contains(it, ignoreCase = true) } }
            ?.substringAfterLast("): ")
            ?.take(80)
        return when {
            !warning.isNullOrBlank() -> warning
            !lastError.isNullOrBlank() -> lastError.take(80)
            b.summary.isNotBlank() -> "stuck: ${b.summary}"
            else -> "no detail from tor"
        }
    }
}
