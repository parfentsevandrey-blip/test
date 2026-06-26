package com.monthcalendar.widget.weather

import com.monthcalendar.widget.R
import java.time.LocalDate
import java.time.LocalDateTime

data class HourForecast(
    val time: LocalDateTime,
    val temp: Double,
    val code: Int,
    val isDay: Boolean,
    val precipProb: Int,
)

data class DailyForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val precipProb: Int,
    val uvMax: Double,
)

/** A rendered-ready weather snapshot. */
data class WeatherData(
    val locationName: String,
    val temp: Double,
    val apparentTemp: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windSpeed: Double,
    val windMax: Double,
    val uvMax: Double,
    val metric: Boolean,
    val hourly: List<HourForecast>,
    val daily: List<DailyForecast>,
    val updatedAt: Long,
) {
    val tempUnit: String get() = if (metric) "°C" else "°F"
    val windUnit: String get() = if (metric) "km/h" else "mph"
    val today: DailyForecast? get() = daily.firstOrNull()
}

/** WMO weather-interpretation codes → label / icon / mood gradient. */
object WeatherCodes {

    /** Localised condition label resource id (resolve with context.getString). */
    fun labelRes(code: Int): Int = when (code) {
        0 -> R.string.wmo_clear
        1 -> R.string.wmo_mainly_clear
        2 -> R.string.wmo_partly_cloudy
        3 -> R.string.wmo_overcast
        45, 48 -> R.string.wmo_fog
        51, 53, 55 -> R.string.wmo_drizzle
        56, 57 -> R.string.wmo_freezing_drizzle
        61 -> R.string.wmo_rain_light
        63 -> R.string.wmo_rain
        65 -> R.string.wmo_rain_heavy
        66, 67 -> R.string.wmo_freezing_rain
        71 -> R.string.wmo_snow_light
        73 -> R.string.wmo_snow
        75 -> R.string.wmo_snow_heavy
        77 -> R.string.wmo_snow_grains
        80, 81, 82 -> R.string.wmo_showers
        85, 86 -> R.string.wmo_snow_showers
        95 -> R.string.wmo_thunder
        96, 99 -> R.string.wmo_thunder_hail
        else -> R.string.wmo_unknown
    }

    fun iconRes(code: Int, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_clear else R.drawable.ic_w_clear_night
        2 -> if (isDay) R.drawable.ic_w_partly else R.drawable.ic_w_partly_night
        3 -> R.drawable.ic_w_cloudy
        45, 48 -> R.drawable.ic_w_fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloudy
    }

    /** Three-stop mood gradient (top, middle, bottom) as ARGB ints. */
    fun gradient(code: Int, isDay: Boolean): IntArray = when {
        code in intArrayOf(95, 96, 99) -> intArrayOf(0xFF1B2233.toInt(), 0xFF2B3650.toInt(), 0xFF454F6B.toInt())
        code in intArrayOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82) ->
            intArrayOf(0xFF374256.toInt(), 0xFF49566E.toInt(), 0xFF61708C.toInt())
        code in intArrayOf(71, 73, 75, 77, 85, 86) ->
            intArrayOf(0xFF6E7C90.toInt(), 0xFF8A99AE.toInt(), 0xFFB8C6D6.toInt())
        code in intArrayOf(45, 48) -> intArrayOf(0xFF5A6473.toInt(), 0xFF747E8D.toInt(), 0xFF99A2AE.toInt())
        code == 3 -> intArrayOf(0xFF4A5568.toInt(), 0xFF626D82.toInt(), 0xFF838EA1.toInt())
        code == 2 -> if (isDay) intArrayOf(0xFF3E72B8.toInt(), 0xFF5285C4.toInt(), 0xFF87AFDB.toInt())
            else intArrayOf(0xFF161D33.toInt(), 0xFF24304E.toInt(), 0xFF3A4A6B.toInt())
        else -> if (isDay) intArrayOf(0xFF2E6FD6.toInt(), 0xFF4A8AE0.toInt(), 0xFF89B9EE.toInt())
            else intArrayOf(0xFF0C1330.toInt(), 0xFF1A2546.toInt(), 0xFF2E3E61.toInt())
    }
}
