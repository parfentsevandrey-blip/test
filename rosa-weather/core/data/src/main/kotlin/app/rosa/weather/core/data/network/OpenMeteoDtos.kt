package app.rosa.weather.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* Wire formats of the Open-Meteo APIs (requested with `timeformat=unixtime`). */

@Serializable
internal data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val timezone: String = "GMT",
    val current: CurrentDto? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null,
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null,
)

@Serializable
internal data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Double? = null,
    @SerialName("pressure_msl") val pressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    val visibility: Double? = null,
    @SerialName("dew_point_2m") val dewPoint: Double? = null,
)

@Serializable
internal data class HourlyDto(
    val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressure: List<Double?> = emptyList(),
)

@Serializable
internal data class DailyDto(
    val time: List<Long> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_min") val apparentMin: List<Double?> = emptyList(),
    val sunrise: List<Long?> = emptyList(),
    val sunset: List<Long?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
    @SerialName("daylight_duration") val daylightDuration: List<Double?> = emptyList(),
)

@Serializable
internal data class Minutely15Dto(
    val time: List<Long> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
internal data class AirQualityResponse(val current: AirCurrentDto? = null)

@Serializable
internal data class AirCurrentDto(
    val time: Long,
    @SerialName("european_aqi") val europeanAqi: Double? = null,
    @SerialName("us_aqi") val usAqi: Double? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
)

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
internal data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val timezone: String? = null,
    val population: Long? = null,
    @SerialName("feature_code") val featureCode: String? = null,
)
