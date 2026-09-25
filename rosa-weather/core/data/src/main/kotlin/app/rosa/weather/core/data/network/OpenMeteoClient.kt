package app.rosa.weather.core.data.network

import app.rosa.weather.core.model.AirQuality
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Thin client over the free, key-less Open-Meteo APIs: forecast (+15-minute nowcast),
 * air quality and geocoding.
 */
@Singleton
class OpenMeteoClient @Inject internal constructor(
    // Built on first use: a widget woken to draw from the cache never pays for the network stack.
    private val client: dagger.Lazy<HttpClient>,
) {
    internal constructor(http: HttpClient) : this(dagger.Lazy { http })

    private val http: HttpClient get() = client.get()

    suspend fun forecast(place: Place, fetchedAt: Long): Forecast = coroutineScope {
        val air = async { runCatching { airQuality(place) }.getOrNull() }
        val response: ForecastResponse = http.get(FORECAST_URL) {
            parameter("latitude", place.latitude)
            parameter("longitude", place.longitude)
            parameter("current", CURRENT)
            parameter("hourly", HOURLY)
            parameter("daily", DAILY)
            parameter("minutely_15", "precipitation,weather_code")
            parameter("forecast_minutely_15", 12)
            parameter("past_hours", 3)
            parameter("forecast_hours", 96)
            parameter("forecast_days", 10)
            parameter("wind_speed_unit", "ms")
            parameter("timezone", "auto")
            parameter("timeformat", "unixtime")
        }.body()
        ForecastMapper.map(place.id, response, air.await(), fetchedAt)
    }

    private suspend fun airQuality(place: Place): AirQuality? {
        val response: AirQualityResponse = http.get(AIR_URL) {
            parameter("latitude", place.latitude)
            parameter("longitude", place.longitude)
            parameter("current", "european_aqi,us_aqi,pm2_5,pm10,ozone")
            parameter("timezone", "auto")
            parameter("timeformat", "unixtime")
        }.body()
        return ForecastMapper.mapAir(response)
    }

    suspend fun search(query: String, language: String, count: Int = 12): List<Place> {
        if (query.isBlank()) return emptyList()
        val response: GeocodingResponse = http.get(GEOCODING_URL) {
            parameter("name", query.trim())
            parameter("count", count)
            parameter("language", language)
            parameter("format", "json")
        }.body()
        return response.results.map(ForecastMapper::mapPlace)
    }

    private companion object {
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

        const val CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
            "uv_index,visibility,dew_point_2m"
        const val HOURLY = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
            "weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day," +
            "relative_humidity_2m,visibility,pressure_msl"
        const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max," +
            "apparent_temperature_min,sunrise,sunset,precipitation_sum,precipitation_probability_max," +
            "wind_speed_10m_max,wind_direction_10m_dominant,uv_index_max,daylight_duration"
    }
}
