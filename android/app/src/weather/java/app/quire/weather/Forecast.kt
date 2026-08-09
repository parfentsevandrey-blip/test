package app.quire.weather

import java.time.LocalDate

/** What it is doing outside right now. */
class Conditions(
    val temperature: Double,
    val feelsLike: Double,
    val sky: Sky,
    val day: Boolean,
    val humidity: Int,
    val wind: Double,
)

/** One day of the forecast: what it will mostly do, and how far the temperature will swing. */
class DayForecast(
    val date: LocalDate,
    val sky: Sky,
    val high: Double,
    val low: Double,
    /** Chance of precipitation over the day, 0..100. */
    val rain: Int,
)

/**
 * Everything one place's weather amounts to.
 *
 * [fetched] is kept because a forecast is only as good as its age: the widget paints the last one
 * it has rather than an empty card, and the app says how old it is instead of pretending.
 */
class Forecast(
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val now: Conditions,
    val days: List<DayForecast>,
    val fetched: Long,
) {
    /** Today plus the next four, which is what the widget has room for and what a week needs. */
    fun ahead(count: Int = 5): List<DayForecast> = days.take(count)
}
