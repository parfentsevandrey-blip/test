package app.papersky.weather.core.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A place the user cares about. All weather values in the app are stored in metric units. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val isDeviceLocation: Boolean = false,
) {
    val subtitle: String? get() = listOfNotNull(region, country).distinct().joinToString(", ").ifBlank { null }

    companion object {
        /** Virtual id that always resolves to the last known device location. */
        const val HERE = "here"
    }
}

@Serializable
data class Forecast(
    val placeId: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val utcOffsetSeconds: Int,
    /** Wall-clock time of the download, epoch millis. */
    val fetchedAt: Long,
    val current: Current,
    val hourly: List<Hour>,
    val daily: List<Day>,
    val nowcast: List<Nowcast> = emptyList(),
) {
    val zone: ZoneId
        get() = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneOffset.ofTotalSeconds(utcOffsetSeconds) }

    fun ageMinutes(nowMillis: Long): Long = (nowMillis - fetchedAt) / 60_000

    /** Index of the hour slot containing [epochSec], clamped to the available range. */
    fun hourIndexAt(epochSec: Long): Int {
        if (hourly.isEmpty()) return -1
        var lo = 0
        var hi = hourly.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (hourly[mid].time <= epochSec) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Hours starting with the one that contains [epochSec]. */
    fun hoursFrom(epochSec: Long, count: Int, step: Int = 1): List<Hour> {
        val start = hourIndexAt(epochSec).coerceAtLeast(0)
        return (0 until count).mapNotNull { hourly.getOrNull(start + it * step) }
    }

    fun dayIndexAt(epochSec: Long): Int {
        val idx = daily.indexOfLast { it.date <= epochSec }
        return idx.coerceAtLeast(0)
    }

    fun dayAt(epochSec: Long): Day? = daily.getOrNull(dayIndexAt(epochSec))

    fun daysFrom(epochSec: Long, count: Int): List<Day> {
        val start = dayIndexAt(epochSec)
        return daily.drop(start).take(count)
    }
}

@Serializable
data class Current(
    val time: Long,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val isDay: Boolean,
    val code: Int,
    val cloudCover: Int,
    val pressure: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val precipitation: Double,
    val visibility: Double? = null,
    val uvIndex: Double? = null,
    val dewPoint: Double? = null,
)

@Serializable
data class Hour(
    val time: Long,
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val precipProbability: Int,
    val precipitation: Double,
    val cloudCover: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val humidity: Int,
    val isDay: Boolean,
    val uvIndex: Double,
    val visibility: Double? = null,
    val pressure: Double,
)

@Serializable
data class Day(
    /** Local midnight as epoch seconds. */
    val date: Long,
    val code: Int,
    val tempMax: Double,
    val tempMin: Double,
    val sunrise: Long,
    val sunset: Long,
    val precipSum: Double,
    val precipProbability: Int,
    val windMax: Double,
    val gustMax: Double,
    val windDirection: Int,
    val uvMax: Double,
    val daylightSeconds: Double,
)

/** 15-minute precipitation nowcast slot. */
@Serializable
data class Nowcast(val time: Long, val precipitation: Double)

fun Long.toInstant(): Instant = Instant.ofEpochSecond(this)
