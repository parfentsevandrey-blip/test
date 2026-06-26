package com.monthcalendar.widget.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.weatherStore: DataStore<Preferences> by preferencesDataStore(name = "weather")

/** Chosen location + units. */
data class WeatherConfig(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    val metric: Boolean = true,
) {
    val isConfigured: Boolean get() = latitude != null && longitude != null
}

/**
 * Persists the weather location/units config and a cached copy of the last
 * Open-Meteo response (raw JSON), so the widget renders instantly and the
 * worker refreshes in the background.
 */
class WeatherStore(private val context: Context) {

    private object Keys {
        val LAT = doublePreferencesKey("lat")
        val LON = doublePreferencesKey("lon")
        val NAME = stringPreferencesKey("name")
        val METRIC = booleanPreferencesKey("metric")
        val CACHE_JSON = stringPreferencesKey("cache_json")
        val CACHE_TIME = longPreferencesKey("cache_time")
        val CACHE_METRIC = booleanPreferencesKey("cache_metric")
    }

    suspend fun config(): WeatherConfig {
        val p = context.weatherStore.data.first()
        return WeatherConfig(
            latitude = p[Keys.LAT],
            longitude = p[Keys.LON],
            locationName = p[Keys.NAME].orEmpty(),
            metric = p[Keys.METRIC] ?: true,
        )
    }

    suspend fun saveConfig(config: WeatherConfig) {
        context.weatherStore.edit { p ->
            config.latitude?.let { p[Keys.LAT] = it }
            config.longitude?.let { p[Keys.LON] = it }
            p[Keys.NAME] = config.locationName
            p[Keys.METRIC] = config.metric
        }
    }

    /** Cached raw response: (json, fetchedAtMillis, metricUnitsUsed). */
    suspend fun cachedJson(): Triple<String, Long, Boolean>? {
        val p = context.weatherStore.data.first()
        val json = p[Keys.CACHE_JSON] ?: return null
        return Triple(json, p[Keys.CACHE_TIME] ?: 0L, p[Keys.CACHE_METRIC] ?: true)
    }

    suspend fun saveCache(json: String, time: Long, metric: Boolean) {
        context.weatherStore.edit { p ->
            p[Keys.CACHE_JSON] = json
            p[Keys.CACHE_TIME] = time
            p[Keys.CACHE_METRIC] = metric
        }
    }

    /** Drop the cached forecast (e.g. after the city changes). */
    suspend fun clearCache() {
        context.weatherStore.edit { p ->
            p.remove(Keys.CACHE_JSON)
            p.remove(Keys.CACHE_TIME)
            p.remove(Keys.CACHE_METRIC)
        }
    }
}
