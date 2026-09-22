package app.papersky.weather.core.data

import androidx.datastore.core.DataStore
import app.papersky.weather.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class PlacesData(
    val saved: List<Place> = emptyList(),
    /** Last resolved device location, shown as the "here" place. */
    val device: Place? = null,
)

class PlacesRepository(private val store: DataStore<PlacesData>) {

    val data: Flow<PlacesData> = store.data

    /** Device location first (if known), then saved places in the user's order. */
    val all: Flow<List<Place>> = store.data.map { d -> listOfNotNull(d.device) + d.saved }

    suspend fun snapshot(): PlacesData = store.data.first()

    suspend fun resolve(placeId: String): Place? {
        val d = snapshot()
        return if (placeId == Place.HERE) d.device else d.saved.firstOrNull { it.id == placeId }
    }

    fun observe(placeId: String): Flow<Place?> = store.data.map { d ->
        if (placeId == Place.HERE) d.device else d.saved.firstOrNull { it.id == placeId }
    }

    suspend fun add(place: Place) {
        store.updateData { d ->
            if (d.saved.any { it.id == place.id }) d else d.copy(saved = d.saved + place)
        }
    }

    suspend fun remove(placeId: String) {
        store.updateData { d -> d.copy(saved = d.saved.filterNot { it.id == placeId }) }
    }

    suspend fun move(fromIndex: Int, toIndex: Int) {
        store.updateData { d ->
            if (fromIndex !in d.saved.indices || toIndex !in d.saved.indices) return@updateData d
            val list = d.saved.toMutableList()
            list.add(toIndex, list.removeAt(fromIndex))
            d.copy(saved = list)
        }
    }

    suspend fun updateDevice(place: Place) {
        store.updateData { d -> d.copy(device = place.copy(id = Place.HERE, isDeviceLocation = true)) }
    }
}
