package app.papersky.weather.core.data

import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first forecast access: readers always observe the cache, [refresh] writes into it.
 * Concurrent refreshes of the same place are coalesced.
 */
class WeatherRepository(
    private val api: OpenMeteoApi,
    private val store: ForecastStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val locks = HashMap<String, Mutex>()

    suspend fun ensureLoaded() = store.ensureLoaded()

    fun observe(placeId: String): Flow<Forecast?> = store.observe(placeId)

    fun peek(placeId: String): Forecast? = store.peek(placeId)

    /**
     * Downloads a fresh forecast unless the cached one is younger than [maxAgeMillis].
     * Returns the forecast now in cache, or the failure if nothing could be fetched.
     */
    suspend fun refresh(place: Place, maxAgeMillis: Long = 0): Result<Forecast> {
        val lock = synchronized(locks) { locks.getOrPut(place.id) { Mutex() } }
        return lock.withLock {
            store.ensureLoaded()
            val cached = store.peek(place.id)
            val fresh = cached != null && clock() - cached.fetchedAt < maxAgeMillis &&
                cached.latitude.near(place.latitude) && cached.longitude.near(place.longitude)
            if (fresh) return@withLock Result.success(cached)

            _refreshing.update { it + place.id }
            try {
                val dto = api.forecast(place.latitude, place.longitude)
                val forecast = dto.toForecast(place.id, clock())
                store.put(forecast)
                Result.success(forecast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _refreshing.update { it - place.id }
            }
        }
    }

    suspend fun forget(placeId: String) = store.remove(placeId)

    private fun Double.near(other: Double) = kotlin.math.abs(this - other) < 0.05
}
