package app.quire.weather

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Where the forecast comes from.
 *
 * Open-Meteo, because it needs no account and no key: an app that asks somebody to go and register
 * for an API key before it can tell them whether to take a coat has already failed. Nothing is
 * sent but a latitude and a longitude, rounded — see [round] — and nothing identifying goes with
 * them.
 *
 * [fetcher] is the seam. Parsing is the part that can be wrong in interesting ways, so it is a
 * pure function over a string and the tests exercise it against real recorded responses instead of
 * a network.
 */
object WeatherRepository {

    /** How long a forecast is worth showing before it is worth fetching again. */
    const val FRESH_FOR_MILLIS = 45L * 60L * 1000L

    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val CURRENT =
        "temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m"
    private const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
            "sunrise,sunset"
    private const val HOURLY = "temperature_2m,weather_code,precipitation_probability,is_day"
    private const val DAYS = 6
    private const val TIMEOUT_MILLIS = 12_000

    /**
     * Coordinates are sent to two decimal places — about a kilometre.
     *
     * That is finer than the weather and coarser than a person: it is enough to pick the right
     * side of a mountain and not enough to pick a house.
     */
    fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    fun url(latitude: Double, longitude: Double): String = buildString {
        append(ENDPOINT)
        append("?latitude=").append(round(latitude))
        append("&longitude=").append(round(longitude))
        append("&current=").append(CURRENT)
        append("&daily=").append(DAILY)
        append("&hourly=").append(HOURLY)
        append("&timezone=auto")
        append("&forecast_days=").append(DAYS)
    }

    /**
     * Fetches and parses, or throws.
     *
     * Callers are expected to have something cached to fall back on; a weather app that shows
     * nothing because a train went into a tunnel is worse than one showing this morning's number
     * with the time it was taken.
     */
    fun fetch(
        latitude: Double,
        longitude: Double,
        place: String,
        now: Long,
        fetcher: (String) -> String = ::get,
    ): Forecast = parse(fetcher(url(latitude, longitude)), place, latitude, longitude, now)

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("weather: HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Turns one response into a forecast.
     *
     * Every field is read by name against the arrays the request asked for rather than by index
     * into a fixed shape, so a provider that adds a column does not silently shift the readings by
     * one. A day whose row is short is dropped rather than guessed at.
     */
    fun parse(
        body: String,
        place: String,
        latitude: Double,
        longitude: Double,
        now: Long,
    ): Forecast {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val conditions = Conditions(
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            sky = Sky.of(current.optInt("weather_code", 3)),
            day = current.optInt("is_day", 1) == 1,
            humidity = current.optInt("relative_humidity_2m", -1),
            wind = current.optDouble("wind_speed_10m", 0.0),
        )

        // The hours are optional: a provider that stops sending them costs the screen a strip,
        // not a forecast.
        val hours = ArrayList<HourForecast>()
        root.optJSONObject("hourly")?.let { hourly ->
            val times = hourly.optJSONArray("time")
            val temps = hourly.optJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val chance = hourly.optJSONArray("precipitation_probability")
            val daylight = hourly.optJSONArray("is_day")
            if (times != null && temps != null && codes != null) {
                for (index in 0 until times.length()) {
                    if (index >= temps.length() || index >= codes.length()) break
                    hours += HourForecast(
                        time = LocalDateTime.parse(times.getString(index)),
                        temperature = temps.getDouble(index),
                        sky = Sky.of(codes.getInt(index)),
                        day = (daylight?.optInt(index, 1) ?: 1) == 1,
                        rain = chance?.optInt(index, 0) ?: 0,
                    )
                }
            }
        }

        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val sunrise = daily.optJSONArray("sunrise")
        val sunset = daily.optJSONArray("sunset")

        val days = ArrayList<DayForecast>(dates.length())
        for (index in 0 until dates.length()) {
            if (index >= codes.length() || index >= highs.length() || index >= lows.length()) break
            days += DayForecast(
                date = LocalDate.parse(dates.getString(index)),
                sky = Sky.of(codes.getInt(index)),
                high = highs.getDouble(index),
                low = lows.getDouble(index),
                rain = rain?.optInt(index, 0) ?: 0,
                sunrise = sunrise?.optString(index)?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                sunset = sunset?.optString(index)?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            )
        }

        return Forecast(
            place = place,
            latitude = latitude,
            longitude = longitude,
            now = conditions,
            days = days,
            fetched = now,
            hours = hours,
        )
    }

    /**
     * A temperature as it is written, in whole degrees.
     *
     * Rounded rather than truncated, because a widget that says 12° when it is 12.8° is wrong in
     * the direction people notice.
     */
    fun degrees(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%d°", Math.round(value))
}
