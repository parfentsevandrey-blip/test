package app.veil.vpn.vpn

import android.content.Intent
import android.net.ConnectivityManager
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
import app.veil.vpn.data.VeilSettings
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.model.Transport
import app.veil.vpn.model.TunnelState
import app.veil.vpn.model.TunnelStats
import app.veil.vpn.net.HandshakeGovernor
import app.veil.vpn.net.LoopbackPorts
import app.veil.vpn.net.SocketProtector
import app.veil.vpn.net.Socks5
import app.veil.vpn.net.SocksProxy
import app.veil.vpn.net.StunSurvey
import app.veil.vpn.tor.Attempt
import app.veil.vpn.tor.SocksEndpoint
import app.veil.vpn.tor.StrategyPlanner
import app.veil.vpn.tor.Torrc
import app.veil.vpn.tor.runFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    /** What this connect found, kept so the failure can say it. */
    private var startedPorts: Map<Transport, Int> = emptyMap()
    private var lastNetwork: NetworkContext? = null
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

    /** When the current connect began, for the timeline in the log. */
    @Volatile private var connectStartedAt = 0L

    /** The STUN survey running alongside the connect, if one was started. */
    private var surveyJob: kotlinx.coroutines.Deferred<StunSurvey.Result?>? = null

    private fun sinceConnect(): Long = System.currentTimeMillis() - connectStartedAt

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
        connectStartedAt = System.currentTimeMillis()
        HandshakeGovernor.reset()
        surveyJob = null
        // Stop the parked engine's shutdown timer before touching it.
        container.wakeEngine()
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

        // The STUN survey starts now and is collected later, so its two seconds
        // overlap the transports and tor coming up instead of adding to them.
        // Only for Snowflake, which is the only thing that reads the answer.
        if (settings.manualTransport == Transport.SNOWFLAKE &&
            StunSurvey.cached(network.fingerprint) == null
        ) {
            surveyJob = scope.async { runCatching { StunSurvey.run(network.fingerprint, protector) }.getOrNull() }
        }

        // Every transport listener first, then tor once. tor is configured for
        // all of them up front so that changing route later is a control-port
        // command rather than a restart — which is what makes the second and
        // third attempts work at all.
        update(TunnelState.Probing(R.string.step_starting_transports, 0, 2))
        val ports = withContext(Dispatchers.IO) {
            runCatching { container.pt.startAll() }.getOrElse {
                VeilLog.e("vpn", "no transport could be started", it)
                emptyMap()
            }
        }
        startedPorts = ports
        lastNetwork = network
        publishLocalListeners(ports)
        VeilLog.i("vpn", "timeline: transports up (+${sinceConnect()}ms)")
        val power = getSystemService(android.os.PowerManager::class.java)
        if (power != null && !power.isIgnoringBatteryOptimizations(packageName)) {
            // The single most common reason a tunnel is dead after the phone
            // has been in a pocket: the system cut this app's network while
            // the screen was off. The setting to stop it doing that is in the
            // app's settings, and this is the moment the user is most likely
            // to care.
            VeilLog.w("vpn", "battery optimisation is ON for this app: the system may cut the tunnel while the screen is off")
        }

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
                    settings.manualTransport !in ports.keys -> getString(
                        R.string.fail_transport_unavailable,
                        getString(settings.manualTransport.labelRes),
                    )
                    else -> getString(
                        R.string.fail_no_bridges_for,
                        getString(settings.manualTransport.labelRes),
                    )
                },
                emptyList(),
            )
            return
        }

        // A tor left warm by the last disconnect is reused. Everything the
        // long part of a connect produces — the directory consensus, the
        // descriptors, the validation of both — is still in that process, so
        // pointing it at a route is a control-port command instead of a fresh
        // bootstrap. The ports it already listens on are kept for the same
        // reason: they are named in a configuration that is not being rewritten.
        val warm = container.tor.isWarmFor(ports)
        update(
            TunnelState.Probing(
                if (warm) R.string.step_resuming_tor else R.string.step_starting_tor,
                1,
                2,
            ),
        )

        var torUp = false
        if (warm) {
            VeilLog.i("vpn", "reusing the warm tor; no bootstrap to repeat")
            torUp = resumeWarmTor(ladder.first())
            if (!torUp) {
                // Most likely something else on the device took one of the
                // loopback ports while the listeners were closed. Nothing is
                // lost but the shortcut, so start cleanly rather than reporting
                // a failure the user cannot act on.
                VeilLog.w("vpn", "the warm tor would not resume; starting a fresh one")
                container.tor.stop()
                update(TunnelState.Probing(R.string.step_starting_tor, 1, 2))
            }
        }

        if (!torUp) {
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
            torUp = container.tor.start(
                torrc = Torrc.build(session),
                fallbackTorrc = Torrc.minimal(session),
                socksPort = reserved[0],
                dnsPort = reserved[1],
                plugins = ports,
            )
        }

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

        VeilLog.i("vpn", "timeline: tor ready for a route (+${sinceConnect()}ms)")
        TunnelBus.publishLadder(ladder)

        // Bridges that arrive late still count. WebTunnel's are handed out per
        // request and are in nobody's built-in list, so on a censored network
        // they only turn up through a fronted request that takes longer than a
        // connect should wait for. Rather than delay the attempt or abandon
        // them, the request runs alongside it and whatever comes back is added
        // to the attempt already in progress.
        // On the app scope, not the connect scope: fetching bridges is slow
        // through a fronted request, and a manual attempt on a blocked
        // transport tears down in seconds. Tying the fetch to that meant it was
        // cancelled every time before it could finish, so WebTunnel — whose
        // bridges have no other source — never got any. Now it runs to
        // completion and persists (BridgeRepository.setRecommended writes them
        // to disk), so the next attempt and the next launch have them even if
        // this attempt has already failed.
        lateBridgeJob?.cancel()
        lateBridgeJob = container.teardownScope.launch {
            val late = runCatching {
                container.planner.fetchLateBridges(
                    network.countryIso,
                    TlsProfile.resolve(settings.tlsProfile, container.settings.installSeed()),
                    settings.dtlsProfile,
                )
            }.getOrDefault(emptyMap())
            late.forEach { (transport, lines) ->
                // Only the chosen method: fresh bridges for something the
                // user did not pick would quietly widen the connect into
                // exactly the search they asked not to have.
                if (transport == settings.manualTransport && transport in ports.keys) {
                    VeilLog.i("vpn", "bridges arrived for ${transport.torName}; adding it")
                    container.tor.addRoute(transport, lines)
                }
            }
        }

        // Mullvad's multiplexer, adapted. Rather than starting one way and
        // waiting out its failure before trying the other, they are started a
        // few seconds apart and whichever answers first is kept.
        //
        // What is being raced here is the two ways of starting one method —
        // Snowflake's broker reached through a fronted request or through an
        // AMP cache, Conjure's station registered with over HTTPS or over DNS —
        // and racing them is the only honest answer to which comes first,
        // because it is genuinely network-dependent. Measured on one user's
        // two networks: on their mobile connection the fronted Snowflake
        // rendezvous returns nothing while the AMP one connects in seconds, and
        // on their Wi-Fi the fronted one connects in thirteen. Guessing an
        // order makes one of those pay for the other. Six seconds apart, with
        // the second added over the control port, costs a few seconds of
        // overlap and never costs a whole failed attempt.
        //
        // These used to be collapsed by transport before racing, which meant
        // exactly one of them ever started.
        //
        // Not every pair can be raced, though. Tor keys a bridge on its address
        // and port, so two lines that share one are a single bridge to it and
        // the second is dropped — offering both at once would silently be
        // offering whichever tor kept. Snowflake's alternative is given its own
        // placeholder address for exactly this reason; Conjure's two registrars
        // share a real relay address that cannot be moved, so those two are
        // tried in turn instead. An attempt left out of the race is not lost:
        // it is still in the ladder below.
        val racers = buildList {
            val claimed = mutableSetOf<String>()
            for (attempt in ladder) {
                if (size >= RACE_WIDTH) break
                val endpoints = attempt.bridges.map { "${it.host}:${it.port}" }
                if (endpoints.any { it in claimed }) continue
                claimed += endpoints
                add(attempt)
            }
        }
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
                        // Two attempts on one method are two ways of starting
                        // it, not a change of method, and saying "Snowflake did
                        // not work, trying Snowflake" would read as a bug.
                        reason = if (next.transport == attempt.transport) {
                            next.why
                        } else {
                            getString(R.string.escalate_reason, getString(attempt.transport.labelRes))
                        },
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

    /**
     * Wakes a warm tor for another connect.
     *
     * A warm tor is in one of two states, and they are handled differently.
     *
     * If the last disconnect was recent, the link was held: see
     * [VeilApp.parkEngine]. Tor's network was never switched off, its
     * connection to the bridge and the circuit through it are still there, and
     * the right thing to do is nothing — re-applying the route would clear the
     * bridge list and set it again, which tor answers by dropping exactly the
     * connection the hold was keeping, turning an instant reconnect back into a
     * slow one. The route is what it was, because the pinned method is what it
     * was; the caller's stream check then confirms the link still carries
     * traffic, and if it does not, the normal rebuild follows.
     *
     * Otherwise the process was parked with its network off. The route is set
     * over the control port, then the network is turned back on — and only then
     * do the SOCKS and DNS listeners exist again, because tor treats
     * `DisableNetwork` as "close everything but the control port". Those two
     * ports were free the whole time the process was parked, so something else
     * on the device may have taken one; if the listeners do not come back, this
     * reports failure and the caller starts a fresh tor.
     *
     * In both cases the bootstrap state is reset locally and then, more to the
     * point, not relied on: a warm tor reports 100% whatever the state of its
     * link, which is why the caller waits for a stream and not for a number.
     */
    private suspend fun resumeWarmTor(opening: Attempt): Boolean {
        // Only a link of the kind that was asked for. A user who disconnects,
        // picks a different method on the Routes screen and reconnects within
        // the hold window must get that method, not the one still running.
        if (container.tor.hasLiveOrConnection() &&
            container.tor.connectedTransport() == opening.transport
        ) {
            VeilLog.i("vpn", "the link was held warm; keeping it as it stands")
            container.tor.resetBootstrap()
            return true
        }
        if (!container.tor.applyRoute(opening.transport, opening.bridges)) return false
        if (!container.tor.setNetworkEnabled(true)) return false
        if (!container.tor.awaitListeners()) {
            VeilLog.w("vpn", "the warm tor did not reopen its listeners")
            return false
        }
        container.tor.resetBootstrap()
        return true
    }

    /**
     * What will be tried: the method the user chose, and only that.
     *
     * There used to be a measurement step here — reachability, NAT behaviour,
     * a ranking — and it earned its place when the app decided for itself which
     * route to take. It does not decide any more. The obfuscation is picked on
     * the Routes screen and remembered, so probing the network answers a
     * question nobody is asking and costs the user half a minute of watching a
     * progress bar before the connect it wanted even starts.
     */
    private suspend fun buildLadder(
        settings: VeilSettings,
        network: NetworkContext,
        available: Set<Transport>,
    ): List<Attempt> {
        // AUTO resolves to a profile that is fixed for this installation, so
        // the population does not all present the same Client Hello.
        val tls = TlsProfile.resolve(settings.tlsProfile, container.settings.installSeed())
        VeilLog.i("vpn", "client hello profile: ${tls.name}")

        // For Snowflake, find out which STUN servers answer from here before
        // the transport is handed a list. It waits for every server on that
        // list to answer or time out before it will so much as ask for a
        // proxy, so this two-and-a-half-second question saves up to five
        // seconds per peer — and is asked once per network, not per connect.
        val iceServers = if (settings.manualTransport == Transport.SNOWFLAKE) {
            val survey = StunSurvey.cached(network.fingerprint)
                ?: surveyJob?.await()
                ?: StunSurvey.run(network.fingerprint, protector)
            VeilLog.i("vpn", "stun: ${survey.answers.size} answer(s), nat ${survey.natBehaviour} (+${sinceConnect()}ms)")
            runCatching { container.pt.setSnowflakeNat(survey.natBehaviour) }
            survey.iceServers
        } else {
            null
        }

        // Refreshing the public bridge list is worth doing, but not worth
        // waiting for: on a censored network the request is exactly as likely
        // to hang as everything else, and the shipped snapshot plus the
        // country recommendation already give us somewhere to start.
        container.teardownScope.launch { container.bridges.refreshFromMoat() }

        return container.planner.pinnedPlan(
            settings.manualTransport,
            tls,
            settings.dtlsProfile,
            available,
            iceServers,
        )
    }

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

        // Bootstrap at 100% is necessary and not sufficient, and treating it as
        // sufficient was the whole of the "connected in one second but nothing
        // works" complaint. A tor that was parked with its network off still
        // reports 100% the instant the network comes back — the number never
        // goes down — while the link underneath is only now being rebuilt,
        // half a minute of it over Snowflake. So the last word is a stream that
        // actually reached the internet, not a percentage. The stagger keeps
        // running meanwhile: on a warm resume the wait is spent here rather
        // than in the bootstrap, and the second racer must still get its turn.
        val socks = container.tor.socks
        val usable = connected && socks != null && awaitUsablePath(slowest.transport, socks)
        stagger.cancel()

        if (!usable || socks == null) {
            container.tor.setNetworkEnabled(false)
            racers.forEach {
                container.memory.recordFailure(network.fingerprint, it.transport)
                container.bridges.recordFailure(it.bridges)
                noteAttemptFailure(it)
            }
            teardownAttempt()
            return false
        }

        val started = runCatching {
            startNativeTunnel(settings, socks, container.tor.dnsPort, network)
        }.onFailure { VeilLog.e("vpn", "could not raise the tunnel", it) }.isSuccess
        if (!started) return false

        val winner = container.tor.connectedTransport() ?: racers.first().transport
        VeilLog.i("vpn", "connected through ${winner.torName}")
        container.memory.recordSuccess(network.fingerprint, winner, 0)
        // Which of the raced ways actually carried it cannot be told apart:
        // the two rendezvous reach the same bridge and tor reports the same
        // fingerprint for both. So neither is blamed. Clearing both counters is
        // the least-wrong thing to do with a race one of them won, and it keeps
        // the ordering neutral rather than crediting whichever happened to be
        // listed first.
        racers.forEach { container.bridges.recordSuccess(it.bridges) }

        update(TunnelState.Connected(winner, System.currentTimeMillis(), socks.port))
        startStatsPump()
        watchNetwork()
        watchScreen()
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

        // Bootstrap says tor has a circuit; a stream that reaches the internet
        // says the tunnel will carry traffic. Only the second earns the user a
        // green screen. See [awaitUsablePath] for why the percentage on its own
        // cannot be trusted, and why this waits patiently rather than failing.
        if (!awaitUsablePath(attempt.transport, socks)) {
            container.tor.setNetworkEnabled(false)
            return false
        }

        val started = runCatching {
            startNativeTunnel(settings, socks, container.tor.dnsPort, network)
        }.onFailure { VeilLog.e("vpn", "could not raise the tunnel", it) }.isSuccess
        if (!started) return false

        update(TunnelState.Connected(attempt.transport, System.currentTimeMillis(), socks.port))
        startStatsPump()
        watchNetwork()
        watchScreen()
        return true
    }

    /**
     * Waits until the tunnel actually carries traffic, and says so on screen.
     *
     * This is the gate that replaced trusting tor's bootstrap percentage, and
     * it is worth being precise about why the percentage is not enough. Tor
     * reports 100% the moment it has ever built a circuit, and it keeps
     * reporting 100% after its network is switched off and back on — the
     * number never goes down. So on a warm reconnect the app was reading
     * "100% Done" from a process whose every connection to its bridge had been
     * closed, attaching the tunnel within a second, and showing a green screen
     * over a link that took another half minute to come back. Every
     * application on the phone found out before the app did.
     *
     * A stream to a host on the internet is the one signal that does not lie.
     * When it opens, the tunnel works; not one second before. It also leaves a
     * circuit warm, so the first application does not pay for building one.
     *
     * Patient rather than strict, and that is deliberate. The first stream over
     * a freshly found Snowflake proxy, and every stream after a warm resume
     * while a proxy is found again, can take tens of seconds; failing the
     * connection for that would repeat an earlier mistake, when a shorter gate
     * failed a working tunnel over and reported that nothing worked. Halfway
     * through, if nothing has opened, tor is asked for a fresh circuit once, in
     * case the one it built runs through a proxy that has since gone. It gives
     * up only after a budget long enough that a link which is coming up has
     * come up — and then honestly, as a failure, never as a green screen.
     */
    private suspend fun awaitUsablePath(transport: Transport, socks: SocksEndpoint): Boolean {
        update(TunnelState.Probing(R.string.step_checking_path, 2, 2))
        val budget = StrategyPlanner.verifyMillis(transport)
        val started = System.currentTimeMillis()
        val deadline = started + budget
        var kicked = false
        var tries = 0
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            tries += 1
            if (probePath(socks)) {
                VeilLog.i(
                    "vpn",
                    "timeline: a stream reached the internet on try $tries, " +
                        "${System.currentTimeMillis() - started}ms after bootstrap (+${sinceConnect()}ms)",
                )
                // Spares, now, before the first application asks. The one
                // circuit the stream just used is about to be shared by
                // everything on the phone; tor builds more on demand, but
                // "on demand" over Snowflake is a second or two that the first
                // few connections would otherwise spend waiting.
                runCatching { container.tor.ensureSpareCircuit() }
                return true
            }
            if (!kicked && System.currentTimeMillis() > started + budget / 2) {
                kicked = true
                VeilLog.w(
                    "vpn",
                    "no stream after ${(System.currentTimeMillis() - started) / 1000}s " +
                        "(link=${container.tor.hasLiveOrConnection()} " +
                        "circuit=${container.tor.hasBuiltCircuit()}); asking tor for a fresh circuit",
                )
                runCatching { container.tor.ensureSpareCircuit() }
            }
            delay(1_500)
        }
        VeilLog.w(
            "vpn",
            "no stream reached the internet within ${budget / 1000}s of bootstrap " +
                "(link=${container.tor.hasLiveOrConnection()} circuit=${container.tor.hasBuiltCircuit()})",
        )
        return false
    }

    /** Retries a stream through the proxy until it opens or the budget runs out. */
    private suspend fun awaitPath(socks: SocksEndpoint, budgetMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + budgetMillis
            while (System.currentTimeMillis() < deadline) {
                if (probePath(socks)) return@withContext true
                delay(1_500)
            }
            false
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
        // The extensions below are bounded. A route that keeps inching forward
        // could otherwise hold the connect for as long as it kept moving, and
        // an attempt nobody can leave is worse than one that ends.
        var ceiling = deadline + LATE_GRACE_CEILING_MILLIS
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
                ceiling = deadline + LATE_GRACE_CEILING_MILLIS
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
                // The timeline that says where a slow connect actually goes:
                // the bridge (up to ~15%), the directory (to ~80%), the circuit
                // (to 100%). Optimising without this is guessing.
                VeilLog.i(
                    "vpn",
                    "timeline: ${attempt.label} ${bootstrap.percent}% " +
                        "${bootstrap.summary} (+${sinceConnect()}ms)",
                )
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
            if (quiet > StrategyPlanner.stallMillis(lastPercent)) {
                VeilLog.w("vpn", "${attempt.label} stalled at $lastPercent%")
                return false
            }
            // A route that has got the link up has earned more than its
            // opening budget. Running out of time one step from a tunnel, on
            // the route that got furthest, is the most expensive way for this
            // to be wrong.
            if (lastPercent >= StrategyPlanner.LATE_BOOTSTRAP_PERCENT &&
                System.currentTimeMillis() > deadline - LATE_GRACE_MILLIS &&
                deadline < ceiling
            ) {
                deadline = minOf(System.currentTimeMillis() + LATE_GRACE_MILLIS, ceiling)
                VeilLog.d("vpn", "${attempt.label} is at $lastPercent%; extending its time")
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
            VeilLog.i("vpn", "timeline: tunnel attached via $socks, dns $dnsTarget (+${sinceConnect()}ms)")
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
        var lastLivenessCheck = System.currentTimeMillis()
        var lastEvidenceAt = System.currentTimeMillis()
        var lastRx = -1L
        var lastTx = -1L
        var probeFailures = 0
        var lastRedial = 0L
        statsJob = scope.launch {
            while (isActive) {
                runCatching { Veiltun.snapshot() }.getOrNull()?.let { snapshot ->
                    snapshotRx = snapshot.rxBytes
                    snapshotTx = snapshot.txBytes
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

                // Is the path still there? Answered by evidence that cannot be
                // misread, in this order: if bytes have moved through the tunnel
                // since the last look, it is alive and nothing more is asked. If
                // nothing has moved for a while — an idle phone moves nothing
                // either — a real stream is opened through the proxy, exactly
                // as an application would. Only when that fails twice running
                // is the path re-dialled.
                //
                // The version before this read tor's connection list over the
                // control port, decided "dead" on any answer it did not like,
                // and re-dialled a working tunnel every twelve seconds —
                // tearing down the Snowflake session and replacing the
                // interface each time. From the outside that was every
                // application on the phone losing the network and reconnecting,
                // over and over. A supervisor that can be wrong must not be
                // allowed to act on its own opinion; this one acts only on a
                // stream that would not open.
                val rx = snapshotRx
                val tx = snapshotTx
                if (rx != lastRx || tx != lastTx) {
                    lastRx = rx
                    lastTx = tx
                    lastEvidenceAt = now
                    probeFailures = 0
                }
                if (now - lastLivenessCheck > LIVENESS_CHECK_MILLIS &&
                    now - lastEvidenceAt > QUIET_BEFORE_PROBE_MILLIS &&
                    !redialing.get()
                ) {
                    lastLivenessCheck = now
                    val socks = container.tor.socks
                    val open = socks != null && probePath(socks)
                    if (open) {
                        lastEvidenceAt = now
                        probeFailures = 0
                    } else {
                        probeFailures += 1
                        VeilLog.w(
                            "vpn",
                            "no traffic for ${(now - lastEvidenceAt) / 1000}s and a stream would not open " +
                                "($probeFailures/$PROBE_FAILURES_BEFORE_REDIAL)",
                        )
                        if (probeFailures >= PROBE_FAILURES_BEFORE_REDIAL &&
                            now - lastRedial > REDIAL_COOLDOWN_MILLIS
                        ) {
                            lastRedial = now
                            probeFailures = 0
                            redial("no traffic for ${(now - lastEvidenceAt) / 1000}s and no stream would open")
                        }
                    }
                }
                delay(1_500)
            }
        }
    }

    private val redialing = AtomicBoolean(false)

    /** The tunnel's byte counters as of the last snapshot, for the supervisor. */
    @Volatile private var snapshotRx = 0L
    @Volatile private var snapshotTx = 0L

    /**
     * One real stream through the proxy: the only test of a path this file
     * trusts. Short, because it runs inside the stats pump; the budgeted,
     * retrying version used when connecting is [awaitPath].
     */
    private suspend fun probePath(socks: SocksEndpoint): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socks5.connect(
                SocksProxy("127.0.0.1", socks.port),
                USABLE_PROBE_HOST,
                USABLE_PROBE_PORT,
                connectTimeoutMillis = PROBE_TIMEOUT_MILLIS,
                readTimeoutMillis = PROBE_TIMEOUT_MILLIS,
            ).close()
        }.isSuccess
    }

    /**
     * Re-dials the bridges and, once the link is back, re-establishes the
     * interface so applications find out.
     *
     * The second half is the part that was missing, and it is why a tunnel
     * that had recovered still left Telegram saying "connecting". An
     * application that tried while the path was down has backed off — some for
     * a minute or more — and nothing tells it to try again. Replacing the VPN
     * interface does: the system reports a network change to every
     * application, and the ones with a connection to make make it now. This is
     * what a commercial client does on every reconnect; a tunnel that comes
     * back without saying so has not come back as far as the phone is
     * concerned.
     *
     * Replacement is atomic on the system side — establishing the new
     * interface retires the old one — so there is no moment without a VPN and
     * nothing leaks around it.
     */
    private fun redial(reason: String) {
        if (!redialing.compareAndSet(false, true)) return
        scope.launch {
            try {
                VeilLog.w("vpn", "re-dialling: $reason")
                if (!container.tor.reconnect()) return@launch
                val socks = container.tor.socks ?: return@launch
                // Wait for the link to come back before telling applications
                // the network changed, but do not give up on the tunnel if it
                // is slow: the interface is replaced either way, and a slow
                // path is still a path.
                awaitPath(socks, REDIAL_PATH_WAIT_MILLIS)
                val settings = container.settings.settings.first()
                val network = lastNetwork ?: return@launch
                runCatching {
                    stopNativeTunnelOnly()
                    startNativeTunnel(settings, socks, container.tor.dnsPort, network)
                }.onFailure { VeilLog.e("vpn", "could not re-attach after the re-dial", it) }
                    .onSuccess { VeilLog.i("vpn", "link is back; applications told") }
            } finally {
                redialing.set(false)
            }
        }
    }

    /**
     * The screen coming on is the moment the user is about to find out whether
     * the tunnel survived the pocket. Doze and carrier NATs both end idle
     * connections quietly, and the five-second liveness check would take up to
     * seventeen seconds to notice and act. Look now instead, so the recovery
     * is under way before the first application tries.
     */
    private var screenReceiver: android.content.BroadcastReceiver? = null

    private fun watchScreen() {
        if (screenReceiver != null) return
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                scope.launch {
                    // A stream, not an opinion. If it opens, the pocket did no
                    // harm and a spare circuit is asked for so the first
                    // application does not wait on one; if it does not, the
                    // re-dial starts now rather than after the pump notices.
                    val socks = container.tor.socks ?: return@launch
                    if (probePath(socks)) {
                        runCatching { container.tor.ensureSpareCircuit() }
                    } else if (!redialing.get()) {
                        redial("the screen came on and no stream would open")
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
        }.onFailure { VeilLog.w("vpn", "could not watch the screen: $it") }
    }

    private fun unwatchScreen() {
        val receiver = screenReceiver ?: return
        screenReceiver = null
        runCatching { unregisterReceiver(receiver) }
    }

    // --- The network underneath ---------------------------------------------

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var watchedNetwork: android.net.Network? = null

    /**
     * Re-dials the moment the phone changes network.
     *
     * The single most common way a mobile tunnel dies is not the censor: it is
     * the phone leaving Wi-Fi for cellular, or the carrier handing it a new
     * address, which kills every TCP connection it had. Tor finds out when its
     * keepalives time out, minutes later. Applications find out immediately
     * and stop working. A commercial VPN client is subscribed to exactly this
     * event and reconnects before the user notices — and so, now, is this.
     *
     * The interface is not touched. It stays up with the kill switch holding
     * it, so nothing leaks while the route is re-established underneath.
     */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        watchedNetwork = connectivity.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val previous = watchedNetwork
                watchedNetwork = network
                if (previous != null && previous != network) {
                    lastNetwork = runCatching { NetworkContext.inspect(this@VeilVpnService) }
                        .getOrNull() ?: lastNetwork
                    redial("the phone moved to a different network")
                }
            }

            override fun onLost(network: android.net.Network) {
                if (network == watchedNetwork) {
                    VeilLog.w("vpn", "the network went away; waiting for the next one")
                }
            }
        }
        runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }.onFailure { VeilLog.w("vpn", "could not watch the network: $it") }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
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

    /** Stops the native side only; the watchers and the pump keep running. */
    private fun stopNativeTunnelOnly() {
        if (nativeTunnelRunning) {
            runCatching { Veiltun.stop() }
            nativeTunnelRunning = false
        }
    }

    private suspend fun stopNativeTunnel() = withContext(Dispatchers.IO) {
        unwatchNetwork()
        unwatchScreen()
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
                // lateBridgeJob is intentionally not cancelled: it persists
                // bridges for next time and is bounded by its own timeout.
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
        // "Nothing worked" is the one thing a failure must not stop at. What
        // was and was not there — which transports started, how many bridges
        // each had, whether the bridge service answered, what the NAT does —
        // is known at this point and is exactly what decides the fix, so it
        // travels with the failure instead of waiting for someone to go and
        // look for it.
        val full = reason + "\n\n" + situation()
        VeilLog.e("vpn", full)
        update(TunnelState.Failed(full, tried))

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

    /** The facts of this connect, in a few short lines. */
    private fun situation(): String = buildString {
        val network = lastNetwork
        appendLine(
            getString(
                R.string.diag_line_network,
                network?.kind?.name?.lowercase() ?: "?",
                network?.countryIso ?: "??",
            ),
        )
        val started = Transport.entries.filter { it.isPluggable && it in startedPorts.keys }
        val missing = Transport.entries.filter { it.isPluggable && it !in startedPorts.keys }
        appendLine(
            getString(
                R.string.diag_line_transports,
                started.joinToString { it.torName }.ifEmpty { "—" },
                missing.joinToString { it.torName }.ifEmpty { "—" },
            ),
        )
        appendLine(
            getString(
                R.string.diag_line_bridges,
                Transport.entries.filter { it.isPluggable }.joinToString { t ->
                    "${t.torName}=${container.bridges.forTransport(t, 99).size}"
                },
            ),
        )
        val fresh = Transport.entries.any { it.isPluggable && container.bridges.hasRecommended(it) }
        appendLine(getString(if (fresh) R.string.diag_line_api_ok else R.string.diag_line_api_none))
    }.trim()

    /**
     * Lets go of the interface and puts the engine to sleep.
     *
     * Not stopped. See [VeilApp.parkEngine]: tor keeps the directory consensus
     * it just fetched, with its network off, so a reconnect within the next few
     * minutes is a control-port command rather than the whole bootstrap again.
     * The process is shut down for real by a timer there if nothing comes back.
     */
    private suspend fun releaseEverything() {
        container.parkEngine()
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

        /** How often, at most, a stream is opened to see whether the path is there. */
        private const val LIVENESS_CHECK_MILLIS = 10_000L

        /**
         * How long the tunnel may carry nothing before a stream is tried.
         * Traffic is evidence enough while it flows; a phone that is idle for
         * this long gets asked, once, at the cost of one small connection.
         */
        private const val QUIET_BEFORE_PROBE_MILLIS = 45_000L

        /**
         * Consecutive probe failures before the path is re-dialled. Two, ten
         * seconds apart: a single failure can be a slow circuit or a busy
         * proxy, and re-dialling on one throws away a session that would have
         * recovered on its own.
         */
        private const val PROBE_FAILURES_BEFORE_REDIAL = 3

        /**
         * One probe's budget. Generous on purpose: a circuit over Snowflake on
         * a mobile network can take this long to carry a first stream, and a
         * probe that gives up early reports a working tunnel as dead.
         */
        private const val PROBE_TIMEOUT_MILLIS = 20_000

        /** How long a re-dial waits for the link before replacing the interface. */
        private const val REDIAL_PATH_WAIT_MILLIS = 45_000L

        /**
         * And no more often than this, however bad it gets. A Snowflake
         * re-dial on a mobile network can itself take most of a minute, and a
         * second re-dial in the middle of the first only starts it over.
         */
        private const val REDIAL_COOLDOWN_MILLIS = 90_000L

        /** One try of the path check; the loop retries until the budget is spent. */
        private const val USABLE_PROBE_TIMEOUT_MILLIS = 10_000

        /** Somewhere on the internet that answers on 443 and is not a name. */
        private const val USABLE_PROBE_HOST = "1.1.1.1"
        private const val USABLE_PROBE_PORT = 443

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

        /**
         * Extra time granted to a route that has the link to its bridge up.
         *
         * Granted repeatedly while it stays there, so the stall timer is what
         * ends it rather than the clock: a route at 95% is either about to
         * finish or about to stop moving, and the second of those is already
         * detected.
         */
        private const val LATE_GRACE_MILLIS = 30_000L

        /** The most any one route may gain from those extensions in total. */
        private const val LATE_GRACE_CEILING_MILLIS = 60_000L
    }
}
