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
    val windUnit: String get() = if (metric) "км/ч" else "mph"
    val today: DailyForecast? get() = daily.firstOrNull()
}

/** WMO weather-interpretation codes → label / icon / mood gradient. */
object WeatherCodes {

    fun label(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        56, 57 -> "Ледяная морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        66, 67 -> "Ледяной дождь"
        71 -> "Небольшой снег"
        73 -> "Снег"
        75 -> "Сильный снег"
        77 -> "Снежные зёрна"
        80, 81, 82 -> "Ливни"
        85, 86 -> "Снегопад"
        95 -> "Гроза"
        96, 99 -> "Гроза с градом"
        else -> "—"
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
