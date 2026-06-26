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

    suspend fun cachedJson(): Pair<String, Long>? {
        val p = context.weatherStore.data.first()
        val json = p[Keys.CACHE_JSON] ?: return null
        return json to (p[Keys.CACHE_TIME] ?: 0L)
    }

    suspend fun saveCache(json: String, time: Long) {
        context.weatherStore.edit { p ->
            p[Keys.CACHE_JSON] = json
            p[Keys.CACHE_TIME] = time
        }
    }
}
