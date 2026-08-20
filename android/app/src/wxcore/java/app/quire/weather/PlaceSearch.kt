package app.quire.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A place somebody named, rather than one a satellite worked out. */
class Place(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Moscow · Russia", with whatever middle term the provider knew. */
    fun describe(): String = listOfNotNull(
        region?.takeIf { it.isNotBlank() && it != name },
        country?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * Looking a place up by name.
 *
 * The point of this is that the weather app can be useful to somebody who will not give it a
 * location — which is a perfectly reasonable position, and one an app should be able to take yes
 * for an answer on. Naming a city is a stated intention; a coordinate read off the device is an
 * ongoing one, and the two are not the same thing to give away.
 *
 * Open-Meteo's geocoding endpoint, same as the forecast: no key, no account. Only the typed
 * query leaves the device.
 */
object PlaceSearch {

    private const val ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
    private const val RESULTS = 8
    private const val TIMEOUT_MILLIS = 12_000

    /** Below this a query matches half the world and the answer is noise. */
    const val MIN_QUERY = 2

    fun url(query: String, language: String): String {
        val name = URLEncoder.encode(query.trim(), "UTF-8")
        return "$ENDPOINT?name=$name&count=$RESULTS&language=$language&format=json"
    }

    fun search(
        query: String,
        language: String = Locale.getDefault().language,
        fetcher: (String) -> String = ::get,
    ): List<Place> {
        if (query.trim().length < MIN_QUERY) return emptyList()
        return parse(fetcher(url(query, language)))
    }

    /**
     * A response with no matches has no `results` key at all rather than an empty array, which is
     * the one thing about this endpoint worth writing down.
     */
    fun parse(body: String): List<Place> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val row = results.optJSONObject(index) ?: return@mapNotNull null
            val name = row.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Place(
                name = name,
                region = row.optString("admin1").takeIf { it.isNotBlank() },
                country = row.optString("country").takeIf { it.isNotBlank() },
                latitude = row.optDouble("latitude", Double.NaN),
                longitude = row.optDouble("longitude", Double.NaN),
            ).takeUnless { it.latitude.isNaN() || it.longitude.isNaN() }
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("geocoding: HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
