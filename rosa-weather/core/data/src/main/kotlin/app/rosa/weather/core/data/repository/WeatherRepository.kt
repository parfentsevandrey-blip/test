package app.rosa.weather.core.data.repository

import androidx.datastore.core.DataStore
import app.rosa.weather.core.data.network.OpenMeteoClient
import app.rosa.weather.core.data.store.ForecastCache
import app.rosa.weather.core.data.util.WallClock
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

sealed interface RefreshResult {
    val forecast: Forecast?

    /** Cache was fresh enough; no request made. */
    data class UpToDate(override val forecast: Forecast) : RefreshResult
    data class Updated(override val forecast: Forecast) : RefreshResult
    data class Failed(val error: Throwable, override val forecast: Forecast?) : RefreshResult
}

@Singleton
class WeatherRepository @Inject constructor(
    private val client: OpenMeteoClient,
    private val cache: DataStore<ForecastCache>,
    private val clock: WallClock,
) {
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())

    /** Place ids with a request in flight — drives spinners in the app and on widgets. */
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    val forecasts: Flow<Map<String, Forecast>> = cache.data.map { it.byPlaceId }

    fun forecast(placeId: String): Flow<Forecast?> =
        cache.data.map { it.byPlaceId[placeId] }.distinctUntilChanged()

    suspend fun cached(placeId: String): Forecast? = cache.data.first().byPlaceId[placeId]

    /**
     * Fetches a fresh forecast unless the cached one is younger than [maxAgeSeconds] *and* was
     * fetched for (roughly) the same coordinates.
     */
    suspend fun refresh(place: Place, maxAgeSeconds: Long = 0): RefreshResult {
        val existing = cached(place.id)
        val now = clock.nowEpochSeconds()
        if (existing != null && maxAgeSeconds > 0 &&
            existing.ageSeconds(now) < maxAgeSeconds &&
            distanceKm(existing.latitude, existing.longitude, place.latitude, place.longitude) < SAME_PLACE_KM
        ) {
            return RefreshResult.UpToDate(existing)
        }
        _refreshing.update { it + place.id }
        return try {
            val fresh = client.forecast(place, fetchedAt = now)
            cache.updateData { it.copy(byPlaceId = it.byPlaceId + (place.id to fresh)) }
            RefreshResult.Updated(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RefreshResult.Failed(e, existing)
        } finally {
            _refreshing.update { it - place.id }
        }
    }

    /** Drops forecasts for places nobody follows any more. */
    suspend fun retainOnly(placeIds: Set<String>) {
        cache.updateData { c ->
            if (c.byPlaceId.keys.all { it in placeIds }) c else c.copy(byPlaceId = c.byPlaceId.filterKeys { it in placeIds })
        }
    }

    companion object {
        /** Open-Meteo snaps to its model grid, so "same place" needs a generous radius. */
        const val SAME_PLACE_KM = 8.0

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
