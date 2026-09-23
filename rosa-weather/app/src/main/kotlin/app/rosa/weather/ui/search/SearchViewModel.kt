package app.rosa.weather.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.repository.PlaceSearchRepository
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Found(val places: List<Place>) : SearchResults
    data object Empty : SearchResults
    data object Error : SearchResults
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: PlaceSearchRepository,
    private val places: PlacesRepository,
    private val weather: WeatherRepository,
) : ViewModel() {
    val query = MutableStateFlow("")

    val results: StateFlow<SearchResults> = query
        .debounce(280)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.trim().length < 2) return@mapLatest SearchResults.Idle
            search.search(q).fold(
                onSuccess = { if (it.isEmpty()) SearchResults.Empty else SearchResults.Found(it) },
                onFailure = { SearchResults.Error },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults.Idle)

    fun onQuery(text: String) {
        query.value = text
    }

    fun add(place: Place, onDone: () -> Unit) {
        viewModelScope.launch {
            places.add(place)
            onDone()
            weather.refresh(place)
        }
    }
}
