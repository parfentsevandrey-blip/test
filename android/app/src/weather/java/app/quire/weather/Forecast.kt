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

/**
 * One quarter-hour of the immediate future: what falls in it, if anything.
 *
 * This is the resolution at which "take the umbrella now or wait twenty minutes" lives — the
 * hourly rows above it answer a different question. Rain arrives in millimetres and snow in
 * centimetres, which is how the provider reports them.
 */
class QuarterCast(
    val time: java.time.LocalDateTime,
    val rain: Double,
    val snow: Double,
) {
    /**
     * Whether a person stepping outside in this quarter-hour would say something is falling.
     * The floors are a light drizzle and a light snow; below them the provider is reporting
     * moisture, not weather.
     */
    val wet: Boolean get() = rain >= 0.1 || snow >= 0.05
}

/**
 * The next turn the sky takes: precipitation starting, or the current spell ending.
 *
 * [snow] names what falls at the moment of the turn, so the card can say "snow in 25 min"
 * rather than calling everything rain. When a spell ends the wording never names it — the sky
 * label above the line already has — so the flag matters only for a start.
 */
class SoonChange(val minutes: Int, val starts: Boolean, val snow: Boolean)

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
    val quarters: List<QuarterCast> = emptyList(),
) {
    /** The hours still ahead, which is the only part of an hourly forecast worth the width. */
    fun hoursAhead(from: java.time.LocalDateTime, count: Int = 24): List<HourForecast> =
        hours.filter { !it.time.isBefore(from.withMinute(0)) }.take(count)

    /** Today plus the next four, which is what the widget has room for and what a week needs. */
    fun ahead(count: Int = 5): List<DayForecast> = days.take(count)

    /**
     * When the sky next changes its mind, or null when it will not within [SOON_MINUTES].
     *
     * Computed against the asking time rather than the fetch time on purpose: the widget paints
     * from a cached forecast at moments nobody chose, and "rain in 25 minutes" written at fetch
     * time would still say 25 half an hour later. The slots carry their own clock, so the same
     * stored forecast answers correctly for as long as it has slots left.
     *
     * Null also when the slot covering [from] is missing — a forecast that ran out of quarters
     * says nothing rather than guessing — and when nothing changes before the horizon: "rain in
     * three hours" is the daily forecast's news, not this line's.
     */
    fun soon(from: java.time.LocalDateTime): SoonChange? {
        val ahead = quarters.filter { it.time.plusMinutes(15).isAfter(from) }
        val current = ahead.firstOrNull()?.takeIf { !it.time.isAfter(from) } ?: return null
        val change = ahead.firstOrNull { it.wet != current.wet } ?: return null
        val minutes = java.time.Duration.between(from, change.time).toMinutes().toInt()
            .coerceAtLeast(1)
        if (minutes > SOON_MINUTES) return null
        // The slot that carries the weather in question: the one ending when a spell ends, the
        // one beginning when a spell begins. Snowfall is centimetres against rain's millimetres,
        // which is close enough to water-equivalent parity to pick the honest word.
        val telling = if (current.wet) current else change
        return SoonChange(minutes, starts = change.wet, snow = telling.snow >= telling.rain)
    }

    companion object {
        /** How far ahead a "soon" is still soon: two hours, past which it is just the forecast. */
        const val SOON_MINUTES = 120
    }
}
