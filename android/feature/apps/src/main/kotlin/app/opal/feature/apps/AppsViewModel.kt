package app.opal.feature.apps

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opal.core.data.InstalledApp
import app.opal.core.data.InstalledAppsRepository
import app.opal.core.data.SettingsRepository
import app.opal.core.data.SplitTunnelPresets
import app.opal.core.model.settings.SplitTunnelMode
import app.opal.core.model.settings.SplitTunnelSettings
import app.opal.core.tunnel.ipc.TunnelClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val checked: Boolean,
)

@Immutable
data class AppsUiState(
    val loading: Boolean = true,
    val mode: SplitTunnelMode = SplitTunnelMode.AllExcept,
    val query: String = "",
    val showSystem: Boolean = false,
    val rows: ImmutableList<AppRow> = persistentListOf(),
    val selectedCount: Int = 0,
    /** Preset apps that are installed on this device. */
    val presetInstalled: Int = 0,
    val presetTotal: Int = SplitTunnelPresets.russianBanksAndGovernment.size,
    val presetApplied: Boolean = false,
    /** System lockdown flag, when the tunnel has reported it. */
    val lockdown: Boolean? = null,
)

class AppsViewModel(
    private val settings: SettingsRepository,
    private val repository: InstalledAppsRepository,
    tunnel: TunnelClient,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { installed.value = repository.load() }
    }

    val state: StateFlow<AppsUiState> =
        combine(
                installed,
                query,
                settings.settings.map { it.splitTunnel }.distinctUntilChanged(),
                tunnel.snapshot.map { it.lockdown }.distinctUntilChanged(),
            ) { apps, q, split, lockdown ->
                build(apps, q, split, lockdown)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun setMode(mode: SplitTunnelMode) = updateSplit { it.copy(mode = mode) }

    fun setShowSystem(show: Boolean) = updateSplit { it.copy(showSystemApps = show) }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setChecked(packageName: String, checked: Boolean) = updateSplit { split ->
        when (split.mode) {
            SplitTunnelMode.AllExcept ->
                split.copy(excluded = split.excluded.toggled(packageName, checked))
            SplitTunnelMode.OnlySelected ->
                split.copy(included = split.included.toggled(packageName, checked))
        }
    }

    /** Adds (or removes) the installed preset apps to the direct list ("all except" mode). */
    fun setPresetApplied(apply: Boolean) {
        val present = installed.value.orEmpty().map { it.packageName }.toSet()
        val preset = SplitTunnelPresets.russianBanksAndGovernment.filter { it in present }
        updateSplit { split ->
            split.copy(
                mode = SplitTunnelMode.AllExcept,
                excluded = if (apply) split.excluded + preset else split.excluded - preset.toSet(),
            )
        }
    }

    private fun updateSplit(transform: (SplitTunnelSettings) -> SplitTunnelSettings) {
        viewModelScope.launch {
            settings.update { it.copy(splitTunnel = transform(it.splitTunnel)) }
        }
    }

    private fun Set<String>.toggled(pkg: String, on: Boolean) = if (on) this + pkg else this - pkg

    companion object {
        internal fun build(
            apps: List<InstalledApp>?,
            query: String,
            split: SplitTunnelSettings,
            lockdown: Boolean?,
        ): AppsUiState {
            val selected =
                if (split.mode == SplitTunnelMode.AllExcept) split.excluded else split.included
            val present = apps.orEmpty().map { it.packageName }.toSet()
            val preset = SplitTunnelPresets.russianBanksAndGovernment.filter { it in present }
            val needle = query.trim()
            val rows =
                apps
                    .orEmpty()
                    .asSequence()
                    // Checked system apps stay visible so they can be unchecked.
                    .filter { split.showSystemApps || !it.isSystem || it.packageName in selected }
                    .filter {
                        needle.isEmpty() ||
                            it.label.contains(needle, ignoreCase = true) ||
                            it.packageName.contains(needle, ignoreCase = true)
                    }
                    .map {
                        AppRow(it.packageName, it.label, it.isSystem, it.packageName in selected)
                    }
                    .toList()
                    .toImmutableList()
            return AppsUiState(
                loading = apps == null,
                mode = split.mode,
                query = query,
                showSystem = split.showSystemApps,
                rows = rows,
                selectedCount = selected.count { it in present },
                presetInstalled = preset.size,
                presetApplied =
                    split.mode == SplitTunnelMode.AllExcept &&
                        preset.isNotEmpty() &&
                        split.excluded.containsAll(preset),
                lockdown = lockdown,
            )
        }
    }
}
