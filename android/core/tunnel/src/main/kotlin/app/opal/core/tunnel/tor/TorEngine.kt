package app.opal.core.tunnel.tor

import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.Torrc
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Tor implementation behind the app. Today: C-tor via JNI ([CTorEngine]). The interface only
 * uses concepts both C-tor and Arti expose (config, SOCKS port, bootstrap/circuit events, NEWNYM),
 * so an Arti Mobile engine can replace it without touching the rest of the tunnel.
 */
interface TorEngine {

    val state: StateFlow<EngineState>

    /** Control-port events while running. Hot flow; nothing is replayed. */
    val events: SharedFlow<TorEvent>

    /** Starts Tor with [config]; returns once the SOCKS listener is open. */
    suspend fun start(config: Torrc): EngineState.Running

    /** Applies [options] from [config] live (no restart). */
    suspend fun reconfigure(config: Torrc, options: Collection<TorOption>)

    suspend fun signal(signal: TorSignal)

    suspend fun getInfo(vararg keys: String): Map<String, String>

    suspend fun stop()
}

sealed interface EngineState {
    data object Stopped : EngineState

    data object Starting : EngineState

    data class Running(val socksPort: Int, val version: String) : EngineState

    data class Failed(val reason: String) : EngineState
}

enum class TorSignal(val command: String) {
    /** New circuits for new streams (rate-limited by Tor to one per ~10 s). */
    NewIdentity("NEWNYM"),
    /** Wake from dormant mode. */
    Active("ACTIVE"),
    Dormant("DORMANT"),
    ClearDnsCache("CLEARDNSCACHE"),
}
