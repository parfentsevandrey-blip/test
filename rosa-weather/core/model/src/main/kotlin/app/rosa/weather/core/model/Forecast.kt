package app.rosa.weather.core.model

import kotlinx.serialization.Serializable

/**
 * A complete, self-contained forecast snapshot. Everything a widget needs to draw itself for
 * the next days is in here, so a widget can keep showing *correct current* conditions from
 * cache (see [ForecastMoment]) even when the network is unavailable for hours.
 *
 * All times are epoch seconds (UTC). Metric units: °C, m/s, hPa, mm, metres.
 */
@Serializable
data class Forecast(
    val placeId: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val fetchedAt: Long,
    val current: CurrentConditions,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val nowcast: List<NowcastPoint> = emptyList(),
    val air: AirQuality? = null,
) {
    fun ageSeconds(nowEpochSeconds: Long): Long = nowEpochSeconds - fetchedAt

    /** Index of the hourly slot that contains [epochSeconds], or -1 if outside the range. */
    fun hourIndexAt(epochSeconds: Long): Int {
        if (hourly.isEmpty()) return -1
        val first = hourly.first().time
        if (epochSeconds < first) return -1
        val idx = ((epochSeconds - first) / 3600L).toInt()
        return if (idx < hourly.size) idx else -1
    }

    /** Daily entry for the local calendar day that contains [epochSeconds]. */
    fun dayAt(epochSeconds: Long): DailyPoint? =
        daily.lastOrNull { it.time <= epochSeconds } ?: daily.firstOrNull()

    /** Hours from [epochSeconds] onward (the current, partially elapsed hour included). */
    fun hoursFrom(epochSeconds: Long): List<HourlyPoint> {
        val start = hourly.indexOfFirst { it.time + 3600 > epochSeconds }
        return if (start < 0) emptyList() else hourly.subList(start, hourly.size)
    }

    /** Days starting with the local "today" for [epochSeconds]. */
    fun daysFrom(epochSeconds: Long): List<DailyPoint> {
        val start = daily.indexOfFirst { it.time + 86_400 > epochSeconds }
        return if (start < 0) emptyList() else daily.subList(start, daily.size)
    }
}

@Serializable
data class CurrentConditions(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double? = null,
    val weatherCode: Int,
    val isDay: Boolean,
    val cloudCover: Int,
    val pressure: Double,
    val windSpeed: Double,
    val windGusts: Double,
    val windDirection: Int,
    val precipitation: Double,
    val uvIndex: Double = 0.0,
    val visibility: Double? = null,
)

@Serializable
data class HourlyPoint(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val precipitationProbability: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val cloudCover: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double = windSpeed,
    val uvIndex: Double = 0.0,
    val isDay: Boolean = true,
    val humidity: Int = 0,
    val visibility: Double? = null,
    val pressure: Double? = null,
)

/** [time] is local midnight of that day expressed as epoch seconds. */
@Serializable
data class DailyPoint(
    val time: Long,
    val weatherCode: Int,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val apparentMax: Double? = null,
    val apparentMin: Double? = null,
    val sunrise: Long?,
    val sunset: Long?,
    val precipitationSum: Double,
    val precipitationProbabilityMax: Int,
    val windSpeedMax: Double,
    val windDirectionDominant: Int? = null,
    val uvIndexMax: Double = 0.0,
    val daylightDuration: Double? = null,
)

/** 15-minute precipitation nowcast. */
@Serializable
data class NowcastPoint(
    val time: Long,
    val precipitation: Double,
    val weatherCode: Int? = null,
)

@Serializable
data class AirQuality(
    val time: Long,
    val europeanAqi: Int? = null,
    val usAqi: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
) {
    val level: AirLevel
        get() = AirLevel.fromEuropeanAqi(europeanAqi ?: return AirLevel.Unknown)
}

enum class AirLevel {
    Good, Fair, Moderate, Poor, VeryPoor, ExtremelyPoor, Unknown;

    companion object {
        fun fromEuropeanAqi(aqi: Int): AirLevel = when {
            aqi <= 20 -> Good
            aqi <= 40 -> Fair
            aqi <= 60 -> Moderate
            aqi <= 80 -> Poor
            aqi <= 100 -> VeryPoor
            else -> ExtremelyPoor
        }
    }
}
