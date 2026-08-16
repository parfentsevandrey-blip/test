package com.cozyhome.weather.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cozyhome.weather.data.GeoPlace
import com.cozyhome.weather.data.Place
import com.cozyhome.weather.data.WeatherRepository
import com.cozyhome.weather.data.WeatherSnapshot
import com.cozyhome.weather.util.LocationHelper
import com.cozyhome.weather.widget.WeatherWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val snapshot: WeatherSnapshot? = null,
    val error: String? = null,
    val searchResults: List<GeoPlace> = emptyList(),
    val searching: Boolean = false,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val cached = repository.cachedSnapshot()
            if (cached != null) {
                _state.update { it.copy(snapshot = cached, loading = false) }
            }
            refreshInternal(repository.savedPlace() ?: cached?.place ?: Place.DEFAULT)
        }
    }

    fun refresh() {
        val place = _state.value.snapshot?.place ?: Place.DEFAULT
        viewModelScope.launch { refreshInternal(place) }
    }

    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            runCatching { repository.searchCities(trimmed) }
                .onSuccess { results ->
                    _state.update { it.copy(searchResults = results, searching = false) }
                }
                .onFailure {
                    _state.update { it.copy(searchResults = emptyList(), searching = false) }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchResults = emptyList(), searching = false) }
    }

    fun selectPlace(geo: GeoPlace) {
        clearSearch()
        viewModelScope.launch {
            refreshInternal(Place(geo.name, geo.latitude, geo.longitude))
        }
    }

    /** Called once ACCESS_COARSE_LOCATION is granted. */
    fun useDeviceLocation() {
        viewModelScope.launch {
            val location = LocationHelper.currentLocation(getApplication()) ?: return@launch
            val name = LocationHelper.placeName(getApplication(), location.latitude, location.longitude)
            refreshInternal(Place(name ?: "Моё место", location.latitude, location.longitude))
        }
    }

    private suspend fun refreshInternal(place: Place) {
        _state.update { it.copy(refreshing = true, error = null) }
        runCatching { repository.refresh(place) }
            .onSuccess { snapshot ->
                _state.update {
                    it.copy(snapshot = snapshot, loading = false, refreshing = false, error = null)
                }
                runCatching { WeatherWidget().updateAll(getApplication()) }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.snapshot == null) (e.message ?: "network error") else null,
                    )
                }
            }
    }
}
