package com.monthcalendar.widget.weather

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

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
            "&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
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

        val dTime = daily.getJSONArray("time")
        val dCode = daily.getJSONArray("weather_code")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dSunrise = daily.optJSONArray("sunrise")
        val dSunset = daily.optJSONArray("sunset")
        val dPrecip = daily.optJSONArray("precipitation_probability_max")
        val dUv = daily.optJSONArray("uv_index_max")
        val forecast = (0 until dTime.length()).map { i ->
            DailyForecast(
                date = LocalDate.parse(dTime.getString(i)),
                code = dCode.getInt(i),
                max = dMax.getDouble(i),
                min = dMin.getDouble(i),
                sunrise = dSunrise?.optString(i)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                sunset = dSunset?.optString(i)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                precipProb = dPrecip?.optInt(i, 0) ?: 0,
                uvMax = dUv?.optDouble(i, 0.0) ?: 0.0,
            )
        }

        // Hourly, trimmed to the next 12 hours from "now" (in the location's tz).
        val nowLocal = runCatching { LocalDateTime.parse(current.getString("time")) }.getOrNull()
        val hourly = ArrayList<HourForecast>()
        root.optJSONObject("hourly")?.let { h ->
            val hTime = h.getJSONArray("time")
            val hTemp = h.getJSONArray("temperature_2m")
            val hCode = h.getJSONArray("weather_code")
            val hPrecip = h.optJSONArray("precipitation_probability")
            val hDay = h.optJSONArray("is_day")
            for (i in 0 until hTime.length()) {
                val t = runCatching { LocalDateTime.parse(hTime.getString(i)) }.getOrNull() ?: continue
                if (nowLocal != null && t.isBefore(nowLocal.withMinute(0))) continue
                hourly += HourForecast(
                    time = t,
                    temp = hTemp.getDouble(i),
                    code = hCode.getInt(i),
                    isDay = (hDay?.optInt(i, 1) ?: 1) == 1,
                    precipProb = hPrecip?.optInt(i, 0) ?: 0,
                )
                if (hourly.size >= 12) break
            }
        }

        return WeatherData(
            locationName = cfg.locationName,
            temp = current.getDouble("temperature_2m"),
            apparentTemp = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            humidity = current.optInt("relative_humidity_2m", 0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            windMax = daily.optJSONArray("wind_speed_10m_max")?.optDouble(0, 0.0) ?: 0.0,
            uvMax = forecast.firstOrNull()?.uvMax ?: 0.0,
            metric = cfg.metric,
            hourly = hourly,
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
