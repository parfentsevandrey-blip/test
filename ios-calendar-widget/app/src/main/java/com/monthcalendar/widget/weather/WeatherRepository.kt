package com.monthcalendar.widget.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class GeoResult(
    val name: String,
    val country: String,
    val admin1: String,
    val latitude: Double,
    val longitude: Double,
) {
    val display: String get() = listOf(name, admin1, country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Talks to Open-Meteo — a free, key-less weather provider (forecast +
 * geocoding). All calls run on IO and fail soft (return null / empty list) so
 * the widget can fall back to its cache.
 */
object WeatherRepository {

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"

    /** Read the cached snapshot (no network). */
    suspend fun cached(context: Context): WeatherData? {
        val store = WeatherStore(context)
        val cfg = store.config()
        val cache = store.cachedJson() ?: return null
        return runCatching { parse(cache.first, cfg, cache.second) }.getOrNull()
    }

    /** Fetch a fresh snapshot from Open-Meteo and cache it. */
    suspend fun refresh(context: Context, nowMillis: Long): WeatherData? = withContext(Dispatchers.IO) {
        val store = WeatherStore(context)
        val cfg = store.config()
        if (!cfg.isConfigured) return@withContext null

        val tempUnit = if (cfg.metric) "celsius" else "fahrenheit"
        val windUnit = if (cfg.metric) "kmh" else "mph"
        val url = "$FORECAST?latitude=${cfg.latitude}&longitude=${cfg.longitude}" +
            "&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=7" +
            "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit"

        val body = httpGet(url) ?: return@withContext null
        store.saveCache(body, nowMillis)
        runCatching { parse(body, cfg, nowMillis) }.getOrNull()
    }

    /** Free-text city search (Open-Meteo geocoding). */
    suspend fun geocode(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$GEOCODE?name=$q&count=6&language=ru&format=json"
        val body = httpGet(url) ?: return@withContext emptyList()
        runCatching {
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList<GeoResult>()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GeoResult(
                    name = o.optString("name"),
                    country = o.optString("country"),
                    admin1 = o.optString("admin1"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(body: String, cfg: WeatherConfig, time: Long): WeatherData {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")

        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val forecast = (0 until times.length()).map { i ->
            DailyForecast(
                date = LocalDate.parse(times.getString(i)),
                code = codes.getInt(i),
                max = maxs.getDouble(i),
                min = mins.getDouble(i),
            )
        }

        return WeatherData(
            locationName = cfg.locationName,
            temp = current.getDouble("temperature_2m"),
            apparentTemp = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            code = current.getInt("weather_code"),
            humidity = current.optInt("relative_humidity_2m", 0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            metric = cfg.metric,
            daily = forecast,
            updatedAt = time,
        )
    }

    private fun httpGet(urlStr: String): String? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
