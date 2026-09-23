package app.rosa.weather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Units
import app.rosa.weather.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val scheduler: SyncScheduler,
    private val widgets: WidgetUpdater,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        val before = repository.current()
        repository.update(transform)
        val after = repository.current()
        if (before.refreshIntervalMinutes != after.refreshIntervalMinutes) {
            scheduler.ensurePeriodic(after.refreshIntervalMinutes, replaceExisting = true)
        }
        // Units change what every widget says: redraw them right away.
        if (before.resolvedUnits() != after.resolvedUnits()) widgets.update()
    }

    fun updateUnits(transform: (Units) -> Units) = update { s -> s.copy(units = transform(s.resolvedUnits())) }
}
