package app.opal.core.tunnel

import android.net.Network
import android.os.ParcelFileDescriptor
import app.opal.core.data.SettingsRepository
import app.opal.core.data.TunnelMemoryRepository
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.SplitTunnelSettings
import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.CircuitInfo
import app.opal.core.model.tunnel.ConnectionEvent
import app.opal.core.model.tunnel.ConnectionMachine
import app.opal.core.model.tunnel.MachineState
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.model.tunnel.ReconnectReason
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelError
import app.opal.core.model.tunnel.TunnelProblem
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.model.tunnel.WarmState
import app.opal.core.model.tunnel.isTunnelActive
import app.opal.core.tunnel.bridges.BridgeCatalog
import app.opal.core.tunnel.bridges.MoatClient
import app.opal.core.tunnel.hev.HevTunnel
import app.opal.core.tunnel.net.NetworkMonitor
import app.opal.core.tunnel.pt.Transports
import app.opal.core.tunnel.session.TorSession
import app.opal.core.tunnel.tor.TorEngine
import app.opal.core.tunnel.tor.TorFiles
import app.opal.core.tunnel.util.LogBuffer
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the VPN service does for the controller (the controller never touches Android services). */
internal interface VpnHost {
    /** Creates the VPN interface; null if not permitted or failed. */
    fun establish(spec: VpnSpec): ParcelFileDescriptor?

    fun setUnderlyingNetwork(network: Network?)

    /** Reasons to keep Tor running changed; the service adjusts its foreground state. */
    fun onHoldsChanged(holds: Set<TunnelController.Hold>)

    fun restartProcess()
}

internal data class VpnSpec(val splitTunnel: SplitTunnelSettings, val underlying: Network?)

/**
 * Process-wide orchestrator in `:tunnel`. Decides *whether* Tor and the VPN interface should run
 * (holds), keeps the user-visible state ([ConnectionMachine] + details) and wires the session's
 * callbacks to the VPN. Everything Tor-internal lives in [TorSession].
 */
internal class TunnelController(
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
    private val memoryRepo: TunnelMemoryRepository,
    private val engine: TorEngine,
    private val transports: Transports,
    private val hev: HevTunnel,
    private val catalog: BridgeCatalog,
    private val moat: MoatClient,
    private val files: TorFiles,
    private val network: NetworkMonitor,
    val log: LogBuffer,
    private val countryHint: () -> String?,
    private val versionCode: Long,
    private val debuggable: Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Reasons for Tor to run. Tor stops when the set becomes empty. */
    enum class Hold {
        /** The VPN is (being) connected. */
        Vpn,
        /** Hot standby: Tor stays up while the VPN is off. */
        Standby,
        /** App opened: bootstrap ahead of the tap on "Connect". */
        Prewarm,
        /** Background directory refresh (warm cache). */
        Refresh,
    }

    private data class Details(
        val bootstrap: BootstrapInfo? = null,
        val transport: TransportKind? = null,
        val racing: List<TransportKind> = emptyList(),
        val circuit: CircuitInfo? = null,
        val connectedSince: Long? = null,
        val newIdentityReadyAt: Long? = null,
        val torRunning: Boolean = false,
        val network: NetworkKind? = null,
        val problem: TunnelProblem? = null,
        val exitCountry: String? = null,
    )

    private val machine = MutableStateFlow(MachineState())
    private val details = MutableStateFlow(Details())
    private val _traffic = MutableStateFlow<TrafficSample?>(null)
    val traffic: StateFlow<TrafficSample?> = _traffic.asStateFlow()

    val snapshot: StateFlow<TunnelSnapshot> =
        combine(machine, details) { m, d ->
                TunnelSnapshot(
                    state = m.state,
                    bootstrap =
                        d.bootstrap.takeUnless { m.torReady && m.state == TunnelState.Connected },
                    transport = d.transport,
                    racing = d.racing,
                    circuit = d.circuit.takeIf { m.torReady },
                    connectedSince = d.connectedSince.takeIf { m.state == TunnelState.Connected },
                    newIdentityReadyAt = d.newIdentityReadyAt,
                    warm =
                        when {
                            m.torReady -> WarmState.Ready
                            d.torRunning -> WarmState.Warming
                            else -> WarmState.Cold
                        },
                    network = d.network,
                    problem = d.problem,
                    exitCountry = d.exitCountry,
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, TunnelSnapshot())

    private val _holds = MutableStateFlow<Set<Hold>>(emptySet())
    val holds: StateFlow<Set<Hold>> = _holds.asStateFlow()

    @Volatile var host: VpnHost? = null

    private val ops = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var tunSpec: VpnSpec? = null
    private var session: TorSession? = null
    private var socksPort: Int? = null
    private var prewarmTimeout: Job? = null

    init {
        scope.launch {
            network.status.collect { status ->
                details.update { it.copy(network = status.kind) }
                if (tun != null) host?.setUnderlyingNetwork(status.network)
                dispatch(
                    if (status.isConnected) ConnectionEvent.NetworkAvailable
                    else ConnectionEvent.NetworkLost
                )
            }
        }
        scope.launch {
            // Settings that shape the VPN interface itself require re-establishing it.
            settingsRepo.settings
                .distinctUntilChangedBy { it.splitTunnel }
                .drop(1)
                .collect { ops.withLock { reestablishIfActive() } }
        }
        scope.launch {
            settingsRepo.settings
                .distinctUntilChangedBy { it.hotStandby }
                .drop(1)
                .collect { s ->
                    if (!s.hotStandby) ops.withLock { release(Hold.Standby) }
                }
        }
        scope.launch {
            settingsRepo.settings.collect { s ->
                details.update { it.copy(exitCountry = s.exitCountry) }
            }
        }
    }

    private fun dispatch(event: ConnectionEvent) {
        machine.update { ConnectionMachine.reduce(it, event) }
    }

    // --- commands -----------------------------------------------------------------------------

    suspend fun connect() = ops.withLock {
        prewarmTimeout?.cancel()
        prewarmTimeout = null
        dispatch(ConnectionEvent.Connect)
        if (!establishTun()) return@withLock
        hold(Hold.Vpn)
        _holds.update { it - Hold.Prewarm - Hold.Standby }
        host?.onHoldsChanged(_holds.value)
        ensureSession()
        socksPort?.let { startHev(it) }
        if (machine.value.torReady) details.update { it.copy(connectedSince = now()) }
    }

    suspend fun disconnect() = ops.withLock {
        val keepTor = settingsRepo.current().hotStandby && session != null
        dispatch(ConnectionEvent.Disconnect(keepTor))
        hev.stop()
        closeTun()
        if (keepTor) hold(Hold.Standby)
        release(Hold.Vpn)
    }

    /** The system revoked the VPN (another VPN app, or the user in Settings). */
    suspend fun revoked() = ops.withLock {
        hev.stop()
        tun = null // already closed by the system
        tunSpec = null
        _holds.value = emptySet()
        stopSession()
        dispatch(ConnectionEvent.Revoked)
        host?.onHoldsChanged(emptySet())
    }

    /**
     * Starts Tor ahead of time; stops again after [timeoutMillis] unless something else needs it.
     */
    suspend fun prewarm(timeoutMillis: Long) = ops.withLock {
        if (_holds.value.isNotEmpty() && Hold.Prewarm !in _holds.value) return@withLock
        hold(Hold.Prewarm)
        ensureSession()
        prewarmTimeout?.cancel()
        prewarmTimeout = scope.launch {
            delay(timeoutMillis)
            ops.withLock { release(Hold.Prewarm) }
        }
    }

    suspend fun cancelPrewarm() = ops.withLock { release(Hold.Prewarm) }

    /**
     * Brings Tor up just long enough to refresh its directory (consensus diffs are cheap), keeping
     * the cache "reasonably live" so the next connection skips the full download.
     */
    suspend fun refreshDirectory(timeoutMillis: Long): Boolean {
        ops.withLock {
            if (_holds.value.isNotEmpty() && machine.value.torReady) return true
            hold(Hold.Refresh)
            ensureSession()
        }
        val ok = withTimeoutOrNull(timeoutMillis) { machine.first { it.torReady } } != null
        if (ok) {
            // Give Tor a moment to fetch fresh microdescriptors after the consensus.
            delay(REFRESH_SETTLE_MS)
            memoryRepo.update { it.copy(lastDirectoryRefreshAt = now()) }
        }
        ops.withLock { release(Hold.Refresh) }
        return ok
    }

    suspend fun newIdentity(): Long? = session?.newIdentity()

    fun exportDiagnostics(): String = buildString {
        appendLine("state: ${snapshot.value.state}")
        appendLine("holds: ${_holds.value}")
        appendLine("transport: ${snapshot.value.transport} racing=${snapshot.value.racing}")
        appendLine("network: ${snapshot.value.network}")
        appendLine("warm cache: ${files.hasCachedConsensus()}")
        appendLine(runCatching { transports.versions }.getOrDefault("transports: n/a"))
        appendLine("---")
        append(log.export())
    }

    // --- internals ----------------------------------------------------------------------------

    private fun hold(reason: Hold) {
        _holds.update { it + reason }
        host?.onHoldsChanged(_holds.value)
    }

    private suspend fun release(reason: Hold) {
        if (reason !in _holds.value) return
        _holds.update { it - reason }
        if (reason == Hold.Prewarm) {
            prewarmTimeout?.cancel()
            prewarmTimeout = null
        }
        if (_holds.value.isEmpty()) {
            stopSession()
            if (machine.value.state !is TunnelState.Error) dispatch(ConnectionEvent.Stopped)
        }
        host?.onHoldsChanged(_holds.value)
    }

    private suspend fun establishTun(): Boolean {
        val settings = settingsRepo.current()
        val spec = VpnSpec(settings.splitTunnel, network.status.value.network)
        val fd = host?.establish(spec)
        if (fd == null) {
            log.e(TAG, "Cannot establish VPN interface")
            dispatch(ConnectionEvent.Fatal(TunnelError.VpnPermissionMissing))
            return false
        }
        val old = tun
        tun = fd
        tunSpec = spec
        // The new interface replaced the old one atomically; move hev over, then drop the old fd.
        socksPort?.let { startHev(it) }
        old?.closeQuietly()
        return true
    }

    private suspend fun reestablishIfActive() {
        if (tun == null || !machine.value.state.isTunnelActive) return
        log.i(TAG, "Split tunnelling changed: re-establishing VPN interface")
        establishTun()
    }

    private suspend fun closeTun() {
        tun?.closeQuietly()
        tun = null
        tunSpec = null
    }

    private suspend fun startHev(port: Int) {
        val fd = tun ?: return
        if (!hev.start(fd, port, HevTunnel.MTU, debuggable)) {
            log.e(TAG, "TUN bridge failed to start")
            dispatch(ConnectionEvent.Fatal(TunnelError.VpnEstablishFailed))
        }
    }

    private fun ensureSession() {
        if (session != null) return
        details.update { it.copy(torRunning = true, problem = null) }
        session =
            TorSession(
                    engine = engine,
                    transports = transports,
                    catalog = catalog,
                    moat = moat,
                    settingsRepo = settingsRepo,
                    memoryRepo = memoryRepo,
                    files = files,
                    network = network,
                    log = log,
                    listener = sessionListener,
                    countryHint = countryHint,
                    versionCode = versionCode,
                    now = now,
                    parentScope = scope,
                )
                .also { it.start() }
    }

    private suspend fun stopSession() {
        val s = session ?: return
        session = null
        socksPort = null
        hev.stop()
        s.stop()
        details.update { Details(network = it.network, exitCountry = it.exitCountry) }
        _traffic.value = null
    }

    private val sessionListener =
        object : TorSession.Listener {
            override fun onSocksPort(port: Int) {
                scope.launch {
                    ops.withLock {
                        socksPort = port
                        if (tun != null) startHev(port)
                    }
                }
            }

            override fun onBootstrap(info: BootstrapInfo) {
                details.update { it.copy(bootstrap = info) }
            }

            override fun onReady() {
                val wasConnected = machine.value.state == TunnelState.Connected
                dispatch(ConnectionEvent.TorReady)
                if (!wasConnected && machine.value.state == TunnelState.Connected) {
                    details.update { it.copy(connectedSince = now(), problem = null) }
                }
            }

            override fun onNotReady(reason: ReconnectReason) {
                dispatch(ConnectionEvent.TorNotReady(reason))
            }

            override fun onTransport(active: TransportKind?, racing: List<TransportKind>) {
                details.update { it.copy(transport = active, racing = racing) }
            }

            override fun onAllFailed() {
                dispatch(ConnectionEvent.AllTransportsFailed)
            }

            override fun onRetry() {
                dispatch(ConnectionEvent.Retry)
            }

            override fun onFatal(error: TunnelError) {
                dispatch(ConnectionEvent.Fatal(error))
                scope.launch {
                    ops.withLock {
                        hev.stop()
                        closeTun()
                        _holds.value = emptySet()
                        stopSession()
                        host?.onHoldsChanged(emptySet())
                    }
                }
            }

            override fun onCircuit(info: CircuitInfo?) {
                details.update { it.copy(circuit = info) }
            }

            override fun onTraffic(sample: TrafficSample) {
                _traffic.value = sample
            }

            override fun onProblem(problem: TunnelProblem?) {
                details.update { it.copy(problem = problem) }
            }

            override fun onNewIdentity(readyAt: Long) {
                details.update { it.copy(newIdentityReadyAt = readyAt) }
            }

            override fun onStuck() {
                log.e(TAG, "Tor cannot restart in-process; restarting the tunnel process")
                host?.restartProcess()
            }
        }

    private fun ParcelFileDescriptor.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {}
    }

    private companion object {
        const val TAG = "controller"
        const val REFRESH_SETTLE_MS = 20_000L
    }
}
