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
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.model.TunnelState
import app.veil.vpn.model.TunnelStats
import app.veil.vpn.net.HandshakeGovernor
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.SocketProtector
import app.veil.vpn.tor.Attempt
import app.veil.vpn.tor.SocksEndpoint
import app.veil.vpn.tor.StrategyPlanner
import app.veil.vpn.tor.Torrc
import app.veil.vpn.tor.runFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

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

    /** How far the last attempt's bootstrap got, for classifying its failure. */
    private var lastBootstrapPercent = 0

    /** Guards against a second stop request piling onto a running teardown. */
    private val stopping = AtomicBoolean(false)
    private var teardownJob: Job? = null
    private var lastStartId = 0

    private val container: VeilApp get() = application as VeilApp

    private val protector = object : SocketProtector {
        override fun protect(socket: Socket) { this@VeilVpnService.protect(socket) }
        override fun protect(socket: DatagramSocket) { this@VeilVpnService.protect(socket) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                requestStop("asked to disconnect")
                return START_NOT_STICKY
            }
            ACTION_NEW_CIRCUIT -> {
                scope.launch { container.tor.requestNewIdentity() }
                return START_STICKY
            }
        }

        lastStartId = startId
        startForeground(
            VpnNotifications.ID,
            VpnNotifications.build(this, TunnelState.Probing("Starting", 0, 1)),
        )
        if (worker?.isActive != true) {
            val previousTeardown = teardownJob
            worker = scope.launch {
                // Connecting again while the previous session is still being
                // torn down must not race it: wait, but never longer than the
                // teardown is allowed to take in the first place.
                previousTeardown?.let {
                    withTimeoutOrNull(TEARDOWN_BUDGET_MILLIS) { it.join() }
                }
                stopping.set(false)
                runTunnel()
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        VeilLog.w("vpn", "another VPN took over the slot")
        requestStop("the VPN slot was taken by another app")
    }

    override fun onDestroy() {
        // Teardown runs on a scope that outlives this service, so cancelling
        // ours here cannot strand it half-finished.
        requestStop("service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    // --- Connection sequence ------------------------------------------------

    private suspend fun runTunnel() {
        HandshakeGovernor.reset()
        val settings = container.settings.settings.first()
        container.bridges.load()
        container.memory.load()

        // Read the network before raising the interface. This app is excluded
        // from its own VPN, so the answer should be the same either way — but
        // "should" is doing work there, and everything downstream depends on
        // this describing the real network rather than our own tunnel: the
        // fingerprint that per-network memory is keyed on, and the resolvers a
        // bypassed lookup is sent to.
        val network = NetworkContext.inspect(this)
        VeilLog.i(
            "vpn",
            "network ${network.kind} ${network.countryIso ?: "??"} (${network.fingerprint})",
        )

        if (settings.killSwitch) {
            // Interface up, nothing reading it: a real kill switch rather than
            // a promise to be careful.
            establishInterface(settings)
            VeilLog.i("vpn", "kill switch active while connecting")
        }

        val ladder = buildLadder(settings, network)
        if (ladder.isEmpty()) {
            fail("No route to try. Add a bridge, or check that the device is online.", emptyList())
            return
        }
        TunnelBus.publishLadder(ladder)

        val tried = mutableListOf<Transport>()
        for ((index, attempt) in ladder.withIndex()) {
            // The worker's own job, not the service scope: cancelling the
            // connect has to end this loop even though the scope lives on.
            coroutineContext.ensureActive()
            tried += attempt.transport
            update(TunnelState.Starting(attempt.transport, index + 1, ladder.size))
            VeilLog.i("vpn", "attempt ${index + 1}/${ladder.size}: ${attempt.label} (${attempt.why})")

            val started = System.currentTimeMillis()
            if (tryAttempt(attempt, index, ladder.size, settings, network)) {
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
            noteAttemptFailure(attempt)
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
        // AUTO resolves to a profile that is fixed for this installation, so
        // the population does not all present the same Client Hello.
        val tls = TlsProfile.resolve(settings.tlsProfile, container.settings.installSeed())
        VeilLog.i("vpn", "client hello profile: ${tls.label}")

        if (settings.routeMode == RouteMode.MANUAL) {
            return container.planner.manualPlan(
                settings.manualTransport,
                tls,
                settings.dtlsProfile,
            )
        }

        update(TunnelState.Probing("Measuring the network", 0, 5))
        // A refresh is cheap when it works and irrelevant when it does not: the
        // shipped snapshot still covers the first connect.
        container.bridges.refreshFromMoat()

        val probe = NetworkProbe(protector, container.cooldown)
        val report = probe.runFor(container.bridges) { done, total, note ->
            update(TunnelState.Probing(note, done, total))
        }
        TunnelBus.publish(report)
        TunnelBus.publishCooldowns(container.cooldown.describe())
        return container.planner.plan(
            network,
            report,
            probe.rank(report),
            tls,
            settings.dtlsProfile,
        )
    }

    /** Runs one rung to a verdict. Returns true when the tunnel is carrying traffic. */
    private suspend fun tryAttempt(
        attempt: Attempt,
        index: Int,
        ladderSize: Int,
        settings: VeilSettings,
        network: NetworkContext,
    ): Boolean {
        val transportPort = runCatching {
            withContext(Dispatchers.IO) {
                container.pt.start(attempt.transport, attempt.bridges, attempt.ampRendezvous)
            }
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

        publishLocalListeners(attempt, transportPort)

        if (!awaitBootstrap(attempt, index, ladderSize)) return false

        val socks = container.tor.socks
        if (socks == null) {
            VeilLog.e("vpn", "tor bootstrapped but exposes no SOCKS listener")
            return false
        }
        publishLocalListeners(attempt, transportPort)

        val started = runCatching {
            startNativeTunnel(settings, socks, container.tor.dnsPort, network)
        }.onFailure { VeilLog.e("vpn", "could not raise the tunnel", it) }.isSuccess
        if (!started) return false

        update(TunnelState.Connected(attempt.transport, System.currentTimeMillis(), socks.port))
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
        lastBootstrapPercent = 0

        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            val bootstrap = container.tor.bootstrap.value
            if (bootstrap.percent != lastPercent) {
                lastPercent = bootstrap.percent
                lastBootstrapPercent = bootstrap.percent
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

    /**
     * Records what a failed rung tells us about its endpoints.
     *
     * The distinction is worth making. A bridge that never got past the first
     * few per cent was unreachable, and is worth a short pause. A bridge that
     * accepted the connection, let tor start talking, and then went silent
     * matches the blackhole penalty: retrying it inside that window is wasted
     * time, and retrying it with a different fingerprint is reported to make
     * the penalty several times longer.
     */
    private fun noteAttemptFailure(attempt: Attempt) {
        val hosts = attempt.bridges.filter { it.hasRoutableAddress }.map { it.host }.distinct()
        if (hosts.isEmpty()) return
        if (lastBootstrapPercent >= FREEZE_LIKE_PROGRESS) {
            hosts.forEach {
                container.cooldown.markFrozen(it, "answered, then stopped at $lastBootstrapPercent%")
            }
        } else {
            hosts.forEach { container.cooldown.markFailed(it, "never answered") }
        }
        TunnelBus.publishCooldowns(container.cooldown.describe())
    }

    /**
     * Publishes the loopback ports this app is responsible for.
     *
     * Any app on the device can connect to these. Nothing here can be closed
     * off while tor needs a TCP listener for its transport plugins, so the
     * honest thing is to show the user exactly what is open.
     */
    private fun publishLocalListeners(attempt: Attempt, transportPort: Int?) {
        val listeners = buildList {
            container.tor.socks?.let {
                add(
                    LocalListener(
                        name = "Tor SOCKS",
                        endpoint = it.toString(),
                        note = "Randomised port. Another app could route traffic through it; " +
                            "that traffic would still leave through Tor.",
                    ),
                )
            }
            container.tor.dnsPort.takeIf { it > 0 }?.let {
                add(
                    LocalListener(
                        name = "Tor DNS",
                        endpoint = "udp://127.0.0.1:$it",
                        note = "Resolves names inside Tor. Needed for .onion addresses to work.",
                    ),
                )
            }
            transportPort?.let {
                add(
                    LocalListener(
                        name = "${attempt.transport.label} plugin",
                        endpoint = "tcp://127.0.0.1:$it",
                        note = "tor reaches its bridges through this. Randomised on every start.",
                    ),
                )
            }
        }
        TunnelBus.publishListeners(listeners)
    }

    // --- Native tunnel ------------------------------------------------------

    private suspend fun startNativeTunnel(
        settings: VeilSettings,
        socks: SocksEndpoint,
        dnsPort: Int,
        network: NetworkContext,
    ) =
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
            config.setSocksAddr(socks.address)
            config.setSocksNetwork(socks.network)
            config.setIsolateBy(settings.isolation.nativeMode)
            config.setDNSMode(settings.dnsMode.nativeMode)
            config.setDNSAddr(dnsTarget)
            config.setBlockUDP(settings.blockUdp)
            config.setUDPTimeoutSec(60)
            config.setDialTimeoutSec(30)
            // Only meaningful together: a suffix list with nowhere to resolve
            // it is off, which is the safe way round.
            if (settings.bypassSuffixes.isNotBlank() && network.dnsServers.isNotEmpty()) {
                config.setBypassSuffixes(settings.bypassSuffixes)
                config.setBypassDNS(network.dnsServers.joinToString(","))
                VeilLog.w(
                    "vpn",
                    "names ending in ${settings.bypassSuffixes} will skip the tunnel " +
                        "and be visible to this network",
                )
            }

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
            VeilLog.i("vpn", "tunnel carrying traffic via $socks, dns $dnsTarget")
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

    private suspend fun stopNativeTunnel() = withContext(Dispatchers.IO) {
        statsJob?.cancel()
        statsJob = null
        if (nativeTunnelRunning) {
            runCatching { Veiltun.stop() }
            runCatching { Veiltun.setLogger(null) }
            nativeTunnelRunning = false
        }
    }

    /**
     * Brings everything down, and does so within a fixed budget.
     *
     * Cancelling a connect is not a matter of setting a flag. tor is inside a
     * bound service, the transports are Go code reached over JNI, and neither
     * can be interrupted mid-call — a `cancel()` is only noticed at the next
     * suspension point, which may be seconds away or, if something is wedged,
     * never. Waiting for that politely is how a cancel turns into a permanent
     * "Disconnecting" that only a force-stop clears.
     *
     * So the teardown is given a deadline it cannot overrun: the work runs in
     * its own job, this waits on that job for as long as the budget allows, and
     * then the service goes down regardless. A blocked native call is left to
     * finish on its own thread; it no longer holds the app hostage.
     *
     * A second stop request while one is running is treated as "the first one
     * is stuck": it stops immediately rather than being ignored.
     */
    private fun requestStop(reason: String) {
        val stopForId = lastStartId
        if (!stopping.compareAndSet(false, true)) {
            VeilLog.w("vpn", "stop requested again ($reason); going down now")
            finishStopNow(stopForId)
            return
        }
        VeilLog.i("vpn", "stopping: $reason")
        update(TunnelState.Stopping)

        val app = container
        teardownJob = app.teardownScope.launch {
            val work = app.teardownScope.launch {
                // Cancel, then give the connect a moment to unwind, but never
                // wait on it: if it is inside a native call it will not return
                // until that call does, and the point of this whole path is
                // that the user is not made to wait for that.
                worker?.cancel()
                withTimeoutOrNull(WORKER_UNWIND_MILLIS) { worker?.join() }
                worker = null
                runCatching { stopNativeTunnel() }
                runCatching { releaseEverything() }
            }
            // join() is cancellable even when the work inside is not, which is
            // the whole point of splitting them.
            if (withTimeoutOrNull(TEARDOWN_BUDGET_MILLIS) { work.join() } == null) {
                VeilLog.w(
                    "vpn",
                    "teardown still running after ${TEARDOWN_BUDGET_MILLIS / 1000}s; " +
                        "shutting down anyway",
                )
            }
            finishStopNow(stopForId)
        }
    }

    /**
     * Drops the notification and the service, and puts the UI back to idle.
     *
     * The start id is the one that was current when the stop was asked for, not
     * whatever is current now: cancelling and immediately reconnecting is a
     * normal thing to do, and the teardown of the old session must not take the
     * new one down with it.
     */
    private fun finishStopNow(stopForId: Int) {
        TunnelBus.reset()
        runCatching {
            android.os.Handler(mainLooper).post {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                // stopSelfResult ignores the request if a newer start command
                // has arrived, so reconnecting during a teardown works.
                runCatching { stopSelfResult(stopForId) }
            }
        }
    }

    private fun fail(reason: String, tried: List<Transport>) {
        VeilLog.e("vpn", reason)
        update(TunnelState.Failed(reason, tried))

        val app = container
        val stopForId = lastStartId
        stopping.set(true)
        teardownJob = app.teardownScope.launch {
            val work = app.teardownScope.launch {
                runCatching { stopNativeTunnel() }
                runCatching { releaseEverything() }
            }
            withTimeoutOrNull(TEARDOWN_BUDGET_MILLIS) { work.join() }
            // The failure itself stays on screen: the notification is detached
            // rather than removed, and the tunnel state keeps saying why.
            runCatching {
                android.os.Handler(mainLooper).post {
                    runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
                    runCatching { stopSelfResult(stopForId) }
                }
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

        /**
         * Bootstrap percentages above this mean tor had a working connection to
         * the bridge before things went quiet.
         */
        private const val FREEZE_LIKE_PROGRESS = 10

        /**
         * How long a cancel may take before the service goes down regardless.
         * Long enough for a clean shutdown, short enough that a wedged native
         * call is never something the user has to force-stop the app to escape.
         */
        private const val TEARDOWN_BUDGET_MILLIS = 6_000L

        /**
         * How long the connect gets to notice it was cancelled before teardown
         * starts anyway. Short, because teardown is what actually stops things.
         */
        private const val WORKER_UNWIND_MILLIS = 1_500L
    }
}
