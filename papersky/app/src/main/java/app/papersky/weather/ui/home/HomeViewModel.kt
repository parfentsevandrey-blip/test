package app.papersky.weather.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.papersky.weather.AppContainer
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.sync.SyncScheduler
import app.papersky.weather.core.sync.WeatherSync
import app.papersky.weather.widget.WidgetDirectory
import app.papersky.weather.widget.resolve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loaded: Boolean = false,
    val places: List<Place> = emptyList(),
    val place: Place? = null,
    val forecast: Forecast? = null,
    val settings: UserSettings = UserSettings(),
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    val hasLocationPermission: Boolean = false,
)

/** Activity-scoped: its weather also tints every other screen. */
class HomeViewModel(private val c: AppContainer, private val app: android.app.Application) : ViewModel() {

    private val failed = MutableStateFlow(false)
    private val permission = MutableStateFlow(c.locator.hasPermission())

    private val selection = combine(c.places.data, c.settings.settings) { places, settings ->
        places to settings
    }

    val state: StateFlow<HomeUiState> = selection
        .flatMapLatest { (places, settings) ->
            val place = places.resolve(settings.selectedPlaceId)
            val forecasts = place?.let { c.weather.observe(it.id) } ?: flowOf(null)
            combine(forecasts, c.weather.refreshing, failed, permission) { forecast, refreshing, failedNow, perm ->
                HomeUiState(
                    loaded = true,
                    places = listOfNotNull(places.device) + places.saved,
                    place = place,
                    forecast = forecast,
                    settings = settings,
                    refreshing = place != null && place.id in refreshing,
                    failed = failedNow,
                    hasLocationPermission = perm,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Whenever the selected place changes and has no fresh data, fetch it.
        viewModelScope.launch {
            selection.map { (places, settings) -> places.resolve(settings.selectedPlaceId) }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.latitude == b?.latitude }
                .collect { place -> if (place != null) refresh(place, force = false) }
        }
    }

    fun select(placeId: String) {
        viewModelScope.launch { c.settings.update { it.copy(selectedPlaceId = placeId) } }
    }

    /** Called when the app comes to the foreground or the user pulls the cord. */
    fun refresh(userInitiated: Boolean) {
        viewModelScope.launch {
            permission.value = c.locator.hasPermission()
            val settings = c.settings.current()
            if (permission.value) c.sync.refreshDevicePlace(foreground = true)
            val place = c.places.snapshot().resolve(settings.selectedPlaceId) ?: return@launch
            refresh(place, force = userInitiated)
        }
    }

    private suspend fun refresh(place: Place, force: Boolean) {
        val before = c.weather.peek(place.id)?.fetchedAt
        val result = c.weather.refresh(place, if (force) 0 else WeatherSync.MIN_REFRESH_AGE_MS)
        failed.update { result.isFailure }
        if (result.getOrNull()?.fetchedAt?.let { it != before } == true) {
            // Keep the home screen in step with what the user just saw.
            WidgetDirectory.updateAll(app)
            SyncScheduler.scheduleSceneShift(app)
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        permission.value = granted
        if (granted) {
            viewModelScope.launch {
                c.sync.refreshDevicePlace(foreground = true)
                c.settings.update { it.copy(selectedPlaceId = Place.HERE, onboarded = true) }
                refresh(userInitiated = true)
            }
        }
    }
}
