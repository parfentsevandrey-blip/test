package app.papersky.weather.core.data

import app.papersky.weather.core.model.Current
import app.papersky.weather.core.model.Day
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Hour
import app.papersky.weather.core.model.Nowcast
import app.papersky.weather.core.model.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Open-Meteo (https://open-meteo.com) — free, key-less forecast and geocoding API, CC BY 4.0.
 * Everything is requested in metric units with unix timestamps; conversion happens at display time.
 */
class OpenMeteoApi(private val client: HttpClient) {

    suspend fun forecast(latitude: Double, longitude: Double): ForecastDto =
        client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", "%.4f".format(java.util.Locale.ROOT, latitude))
            parameter("longitude", "%.4f".format(java.util.Locale.ROOT, longitude))
            parameter("current", CURRENT_FIELDS)
            parameter("hourly", HOURLY_FIELDS)
            parameter("daily", DAILY_FIELDS)
            parameter("minutely_15", "precipitation")
            parameter("forecast_minutely_15", 12)
            parameter("timezone", "auto")
            parameter("forecast_days", 10)
            parameter("past_hours", 3)
            parameter("wind_speed_unit", "ms")
            parameter("timeformat", "unixtime")
        }.body()

    suspend fun search(query: String, language: String, count: Int = 12): List<GeoResultDto> =
        client.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", query)
            parameter("count", count)
            parameter("language", language)
            parameter("format", "json")
        }.body<GeoSearchDto>().results.orEmpty()

    private companion object {
        const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility," +
            "uv_index,dew_point_2m"
        const val HOURLY_FIELDS = "temperature_2m,apparent_temperature,precipitation_probability,precipitation," +
            "weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,relative_humidity_2m,is_day," +
            "uv_index,visibility,pressure_msl"
        const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum," +
            "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant," +
            "uv_index_max,daylight_duration"
    }
}

@Serializable
data class ForecastDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "UTC",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null,
)

@Serializable
data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Double? = null,
    @SerialName("apparent_temperature") val apparent: Double? = null,
    @SerialName("is_day") val isDay: Int = 1,
    val precipitation: Double? = null,
    @SerialName("weather_code") val code: Int = 0,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    @SerialName("pressure_msl") val pressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    val visibility: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
)

@Serializable
data class HourlyDto(
    val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("apparent_temperature") val apparent: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipProbability: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val code: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressure: List<Double?> = emptyList(),
)

@Serializable
data class DailyDto(
    val time: List<Long>,
    @SerialName("weather_code") val code: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
    val sunrise: List<Long?> = emptyList(),
    val sunset: List<Long?> = emptyList(),
    @SerialName("precipitation_sum") val precipSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipProbability: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val gustMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirection: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvMax: List<Double?> = emptyList(),
    @SerialName("daylight_duration") val daylight: List<Double?> = emptyList(),
)

@Serializable
data class Minutely15Dto(
    val time: List<Long> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
)

@Serializable
data class GeoSearchDto(val results: List<GeoResultDto>? = null)

@Serializable
data class GeoResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val timezone: String? = null,
    val population: Long? = null,
) {
    fun toPlace(): Place = Place(
        id = "om-$id",
        name = name,
        region = admin1?.takeIf { it != name },
        country = country,
        latitude = latitude,
        longitude = longitude,
        timezone = timezone,
    )
}

/** Converts the column-oriented response into rows; tolerant of nulls and ragged arrays. */
fun ForecastDto.toForecast(placeId: String, fetchedAt: Long): Forecast {
    val c = current
    val h = hourly
    val hours = h.time.indices.mapNotNull { i ->
        val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
        Hour(
            time = h.time[i],
            temperature = temp,
            feelsLike = h.apparent.getOrNull(i) ?: temp,
            code = h.code.getOrNull(i) ?: 0,
            precipProbability = h.precipProbability.getOrNull(i)?.roundToInt() ?: 0,
            precipitation = h.precipitation.getOrNull(i) ?: 0.0,
            cloudCover = h.cloudCover.getOrNull(i)?.roundToInt() ?: 0,
            windSpeed = h.windSpeed.getOrNull(i) ?: 0.0,
            windDirection = h.windDirection.getOrNull(i)?.roundToInt() ?: 0,
            windGusts = h.windGusts.getOrNull(i) ?: 0.0,
            humidity = h.humidity.getOrNull(i)?.roundToInt() ?: 0,
            isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
            uvIndex = h.uvIndex.getOrNull(i) ?: 0.0,
            visibility = h.visibility.getOrNull(i),
            pressure = h.pressure.getOrNull(i) ?: c.pressure ?: 1013.0,
        )
    }
    val d = daily
    val days = d.time.indices.mapNotNull { i ->
        val max = d.tempMax.getOrNull(i) ?: return@mapNotNull null
        val min = d.tempMin.getOrNull(i) ?: return@mapNotNull null
        Day(
            date = d.time[i],
            code = d.code.getOrNull(i) ?: 0,
            tempMax = max,
            tempMin = min,
            sunrise = d.sunrise.getOrNull(i) ?: 0,
            sunset = d.sunset.getOrNull(i) ?: 0,
            precipSum = d.precipSum.getOrNull(i) ?: 0.0,
            precipProbability = d.precipProbability.getOrNull(i)?.roundToInt() ?: 0,
            windMax = d.windMax.getOrNull(i) ?: 0.0,
            gustMax = d.gustMax.getOrNull(i) ?: 0.0,
            windDirection = d.windDirection.getOrNull(i)?.roundToInt() ?: 0,
            uvMax = d.uvMax.getOrNull(i) ?: 0.0,
            daylightSeconds = d.daylight.getOrNull(i) ?: 0.0,
        )
    }
    val nowcast = minutely15?.let { m ->
        m.time.indices.map { i -> Nowcast(m.time[i], m.precipitation.getOrNull(i) ?: 0.0) }
    }.orEmpty()

    return Forecast(
        placeId = placeId,
        latitude = latitude,
        longitude = longitude,
        timezone = timezone,
        utcOffsetSeconds = utcOffsetSeconds,
        fetchedAt = fetchedAt,
        current = Current(
            time = c.time,
            temperature = c.temperature,
            feelsLike = c.apparent ?: c.temperature,
            humidity = c.humidity?.roundToInt() ?: 0,
            isDay = c.isDay == 1,
            code = c.code,
            cloudCover = c.cloudCover?.roundToInt() ?: 0,
            pressure = c.pressure ?: 1013.0,
            windSpeed = c.windSpeed ?: 0.0,
            windDirection = c.windDirection?.roundToInt() ?: 0,
            windGusts = c.windGusts ?: 0.0,
            precipitation = c.precipitation ?: 0.0,
            visibility = c.visibility,
            uvIndex = c.uvIndex,
            dewPoint = c.dewPoint,
        ),
        hourly = hours,
        daily = days,
        nowcast = nowcast,
    )
}
