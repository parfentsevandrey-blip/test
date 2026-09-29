package app.opal.core.tunnel.session

import android.net.Network
import android.os.SystemClock
import app.opal.core.data.SettingsRepository
import app.opal.core.data.TunnelMemoryRepository
import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.moat.toBridgeSets
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.BridgeStat
import app.opal.core.model.settings.CircumventionCache
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tor.CircuitStatus
import app.opal.core.model.tor.LogSeverity
import app.opal.core.model.tor.OrConnStatus
import app.opal.core.model.tor.RelayRef
import app.opal.core.model.tor.StreamStatus
import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorEventParser
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.torrc
import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.BootstrapPhase
import app.opal.core.model.tunnel.CircuitInfo
import app.opal.core.model.tunnel.ReconnectReason
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelError
import app.opal.core.model.tunnel.TunnelProblem
import app.opal.core.tunnel.bridges.BridgeCatalog
import app.opal.core.tunnel.bridges.MoatClient
import app.opal.core.tunnel.net.NetworkMonitor
import app.opal.core.tunnel.pt.TransportEvent
import app.opal.core.tunnel.pt.Transports
import app.opal.core.tunnel.tor.EngineState
import app.opal.core.tunnel.tor.TorEngine
import app.opal.core.tunnel.tor.TorFiles
import app.opal.core.tunnel.tor.TorSignal
import app.opal.core.tunnel.util.LogBuffer
import app.opal.core.tunnel.util.LoopbackPorts
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One continuous run of Tor, from start to stop, including restarts after crashes or stalls.
 *
 * Owns: the transport race (Auto), Circumvention Settings API fallback, the watchdog escalation
 * ladder, reactions to network changes and live settings. All state is confined to a single-thread
 * dispatcher, so no locks are needed; blocking work always hops to IO.
 */
@OptIn(ExperimentalCoroutinesApi::class)
// Single owner of one Tor session's mutable state (race, watchdog, Settings API, runtime options),
// driven from one coroutine context; splitting it is tracked in CLAUDE.md (tech debt).
@Suppress("LargeClass")
internal class TorSession(
    private val engine: TorEngine,
    private val transports: Transports,
    private val catalog: BridgeCatalog,
    private val moat: MoatClient,
    private val settingsRepo: SettingsRepository,
    private val memoryRepo: TunnelMemoryRepository,
    private val files: TorFiles,
    private val network: NetworkMonitor,
    private val log: LogBuffer,
    private val listener: Listener,
    private val countryHint: () -> String?,
    private val versionCode: Long,
    private val now: () -> Long,
    parentScope: CoroutineScope,
    /** Time the device has spent suspended since boot: stops only while the CPU is asleep. */
    private val deepSleepMillis: () -> Long = {
        SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
    },
    /** The screen is on: someone is about to use the network. */
    private val interactive: () -> Boolean = { true },
) {
    interface Listener {
        fun onSocksPort(port: Int)

        fun onBootstrap(info: BootstrapInfo)

        fun onReady()

        fun onNotReady(reason: ReconnectReason)

        fun onTransport(active: TransportKind?, racing: List<TransportKind>)

        fun onAllFailed()

        fun onRetry()

        fun onFatal(error: TunnelError)

        fun onCircuit(info: CircuitInfo?)

        fun onTraffic(sample: TrafficSample)

        fun onProblem(problem: TunnelProblem?)

        fun onNewIdentity(readyAt: Long)

        /** Tor cannot be restarted in this process any more; the process must be restarted. */
        fun onStuck()
    }

    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext + job + dispatcher + CoroutineName("tor-session")
        )

    private val inspector = CircuitInspector(engine, log)

    /** Bridges Tor currently skips (GUARD DOWN without UP since), for the log only. */
    private val unreachableBridges = HashSet<String>()
    private val tracker = BridgeUsageTracker()
    private val watchdog = Watchdog(now)

    // --- state confined to the session dispatcher -------------------------------------------
    private var settings = AppSettings()
    private var plan: BridgePlan? = null
    private var configured: List<BridgeLine> = emptyList()
    private var expanded = false
    private var ready = false
    private var stopping = false
    private var lastProgress = -1
    private var lastProgressAt = 0L
    private var attemptStartedAt = 0L
    private var settingsApiTried = false
    private var failedRounds = 0
    private var geoIpLoaded = false
    private var appliedExitCountry: String? = null
    private var appliedDataSaver = false
    private var escalation = 0
    private var nextEscalationAt = 0L
    private var healthySince = 0L
    private var preferredCircuit: String? = null
    private var circuitRefresh: Job? = null
    private var lastNetwork: Network? = null
    private var totalRead = 0L
    private var totalWritten = 0L
    private var trafficSeq = 0L
    private var newIdentityReadyAt = 0L
    private var crashes = 0
    private var slowHintShown = false
    private val restricted = RestrictedNetwork()

    /**
     * Tor's network as last asked for (`DisableNetwork`), set before the request goes out: the
     * handlers below wait for Tor and interleave, so a check after the wait must see whether
     * another one has turned the network off (or on) meanwhile.
     */
    private var networkWanted = false

    /** When the network went away (0: it did not); session transports may outlive a short gap. */
    private var lostAt = 0L

    private val sleepWatch = SleepWatch()

    /** When Tor last became ready, and when traffic was last heavy (see loadGeoIpWhenQuiet). */
    private var readyAt = 0L
    private var lastBusyAt = 0L
    private var geoIpJob: Job? = null

    private val policy: ModePolicy
        get() = ModePolicy.of(settings.connectionMode, configured)

    val isReady: Boolean
        get() = ready

    fun start() {
        scope.launch {
            // Subscribe before Tor starts so no early bootstrap event is missed.
            launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect(::onEvent) }
            launch(start = CoroutineStart.UNDISPATCHED) {
                transports.events.collect(::onTransportEvent)
            }
            settings = settingsRepo.current()
            if (!startTor()) return@launch
            launch { raceLoop() }
            launch { watchdogLoop() }
            launch { network.status.collect(::onNetwork) }
            launch { engine.state.collect(::onEngineState) }
            launch { settingsRepo.settings.collect(::onSettings) }
            launch { settingsApiRefreshLoop() }
        }
    }

    suspend fun stop() {
        withContext(dispatcher) { stopping = true }
        job.cancel()
        withContext(NonCancellable) {
            engine.stop()
            transports.stopAll()
        }
    }

    /** `SIGNAL NEWNYM`; returns the time until which another request would be rate-limited. */
    suspend fun newIdentity(): Long =
        withContext(dispatcher) {
            val t = now()
            if (t >= newIdentityReadyAt && engine.state.value is EngineState.Running) {
                runCatching { engine.signal(TorSignal.NewIdentity) }
                    .onFailure { log.w(TAG, "NEWNYM failed: ${it.message}") }
                newIdentityReadyAt = t + NEWNYM_COOLDOWN_MS
                preferredCircuit = null
                listener.onNewIdentity(newIdentityReadyAt)
                scheduleCircuitRefresh(delayMs = 3_000)
            }
            newIdentityReadyAt
        }

    // --- start / restart ----------------------------------------------------------------------

    private suspend fun startTor(): Boolean {
        val memory = memoryRepo.current()
        val candidates = catalog.candidates(memory)
        val newPlan =
            RacePlanner.plan(
                settings,
                memory,
                network.status.value.kind,
                candidates,
                catalog.custom(settings, memory),
            )
        plan = newPlan
        var initial = newPlan.initial
        if (initial.isEmpty() && newPlan.settingsApiAllowed) {
            // e.g. WebTunnel chosen but no WebTunnel bridges known yet: ask the Settings API first.
            fetchSettingsApi(MoatClient.Route.DomainFronted, addToRace = false)
            initial =
                RacePlanner.plan(
                        settings,
                        memoryRepo.current(),
                        network.status.value.kind,
                        catalog.candidates(memoryRepo.current()),
                        emptyList(),
                    )
                    .initial
        }
        if (initial.isEmpty()) {
            log.w(TAG, "No bridges available for mode ${settings.connectionMode}")
            listener.onProblem(TunnelProblem.CannotReachBridges)
            listener.onAllFailed()
            return false
        }
        configured = initial
        expanded = newPlan.expansion.isEmpty()
        transports.configureSnowflake()
        val running =
            try {
                val ports = transports.ensure(configured.map { it.transport })
                networkWanted = network.status.value.isConnected
                val config =
                    TorConfigFactory.startup(
                        configured,
                        ports,
                        settings,
                        socksPort = LoopbackPorts.free(),
                        networkUp = networkWanted,
                    )
                engine.start(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsatisfiedLinkError) {
                log.e(TAG, "Native library missing: ${e.message}")
                listener.onFatal(TunnelError.NativeLibraryMissing)
                return false
            } catch (e: Exception) {
                log.e(TAG, "Tor failed to start: ${e.message}")
                if (e.message?.contains("still running") == true) listener.onStuck()
                else listener.onFatal(TunnelError.TorStartFailed)
                return false
            }
        appliedDataSaver = settings.dataSaver
        appliedExitCountry = null
        geoIpLoaded = false
        log.i(TAG, "Tor ${running.version} started; ${describe(configured)}")
        listener.onSocksPort(running.socksPort)
        announceTransports()
        resetProgress()
        if (memory.circumvention == null && newPlan.settingsApiAllowed && !settingsApiTried) {
            // First run: ask the Settings API in parallel with the race.
            settingsApiTried = true
            scope.launch { fetchSettingsApi(MoatClient.Route.DomainFronted, addToRace = true) }
        }
        return true
    }

    private suspend fun restartTor(reason: String) {
        log.w(TAG, "Restarting Tor: $reason")
        ready = false
        runCatching { engine.stop() }
        tracker.reset()
        unreachableBridges.clear()
        // A new Tor starts over: so do the hint and its clock.
        if (restricted.reset()) listener.onProblem(null)
        resetProgress()
        settings = settingsRepo.current()
        startTor()
    }

    // --- events -------------------------------------------------------------------------------

    private suspend fun onEvent(event: TorEvent) {
        when (event) {
            is TorEvent.Bootstrap -> onBootstrap(event)
            TorEvent.CircuitEstablished -> onReady()
            is TorEvent.CircuitNotEstablished -> {
                log.i(TAG, "Circuits not established: ${event.reason}")
                onNotReady(ReconnectReason.CircuitLost)
            }
            is TorEvent.Bandwidth -> onBandwidth(event)
            is TorEvent.OrConn -> {
                tracker.onOrConn(event)
                if (!ready && event.status == OrConnStatus.CONNECTED) lastProgressAt = now()
            }
            is TorEvent.Circuit -> {
                watchdog.onEvent(event)
                if (event.status == CircuitStatus.BUILT) {
                    if (!ready) lastProgressAt = now()
                    scheduleCircuitRefresh()
                }
                if (event.status == CircuitStatus.CLOSED && event.id == preferredCircuit) {
                    preferredCircuit = null
                    scheduleCircuitRefresh()
                }
            }
            is TorEvent.Stream -> {
                watchdog.onEvent(event)
                if (event.status == StreamStatus.SUCCEEDED && event.circuitId != preferredCircuit) {
                    preferredCircuit = event.circuitId
                    scheduleCircuitRefresh()
                }
            }
            is TorEvent.GeneralStatus ->
                if (event.action == "CLOCK_SKEW") listener.onProblem(TunnelProblem.ClockSkew)
            is TorEvent.Log ->
                when (event.severity) {
                    LogSeverity.ERR -> log.e("tor", event.message)
                    LogSeverity.WARN -> log.w("tor", event.message)
                    LogSeverity.NOTICE -> log.d("tor", event.message)
                }
            is TorEvent.NetworkLiveness ->
                log.d(TAG, "Tor network liveness: ${if (event.up) "up" else "down"}")
            is TorEvent.Guard -> onGuard(event)
            else -> Unit
        }
    }

    private fun onBandwidth(event: TorEvent.Bandwidth) {
        totalRead += event.read
        totalWritten += event.written
        listener.onTraffic(
            TrafficSample(event.read, event.written, totalRead, totalWritten, ++trafficSeq)
        )
        watchdog.onEvent(event)
        // Bytes arriving during bootstrap (e.g. a long consensus download) count as progress.
        if (!ready && event.read >= PROGRESS_BYTES_PER_SECOND) lastProgressAt = now()
        if (event.read + event.written >= BUSY_BYTES_PER_SECOND) lastBusyAt = now()
        // The bridge answers: the session survived the sleep after all.
        if (event.read > 0) sleepWatch.clear()
    }

    /**
     * For the diagnostics log. Tor blames a bridge when a circuit gets no answer from it (with
     * Snowflake: while the proxy changes) and skips it for new circuits until it is retried: after
     * 10 minutes, or right away once no bridge is left and an app needs a new circuit.
     */
    private fun onGuard(event: TorEvent.Guard) {
        val fingerprint = RelayRef.parse(event.name).fingerprint
        val bridge = configured.firstOrNull { it.fingerprint == fingerprint } ?: return
        // Tor repeats DOWN for every circuit that fails: log changes only.
        val change =
            when (event.status) {
                "DOWN" -> "marked unreachable by Tor".takeIf { unreachableBridges.add(fingerprint) }
                "UP" -> "reachable again".takeIf { unreachableBridges.remove(fingerprint) }
                else -> null
            } ?: return
        log.i(TAG, "Bridge (${bridge.transport}) $change")
    }

    private fun onBootstrap(event: TorEvent.Bootstrap) {
        if (event.progress > lastProgress) {
            lastProgress = event.progress
            lastProgressAt = now()
            if (slowHintShown) {
                slowHintShown = false
                listener.onProblem(null)
            }
        }
        if (!ready)
            listener.onBootstrap(BootstrapInfo(event.progress, BootstrapPhase.fromTag(event.tag)))
        if (event.isProblem) {
            log.w(
                TAG,
                "Bootstrap problem at ${event.tag}: ${event.warning} (${event.reason}, x${event.count})",
            )
            if (!ready && (event.count ?: 0) >= 3)
                listener.onProblem(TunnelProblem.CannotReachBridges)
        }
    }

    private fun onTransportEvent(event: TransportEvent) {
        when (event) {
            // Broker/rendezvous errors are routine while Snowflake looks for a proxy (it retries);
            // the user hears about it only if nothing progresses for a while (see waitTick).
            is TransportEvent.Error -> log.d(TAG, "Transport ${event.transport}: ${event.message}")
            is TransportEvent.Stopped ->
                event.message?.let { log.d(TAG, "Transport ${event.transport} stopped: $it") }
            is TransportEvent.Connected -> Unit
        }
    }

    private suspend fun onReady() {
        if (ready) return
        ready = true
        readyAt = now()
        failedRounds = 0
        healthySince = now()
        watchdog.arm()
        restricted.reset()
        listener.onProblem(null)
        listener.onReady()
        scope.launch { afterReady() }
    }

    private fun onNotReady(reason: ReconnectReason) {
        if (!ready) return
        ready = false
        sleepWatch.clear()
        resetProgress()
        listener.onNotReady(reason)
    }

    private suspend fun afterReady() {
        val guard = runCatching {
            engine
                .getInfo("circuit-status")["circuit-status"]
                .orEmpty()
                .lineSequence()
                .mapNotNull { TorEventParser.parse("CIRC", it) as? TorEvent.Circuit }
                .firstOrNull { it.status == CircuitStatus.BUILT && it.path.isNotEmpty() }
                ?.path
                ?.first()
                ?.fingerprint
        }
            .getOrNull()
        val winner = tracker.bridgeFor(guard, configured)
        val networkKind = network.status.value.kind
        if (winner != null) {
            val kind = winner.transport
            if (configured.any { it.transport != kind }) {
                // The race is decided: keep only the winner's bridges.
                val losers = configured.map { it.transport }.toSet() - kind
                setBridges(configured.filter { it.transport == kind })
                transports.stop(losers)
                log.i(TAG, "Race won by $kind")
            }
            expanded = true
            listener.onTransport(kind, emptyList())
        } else {
            announceTransports()
        }
        val failed = tracker.failedBridges(configured)
        memoryRepo.update { memory ->
            var stats = memory.bridgeStats
            if (winner != null) stats = stats.bump(winner.id, success = true, now())
            for (bridge in failed) if (bridge != winner)
                stats = stats.bump(bridge.id, success = false, now())
            memory.copy(
                winners =
                    if (
                        winner != null &&
                            networkKind != null &&
                            settings.connectionMode == ConnectionMode.Auto
                    )
                        memory.winners + (networkKind to winner.transport)
                    else memory.winners,
                lastWorkingBridge =
                    if (winner != null && networkKind != null)
                        memory.lastWorkingBridge + (networkKind to winner.id)
                    else memory.lastWorkingBridge,
                bridgeStats = stats.pruned(),
                lastBootstrapAt = now(),
            )
        }
        applyRuntimeOptions()
        scheduleCircuitRefresh(delayMs = 0)
    }

    // --- race ---------------------------------------------------------------------------------

    private suspend fun raceLoop() {
        while (true) {
            delay(1_000)
            raceTick()
        }
    }

    /**
     * One step of the race supervisor: widen the race, ask the Settings API, or give up a round.
     */
    private suspend fun raceTick() {
        if (ready || stopping || engine.state.value !is EngineState.Running) return
        if (!network.status.value.isConnected) {
            resetProgress()
            return
        }
        val t = now()
        restrictedTick(t)
        val quiet = t - lastProgressAt
        if (!policy.race) {
            waitTick(quiet)
            return
        }
        if (!expanded && quiet >= EXPAND_AFTER_MS) {
            expand()
            return
        }
        if (quiet < GIVE_UP_AFTER_QUIET_MS || t - attemptStartedAt < MIN_ATTEMPT_MS) return
        val canAskApi = plan?.settingsApiAllowed == true && !settingsApiTried
        if (canAskApi) {
            settingsApiTried = true
            if (fetchSettingsApi(MoatClient.Route.DomainFronted, addToRace = true)) {
                resetProgress()
                return
            }
        }
        roundFailed()
    }

    /**
     * Tells the user when the network does not reach the internet (see [RestrictedNetwork]). Only a
     * hint: races, retries and the transports go on exactly as without it.
     */
    private fun restrictedTick(t: Long) {
        val status = network.status.value
        when (restricted.tick(t, status.validated, lastProgressAt)) {
            RestrictedNetwork.Change.Show -> {
                log.i(
                    TAG,
                    "No progress for ${(t - lastProgressAt) / 1000}s and Android does not " +
                        "validate the ${status.kind} network: restricted",
                )
                listener.onProblem(TunnelProblem.NetworkRestricted)
            }
            RestrictedNetwork.Change.Hide -> {
                log.i(TAG, "Progress again or the network validated: hint cleared")
                listener.onProblem(null)
            }
            null -> Unit
        }
    }

    /**
     * Single-transport modes (Snowflake by default): Tor and the transport keep retrying on their
     * own, so nothing is torn down here — no DisableNetwork toggles, no bridge changes, no
     * "blocked" verdict. The user only hears when it takes long; where the mode allows it, fresh
     * bridges of this transport are fetched once.
     */
    private suspend fun waitTick(quiet: Long) {
        if (quiet >= SLOW_HINT_MS && !slowHintShown && !restricted.shown) {
            slowHintShown = true
            listener.onProblem(
                if (settings.connectionMode == ConnectionMode.Snowflake)
                    TunnelProblem.SnowflakeUnavailable
                else TunnelProblem.CannotReachBridges
            )
        }
        if (
            quiet >= GIVE_UP_AFTER_QUIET_MS && plan?.settingsApiAllowed == true && !settingsApiTried
        ) {
            settingsApiTried = true
            if (fetchSettingsApi(MoatClient.Route.DomainFronted, addToRace = true)) resetProgress()
        }
    }

    private suspend fun expand() {
        val p = plan ?: return
        expanded = true
        val lines = (configured + p.expansion).distinctBy { it.raw }
        if (lines.size == configured.size) return
        log.i(TAG, "No progress for ${EXPAND_AFTER_MS / 1000}s: racing ${describe(lines)}")
        setBridges(lines)
        announceTransports()
        resetProgress()
    }

    private suspend fun roundFailed() {
        failedRounds++
        log.w(TAG, "Every bridge failed (round $failedRounds)")
        listener.onProblem(TunnelProblem.CannotReachBridges)
        listener.onAllFailed()
        memoryRepo.update { memory ->
            var stats = memory.bridgeStats
            for (bridge in configured) stats = stats.bump(bridge.id, success = false, now())
            memory.copy(bridgeStats = stats.pruned())
        }
        delay(min(RETRY_BASE_MS shl (failedRounds - 1).coerceAtMost(4), RETRY_MAX_MS))
        if (ready) return
        // Try everything we know again (the Settings API may have been refreshed meanwhile).
        val memory = memoryRepo.current()
        val p =
            RacePlanner.plan(
                settings,
                memory,
                network.status.value.kind,
                catalog.candidates(memory),
                catalog.custom(settings, memory),
            )
        plan = p
        expanded = true
        settingsApiTried = false
        setBridges(p.all.ifEmpty { configured })
        toggleNetwork()
        announceTransports()
        listener.onRetry()
        resetProgress()
    }

    private suspend fun setBridges(lines: List<BridgeLine>) {
        try {
            val ports = transports.ensure(lines.map { it.transport })
            engine.reconfigure(
                TorConfigFactory.bridges(lines, ports),
                TorConfigFactory.BRIDGE_OPTIONS,
            )
            configured = lines
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(TAG, "Cannot apply bridges: ${e.message}")
        }
    }

    private fun announceTransports() {
        val kinds = configured.map { it.transport }.distinct()
        if (kinds.size == 1) listener.onTransport(kinds.single(), emptyList())
        else listener.onTransport(null, kinds)
    }

    private fun resetProgress() {
        lastProgressAt = now()
        attemptStartedAt = lastProgressAt
    }

    // --- Settings API -------------------------------------------------------------------------

    /**
     * Fetches settings (and the current built-ins) for this location and caches them. When
     * [addToRace], new bridges suitable for the current mode are added to Tor right away. Returns
     * true if bridges were added.
     */
    private suspend fun fetchSettingsApi(route: MoatClient.Route, addToRace: Boolean): Boolean {
        val country =
            if (route is MoatClient.Route.ViaTor)
                memoryRepo.current().detectedCountry ?: countryHint()
            else null
        val result = runCatching {
            val response = moat.settings(route, country)
            val sets =
                if (response.errors.isNullOrEmpty()) response.toBridgeSets()
                else moat.defaults(route).toBridgeSets()
            val builtin = runCatching { moat.builtin(route) }.getOrDefault(emptyMap())
            Triple(response.country, sets, builtin)
        }
        val (detected, sets, builtin) =
            result.getOrElse { e ->
                if (e is CancellationException) throw e
                log.w(TAG, "Settings API unreachable (${route.javaClass.simpleName}): ${e.message}")
                memoryRepo.update { m ->
                    m.copy(
                        circumvention =
                            (m.circumvention ?: CircumventionCache(fetchedAt = 0)).copy(
                                lastFailureAt = now()
                            )
                    )
                }
                if (!ready) listener.onProblem(TunnelProblem.SettingsApiUnreachable)
                return false
            }
        log.i(TAG, "Settings API: ${sets.size} bridge sets")
        memoryRepo.update { m ->
            m.copy(
                circumvention =
                    CircumventionCache(
                        fetchedAt = now(),
                        country = detected,
                        settings = sets,
                        builtin = builtin,
                    ),
                detectedCountry = detected ?: m.detectedCountry,
            )
        }
        if (!addToRace || ready) return false
        val memory = memoryRepo.current()
        val mode = settings.connectionMode
        val fresh =
            catalog.candidates(memory).values.flatten().filter { line ->
                (mode == ConnectionMode.Auto || line.transport == mode.transport) &&
                    configured.none { it.raw == line.raw }
            }
        if (fresh.isEmpty()) return false
        expanded = true
        setBridges(configured + fresh)
        announceTransports()
        return true
    }

    private suspend fun settingsApiRefreshLoop() {
        while (true) {
            if (ready && !stopping) {
                val cache = memoryRepo.current().circumvention
                if (cache == null || now() - cache.fetchedAt > SETTINGS_API_MAX_AGE_MS) {
                    val port = (engine.state.value as? EngineState.Running)?.socksPort
                    if (port != null)
                        fetchSettingsApi(MoatClient.Route.ViaTor(port), addToRace = false)
                }
            }
            delay(SETTINGS_API_CHECK_MS)
        }
    }

    // --- watchdog -----------------------------------------------------------------------------

    private suspend fun watchdogLoop() {
        var asleep = deepSleepMillis()
        while (true) {
            delay(WATCHDOG_TICK_MS)
            val total = deepSleepMillis()
            noteSleep(total - asleep)
            asleep = total
            // Silence without a network says nothing about the session (see onNetwork).
            if (!network.status.value.isConnected) continue
            val awake = watchdog.onTick(WATCHDOG_TICK_MS)
            if (!ready || stopping) continue
            if (sleepWatch.renewNow(interactive)) renewAfterSleep() else if (awake) watchdogTick()
        }
    }

    /** See [SleepWatch]: the phone slept [sleptMillis] since the last tick. */
    private fun noteSleep(sleptMillis: Long) {
        val watching = ready && configured.isSessionOnly()
        if (sleepWatch.onTick(sleptMillis, configured.sessionExpiryMillis(), watching)) {
            log.i(
                TAG,
                "Asleep for ${sleptMillis / 1000}s: the ${describe(configured)} session expired",
            )
        }
    }

    /** The screen came on after such a sleep: a new session now, before apps ask for data. */
    private suspend fun renewAfterSleep() {
        log.i(TAG, "Screen on after a long sleep: new ${describe(configured)} session")
        onNotReady(ReconnectReason.Stalled)
        toggleNetwork()
        resetProgress()
        watchdog.arm()
        nextEscalationAt = now() + SESSION_RENEW_INTERVAL_MS
    }

    private suspend fun watchdogTick() {
        val t = now()
        val stall = watchdog.evaluate()
        if (stall == null) {
            if (escalation > 0 && t - healthySince >= HEALTHY_RESET_MS) {
                escalation = 0
                listener.onProblem(null)
            }
            return
        }
        if (t < nextEscalationAt) return
        if (!policy.watchdogEscalates) {
            renewDeadSession(t)
            return
        }
        escalation++
        nextEscalationAt =
            t + min(ESCALATION_BASE_MS shl (escalation - 1).coerceAtMost(4), ESCALATION_MAX_MS)
        log.w(TAG, "Watchdog: $stall, escalation level $escalation")
        listener.onProblem(TunnelProblem.ConnectionFrozen)
        when (escalation) {
            1 -> {
                // New circuits and fresh connections to the bridge (a frozen TCP session dies).
                onNotReady(ReconnectReason.Stalled)
                runCatching { engine.signal(TorSignal.NewIdentity) }
                toggleNetwork()
            }
            2 -> {
                onNotReady(ReconnectReason.TransportSwitch)
                switchBridge()
            }
            else -> {
                onNotReady(ReconnectReason.Restart)
                restartTor("watchdog escalation $escalation")
            }
        }
        watchdog.arm()
        healthySince = now()
    }

    /**
     * Snowflake replaces a silent proxy by itself (about 20 s to notice, up to ~45 s until the next
     * one carries data), and the circuits work again right after: a stall alone is left alone.
     * NEWNYM would only retire those circuits, a teardown restart the proxy search.
     *
     * A minute without a single byte while Tor has work waiting outlasts any swap: the session
     * behind the proxies is gone. The Snowflake server forgets a client after 4 minutes without it
     * (a phone asleep or out of coverage), while the client keeps writing into that session for
     * minutes more. Closing Tor's connections makes the client start a new session.
     */
    private suspend fun renewDeadSession(t: Long) {
        val stall = watchdog.deadSession() ?: return
        nextEscalationAt = t + SESSION_RENEW_INTERVAL_MS
        log.w(
            TAG,
            "Watchdog: $stall, nothing read for ${watchdog.silentMillis() / 1000}s: " +
                "new ${describe(configured)} session",
        )
        listener.onProblem(TunnelProblem.ConnectionFrozen)
        onNotReady(ReconnectReason.Stalled)
        toggleNetwork()
        watchdog.arm()
    }

    /** Stops trusting the current bridge and races all other candidates. */
    private suspend fun switchBridge() {
        val memory = memoryRepo.current()
        val current = network.status.value.kind?.let { memory.lastWorkingBridge[it] }
        if (current != null) {
            memoryRepo.update { m ->
                m.copy(bridgeStats = m.bridgeStats.bump(current, success = false, now()).pruned())
            }
        }
        val p =
            RacePlanner.plan(
                settings,
                memoryRepo.current(),
                network.status.value.kind,
                catalog.candidates(memoryRepo.current()),
                catalog.custom(settings, memoryRepo.current()),
            )
        val lines = p.all.filter { it.id != current }.ifEmpty { p.all }
        plan = p
        expanded = true
        setBridges(lines)
        announceTransports()
        toggleNetwork()
        resetProgress()
    }

    // --- network, engine, settings ------------------------------------------------------------

    private suspend fun onNetwork(status: NetworkMonitor.Status) {
        val previous = lastNetwork
        lastNetwork = status.network
        if (engine.state.value !is EngineState.Running || status.network == previous) return
        // A network that appears, goes or changes starts the clock over — before anything below
        // waits for Tor: the race tick runs meanwhile and would judge the new network by the old
        // one's silence (seen in the emulator: "restricted" a second after a switch to Wi-Fi).
        resetProgress()
        if (restricted.reset()) listener.onProblem(null)
        if (configured.isSessionOnly()) onSessionNetwork(status, previous)
        else onTcpNetwork(status, previous)
    }

    /**
     * Snowflake and dnstt carry Tor inside a session of their own that outlives a change of
     * network: the transport finds a new path by itself (Snowflake a new proxy, dnstt its resolver
     * over the new network) and Tor's circuits stay. Closing Tor's connections here — 1.0.5 did it
     * on every switch and every gap — threw that session away, and on mobile networks connections
     * got worse. Only a gap longer than the servers keep a silent session calls for a new one; the
     * watchdog still renews a session that died anyway.
     */
    private suspend fun onSessionNetwork(status: NetworkMonitor.Status, previous: Network?) {
        val network = status.network
        if (network == null) {
            log.i(TAG, "Network lost: the ${describe(configured)} session waits for it")
            lostAt = now()
            return
        }
        val gap = if (lostAt != 0L) now() - lostAt else 0L
        lostAt = 0L
        when {
            // Tor was started without a network (DisableNetwork 1).
            !networkWanted -> {
                log.i(TAG, "Network available (${status.kind})")
                setDisableNetwork(false)
            }
            ready && gap >= configured.sessionExpiryMillis() -> {
                log.i(
                    TAG,
                    "Network back (${status.kind}) after ${gap / 1000}s: " +
                        "new ${describe(configured)} session",
                )
                onNotReady(ReconnectReason.NetworkChanged)
                toggleNetwork()
                resetProgress()
            }
            else -> {
                val what =
                    when {
                        gap > 0 -> "back after ${gap / 1000}s"
                        previous == null -> "available"
                        else -> "changed"
                    }
                log.i(TAG, "Network $what (${status.kind}): the session carries on")
                // A fresh minute for the transport to find its new path before any verdict.
                watchdog.arm()
            }
        }
    }

    /** obfs4, WebTunnel, meek: their TCP connections to the bridge die with the old network. */
    private suspend fun onTcpNetwork(status: NetworkMonitor.Status, previous: Network?) {
        when {
            status.network == null -> {
                log.i(TAG, "Network lost")
                onNotReady(ReconnectReason.NetworkChanged)
                setDisableNetwork(true)
            }
            previous == null -> {
                log.i(TAG, "Network available (${status.kind})")
                setDisableNetwork(false)
                resetProgress()
            }
            else -> {
                log.i(TAG, "Network changed (${status.kind}, $previous → ${status.network})")
                // Soft reconnect: close stale connections on the old network, keep Tor running.
                onNotReady(ReconnectReason.NetworkChanged)
                toggleNetwork()
                resetProgress()
            }
        }
    }

    private suspend fun onEngineState(state: EngineState) {
        if (state !is EngineState.Failed || stopping) return
        crashes++
        ready = false
        listener.onNotReady(ReconnectReason.Restart)
        val backoff = min(1_000L shl (crashes - 1).coerceAtMost(5), 30_000L)
        log.e(TAG, "Tor stopped unexpectedly (${state.reason}); restarting in ${backoff}ms")
        delay(backoff)
        if (!stopping) restartTor("crash #$crashes")
    }

    private suspend fun onSettings(new: AppSettings) {
        val old = settings
        settings = new
        if (engine.state.value !is EngineState.Running) return
        if (old.connectionMode != new.connectionMode || old.customBridges != new.customBridges) {
            log.i(TAG, "Connection mode changed to ${new.connectionMode}")
            val memory = memoryRepo.current()
            val p =
                RacePlanner.plan(
                    new,
                    memory,
                    network.status.value.kind,
                    catalog.candidates(memory),
                    catalog.custom(new, memory),
                )
            plan = p
            if (p.initial.isNotEmpty()) {
                expanded = p.expansion.isEmpty()
                setBridges(p.initial)
                announceTransports()
                onNotReady(ReconnectReason.TransportSwitch)
                // New bridges (a dnstt line added on a restricted network, say) get a fresh start;
                // the toggle below leaves Tor's network on.
                if (restricted.reset()) listener.onProblem(null)
                resetProgress()
                toggleNetwork()
                resetProgress()
            }
        }
        if (ready && (old.exitCountry != new.exitCountry || old.dataSaver != new.dataSaver))
            applyRuntimeOptions()
    }

    /**
     * GeoIP (needed for countries and ExitNodes), exit country and data saver — after bootstrap.
     */
    private suspend fun applyRuntimeOptions() {
        try {
            // Tor parses GeoIP in its main loop and serves nothing meanwhile (seconds on a phone,
            // minutes in the emulator) — right after connecting, that is when apps load their
            // first pages. Only an exit country needs it at once; the countries on the circuit
            // panel wait for a quiet moment.
            if (settings.exitCountry != null) loadGeoIp() else loadGeoIpWhenQuiet()
            val country = settings.exitCountry?.takeIf { geoIpLoaded }
            if (country != appliedExitCountry) {
                engine.reconfigure(
                    torrc { exitCountry(country) },
                    listOf(TorOption.ExitNodes, TorOption.StrictNodes),
                )
                appliedExitCountry = country
                // New circuits for new streams so the choice takes effect now.
                engine.signal(TorSignal.NewIdentity)
                scheduleCircuitRefresh(delayMs = 3_000)
            }
            if (settings.dataSaver != appliedDataSaver) {
                engine.reconfigure(
                    torrc { reducedConnectionPadding(settings.dataSaver) },
                    listOf(TorOption.ReducedConnectionPadding),
                )
                appliedDataSaver = settings.dataSaver
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(TAG, "Cannot apply runtime options: ${e.message}")
        }
    }

    private suspend fun loadGeoIp() {
        if (geoIpLoaded || !files.ensureGeoIp(versionCode)) return
        engine.reconfigure(
            torrc { geoIp(files.geoIp.absolutePath, files.geoIp6.absolutePath) },
            listOf(TorOption.GeoIpFile, TorOption.GeoIpv6File),
        )
        geoIpLoaded = true
    }

    /**
     * GeoIP once traffic has been light for a few seconds after connecting (at the latest a few
     * minutes in), then the circuit panel gets its countries.
     */
    private fun loadGeoIpWhenQuiet() {
        if (geoIpLoaded || geoIpJob?.isActive == true) return
        geoIpJob = scope.launch {
            while (true) {
                delay(1_000)
                if (!ready) continue
                val t = now()
                val settled = t - readyAt >= GEOIP_AFTER_READY_MS && t - lastBusyAt >= QUIET_MS
                if (settled || t - readyAt >= GEOIP_AT_LATEST_MS) break
            }
            runCatching { loadGeoIp() }
                .onFailure {
                    if (it is CancellationException) throw it
                    log.w(TAG, "Cannot load GeoIP: ${it.message}")
                }
            if (geoIpLoaded) scheduleCircuitRefresh(delayMs = 0)
        }
    }

    private suspend fun setDisableNetwork(disabled: Boolean) {
        networkWanted = !disabled
        runCatching {
            engine.reconfigure(
                torrc { disableNetwork(disabled) },
                listOf(TorOption.DisableNetwork),
            )
        }
            .onFailure { log.w(TAG, "DisableNetwork=$disabled failed: ${it.message}") }
        // Not if the network was turned off again while this request waited (lost network, pause).
        if (networkWanted) ensureSocksListener()
    }

    /**
     * `DisableNetwork 1` closes the SOCKS listener; `0` reopens it on the same fixed port, which
     * hev keeps using. If Tor could not bind it again (the port was taken meanwhile), restart Tor
     * on a new port: the listener's `onSocksPort` then moves hev over.
     */
    private suspend fun ensureSocksListener() {
        val expected = (engine.state.value as? EngineState.Running)?.socksPort ?: return
        val listeners =
            runCatching { engine.getInfo(SOCKS_LISTENERS)[SOCKS_LISTENERS] }.getOrNull() ?: return
        if (Regex("127\\.0\\.0\\.1:$expected\\b").containsMatchIn(listeners)) return
        // Closed again on purpose while the question was pending: nothing to repair.
        if (!networkWanted) return
        restartTor("SOCKS listener did not reopen on port $expected")
    }

    private suspend fun toggleNetwork() {
        setDisableNetwork(true)
        setDisableNetwork(false)
    }

    // --- circuit info -------------------------------------------------------------------------

    private fun scheduleCircuitRefresh(delayMs: Long = 1_500) {
        if (!ready) return
        circuitRefresh?.cancel()
        circuitRefresh = scope.launch {
            delay(delayMs)
            // Nothing else asks again while streams keep using the same circuit.
            while (!refreshCircuit()) delay(CIRCUIT_RETRY_MS)
        }
    }

    /** Returns false when Tor did not answer (worth asking again). */
    private suspend fun refreshCircuit(): Boolean {
        if (!ready) return true
        // A cancelled refresh (a newer one replaces it) must not publish anything, and a failed
        // query says nothing about the circuit: the panel keeps what it shows.
        val info = runCatching {
            inspector.inspect(configured, preferredCircuit, geoIpLoaded)
        }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                log.d(TAG, "Circuit info unavailable: ${e.message}")
                return e !is IOException
            }
        listener.onCircuit(info)
        return true
    }

    private fun describe(lines: List<BridgeLine>): String =
        lines
            .groupingBy { it.transport }
            .eachCount()
            .entries
            .joinToString { "${it.key}×${it.value}" }

    private companion object {
        const val SOCKS_LISTENERS = "net/listeners/socks"
        const val TAG = "session"
        const val EXPAND_AFTER_MS = 10_000L
        /** First bootstrap through Snowflake often takes 1–2 minutes: hint only after that. */
        const val SLOW_HINT_MS = 120_000L
        const val SESSION_RENEW_INTERVAL_MS = 3 * 60_000L
        /** GeoIP: not before this long after connecting, and only after this long of quiet… */
        const val GEOIP_AFTER_READY_MS = 15_000L
        const val QUIET_MS = 5_000L
        /** …but at the latest this long after connecting. */
        const val GEOIP_AT_LATEST_MS = 3 * 60_000L
        /** Traffic above this (read + written per second) is not "quiet". */
        const val BUSY_BYTES_PER_SECOND = 32 * 1024L
        const val WATCHDOG_TICK_MS = 2_000L
        const val CIRCUIT_RETRY_MS = 10_000L
        const val GIVE_UP_AFTER_QUIET_MS = 60_000L
        const val MIN_ATTEMPT_MS = 90_000L
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 10 * 60_000L
        const val PROGRESS_BYTES_PER_SECOND = 1_024L
        const val ESCALATION_BASE_MS = 30_000L
        const val ESCALATION_MAX_MS = 5 * 60_000L
        const val HEALTHY_RESET_MS = 3 * 60_000L
        const val NEWNYM_COOLDOWN_MS = 10_000L
        const val SETTINGS_API_MAX_AGE_MS = 24 * 60 * 60_000L
        const val SETTINGS_API_CHECK_MS = 60 * 60_000L
    }
}

private fun Map<String, BridgeStat>.bump(
    id: String,
    success: Boolean,
    at: Long,
): Map<String, BridgeStat> {
    val stat = this[id] ?: BridgeStat()
    val updated =
        if (success) stat.copy(successes = stat.successes + 1, lastSuccessAt = at)
        else stat.copy(failures = stat.failures + 1, lastFailureAt = at)
    return this + (id to updated)
}

/** Keeps the statistics small: the 200 most recently used bridges. */
private fun Map<String, BridgeStat>.pruned(): Map<String, BridgeStat> =
    if (size <= 200) this
    else
        entries
            .sortedByDescending { maxOf(it.value.lastSuccessAt ?: 0, it.value.lastFailureAt ?: 0) }
            .take(200)
            .associate { it.toPair() }
