package com.cozyhome.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cozy_weather")

class WeatherRepository(private val context: Context) {

    private val placeKey = stringPreferencesKey("place_json")
    private val snapshotKey = stringPreferencesKey("snapshot_json")
    private val json = ApiFactory.json

    val snapshotFlow: Flow<WeatherSnapshot?> = context.dataStore.data.map { prefs ->
        prefs[snapshotKey]?.let { runCatching { json.decodeFromString<WeatherSnapshot>(it) }.getOrNull() }
    }

    suspend fun cachedSnapshot(): WeatherSnapshot? = snapshotFlow.first()

    suspend fun savedPlace(): Place? = context.dataStore.data.first()[placeKey]
        ?.let { runCatching { json.decodeFromString<Place>(it) }.getOrNull() }

    suspend fun savePlace(place: Place) {
        context.dataStore.edit { it[placeKey] = json.encodeToString(Place.serializer(), place) }
    }

    /** Fetches a fresh forecast, persists it for the app + widget, and returns it. */
    suspend fun refresh(place: Place): WeatherSnapshot = withContext(Dispatchers.IO) {
        val forecast = ApiFactory.weatherApi.forecast(place.latitude, place.longitude)
        val snapshot = WeatherSnapshot(place, forecast, System.currentTimeMillis())
        context.dataStore.edit {
            it[placeKey] = json.encodeToString(Place.serializer(), place)
            it[snapshotKey] = json.encodeToString(WeatherSnapshot.serializer(), snapshot)
        }
        snapshot
    }

    suspend fun searchCities(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        ApiFactory.geocodingApi.search(query).results
    }
}
