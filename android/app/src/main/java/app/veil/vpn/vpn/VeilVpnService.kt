package app.veil.vpn.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import app.veil.tun.veiltun.Config
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.R
import app.veil.vpn.VeilApp
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.AppRoutingMode
import app.veil.vpn.data.NetworkContext
import app.veil.vpn.data.RouteMode
import app.veil.vpn.data.VeilSettings
import app.veil.vpn.model.Transport
import app.veil.vpn.model.TunnelState
import app.veil.vpn.model.TunnelStats
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.SocketProtector
import app.veil.vpn.tor.Attempt
import app.veil.vpn.tor.StrategyPlanner
import app.veil.vpn.tor.Torrc
import app.veil.vpn.tor.runFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramSocket
import java.net.Socket

/**
 * The VPN itself.
 *
 * Sequence for one connect:
 *
 *  1. If the kill switch is on, put the interface up immediately with nothing
 *     reading it. Packets are dropped, so nothing leaks while we are still
 *     working out how to get out.
 *  2. Measure the network, then walk the escalation ladder. Each rung starts
 *     its pluggable transport, writes a torrc and waits for tor to bootstrap
 *     within a budget that suits that transport.
 *  3. On the first rung that reaches 100%, re-establish the interface and hand
 *     its file descriptor to the native tunnel, pointed at tor's SOCKS port.
 *
 * Our own package is always excluded from the VPN. Without that, tor's and the
 * transports' own sockets would be routed back into the interface they are
 * meant to feed, and nothing would ever connect.
 */
class VeilVpnService : VpnService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var worker: Job? = null

    private var tunnelInterface: ParcelFileDescriptor? = null
    private var nativeTunnelRunning = false
    private var statsJob: Job? = null

    private val container: VeilApp get() = application as VeilApp

    private val protector = object : SocketProtector {
        override fun protect(socket: Socket) { this@VeilVpnService.protect(socket) }
        override fun protect(socket: DatagramSocket) { this@VeilVpnService.protect(socket) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            ACTION_NEW_CIRCUIT -> {
                scope.launch { container.tor.requestNewIdentity() }
                return START_STICKY
            }
        }

        startForeground(
            VpnNotifications.ID,
            VpnNotifications.build(this, TunnelState.Probing("Starting", 0, 1)),
        )
        if (worker?.isActive != true) {
            worker = scope.launch { runTunnel() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        VeilLog.w("vpn", "another VPN took over the slot")
        stopTunnel()
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    // --- Connection sequence ------------------------------------------------

    private suspend fun runTunnel() {
        val settings = container.settings.settings.first()
        container.bridges.load()
        container.memory.load()

        if (settings.killSwitch) {
            // Interface up, nothing reading it: a real kill switch rather than
            // a promise to be careful.
            establishInterface(settings)
            VeilLog.i("vpn", "kill switch active while connecting")
        }

        val network = NetworkContext.inspect(this)
        VeilLog.i(
            "vpn",
            "network ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})",
        )

        val ladder = buildLadder(settings, network)
        if (ladder.isEmpty()) {
            fail("No route to try. Add a bridge, or check that the device is online.", emptyList())
            return
        }
        TunnelBus.publishLadder(ladder)

        val tried = mutableListOf<Transport>()
        for ((index, attempt) in ladder.withIndex()) {
            if (!scope.isActive) return
            tried += attempt.transport
            update(TunnelState.Starting(attempt.transport, index + 1, ladder.size))
            VeilLog.i("vpn", "attempt ${index + 1}/${ladder.size}: ${attempt.label} (${attempt.why})")

            val started = System.currentTimeMillis()
            if (tryAttempt(attempt, index, ladder.size, settings)) {
                container.memory.recordSuccess(
                    network.fingerprint,
                    attempt.transport,
                    System.currentTimeMillis() - started,
                )
                container.bridges.recordSuccess(attempt.bridges)
                return
            }

            container.memory.recordFailure(network.fingerprint, attempt.transport)
            container.bridges.recordFailure(attempt.bridges)
            teardownAttempt()

            val next = ladder.getOrNull(index + 1)
            if (next != null) {
                update(
                    TunnelState.Escalating(
                        from = attempt.transport,
                        to = next.transport,
                        reason = "${attempt.label} did not bootstrap in time",
                    ),
                )
                delay(600)
            }
        }

        fail("Every route was tried and none held.", tried)
    }

    private suspend fun buildLadder(settings: VeilSettings, network: NetworkContext): List<Attempt> {
        if (settings.routeMode == RouteMode.MANUAL) {
            return container.planner.manualPlan(settings.manualTransport)
        }

        update(TunnelState.Probing("Measuring the network", 0, 5))
        // A refresh is cheap when it works and irrelevant when it does not: the
        // shipped snapshot still covers the first connect.
        container.bridges.refreshFromMoat()

        val probe = NetworkProbe(protector)
        val report = probe.runFor(container.bridges) { done, total, note ->
            update(TunnelState.Probing(note, done, total))
        }
        TunnelBus.publish(report)
        return container.planner.plan(network, report, probe.rank(report))
    }

    /** Runs one rung to a verdict. Returns true when the tunnel is carrying traffic. */
    private suspend fun tryAttempt(
        attempt: Attempt,
        index: Int,
        ladderSize: Int,
        settings: VeilSettings,
    ): Boolean {
        val transportPort = runCatching {
            container.pt.start(attempt.transport, attempt.bridges, attempt.ampRendezvous)
        }.getOrElse {
            VeilLog.e("vpn", "could not start ${attempt.label}", it)
            return false
        }

        val torrc = Torrc.build(
            Torrc.Plan(
                transport = attempt.transport,
                bridges = attempt.bridges,
                transportPort = transportPort,
            ),
        )
        if (!container.tor.start(torrc)) return false

        if (!awaitBootstrap(attempt, index, ladderSize)) return false

        val socksPort = container.tor.socksPort
        if (socksPort <= 0) {
            VeilLog.e("vpn", "tor bootstrapped but exposes no SOCKS port")
            return false
        }

        val started = runCatching {
            startNativeTunnel(settings, socksPort, container.tor.dnsPort)
        }.onFailure { VeilLog.e("vpn", "could not raise the tunnel", it) }.isSuccess
        if (!started) return false

        update(TunnelState.Connected(attempt.transport, System.currentTimeMillis(), socksPort))
        startStatsPump()
        return true
    }

    /**
     * Waits for tor to finish bootstrapping, giving up either when the rung's
     * budget runs out or when progress stops moving. The stall check is what
     * makes the difference between a tool that gives up in half a minute and
     * one that leaves the user staring at "10%" for five.
     */
    private suspend fun awaitBootstrap(attempt: Attempt, index: Int, ladderSize: Int): Boolean {
        val budget = StrategyPlanner.budgetMillis(attempt.transport)
        val deadline = System.currentTimeMillis() + budget
        var lastPercent = -1
        var lastProgressAt = System.currentTimeMillis()

        while (scope.isActive && System.currentTimeMillis() < deadline) {
            val bootstrap = container.tor.bootstrap.value
            if (bootstrap.percent != lastPercent) {
                lastPercent = bootstrap.percent
                lastProgressAt = System.currentTimeMillis()
                update(
                    TunnelState.Bootstrapping(
                        transport = attempt.transport,
                        percent = bootstrap.percent,
                        summary = bootstrap.summary.ifEmpty { attempt.label },
                        attempt = index + 1,
                        ladderSize = ladderSize,
                    ),
                )
            }
            if (bootstrap.isDone) return true
            if (System.currentTimeMillis() - lastProgressAt > StrategyPlanner.STALL_MILLIS) {
                VeilLog.w("vpn", "${attempt.label} stalled at $lastPercent%")
                return false
            }
            delay(250)
        }
        VeilLog.w("vpn", "${attempt.label} ran out of time at $lastPercent%")
        return false
    }

    // --- Native tunnel ------------------------------------------------------

    private suspend fun startNativeTunnel(settings: VeilSettings, socksPort: Int, dnsPort: Int) =
        withContext(Dispatchers.IO) {
            val descriptor = establishInterface(settings)
                ?: error("could not establish the VPN interface")

            val dnsTarget = when (settings.dnsMode.nativeMode) {
                "udp" -> {
                    require(dnsPort > 0) { "tor exposes no DNSPort" }
                    "127.0.0.1:$dnsPort"
                }
                "tcp" -> settings.tcpDnsResolver
                else -> settings.dohEndpoint
            }

            val config = Config()
            config.setFd(descriptor.detachFd().toLong())
            config.setMtu(MTU.toLong())
            config.setSocksAddr("127.0.0.1:$socksPort")
            config.setIsolateBy(settings.isolation.nativeMode)
            config.setDNSMode(settings.dnsMode.nativeMode)
            config.setDNSAddr(dnsTarget)
            config.setBlockUDP(settings.blockUdp)
            config.setUDPTimeoutSec(60)
            config.setDialTimeoutSec(30)

            // The descriptor now belongs to the native side.
            tunnelInterface = null

            Veiltun.resetStats()
            Veiltun.setLogger { level, message ->
                when (level) {
                    "warn" -> VeilLog.w("tun", message)
                    "error" -> VeilLog.e("tun", message)
                    else -> VeilLog.d("tun", message)
                }
            }
            Veiltun.start(config)
            nativeTunnelRunning = true
            VeilLog.i("vpn", "tunnel carrying traffic via 127.0.0.1:$socksPort, dns $dnsTarget")
        }

    /**
     * Builds and raises the interface. Until the native tunnel is attached to
     * the returned descriptor, packets that enter it are simply dropped, which
     * is exactly the behaviour the kill switch needs.
     */
    private fun establishInterface(settings: VeilSettings): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS_V4, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(TUN_DNS_V4)
            // Tor hands out virtual addresses from this range for names it
            // resolved; the route has to exist or .onion never works.
            .addRoute(Torrc.VIRTUAL_NETWORK_V4.substringBefore('/'), 10)
            .setBlocking(false)

        // Claim IPv6 too. Without an IPv6 route, an IPv6-capable network would
        // simply bypass the tunnel.
        runCatching {
            builder.addAddress(TUN_ADDRESS_V6, 64)
            builder.addRoute("::", 0)
        }.onFailure { VeilLog.w("vpn", "IPv6 unavailable on this interface: $it") }

        applyAppRouting(builder, settings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.setConfigureIntent(VpnNotifications.contentIntent(this))

        val previous = tunnelInterface
        val descriptor = runCatching { builder.establish() }.getOrElse {
            VeilLog.e("vpn", "establish() failed", it)
            null
        }
        // Establishing a new interface invalidates the old one; close it only
        // afterwards so the VPN never blinks off.
        runCatching { previous?.close() }
        tunnelInterface = descriptor
        return descriptor
    }

    private fun applyAppRouting(builder: Builder, settings: VeilSettings) {
        val self = packageName
        when (settings.appRoutingMode) {
            AppRoutingMode.ALL -> {
                runCatching { builder.addDisallowedApplication(self) }
            }
            AppRoutingMode.ONLY_SELECTED -> {
                val chosen = settings.selectedApps.filter { it != self }
                if (chosen.isEmpty()) {
                    VeilLog.w("vpn", "no apps selected; routing everything instead")
                    runCatching { builder.addDisallowedApplication(self) }
                } else {
                    // Our own package is left out of the allow list, which
                    // excludes it just as effectively.
                    chosen.forEach { name ->
                        runCatching { builder.addAllowedApplication(name) }
                            .onFailure { VeilLog.w("vpn", "cannot route $name: $it") }
                    }
                }
            }
            AppRoutingMode.EXCEPT_SELECTED -> {
                (settings.selectedApps + self).forEach { name ->
                    runCatching { builder.addDisallowedApplication(name) }
                        .onFailure { VeilLog.w("vpn", "cannot exclude $name: $it") }
                }
            }
        }
    }

    private fun startStatsPump() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                runCatching { Veiltun.snapshot() }.getOrNull()?.let { snapshot ->
                    TunnelBus.publish(
                        TunnelStats(
                            rxBytes = snapshot.rxBytes,
                            txBytes = snapshot.txBytes,
                            tcpOpen = snapshot.tcpOpen,
                            dnsQueries = snapshot.dnsQueries,
                            dnsErrors = snapshot.dnsErrors,
                            blockedUdp = snapshot.blocked,
                            dialErrors = snapshot.dialErrors,
                        ),
                    )
                }
                TunnelBus.publishCircuit(container.tor.describeCircuit())
                delay(1_500)
            }
        }
    }

    // --- Teardown -----------------------------------------------------------

    private suspend fun teardownAttempt() {
        stopNativeTunnel()
        container.tor.stop()
        container.pt.stopSnowflake()
    }

    private fun stopNativeTunnel() {
        statsJob?.cancel()
        statsJob = null
        if (nativeTunnelRunning) {
            runCatching { Veiltun.stop() }
            runCatching { Veiltun.setLogger(null) }
            nativeTunnelRunning = false
        }
    }

    private fun stopTunnel() {
        worker?.cancel()
        worker = null
        update(TunnelState.Stopping)

        // Tearing the native tunnel down waits briefly for in-flight relays,
        // and this is reached from onStartCommand and onDestroy, both of which
        // run on the main thread. Doing it inline is an ANR waiting to happen.
        scope.launch {
            stopNativeTunnel()
            releaseEverything()
            TunnelBus.reset()
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun fail(reason: String, tried: List<Transport>) {
        VeilLog.e("vpn", reason)
        update(TunnelState.Failed(reason, tried))
        stopNativeTunnel()
        scope.launch {
            releaseEverything()
            withContext(Dispatchers.Main) {
                // The interface is already down; drop the foreground state too
                // so the notification does not claim a tunnel that is gone.
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private suspend fun releaseEverything() {
        container.tor.stop()
        container.pt.stopAll()
        runCatching { tunnelInterface?.close() }
        tunnelInterface = null
    }

    private fun update(state: TunnelState) {
        TunnelBus.publish(state)
        VpnNotifications.update(this, state)
    }

    companion object {
        const val ACTION_DISCONNECT = "app.veil.vpn.DISCONNECT"
        const val ACTION_NEW_CIRCUIT = "app.veil.vpn.NEW_CIRCUIT"

        private const val MTU = 1500
        private const val TUN_ADDRESS_V4 = "10.55.0.1"
        private const val TUN_DNS_V4 = "10.55.0.2"
        private const val TUN_ADDRESS_V6 = "fd00:5645:494c::1"
    }
}
