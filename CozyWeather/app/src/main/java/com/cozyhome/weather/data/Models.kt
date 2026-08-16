package com.cozyhome.weather.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    @SerialName("current") val current: CurrentWeather,
    @SerialName("hourly") val hourly: HourlyBlock,
    @SerialName("daily") val daily: DailyBlock,
)

@Serializable
data class CurrentWeather(
    @SerialName("time") val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val feelsLike: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("pressure_msl") val pressure: Double,
    @SerialName("is_day") val isDay: Int,
)

@Serializable
data class HourlyBlock(
    @SerialName("time") val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
)

@Serializable
data class DailyBlock(
    @SerialName("time") val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerialName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerialName("sunrise") val sunrise: List<String> = emptyList(),
    @SerialName("sunset") val sunset: List<String> = emptyList(),
)

@Serializable
data class GeoResponse(
    @SerialName("results") val results: List<GeoPlace> = emptyList(),
)

@Serializable
data class GeoPlace(
    @SerialName("id") val id: Long = 0,
    @SerialName("name") val name: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("country") val country: String? = null,
    @SerialName("admin1") val admin1: String? = null,
)

@Serializable
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        val DEFAULT = Place("Москва", 55.7558, 37.6173)
    }
}

/** Everything the app and the widget need to render, cached as one JSON blob. */
@Serializable
data class WeatherSnapshot(
    val place: Place,
    val forecast: ForecastResponse,
    val fetchedAtEpochMs: Long,
)
