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
import java.util.Locale

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

    /** Read the cached snapshot (no network), labelled with the units it was fetched in. */
    suspend fun cached(context: Context): WeatherData? {
        val store = WeatherStore(context)
        val cfg = store.config()
        val cache = store.cachedJson() ?: return null
        return runCatching { parse(cache.first, cfg.locationName, cache.third, cache.second) }.getOrNull()
    }

    /** Fetch a fresh snapshot from Open-Meteo and cache it. Returns null on failure. */
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
        store.saveCache(body, nowMillis, cfg.metric)
        runCatching { parse(body, cfg.locationName, cfg.metric, nowMillis) }.getOrNull()
    }

    /** Free-text city search (Open-Meteo geocoding), de-duplicated. */
    suspend fun geocode(query: String): List<GeoResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val url = "$GEOCODE?name=$q&count=8&language=$lang&format=json"
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
            }.distinctBy { it.display.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun parse(body: String, locationName: String, metric: Boolean, time: Long): WeatherData {
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
        // Guard against any array being shorter than `time` (malformed response).
        val dCount = minOf(dTime.length(), dCode.length(), dMax.length(), dMin.length())
        val forecast = (0 until dCount).map { i ->
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
            locationName = locationName,
            temp = current.getDouble("temperature_2m"),
            apparentTemp = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            humidity = current.optInt("relative_humidity_2m", 0),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            windMax = daily.optJSONArray("wind_speed_10m_max")?.optDouble(0, 0.0) ?: 0.0,
            uvMax = forecast.firstOrNull()?.uvMax ?: 0.0,
            metric = metric,
            hourly = hourly,
            daily = forecast,
            updatedAt = time,
        )
    }

    /**
     * Resilient GET: a real product fails soft, not on the first hiccup. Sets a
     * User-Agent, retries on IOException / 5xx / 429 with exponential backoff
     * (honouring Retry-After), reads the error stream so a 4xx isn't a silent
     * null. Returns null only after exhausting attempts.
     */
    private fun httpGet(urlStr: String, maxAttempts: Int = 3): String? {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val code = conn.responseCode
                when {
                    code in 200..299 ->
                        return conn.inputStream.bufferedReader().use { it.readText() }
                    (code == 429 || code in 500..599) && attempt < maxAttempts -> {
                        conn.errorStream?.close()
                        val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull()
                        backoff(attempt, retryAfter)
                    }
                    else -> {
                        conn.errorStream?.close()
                        return null
                    }
                }
            } catch (io: java.io.IOException) {
                if (attempt >= maxAttempts) return null
                backoff(attempt, null)
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun backoff(attempt: Int, retryAfterSec: Long?) {
        val base = retryAfterSec?.let { it * 1000 } ?: (300L * (1 shl (attempt - 1)))
        val jitter = (attempt * 70L) % 250L
        try {
            Thread.sleep((base + jitter).coerceAtMost(8_000L))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private const val USER_AGENT = "CalendarWeatherWidget/1.0 (Android; Open-Meteo client)"
}
