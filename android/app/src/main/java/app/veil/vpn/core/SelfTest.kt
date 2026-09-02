package app.veil.vpn.core

import app.veil.vpn.VeilApp
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.model.Transport
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.NatBehaviour
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.tor.Torrc
import kotlinx.coroutines.Dispatchers
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
 * So each stage is exercised on its own, in order, and reports what it found.
 * Nothing here is a substitute for connecting; it is a substitute for guessing.
 */
object SelfTest {

    suspend fun run(app: VeilApp): String = withContext(Dispatchers.IO) {
        val out = StringBuilder()
        fun line(text: String) {
            out.appendLine(text)
            VeilLog.i("selftest", text)
        }

        line("--- Veil self-test ---")

        // 1. Is there a network at all? Everything below is meaningless if not,
        //    and reporting censorship when the phone is simply offline is a lie
        //    the app has no business telling.
        val network = runCatching { NetworkContext.inspect(app) }.getOrNull()
        if (network == null || !network.isOnline) {
            line("network: offline — nothing else can be tested")
            return@withContext out.toString()
        }
        line("network: ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})")
        line("resolvers: ${network.dnsServers.joinToString().ifEmpty { "none reported" }}")

        // 2. The transports. This is the stage most likely to be silently
        //    broken, and the one that makes everything else impossible: tor can
        //    only be pointed at a plugin that is listening.
        val ports = runCatching { app.pt.startAll() }.getOrElse {
            line("transports: NONE STARTED — ${it.message}")
            emptyMap()
        }
        if (ports.isEmpty()) {
            line("transports: none are listening; only a direct connection could be tried")
        } else {
            Transport.entries.filter { it.isPluggable }.forEach { transport ->
                val port = ports[transport]
                line(
                    "transport ${transport.torName}: " +
                        if (port != null) "listening on 127.0.0.1:$port" else "did not start",
                )
            }
        }
        line("lyrebird ${app.pt.lyrebirdVersion}, snowflake ${app.pt.snowflakeVersion}")

        // 3. What we have to point them at.
        app.bridges.load()
        Transport.entries.filter { it.isPluggable }.forEach { transport ->
            line("bridges ${transport.torName}: ${app.bridges.forTransport(transport, 99).size}")
        }

        // 4. Can we reach the bridge API at all? Fresh bridges are the
        //    difference between the public set everyone has and one nobody has
        //    enumerated yet.
        val refreshed = withTimeoutOrNull(30_000) { app.bridges.refreshFromMoat() }
        line(
            "bridge API: " + when {
                refreshed == null -> "no answer within 30s"
                refreshed.isSuccess -> "answered, ${refreshed.getOrNull()} bridges"
                else -> "failed — ${refreshed.exceptionOrNull()?.message}"
            },
        )

        // 5. What the network does to us.
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
                        " — Snowflake will struggle here, WebTunnel and Conjure will not"
                    } else {
                        ""
                    },
            )
        }

        // 6. Tor itself, with its network left off: this asks only whether it
        //    accepts our configuration and answers its control port, which is
        //    the one thing a blocked network cannot cause to fail.
        val reserved = runCatching { LoopbackPorts.reserve(2) }.getOrDefault(emptyList())
        if (reserved.size < 2) {
            line("tor: could not reserve local ports")
            return@withContext out.toString()
        }
        val session = Torrc.Session(ports, reserved[0], reserved[1], opening = null)
        val started = withTimeoutOrNull(60_000) {
            app.tor.start(Torrc.build(session), Torrc.minimal(session), reserved[0], reserved[1])
        } ?: false
        if (started) {
            line("tor: started, control port answering, socks ${app.tor.socks}")
        } else {
            line("tor: DID NOT START — ${app.tor.lastError.value ?: "no reason reported"}")
        }
        runCatching { app.tor.stop() }
        app.pt.stopAll()

        line("--- end ---")
        out.toString()
    }
}
