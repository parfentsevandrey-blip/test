package app.quire.weather

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The last forecast, kept where a widget can reach it without a network.
 *
 * A home-screen widget is painted at moments nobody chose — a launcher restarting, a wallpaper
 * changing, midnight — and none of them are a good time to wait on a request. So the card is
 * always drawn from here, and fetching is a separate thing that happens on its own schedule and
 * writes here when it succeeds.
 *
 * It is written as JSON rather than as a dozen keys because the whole point is that a forecast is
 * one consistent reading: a half-updated card showing yesterday's high next to today's icon would
 * be worse than a stale one.
 */
object WeatherStore {

    private const val FILE = "quire-weather"
    private const val KEY_FORECAST = "forecast"
    private const val KEY_PLACE = "place"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_PINNED = "pinned"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, forecast: Forecast) {
        prefs(context).edit {
            putString(KEY_FORECAST, encode(forecast).toString())
            putString(KEY_PLACE, forecast.place)
            putFloat(KEY_LATITUDE, forecast.latitude.toFloat())
            putFloat(KEY_LONGITUDE, forecast.longitude.toFloat())
        }
    }

    fun load(context: Context): Forecast? {
        val raw = prefs(context).getString(KEY_FORECAST, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    /**
     * The last place a forecast was fetched for.
     *
     * Location can be unavailable for a long time — indoors, permission not yet granted, provider
     * asleep — and a widget that forgets where it is every time is useless. The coordinates
     * outlive the forecast on purpose.
     */
    fun lastPlace(context: Context): Triple<String, Double, Double>? {
        val p = prefs(context)
        if (!p.contains(KEY_LATITUDE)) return null
        return Triple(
            p.getString(KEY_PLACE, "").orEmpty(),
            p.getFloat(KEY_LATITUDE, 0f).toDouble(),
            p.getFloat(KEY_LONGITUDE, 0f).toDouble(),
        )
    }

    fun rememberPlace(context: Context, place: String, latitude: Double, longitude: Double) {
        prefs(context).edit {
            putString(KEY_PLACE, place)
            putFloat(KEY_LATITUDE, latitude.toFloat())
            putFloat(KEY_LONGITUDE, longitude.toFloat())
        }
    }

    /**
     * Whether the place was named rather than measured.
     *
     * A pinned place outranks anything the device knows: somebody who typed "Berlin" while
     * sitting in Munich meant Berlin, and a location fix arriving afterwards is not new
     * information about what they wanted.
     */
    fun pinned(context: Context): Boolean = prefs(context).getBoolean(KEY_PINNED, false)

    fun pin(context: Context, place: Place) {
        prefs(context).edit {
            putBoolean(KEY_PINNED, true)
            putString(KEY_PLACE, place.name)
            putFloat(KEY_LATITUDE, place.latitude.toFloat())
            putFloat(KEY_LONGITUDE, place.longitude.toFloat())
            // The forecast belongs to the old place; keeping it would show Munich's weather under
            // Berlin's name until the next fetch landed.
            remove(KEY_FORECAST)
        }
    }

    /** Hands the choice of place back to the device. */
    fun unpin(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_PINNED, false)
            remove(KEY_FORECAST)
        }
    }

    fun clear(context: Context) = prefs(context).edit { clear() }

    // ---- Wire format ---------------------------------------------------

    fun encode(forecast: Forecast): JSONObject = JSONObject().apply {
        put("place", forecast.place)
        put("latitude", forecast.latitude)
        put("longitude", forecast.longitude)
        put("fetched", forecast.fetched)
        put(
            "now",
            JSONObject().apply {
                put("temperature", forecast.now.temperature)
                put("feelsLike", forecast.now.feelsLike)
                put("sky", forecast.now.sky.name)
                put("day", forecast.now.day)
                put("humidity", forecast.now.humidity)
                put("wind", forecast.now.wind)
            },
        )
        put(
            "hours",
            JSONArray().apply {
                forecast.hours.forEach { hour ->
                    put(
                        JSONObject().apply {
                            put("t", hour.time.toString())
                            put("c", hour.temperature)
                            put("s", hour.sky.name)
                            put("d", hour.day)
                            put("r", hour.rain)
                        },
                    )
                }
            },
        )
        put(
            "days",
            JSONArray().apply {
                forecast.days.forEach { day ->
                    put(
                        JSONObject().apply {
                            put("date", day.date.toString())
                            put("sky", day.sky.name)
                            put("high", day.high)
                            put("low", day.low)
                            put("rain", day.rain)
                            day.sunrise?.let { put("sunrise", it.toString()) }
                            day.sunset?.let { put("sunset", it.toString()) }
                        },
                    )
                }
            },
        )
    }

    fun decode(json: JSONObject): Forecast {
        val now = json.getJSONObject("now")
        val days = json.getJSONArray("days")
        return Forecast(
            place = json.optString("place"),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            fetched = json.optLong("fetched", 0L),
            now = Conditions(
                temperature = now.getDouble("temperature"),
                feelsLike = now.optDouble("feelsLike", now.getDouble("temperature")),
                sky = sky(now.optString("sky")),
                day = now.optBoolean("day", true),
                humidity = now.optInt("humidity", -1),
                wind = now.optDouble("wind", 0.0),
            ),
            days = (0 until days.length()).map { index ->
                val day = days.getJSONObject(index)
                DayForecast(
                    date = LocalDate.parse(day.getString("date")),
                    sky = sky(day.optString("sky")),
                    high = day.getDouble("high"),
                    low = day.getDouble("low"),
                    rain = day.optInt("rain", 0),
                    sunrise = day.optString("sunrise").takeIf { it.isNotBlank() }
                        ?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
                    sunset = day.optString("sunset").takeIf { it.isNotBlank() }
                        ?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
                )
            },
            hours = json.optJSONArray("hours")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val hour = array.optJSONObject(index) ?: return@mapNotNull null
                    runCatching {
                        HourForecast(
                            time = java.time.LocalDateTime.parse(hour.getString("t")),
                            temperature = hour.getDouble("c"),
                            sky = sky(hour.optString("s")),
                            day = hour.optBoolean("d", true),
                            rain = hour.optInt("r", 0),
                        )
                    }.getOrNull()
                }
            }.orEmpty(),
        )
    }

    /** A stored name this build no longer has is overcast, not an exception. */
    private fun sky(name: String): Sky =
        Sky.entries.firstOrNull { it.name == name } ?: Sky.OVERCAST
}
