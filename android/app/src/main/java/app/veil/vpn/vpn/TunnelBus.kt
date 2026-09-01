package app.veil.vpn.vpn

import app.veil.vpn.model.TunnelState
import app.veil.vpn.model.TunnelStats
import app.veil.vpn.net.ProbeReport
import app.veil.vpn.tor.Attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A port this app is responsible for having open on loopback.
 *
 * Published so the diagnostics screen can show it. Guides for spotting
 * circumvention software on a device work by connecting to well-known local
 * proxy ports, and a tool that opens them should say so rather than leave the
 * user to find out.
 */
data class LocalListener(val name: String, val endpoint: String, val note: String)

/**
 * The one place the UI reads tunnel state from.
 *
 * The VPN service and the Activity share a process, so a plain set of
 * StateFlows is both the simplest and the most robust channel between them: no
 * binder to lose, no state to rebuild when the Activity is recreated, and the
 * service keeps publishing while no UI exists at all.
 */
object TunnelBus {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats.asStateFlow()

    private val _probe = MutableStateFlow(ProbeReport())
    val probe: StateFlow<ProbeReport> = _probe.asStateFlow()

    private val _ladder = MutableStateFlow<List<Attempt>>(emptyList())
    val ladder: StateFlow<List<Attempt>> = _ladder.asStateFlow()

    private val _circuit = MutableStateFlow<String?>(null)
    val circuit: StateFlow<String?> = _circuit.asStateFlow()

    private val _localListeners = MutableStateFlow<List<LocalListener>>(emptyList())
    val localListeners: StateFlow<List<LocalListener>> = _localListeners.asStateFlow()

    private val _cooldowns = MutableStateFlow<List<String>>(emptyList())
    val cooldowns: StateFlow<List<String>> = _cooldowns.asStateFlow()

    private val _snowflakeProxyServed = MutableStateFlow(0)
    val snowflakeProxyServed: StateFlow<Int> = _snowflakeProxyServed.asStateFlow()

    internal fun publish(state: TunnelState) { _state.value = state }
    internal fun publish(stats: TunnelStats) { _stats.value = stats }
    internal fun publish(report: ProbeReport) { _probe.value = report }
    internal fun publishLadder(ladder: List<Attempt>) { _ladder.value = ladder }
    internal fun publishCircuit(path: String?) { _circuit.value = path }
    internal fun publishListeners(listeners: List<LocalListener>) { _localListeners.value = listeners }
    internal fun publishCooldowns(entries: List<String>) { _cooldowns.value = entries }
    internal fun noteSnowflakeClient() { _snowflakeProxyServed.value += 1 }

    /**
     * Last resort for a UI that has been told the tunnel is stopping and never
     * heard otherwise. Only the state is cleared; nothing is torn down here.
     */
    fun forceIdle() {
        _state.value = TunnelState.Idle
        _stats.value = TunnelStats()
        _circuit.value = null
        _localListeners.value = emptyList()
    }

    internal fun reset() {
        _state.value = TunnelState.Idle
        _stats.value = TunnelStats()
        _circuit.value = null
        _localListeners.value = emptyList()
    }
}
