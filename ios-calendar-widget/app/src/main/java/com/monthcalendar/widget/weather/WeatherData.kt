package com.monthcalendar.widget.weather

import java.time.LocalDate

data class DailyForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
)

/** A rendered-ready weather snapshot. */
data class WeatherData(
    val locationName: String,
    val temp: Double,
    val apparentTemp: Double,
    val code: Int,
    val humidity: Int,
    val windSpeed: Double,
    val metric: Boolean,
    val daily: List<DailyForecast>,
    val updatedAt: Long,
) {
    val tempUnit: String get() = if (metric) "°C" else "°F"
    val windUnit: String get() = if (metric) "км/ч" else "mph"
}

/** WMO weather-interpretation codes → Russian label + emoji glyph. */
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

    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "🌨️"
        80, 81, 82 -> "🌦️"
        85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}
