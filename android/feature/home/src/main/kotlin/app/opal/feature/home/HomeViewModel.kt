package app.opal.feature.home

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opal.core.data.SettingsRepository
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.tunnel.ipc.TunnelClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val snapshot: TunnelSnapshot = TunnelSnapshot(),
    /** Download bytes/s, oldest first, one per second (Tor BW events), connected session only. */
    val down: ImmutableList<Long> = persistentListOf(),
    val up: ImmutableList<Long> = persistentListOf(),
    val traffic: TrafficSample? = null,
    val mode: ConnectionMode = ConnectionMode.Snowflake,
)

class HomeViewModel(private val tunnel: TunnelClient, settings: SettingsRepository) : ViewModel() {

    private val history = MutableStateFlow(SpeedHistory())

    init {
        viewModelScope.launch {
            combine(tunnel.snapshot.map { it.state == TunnelState.Connected }, tunnel.traffic) {
                    connected,
                    sample ->
                    connected to sample
                }
                .collect { (connected, sample) ->
                    history.update {
                        if (!connected || sample == null) SpeedHistory() else it + sample
                    }
                }
        }
    }

    val state: StateFlow<HomeUiState> =
        combine(tunnel.snapshot, history, tunnel.traffic, settings.settings) {
                snapshot,
                h,
                traffic,
                s ->
                HomeUiState(
                    snapshot = snapshot,
                    down = h.down,
                    up = h.up,
                    traffic = traffic.takeIf { snapshot.state == TunnelState.Connected },
                    mode = s.connectionMode,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                HomeUiState(snapshot = tunnel.snapshot.value),
            )

    /** VPN consent dialog, or null when consent was already granted. */
    fun consentIntent(): Intent? = tunnel.consentIntent()

    fun connect() = tunnel.connect()

    fun disconnect() = tunnel.disconnect()

    fun newIdentity() = tunnel.newIdentity()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Fixed window of the last [CAPACITY] one-second samples. */
internal data class SpeedHistory(
    val down: ImmutableList<Long> = persistentListOf(),
    val up: ImmutableList<Long> = persistentListOf(),
) {
    operator fun plus(sample: TrafficSample) =
        SpeedHistory(
            (down + sample.read).takeLast(CAPACITY).toImmutableList(),
            (up + sample.written).takeLast(CAPACITY).toImmutableList(),
        )

    companion object {
        const val CAPACITY = 60
    }
}
