package app.veil.vpn.core

import app.veil.vpn.VeilApp
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.model.Transport
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.NatBehaviour
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.tor.Torrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers "why is nothing working" without asking the user to connect first.
 *
 * A failed connect can only say that every route was tried and none held, which
 * is true and useless: it does not distinguish a network that blocks everything
 * from a transport that never started, a bridge list that is empty, or a Tor
 * that refuses its own configuration. Those have completely different fixes,
 * and from a phone in another country the difference is not otherwise visible.
 *
 * Each stage is exercised on its own and reports what it found. Two rules keep
 * it from being a crash of its own, which an earlier version was:
 *
 *  - It is single-flight. It drives the shared tor and transport controllers,
 *    and two runs at once — a double tap — had them starting the tor service
 *    twice against its static lock. A mutex makes a second run wait rather than
 *    collide.
 *  - It never fights a live tunnel. Starting tor and then stopping it, or
 *    stopping every transport, while a connection is using them is the other
 *    way it took the process down. When the tunnel is up, the stage that would
 *    start tor is skipped and says so.
 */
object SelfTest {

    /** How the caller learns where the run has got to. */
    fun interface Progress {
        fun update(done: Int, total: Int, label: String)
    }

    private val mutex = Mutex()
    private const val TOTAL_STEPS = 6

    /** True while a run is in progress, so the UI can refuse to start another. */
    val isRunning: Boolean get() = mutex.isLocked

    suspend fun run(app: VeilApp, progress: Progress): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val out = StringBuilder()
            fun line(text: String) {
                out.appendLine(text)
                VeilLog.i("selftest", text)
            }
            var step = 0
            fun stage(label: String) {
                progress.update(step, TOTAL_STEPS, label)
                step += 1
            }

            line("--- Veil self-test ---")
            line("version ${app.let { runCatching { it.packageManager.getPackageInfo(it.packageName, 0).versionName }.getOrNull() ?: "?" }}")

            // 1. Network.
            stage("Network")
            val network = runCatching { NetworkContext.inspect(app) }.getOrNull()
            if (network == null || !network.isOnline) {
                line("network: OFFLINE — nothing else can be tested")
                progress.update(TOTAL_STEPS, TOTAL_STEPS, "Done")
                return@withContext out.toString()
            }
            line("network: ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})")
            line("resolvers: ${network.dnsServers.joinToString().ifEmpty { "none reported" }}")

            // Whether a real connection is using the shared controllers. If so,
            // the tor stage is skipped rather than allowed to collide with it.
            val tunnelLive = runCatching { app.tor.isRunning }.getOrDefault(false)

            // 2. Transports. Only started here when nothing else is using them.
            stage("Transports")
            val ports = if (tunnelLive) {
                line("transports: tunnel is live, leaving them as they are")
                emptyMap()
            } else {
                runCatching { app.pt.startAll() }.getOrElse {
                    line("transports: NONE STARTED — ${it.message}")
                    emptyMap()
                }
            }
            if (!tunnelLive) {
                Transport.entries.filter { it.isPluggable }.forEach { transport ->
                    val port = ports[transport]
                    line(
                        "transport ${transport.torName}: " +
                            if (port != null) "listening on 127.0.0.1:$port" else "DID NOT START",
                    )
                }
                line("lyrebird ${app.pt.lyrebirdVersion}, snowflake ${app.pt.snowflakeVersion}")
            }

            // 3. Bridges we hold.
            stage("Bridges")
            runCatching { app.bridges.load() }
            Transport.entries.filter { it.isPluggable }.forEach { transport ->
                line("bridges ${transport.torName}: ${app.bridges.forTransport(transport, 99).size}")
            }

            // 4. The bridge service — the only source of WebTunnel.
            stage("Bridge service")
            val country = network.countryIso
            val fetched = if (country.isNullOrBlank()) {
                null
            } else {
                withTimeoutOrNull(45_000) { runCatching { app.bridges.refreshCountry(country) }.getOrNull() }
            }
            line(
                "bridge service: " + when {
                    country.isNullOrBlank() -> "country unknown, not asked"
                    fetched == null -> "no answer within 45s"
                    fetched > 0 -> "answered, $fetched bridge line(s) incl. any WebTunnel"
                    else -> "answered but returned nothing usable"
                },
            )
            line("bridges webtunnel after refresh: ${app.bridges.forTransport(Transport.WEBTUNNEL, 99).size}")

            // 5. What the network does to us.
            stage("Probe")
            val report = runCatching {
                withTimeoutOrNull(60_000) { NetworkProbe().run(app.bridges.probeTargets()) }
            }.getOrNull()
            if (report == null) {
                line("probe: did not finish")
            } else {
                report.results.forEach { line("probe ${it.verdict}: ${it.millis} ms") }
                line(
                    "nat: ${report.natBehaviour}" +
                        if (report.natBehaviour == NatBehaviour.SYMMETRIC) {
                            " — Snowflake will struggle, WebTunnel and Conjure will not"
                        } else {
                            ""
                        },
                )
            }

            // 6. Tor, network off — only that it accepts its config and answers.
            stage("Tor")
            if (tunnelLive) {
                line("tor: tunnel already up, not restarting it for the test")
            } else {
                val reserved = runCatching { LoopbackPorts.reserve(2) }.getOrDefault(emptyList())
                if (reserved.size < 2) {
                    line("tor: could not reserve local ports")
                } else {
                    val session = Torrc.Session(ports, reserved[0], reserved[1], opening = null)
                    val started = withTimeoutOrNull(60_000) {
                        runCatching {
                            app.tor.start(
                                Torrc.build(session),
                                Torrc.minimal(session),
                                reserved[0],
                                reserved[1],
                            )
                        }.getOrDefault(false)
                    } ?: false
                    if (started) {
                        line("tor: started, control port answering, socks ${app.tor.socks}")
                    } else {
                        line("tor: DID NOT START — ${app.tor.lastError.value ?: "no reason reported"}")
                    }
                    runCatching { app.tor.stop() }
                    runCatching { app.pt.stopAll() }
                }
            }

            progress.update(TOTAL_STEPS, TOTAL_STEPS, "Done")
            line("--- end ---")
            out.toString()
        }
    }
}
