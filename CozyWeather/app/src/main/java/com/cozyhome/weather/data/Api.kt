package com.cozyhome.weather.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,pressure_msl",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 6,
        @Query("language") language: String = "ru",
    ): GeoResponse
}

object ApiFactory {
    val json: Json = Json { ignoreUnknownKeys = true }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val weatherApi: WeatherApi by lazy { retrofit("https://api.open-meteo.com/").create(WeatherApi::class.java) }
    val geocodingApi: GeocodingApi by lazy { retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java) }
}
