package app.opal.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opal.core.data.SettingsRepository
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ThemeMode
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.tunnel.ipc.TunnelClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val connected: Boolean = false,
    val alwaysOn: Boolean? = null,
    val lockdown: Boolean? = null,
    val loaded: Boolean = false,
)

class SettingsViewModel(private val repository: SettingsRepository, tunnel: TunnelClient) :
    ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(repository.settings, tunnel.snapshot) { s, snap ->
                SettingsUiState(
                    settings = s,
                    connected = snap.state == TunnelState.Connected,
                    alwaysOn = snap.alwaysOn,
                    lockdown = snap.lockdown,
                    loaded = true,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setHotStandby(on: Boolean) = update { it.copy(hotStandby = on) }

    fun setPrepareOnOpen(on: Boolean) = update { it.copy(prepareOnOpen = on) }

    fun setBackgroundRefresh(on: Boolean) = update { it.copy(backgroundDirectoryRefresh = on) }

    fun setDataSaver(on: Boolean) = update { it.copy(dataSaver = on) }

    fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }

    fun setSimplifiedGraphics(on: Boolean) = update { it.copy(simplifiedGraphics = on) }

    fun setExitCountry(code: String?) = update { it.copy(exitCountry = code) }
}
