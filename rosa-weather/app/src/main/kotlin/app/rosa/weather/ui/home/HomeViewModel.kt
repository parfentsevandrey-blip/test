package app.rosa.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.location.CurrentPlaceResolver
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.WeatherSyncer
import app.rosa.weather.core.data.util.NetworkMonitor
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlacePage(val place: Place, val forecast: Forecast?)

data class HomeUiState(
    val loaded: Boolean = false,
    val pages: List<PlacePage> = emptyList(),
    val selectedId: String? = null,
    val units: Units = Units(),
    val settings: AppSettings = AppSettings(),
    val refreshing: Set<String> = emptySet(),
    val online: Boolean = true,
    val followDevice: Boolean = true,
) {
    val selectedIndex: Int get() = pages.indexOfFirst { it.place.id == selectedId }.coerceAtLeast(0)
    val isRefreshing: Boolean get() = refreshing.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val places: PlacesRepository,
    weather: WeatherRepository,
    settings: SettingsRepository,
    private val currentPlace: CurrentPlaceResolver,
    private val syncer: WeatherSyncer,
    network: NetworkMonitor,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        places.saved,
        weather.forecasts,
        settings.settings,
        weather.refreshing,
        network.online,
    ) { saved, forecasts, appSettings, refreshing, online ->
        HomeUiState(
            loaded = true,
            pages = saved.all.map { PlacePage(it, forecasts[it.id]) },
            selectedId = saved.selectedId,
            units = appSettings.resolvedUnits(),
            settings = appSettings,
            refreshing = refreshing,
            online = online,
            followDevice = saved.followDeviceLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private var refreshJob: Job? = null

    /** Called when the screen becomes visible: cheap if data is fresh (the syncer skips it). */
    fun onVisible() = sync(force = false, reason = SyncReason.AppForeground)

    fun refresh() = sync(force = true, reason = SyncReason.UserRequest)

    fun onLocationPermission(granted: Boolean) {
        if (granted) sync(force = true, reason = SyncReason.UserRequest)
    }

    fun select(placeId: String) {
        viewModelScope.launch { places.select(placeId) }
    }

    private fun sync(force: Boolean, reason: SyncReason) {
        if (refreshJob?.isActive == true && !force) return
        refreshJob = viewModelScope.launch {
            currentPlace.resolve(inBackground = false)
            syncer.sync(reason, force = force, inBackground = false)
        }
    }
}
