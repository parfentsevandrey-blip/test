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
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.NetworkProbe
import app.veil.vpn.net.ProbeReport
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

    /** The slow bridge fetch that runs alongside a connect attempt. */
    private var lateBridgeJob: Job? = null
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
            VpnNotifications.build(this, TunnelState.Probing(R.string.step_starting, 0, 1)),
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

        // Mullvad's client keeps an offline monitor for the same reason: a
        // phone with no network at all will fail every route, and reporting
        // that as censorship is a lie that sends the user looking in the wrong
        // place. This is the one failure the app can be certain about.
        if (!network.isOnline) {
            fail(getString(R.string.fail_offline), emptyList())
            return
        }

        if (settings.killSwitch) {
            // Interface up, nothing reading it: a real kill switch rather than
            // a promise to be careful.
            establishInterface(settings)
            VeilLog.i("vpn", "kill switch active while connecting")
        }

        // Every transport listener first, then tor once. tor is configured for
        // all of them up front so that changing route later is a control-port
        // command rather than a restart — which is what makes the second and
        // third attempts work at all.
        update(TunnelState.Probing(R.string.step_starting_transports, 0, 3))
        val ports = withContext(Dispatchers.IO) {
            runCatching { container.pt.startAll() }.getOrElse {
                VeilLog.e("vpn", "no transport could be started", it)
                emptyMap()
            }
        }
        publishLocalListeners(ports)

        // The ladder is decided before tor is started, so that tor can be
        // started already pointed at its first rung. Tor is at its most
        // reliable doing what it was launched to do; only changing route needs
        // the control port, and only the second rung onwards is a change.
        val ladder = buildLadder(settings, network, ports.keys)
        if (ladder.isEmpty()) {
            // "Nothing to try" has three quite different causes, and telling
            // them apart is the difference between a user who can act on the
            // message and one who can only reinstall.
            fail(
                when {
                    ports.isEmpty() -> getString(R.string.fail_no_transports)
                    settings.routeMode == RouteMode.MANUAL -> getString(
                        R.string.fail_transport_unavailable,
                        getString(settings.manualTransport.labelRes),
                    )
                    else -> getString(R.string.fail_no_route)
                },
                emptyList(),
            )
            return
        }

        update(TunnelState.Probing(R.string.step_starting_tor, 2, 3))
        // Both ports are chosen here rather than left to `SocksPort auto`,
        // which binds a different ephemeral port every time tor reopens its
        // listeners and so cannot be read once and relied on afterwards.
        val reserved = runCatching { LoopbackPorts.reserve(2) }.getOrDefault(emptyList())
        if (reserved.size < 2) {
            fail(getString(R.string.fail_ports), emptyList())
            return
        }
        val session = Torrc.Session(
            plugins = ports,
            socksPort = reserved[0],
            dnsPort = reserved[1],
            opening = ladder.first(),
        )
        val torUp = container.tor.start(
            torrc = Torrc.build(session),
            fallbackTorrc = Torrc.minimal(session),
            socksPort = reserved[0],
            dnsPort = reserved[1],
        )
        if (!torUp) {
            fail(
                container.tor.lastError.value
                    ?.let { getString(R.string.fail_tor_start_reason, it) }
                    ?: getString(R.string.fail_tor_start),
                emptyList(),
            )
            return
        }
        publishLocalListeners(ports)

        TunnelBus.publishLadder(ladder)

        // Bridges that arrive late still count. WebTunnel's are handed out per
        // request and are in nobody's built-in list, so on a censored network
        // they only turn up through a fronted request that takes longer than a
        // connect should wait for. Rather than delay the attempt or abandon
        // them, the request runs alongside it and whatever comes back is added
        // to the attempt already in progress.
        lateBridgeJob?.cancel()
        lateBridgeJob = scope.launch {
            val late = runCatching {
                container.planner.fetchLateBridges(
                    network.countryIso,
                    TlsProfile.resolve(settings.tlsProfile, container.settings.installSeed()),
                    settings.dtlsProfile,
                )
            }.getOrDefault(emptyMap())
            late.forEach { (transport, lines) ->
                if (transport in ports.keys) {
                    VeilLog.i("vpn", "bridges arrived for ${transport.torName}; adding it")
                    container.tor.addRoute(transport, lines)
                }
            }
        }

        // Mullvad's multiplexer, adapted. Rather than walking the ladder and
        // waiting out each dead route in turn, the first few routes are started
        // a few seconds apart and whichever answers first is kept. Time spent
        // waiting for a route to fail is time spent learning nothing.
        val racers = ladder.distinctBy { it.transport }.take(RACE_WIDTH)
        if (racers.size > 1 && raceRoutes(racers, settings, network, ladder.size)) return

        val tried = racers.map { it.transport }.toMutableList()
        val rest = if (racers.size > 1) ladder.filter { it !in racers } else ladder
        for ((index, attempt) in rest.withIndex()) {
            // The worker's own job, not the service scope: cancelling the
            // connect has to end this loop even though the scope lives on.
            coroutineContext.ensureActive()
            tried += attempt.transport
            update(TunnelState.Starting(attempt.transport, index + 1, rest.size))
            VeilLog.i("vpn", "attempt ${index + 1}/${rest.size}: ${attempt.label} (${attempt.why})")

            val started = System.currentTimeMillis()
            if (tryAttempt(attempt, index + racers.size, rest.size, settings, network)) {
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

            val next = rest.getOrNull(index + 1)
            if (next != null) {
                update(
                    TunnelState.Escalating(
                        from = attempt.transport,
                        to = next.transport,
                        reason = getString(R.string.escalate_reason, getString(attempt.transport.labelRes)),
                    ),
                )
                delay(600)
            }
        }

        // When the bridge service could not be reached, that is the thing worth
        // telling the user: it means the app was running on the bridges it
        // shipped with, which are public and therefore the first ones a censor
        // enumerates. Adding bridges by hand is then the fix, and saying
        // "everything was tried" instead sends them nowhere.
        val starved = Transport.entries
            .filter { it.isPluggable }
            .none { container.bridges.hasRecommended(it) }
        fail(
            when {
                starved -> getString(R.string.fail_no_fresh_bridges)
                container.tor.lastError.value != null ->
                    getString(R.string.fail_all_routes_reason, container.tor.lastError.value)
                else -> getString(R.string.fail_all_routes)
            },
            tried,
        )
    }

    private suspend fun buildLadder(
        settings: VeilSettings,
        network: NetworkContext,
        available: Set<Transport>,
    ): List<Attempt> {
        // AUTO resolves to a profile that is fixed for this installation, so
        // the population does not all present the same Client Hello.
        val tls = TlsProfile.resolve(settings.tlsProfile, container.settings.installSeed())
        VeilLog.i("vpn", "client hello profile: ${tls.name}")

        if (settings.routeMode == RouteMode.MANUAL) {
            return container.planner.manualPlan(
                settings.manualTransport,
                tls,
                settings.dtlsProfile,
                available,
            )
        }

        // Refreshing the public bridge list is worth doing, but not worth
        // waiting for: on a censored network the request is exactly as likely
        // to hang as everything else, and the shipped snapshot plus the
        // country recommendation already give us somewhere to start.
        container.teardownScope.launch { container.bridges.refreshFromMoat() }

        // Measuring a network we already have a confirmed answer for is time
        // the user spends watching a progress bar for information we are not
        // going to act on. If something worked here recently, go straight to
        // it; the ladder below it is still there if it has stopped working.
        val remembered = container.memory.preferredFor(network.fingerprint)
        if (remembered != null && (remembered == Transport.DIRECT || remembered in available)) {
            VeilLog.i("vpn", "skipping the probe: ${remembered.torName} worked here before")
            update(TunnelState.Probing(R.string.step_reconnecting_known, 1, 3))
            return container.planner.plan(
                network,
                ProbeReport(),
                emptyList(),
                tls,
                settings.dtlsProfile,
                available,
            )
        }

        update(TunnelState.Probing(R.string.step_measuring, 1, 3))
        val probe = NetworkProbe(protector, container.cooldown)
        val report = probe.runFor(container.bridges) { done, total, noteRes ->
            update(TunnelState.Probing(noteRes, done, total))
        }
        TunnelBus.publish(report)
        TunnelBus.publishCooldowns(container.cooldown.describe())
        return container.planner.plan(
            network,
            report,
            probe.rank(report),
            tls,
            settings.dtlsProfile,
            available,
        )
    }

    /**
     * Starts several routes a few seconds apart and keeps whichever connects.
     *
     * The first of them is already in the torrc, so tor has been working on it
     * since it started; the rest are added over the control port while it does.
     * Tor tries the bridges it has, so this costs one command per route and no
     * restarts.
     *
     * Only routes that differ in kind are raced. Three obfs4 bridges opened at
     * once look like exactly what they are; an HTTPS connection, a WebRTC
     * session and a connection to a phantom that never answers look like three
     * unrelated things, which is the point.
     */
    private suspend fun raceRoutes(
        racers: List<Attempt>,
        settings: VeilSettings,
        network: NetworkContext,
        ladderSize: Int,
    ): Boolean {
        val slowest = racers.maxByOrNull { StrategyPlanner.budgetMillis(it.transport) }
            ?: return false
        VeilLog.i("vpn", "racing ${racers.joinToString(", ") { it.label }}")
        update(TunnelState.Starting(racers.first().transport, 1, ladderSize))

        container.tor.resetBootstrap()
        container.tor.awaitListeners()

        // The stagger runs alongside the wait rather than before it, so the
        // first route gets its head start and the others arrive while it is
        // still being tried.
        val stagger = scope.launch {
            racers.drop(1).forEach { attempt ->
                delay(RACE_STAGGER_MILLIS)
                container.tor.addRoute(attempt.transport, attempt.bridges)
            }
        }

        val connected = awaitBootstrap(slowest, 0, ladderSize)
        stagger.cancel()

        if (!connected) {
            container.tor.setNetworkEnabled(false)
            racers.forEach {
                container.memory.recordFailure(network.fingerprint, it.transport)
                container.bridges.recordFailure(it.bridges)
                noteAttemptFailure(it)
            }
            teardownAttempt()
            return false
        }

        val socks = container.tor.socks ?: return false
        val started = runCatching {
            startNativeTunnel(settings, socks, container.tor.dnsPort, network)
        }.onFailure { VeilLog.e("vpn", "could not raise the tunnel", it) }.isSuccess
        if (!started) return false

        // Which of them won is worth knowing: it is what the next connect on
        // this network starts with.
        val winner = container.tor.connectedTransport() ?: racers.first().transport
        VeilLog.i("vpn", "connected through ${winner.torName}")
        container.memory.recordSuccess(network.fingerprint, winner, 0)
        racers.firstOrNull { it.transport == winner }?.let {
            container.bridges.recordSuccess(it.bridges)
        }

        update(TunnelState.Connected(winner, System.currentTimeMillis(), socks.port))
        startStatsPump()
        return true
    }

    /** Runs one rung to a verdict. Returns true when the tunnel is carrying traffic. */
    private suspend fun tryAttempt(
        attempt: Attempt,
        index: Int,
        ladderSize: Int,
        settings: VeilSettings,
        network: NetworkContext,
    ): Boolean {
        // Switching route is now two control-port commands. Everything the
        // transport needs beyond its address travels in the bridge line, which
        // the plugin reads per connection, so nothing has to be restarted.
        container.tor.resetBootstrap()

        // The first rung is already in the torrc, so tor has been working on it
        // since it started. Re-applying it would mean clearing the bridges and
        // putting the same ones straight back, which tor reacts to by dropping
        // the connections it had already opened.
        if (index > 0) {
            if (!container.tor.applyRoute(attempt.transport, attempt.bridges)) {
                VeilLog.e("vpn", "could not point tor at ${attempt.label}")
                return false
            }
            if (!container.tor.setNetworkEnabled(true)) {
                VeilLog.e("vpn", "could not enable tor's network")
                return false
            }
        }
        container.tor.awaitListeners()

        if (!awaitBootstrap(attempt, index, ladderSize)) {
            // Off, not just re-pointed: this is what closes every connection to
            // the bridge that just failed, so the next rung starts clean.
            container.tor.setNetworkEnabled(false)
            return false
        }

        val socks = container.tor.socks
        if (socks == null) {
            VeilLog.e("vpn", "tor bootstrapped but exposes no SOCKS listener")
            return false
        }

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
        var deadline = System.currentTimeMillis() + budget
        var routesSeenAt = container.tor.lastRouteAddedAtMillis
        var lastPercent = -1
        var lastProgressAt = System.currentTimeMillis()
        var lastPollAt = 0L
        lastBootstrapPercent = 0

        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            // Events alone are not enough. tor announces progress only when it
            // increases, and it keeps its counter across a route change, so a
            // rung that resumes where the last one stopped can be working
            // perfectly while saying nothing. Ask it directly now and then.
            val now = System.currentTimeMillis()

            // A route added while this was waiting — a racer starting, or
            // bridges that arrived from the bridge service — has not had its
            // turn yet, so the clock starts again for it. Otherwise fetching
            // bridges during an attempt would deliver them just in time to be
            // abandoned.
            val routeAddedAt = container.tor.lastRouteAddedAtMillis
            if (routeAddedAt != routesSeenAt) {
                routesSeenAt = routeAddedAt
                deadline = now + budget
                lastProgressAt = now
                VeilLog.d("vpn", "another route joined; giving it its own budget")
            }

            val bootstrap = if (now - lastPollAt >= BOOTSTRAP_POLL_MILLIS) {
                lastPollAt = now
                container.tor.refreshBootstrap()
            } else {
                container.tor.bootstrap.value
            }
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
            // Tor knows before we do. A route that handshakes and is then cut
            // reports the same percentage with a rising failure count, and
            // waiting out a stall timer on it costs most of a minute for an
            // answer already given. It only counts once the route has also
            // stopped moving, though: a slow transport failing its first
            // attempts while it hunts for a proxy is ordinary, and cutting it
            // off then would throw away the route most likely to work.
            val quiet = System.currentTimeMillis() - lastProgressAt
            if (bootstrap.isHopeless && quiet > HOPELESS_QUIET_MILLIS) {
                VeilLog.w(
                    "vpn",
                    "${attempt.label} failed ${bootstrap.problems} times at $lastPercent%; moving on",
                )
                return false
            }
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
        // Conjure's bridge address is never actually dialled — the connection
        // goes to a phantom the station picks — so cooling it down would only
        // punish a host that had nothing to do with the failure.
        if (attempt.transport == Transport.CONJURE) return
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
    private fun publishLocalListeners(ports: Map<Transport, Int>) {
        val listeners = buildList {
            container.tor.socks?.let {
                add(
                    LocalListener(
                        name = getString(R.string.listener_socks),
                        endpoint = it.toString(),
                        note = getString(R.string.listener_socks_note),
                    ),
                )
            }
            container.tor.dnsPort.takeIf { it > 0 }?.let {
                add(
                    LocalListener(
                        name = getString(R.string.listener_dns),
                        endpoint = "udp://127.0.0.1:$it",
                        note = getString(R.string.listener_dns_note),
                    ),
                )
            }
            ports.forEach { (transport, port) ->
                add(
                    LocalListener(
                        name = getString(R.string.listener_plugin, getString(transport.labelRes)),
                        endpoint = "tcp://127.0.0.1:$port",
                        note = getString(R.string.listener_plugin_note),
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
        var lastCircuitCheck = System.currentTimeMillis()
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

                // Tor stops keeping spare circuits when the tunnel has been
                // quiet, so the first thing the user does after a pause pays
                // for building one. Over Snowflake that is the difference
                // between a page loading and a page seeming to hang.
                val now = System.currentTimeMillis()
                if (now - lastCircuitCheck > CIRCUIT_UPKEEP_MILLIS) {
                    lastCircuitCheck = now
                    runCatching { container.tor.ensureSpareCircuit() }
                }
                delay(1_500)
            }
        }
    }

    // --- Teardown -----------------------------------------------------------

    /**
     * Between rungs nothing is torn down any more: tor stays up with its
     * network off, and the next rung is a `SETCONF` away. Only the native
     * tunnel, if one was raised, has to go.
     */
    private suspend fun teardownAttempt() {
        stopNativeTunnel()
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
                lateBridgeJob?.cancel()
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
                lateBridgeJob?.cancel()
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

        /** How often to ask tor where it is, rather than wait to be told. */
        private const val BOOTSTRAP_POLL_MILLIS = 2_000L

        /** How often to check that a circuit is standing by. */
        private const val CIRCUIT_UPKEEP_MILLIS = 60_000L

        /**
         * How long a route must have stopped moving before tor's own verdict
         * that it is failing is acted on.
         */
        private const val HOPELESS_QUIET_MILLIS = 12_000L

        /**
         * How many routes are started together. Three different kinds of
         * connection is a plausible thing for a phone to be doing at once; more
         * begins to look like the fan of handshakes DPI is watching for.
         */
        private const val RACE_WIDTH = 3

        /** How far apart the racing routes are started. */
        private const val RACE_STAGGER_MILLIS = 6_000L
    }
}
