package app.rosa.weather.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.WeatherSyncer
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlacesUiState(
    val device: Place? = null,
    val followDevice: Boolean = true,
    val places: List<Place> = emptyList(),
    val forecasts: Map<String, Forecast> = emptyMap(),
    val units: Units = Units(),
)

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val repository: PlacesRepository,
    weather: WeatherRepository,
    settings: SettingsRepository,
    private val syncer: WeatherSyncer,
) : ViewModel() {
    val state: StateFlow<PlacesUiState> = combine(repository.saved, weather.forecasts, settings.settings) { saved, forecasts, s ->
        PlacesUiState(saved.lastDeviceLocation, saved.followDeviceLocation, saved.places, forecasts, s.resolvedUnits())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())

    fun select(id: String) = viewModelScope.launch { repository.select(id) }

    fun remove(id: String) = viewModelScope.launch { repository.remove(id) }

    fun move(from: Int, to: Int) = viewModelScope.launch { repository.move(from, to) }

    fun setFollowDevice(enabled: Boolean) = viewModelScope.launch {
        repository.setFollowDeviceLocation(enabled)
        if (enabled) syncer.sync(SyncReason.UserRequest, inBackground = false)
    }
}
