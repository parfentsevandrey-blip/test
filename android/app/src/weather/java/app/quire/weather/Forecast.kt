package app.quire.weather

import java.time.LocalDate

/**
 * What it is doing outside right now.
 *
 * The last four are the ones a widget has no room for and a screen does. They default to "not
 * known" rather than to zero: a gust of 0 km/h and a provider that did not send the field are
 * different facts, and only one of them is worth a card.
 */
class Conditions(
    val temperature: Double,
    val feelsLike: Double,
    val sky: Sky,
    val day: Boolean,
    val humidity: Int,
    val wind: Double,
    /** The strongest gust, in km/h, or negative where it is not known. */
    val gust: Double = -1.0,
    /** Where the wind is coming from, in degrees clockwise from north, or negative if unknown. */
    val direction: Int = -1,
    /** Surface pressure in hectopascals, or negative if unknown. */
    val pressure: Double = -1.0,
    /** Today's peak UV index, or negative if unknown. */
    val uv: Double = -1.0,
) {
    /** Which of the eight points the wind is coming from, or null where it is not known. */
    val quarter: Int? get() = if (direction < 0) null else ((direction + 22) % 360) / 45
}

/** One hour of the next day and a bit: the shape of what is coming, rather than its summary. */
class HourForecast(
    val time: java.time.LocalDateTime,
    val temperature: Double,
    val sky: Sky,
    val day: Boolean,
    val rain: Int,
)

/** One day of the forecast: what it will mostly do, and how far the temperature will swing. */
class DayForecast(
    val date: LocalDate,
    val sky: Sky,
    val high: Double,
    val low: Double,
    /** Chance of precipitation over the day, 0..100. */
    val rain: Int,
    /** Local times the sun crosses the horizon, where the provider knew them. */
    val sunrise: java.time.LocalDateTime? = null,
    val sunset: java.time.LocalDateTime? = null,
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
    val hours: List<HourForecast> = emptyList(),
) {
    /** The hours still ahead, which is the only part of an hourly forecast worth the width. */
    fun hoursAhead(from: java.time.LocalDateTime, count: Int = 24): List<HourForecast> =
        hours.filter { !it.time.isBefore(from.withMinute(0)) }.take(count)

    /** Today plus the next four, which is what the widget has room for and what a week needs. */
    fun ahead(count: Int = 5): List<DayForecast> = days.take(count)
}
