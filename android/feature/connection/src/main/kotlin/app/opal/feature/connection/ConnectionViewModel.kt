package app.opal.feature.connection

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opal.core.data.SettingsRepository
import app.opal.core.data.TunnelMemoryRepository
import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.CircumventionCache
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.tunnel.ipc.TunnelClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsApiInfo(
    val fetchedAt: Long,
    val country: String?,
    val types: ImmutableList<String>,
)

@Immutable
data class ConnectionUiState(
    val mode: ConnectionMode = ConnectionMode.Snowflake,
    val customCount: Int = 0,
    val settingsApi: SettingsApiInfo? = null,
    val winners: ImmutableMap<NetworkKind, TransportKind> = persistentMapOf(),
    /** Transport carrying the tunnel right now (connected sessions only). */
    val activeTransport: TransportKind? = null,
)

class ConnectionViewModel(
    private val settings: SettingsRepository,
    memory: TunnelMemoryRepository,
    tunnel: TunnelClient,
) : ViewModel() {

    val state: StateFlow<ConnectionUiState> =
        combine(settings.settings, memory.memory, tunnel.snapshot) { s, m, snap ->
                ConnectionUiState(
                    mode = s.connectionMode,
                    customCount = s.customBridges.count { BridgeLine.parseOrNull(it) != null },
                    settingsApi = m.circumvention?.toInfo(),
                    winners = m.winners.toImmutableMap(),
                    activeTransport = snap.transport.takeIf { snap.state == TunnelState.Connected },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionUiState())

    fun setMode(mode: ConnectionMode) {
        viewModelScope.launch { settings.update { it.copy(connectionMode = mode) } }
    }

    private fun CircumventionCache.toInfo(): SettingsApiInfo? =
        if (settings.isEmpty() && builtin.isEmpty()) {
            null
        } else {
            SettingsApiInfo(
                fetchedAt = fetchedAt,
                country = country,
                types = (settings.map { it.type } + builtin.keys).distinct().toImmutableList(),
            )
        }
}
