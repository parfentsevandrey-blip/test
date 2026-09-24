package app.opal.core.tunnel.session

import android.net.Network
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

    private val inspector = CircuitInspector(engine)
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
    private var newIdentityReadyAt = 0L
    private var crashes = 0

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
                catalog.custom(settings),
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
        transports.configureSnowflakeDefaults(
            catalog.builtin(memory)[TransportKind.Snowflake].firstOrNull()
        )
        val running =
            try {
                val ports = transports.ensure(configured.map { it.transport })
                val config =
                    TorConfigFactory.startup(
                        configured,
                        ports,
                        settings,
                        networkUp = network.status.value.isConnected,
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
            is TorEvent.Bandwidth -> {
                totalRead += event.read
                totalWritten += event.written
                listener.onTraffic(
                    TrafficSample(event.read, event.written, totalRead, totalWritten)
                )
                watchdog.onEvent(event)
                // Bytes arriving during bootstrap (e.g. a long consensus download) count as
                // progress.
                if (!ready && event.read >= PROGRESS_BYTES_PER_SECOND) lastProgressAt = now()
            }
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
            else -> Unit
        }
    }

    private fun onBootstrap(event: TorEvent.Bootstrap) {
        if (event.progress > lastProgress) {
            lastProgress = event.progress
            lastProgressAt = now()
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
            is TransportEvent.Error -> {
                log.d(TAG, "Transport ${event.transport}: ${event.message}")
                if (
                    !ready &&
                        event.transport == "snowflake" &&
                        configured.all { it.transport == TransportKind.Snowflake }
                ) {
                    listener.onProblem(TunnelProblem.SnowflakeUnavailable)
                }
            }
            is TransportEvent.Stopped ->
                event.message?.let { log.d(TAG, "Transport ${event.transport} stopped: $it") }
            is TransportEvent.Connected -> Unit
        }
    }

    private suspend fun onReady() {
        if (ready) return
        ready = true
        failedRounds = 0
        healthySince = now()
        watchdog.arm()
        listener.onProblem(null)
        listener.onReady()
        scope.launch { afterReady() }
    }

    private fun onNotReady(reason: ReconnectReason) {
        if (!ready) return
        ready = false
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
        refreshCircuit()
    }

    // --- race ---------------------------------------------------------------------------------

    private suspend fun raceLoop() {
        while (true) {
            delay(1_000)
            if (ready || stopping || engine.state.value !is EngineState.Running) continue
            if (!network.status.value.isConnected) {
                resetProgress()
                continue
            }
            val t = now()
            val quiet = t - lastProgressAt
            if (!expanded && quiet >= EXPAND_AFTER_MS) {
                expand()
                continue
            }
            if (quiet >= GIVE_UP_AFTER_QUIET_MS && t - attemptStartedAt >= MIN_ATTEMPT_MS) {
                val canAskApi = plan?.settingsApiAllowed == true && !settingsApiTried
                if (canAskApi) {
                    settingsApiTried = true
                    if (fetchSettingsApi(MoatClient.Route.DomainFronted, addToRace = true)) {
                        resetProgress()
                        continue
                    }
                }
                roundFailed()
            }
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
                catalog.custom(settings),
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
        while (true) {
            delay(2_000)
            if (!ready || stopping) continue
            val t = now()
            val stall = watchdog.evaluate()
            if (stall == null) {
                if (escalation > 0 && t - healthySince >= HEALTHY_RESET_MS) {
                    escalation = 0
                    listener.onProblem(null)
                }
                continue
            }
            if (t < nextEscalationAt) continue
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
                catalog.custom(settings),
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
        if (engine.state.value !is EngineState.Running) return
        when {
            status.network == null -> {
                log.i(TAG, "Network lost")
                setDisableNetwork(true)
                onNotReady(ReconnectReason.NetworkChanged)
            }
            previous == null -> {
                log.i(TAG, "Network available (${status.kind})")
                setDisableNetwork(false)
                resetProgress()
            }
            previous != status.network -> {
                log.i(TAG, "Network changed (${status.kind})")
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
                    catalog.custom(new),
                )
            plan = p
            if (p.initial.isNotEmpty()) {
                expanded = p.expansion.isEmpty()
                setBridges(p.initial)
                announceTransports()
                onNotReady(ReconnectReason.TransportSwitch)
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
            if (!geoIpLoaded && files.ensureGeoIp(versionCode)) {
                engine.reconfigure(
                    torrc { geoIp(files.geoIp.absolutePath, files.geoIp6.absolutePath) },
                    listOf(TorOption.GeoIpFile, TorOption.GeoIpv6File),
                )
                geoIpLoaded = true
            }
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

    private suspend fun setDisableNetwork(disabled: Boolean) {
        runCatching {
            engine.reconfigure(
                torrc { disableNetwork(disabled) },
                listOf(TorOption.DisableNetwork),
            )
        }
            .onFailure { log.w(TAG, "DisableNetwork=$disabled failed: ${it.message}") }
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
            refreshCircuit()
        }
    }

    private suspend fun refreshCircuit() {
        if (!ready) return
        val info = runCatching {
            inspector.inspect(configured, preferredCircuit, geoIpLoaded)
        }
            .getOrNull()
        listener.onCircuit(info)
    }

    private fun describe(lines: List<BridgeLine>): String =
        lines
            .groupingBy { it.transport }
            .eachCount()
            .entries
            .joinToString { "${it.key}×${it.value}" }

    private companion object {
        const val TAG = "session"
        const val EXPAND_AFTER_MS = 10_000L
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
