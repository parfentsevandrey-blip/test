package app.veil.vpn.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.veil.vpn.VeilApp
import app.veil.vpn.core.VeilLog
import app.veil.vpn.data.AppRoutingMode
import app.veil.vpn.data.DnsMode
import app.veil.vpn.data.InstalledApp
import app.veil.vpn.data.InstalledApps
import app.veil.vpn.data.IsolationMode
import app.veil.vpn.data.DEFAULT_BYPASS_SUFFIXES
import app.veil.vpn.data.RouteMode
import app.veil.vpn.model.DtlsProfile
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.data.VeilSettings
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.Transport
import app.veil.vpn.model.TunnelState
import app.veil.vpn.net.MoatChallenge
import app.veil.vpn.vpn.TunnelBus
import app.veil.vpn.vpn.VeilVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the bridge-request flow is currently doing. */
sealed interface MoatFlow {
    data object Idle : MoatFlow
    data object Loading : MoatFlow
    data class Solving(val challenge: MoatChallenge) : MoatFlow
    data class Done(val added: Int) : MoatFlow
    data class Error(val message: String) : MoatFlow
}

class VeilViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application as VeilApp

    val tunnelState = TunnelBus.state
    val stats = TunnelBus.stats
    val probe = TunnelBus.probe
    val ladder = TunnelBus.ladder
    val circuit = TunnelBus.circuit
    val snowflakeServed = TunnelBus.snowflakeProxyServed
    val localListeners = TunnelBus.localListeners
    val cooldowns = TunnelBus.cooldowns
    val bootstrap = container.tor.bootstrap
    val logs = VeilLog.lines
    val knownBridges = container.bridges.bridges

    val settings: StateFlow<VeilSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, VeilSettings())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _moat = MutableStateFlow<MoatFlow>(MoatFlow.Idle)
    val moat: StateFlow<MoatFlow> = _moat.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    init {
        viewModelScope.launch { container.bridges.load() }
        watchForStuckShutdown()
    }

    /**
     * Puts the UI back to idle if a disconnect never reports finishing.
     *
     * The service already bounds its own teardown, but it can be killed part
     * way through, and a screen that says "Disconnecting" for ever with no
     * service left to answer is the worst outcome: the only escape is force
     * stopping the app. After this long, showing idle is both more honest and
     * more useful, because tapping connect again is then possible.
     */
    private fun watchForStuckShutdown() {
        viewModelScope.launch {
            tunnelState.collectLatest { state ->
                if (state !is TunnelState.Stopping) return@collectLatest
                val settled = withTimeoutOrNull(STUCK_SHUTDOWN_MILLIS) {
                    // collectLatest cancels this the moment the state changes,
                    // so reaching the end of the wait means it never did.
                    kotlinx.coroutines.awaitCancellation()
                }
                if (settled == null) {
                    VeilLog.w("ui", "disconnect never reported finishing; releasing the UI")
                    stopTunnel()
                    TunnelBus.forceIdle()
                }
            }
        }
    }

    // --- Tunnel -------------------------------------------------------------

    /** Called once the VPN consent dialog has been accepted. */
    fun startTunnel() {
        val context = getApplication<Application>()
        context.startForegroundService(Intent(context, VeilVpnService::class.java))
    }

    fun stopTunnel() {
        val context = getApplication<Application>()
        context.startService(
            Intent(context, VeilVpnService::class.java).setAction(VeilVpnService.ACTION_DISCONNECT),
        )
    }

    fun requestNewCircuit() {
        val context = getApplication<Application>()
        context.startService(
            Intent(context, VeilVpnService::class.java).setAction(VeilVpnService.ACTION_NEW_CIRCUIT),
        )
    }

    // --- Settings -----------------------------------------------------------

    fun setRouteMode(mode: RouteMode) = edit { container.settings.setRouteMode(mode) }
    fun setManualTransport(t: Transport) = edit { container.settings.setManualTransport(t) }
    fun setBlockUdp(value: Boolean) = edit { container.settings.setBlockUdp(value) }
    fun setDnsMode(mode: DnsMode) = edit { container.settings.setDnsMode(mode) }
    fun setIsolation(mode: IsolationMode) = edit { container.settings.setIsolation(mode) }
    fun setKillSwitch(value: Boolean) = edit { container.settings.setKillSwitch(value) }
    fun setAutoStart(value: Boolean) = edit { container.settings.setAutoStartOnBoot(value) }
    fun setSnowflakeProxy(value: Boolean) = edit { container.settings.setRunSnowflakeProxy(value) }
    fun setAppRoutingMode(mode: AppRoutingMode) = edit { container.settings.setAppRoutingMode(mode) }
    fun setTlsProfile(profile: TlsProfile) = edit { container.settings.setTlsProfile(profile) }
    fun setDtlsProfile(profile: DtlsProfile) = edit { container.settings.setDtlsProfile(profile) }

    /** An empty suffix list is how the bypass stays off; there is no separate flag. */
    fun setBypassLocal(enabled: Boolean) = edit {
        container.settings.setBypassSuffixes(if (enabled) DEFAULT_BYPASS_SUFFIXES else "")
    }
    fun toggleApp(packageName: String) = edit { container.settings.toggleApp(packageName) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch {
            _apps.value = InstalledApps.load(getApplication())
        }
    }

    // --- Bridges ------------------------------------------------------------

    fun refreshBridges() {
        viewModelScope.launch {
            _busyMessage.value = "Fetching bridges"
            val result = container.bridges.refreshFromMoat()
            _busyMessage.value = result.fold(
                onSuccess = { "Fetched $it bridges" },
                onFailure = { "Could not reach the bridge API: ${it.message}" },
            )
        }
    }

    fun customBridgeText(onLoaded: (String) -> Unit) {
        viewModelScope.launch { onLoaded(container.bridges.customText()) }
    }

    fun saveCustomBridges(text: String) {
        viewModelScope.launch {
            container.bridges.replaceCustom(text)
            _busyMessage.value = "Saved ${BridgeLine.parseAll(text).size} bridge lines"
        }
    }

    /** Starts the CAPTCHA flow that hands out unpublished bridges. */
    fun requestBridgesFromMoat(transport: String = "obfs4") {
        viewModelScope.launch {
            _moat.value = MoatFlow.Loading
            _moat.value = runCatching { container.moat.requestChallenge(transport) }.fold(
                onSuccess = { MoatFlow.Solving(it) },
                onFailure = { MoatFlow.Error(it.message ?: "the bridge API did not answer") },
            )
        }
    }

    fun submitMoatSolution(solution: String) {
        val current = _moat.value as? MoatFlow.Solving ?: return
        viewModelScope.launch {
            _moat.value = MoatFlow.Loading
            _moat.value = runCatching {
                val lines = container.moat.solveChallenge(current.challenge, solution)
                val added = container.bridges.addCustom(lines.joinToString("\n") { it.raw })
                MoatFlow.Done(added)
            }.getOrElse { MoatFlow.Error(it.message ?: "the answer was rejected") }
        }
    }

    fun dismissMoat() { _moat.value = MoatFlow.Idle }

    fun clearMessage() { _busyMessage.value = null }

    fun clearLogs() = VeilLog.clear()

    fun logDump(): String = VeilLog.dump()

    /** Clears the list of endpoints being rested, so everything is tried again. */
    fun clearCooldowns() {
        container.cooldown.clear()
        _busyMessage.value = "Every endpoint is back in the rotation"
    }

    private companion object {
        const val STUCK_SHUTDOWN_MILLIS = 12_000L
    }

    fun forgetLearnedRoutes() {
        viewModelScope.launch {
            container.memory.forget()
            _busyMessage.value = "Forgot what worked on every network"
        }
    }
}
