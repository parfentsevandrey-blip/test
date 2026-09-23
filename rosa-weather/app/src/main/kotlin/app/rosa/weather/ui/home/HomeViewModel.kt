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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _userRefreshing = MutableStateFlow(false)

    /** True while a refresh the user asked for (pull, button) is running — whatever its outcome. */
    val userRefreshing: StateFlow<Boolean> = _userRefreshing.asStateFlow()

    /** Called when the screen becomes visible: cheap if data is fresh (the syncer skips it). */
    fun onVisible() {
        sync(force = false, reason = SyncReason.AppForeground)
    }

    fun refresh() {
        _userRefreshing.value = true
        sync(force = true, reason = SyncReason.UserRequest).invokeOnCompletion {
            // A second pull may have started meanwhile; the drop settles when the last one ends.
            if (refreshJob?.isActive != true) _userRefreshing.value = false
        }
    }

    fun onLocationPermission(granted: Boolean) {
        if (granted) refresh()
    }

    fun select(placeId: String) {
        viewModelScope.launch { places.select(placeId) }
    }

    private fun sync(force: Boolean, reason: SyncReason): Job {
        refreshJob?.takeIf { it.isActive && !force }?.let { return it }
        return viewModelScope.launch {
            try {
                currentPlace.resolve(inBackground = false)
                syncer.sync(reason, force = force, inBackground = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline or storage hiccup: the cached forecast stays on screen.
            }
        }.also { refreshJob = it }
    }
}
