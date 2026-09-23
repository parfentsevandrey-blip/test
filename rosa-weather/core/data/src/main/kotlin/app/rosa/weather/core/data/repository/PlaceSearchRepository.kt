package app.rosa.weather.core.data.repository

import app.rosa.weather.core.data.network.OpenMeteoClient
import app.rosa.weather.core.data.util.suspendRunCatching
import app.rosa.weather.core.model.Place
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceSearchRepository @Inject constructor(private val client: OpenMeteoClient) {
    suspend fun search(query: String, locale: Locale = Locale.getDefault()): Result<List<Place>> =
        suspendRunCatching { client.search(query, language = locale.language.ifBlank { "en" }) }
}
