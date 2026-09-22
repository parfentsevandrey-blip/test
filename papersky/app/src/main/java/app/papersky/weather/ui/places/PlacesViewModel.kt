package app.papersky.weather.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.papersky.weather.AppContainer
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val places: List<Place>) : SearchState
    data object Failed : SearchState
}

data class PlaceRow(val place: Place, val forecast: Forecast?, val selected: Boolean)

class PlacesViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    val search: StateFlow<SearchState> = query
        .debounce(320)
        .mapLatest { q ->
            val text = q.trim()
            if (text.length < 2) return@mapLatest SearchState.Idle
            try {
                SearchState.Results(c.api.search(text, Locale.getDefault().language).map { it.toPlace() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchState.Failed
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    val rows: StateFlow<List<PlaceRow>> = combine(c.places.all, c.settings.settings) { places, s -> places to s.selectedPlaceId }
        .flatMapLatest { (places, selected) ->
            if (places.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(places.map { p -> c.weather.observe(p.id).map { f -> PlaceRow(p, f, p.id == selected) } }) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Fill in thumbnails for places we have never downloaded.
        viewModelScope.launch {
            c.weather.ensureLoaded()
            c.places.snapshot().let { listOfNotNull(it.device) + it.saved }
                .filter { c.weather.peek(it.id) == null }
                .forEach { launch { c.weather.refresh(it) } }
        }
    }

    fun add(place: Place) {
        viewModelScope.launch {
            c.places.add(place)
            c.settings.update { it.copy(selectedPlaceId = place.id, onboarded = true) }
            query.value = ""
            c.weather.refresh(place)
        }
    }

    fun remove(placeId: String) {
        viewModelScope.launch {
            c.places.remove(placeId)
            c.weather.forget(placeId)
            c.settings.update { if (it.selectedPlaceId == placeId) it.copy(selectedPlaceId = Place.HERE) else it }
        }
    }

    fun select(placeId: String) {
        viewModelScope.launch { c.settings.update { it.copy(selectedPlaceId = placeId) } }
    }

    fun move(from: Int, to: Int) {
        viewModelScope.launch { c.places.move(from, to) }
    }

    fun locate(granted: Boolean) {
        if (!granted) return
        viewModelScope.launch {
            val place = c.sync.refreshDevicePlace(foreground = true) ?: return@launch
            c.settings.update { it.copy(selectedPlaceId = Place.HERE) }
            c.weather.refresh(place, 0)
        }
    }

    fun hasLocationPermission() = c.locator.hasPermission()
}
