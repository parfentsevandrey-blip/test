package app.rosa.weather.core.data.repository

import androidx.datastore.core.DataStore
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SavedPlaces
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class PlacesRepository @Inject constructor(private val store: DataStore<SavedPlaces>) {
    val saved: Flow<SavedPlaces> = store.data

    suspend fun snapshot(): SavedPlaces = store.data.first()

    suspend fun add(place: Place) {
        store.updateData { s ->
            val exists = s.places.any { it.id == place.id }
            s.copy(places = if (exists) s.places else s.places + place, selectedId = place.id)
        }
    }

    suspend fun remove(id: String) {
        store.updateData { s ->
            val places = s.places.filterNot { it.id == id }
            s.copy(places = places, selectedId = if (s.selectedId == id) null else s.selectedId)
        }
    }

    suspend fun move(fromIndex: Int, toIndex: Int) {
        store.updateData { s ->
            if (fromIndex !in s.places.indices || toIndex !in s.places.indices) return@updateData s
            val list = s.places.toMutableList()
            list.add(toIndex, list.removeAt(fromIndex))
            s.copy(places = list)
        }
    }

    suspend fun select(id: String) {
        store.updateData { it.copy(selectedId = id) }
    }

    suspend fun setDeviceLocation(place: Place) {
        require(place.isCurrentLocation)
        store.updateData { it.copy(lastDeviceLocation = place) }
    }

    suspend fun setFollowDeviceLocation(enabled: Boolean) {
        store.updateData { it.copy(followDeviceLocation = enabled) }
    }
}
