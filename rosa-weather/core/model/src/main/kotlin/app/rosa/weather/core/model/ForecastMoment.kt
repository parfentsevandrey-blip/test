package app.rosa.weather.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The weather "as of" an arbitrary instant, reconstructed from a cached [Forecast].
 *
 * This is what makes widgets truthful between network syncs: instead of freezing on the value
 * observed at fetch time, every render asks "what is it like *now*?" and interpolates the hourly
 * forecast, the 15-minute nowcast and the real sun/moon position for that exact moment.
 */
data class ForecastMoment(
    val epochSeconds: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val humidity: Int,
    val dewPoint: Double?,
    val cloudCover: Int,
    val pressure: Double,
    val windSpeed: Double,
    val windGusts: Double,
    val windDirection: Int,
    /** Precipitation rate, mm/h. */
    val precipitation: Double,
    val precipitationProbability: Int,
    val uvIndex: Double,
    val visibility: Double?,
    val sun: SkyPosition,
    val moon: SkyPosition,
    val moonPhase: MoonPhase,
    val today: DailyPoint?,
    val visual: WeatherVisual,
) {
    val dayPhase: DayPhase get() = DayPhase.fromSunElevation(sun.elevation)
    val isDay: Boolean get() = sun.elevation > -0.83
}

/** How long a direct "current" observation stays more trustworthy than the hourly model. */
private const val CURRENT_TRUST_SECONDS = 90 * 60.0

fun Forecast.momentAt(epochSeconds: Long): ForecastMoment {
    val hourlyValues = interpolateHourly(epochSeconds)
    val c = current
    val weight = (abs(epochSeconds - c.time) / CURRENT_TRUST_SECONDS).coerceIn(0.0, 1.0)

    fun blend(fromCurrent: Double, fromHourly: Double?) =
        if (fromHourly == null) fromCurrent else fromCurrent + (fromHourly - fromCurrent) * weight

    val nearestHour = hourly.getOrNull(hourIndexAt(epochSeconds))
    val code = if (weight < 0.5 || nearestHour == null) c.weatherCode else nearestHour.weatherCode
    val nowcastPrecip = nowcastAt(epochSeconds)
    // `current.precipitation` is the sum over the preceding 15 minutes: convert to mm/h.
    val precipitation = nowcastPrecip ?: blend(c.precipitation * 4, hourlyValues?.precipitation)
    val condition = WeatherCondition.fromWmo(code)
    val cloudCover = blend(c.cloudCover.toDouble(), hourlyValues?.cloudCover).roundToInt()
    val windSpeed = blend(c.windSpeed, hourlyValues?.windSpeed)
    val humidity = blend(c.humidity.toDouble(), hourlyValues?.humidity).roundToInt()

    return ForecastMoment(
        epochSeconds = epochSeconds,
        temperature = blend(c.temperature, hourlyValues?.temperature),
        apparentTemperature = blend(c.apparentTemperature, hourlyValues?.apparentTemperature),
        weatherCode = code,
        condition = condition,
        humidity = humidity,
        dewPoint = c.dewPoint,
        cloudCover = cloudCover,
        pressure = blend(c.pressure, hourlyValues?.pressure),
        windSpeed = windSpeed,
        windGusts = maxOf(blend(c.windGusts, hourlyValues?.windGusts), windSpeed),
        windDirection = if (weight < 0.5 || nearestHour == null) c.windDirection else nearestHour.windDirection,
        precipitation = precipitation,
        precipitationProbability = nearestHour?.precipitationProbability ?: 0,
        uvIndex = blend(c.uvIndex, hourlyValues?.uvIndex).coerceAtLeast(0.0),
        visibility = c.visibility,
        sun = Astronomy.sun(epochSeconds, latitude, longitude),
        moon = Astronomy.moon(epochSeconds, latitude, longitude),
        moonPhase = Astronomy.moonPhase(epochSeconds),
        today = dayAt(epochSeconds),
        visual = WeatherVisual.from(condition, cloudCover, precipitation, windSpeed, humidity),
    )
}

private class HourlyValues(
    val temperature: Double,
    val apparentTemperature: Double,
    val precipitation: Double,
    val cloudCover: Double,
    val windSpeed: Double,
    val windGusts: Double,
    val humidity: Double,
    val uvIndex: Double,
    val pressure: Double?,
)

private fun Forecast.interpolateHourly(epochSeconds: Long): HourlyValues? {
    if (hourly.isEmpty()) return null
    val i = hourly.indexOfLast { it.time <= epochSeconds }
    val a = hourly.getOrNull(i) ?: hourly.first()
    val b = hourly.getOrNull(i + 1) ?: a
    val t = if (b.time == a.time) 0.0 else ((epochSeconds - a.time).toDouble() / (b.time - a.time)).coerceIn(0.0, 1.0)
    fun lerp(x: Double, y: Double) = x + (y - x) * t
    return HourlyValues(
        temperature = lerp(a.temperature, b.temperature),
        apparentTemperature = lerp(a.apparentTemperature, b.apparentTemperature),
        // Open-Meteo precipitation is the sum over the preceding hour: take the upcoming slot.
        precipitation = b.precipitation,
        cloudCover = lerp(a.cloudCover.toDouble(), b.cloudCover.toDouble()),
        windSpeed = lerp(a.windSpeed, b.windSpeed),
        windGusts = lerp(a.windGusts, b.windGusts),
        humidity = lerp(a.humidity.toDouble(), b.humidity.toDouble()),
        uvIndex = lerp(a.uvIndex, b.uvIndex),
        pressure = if (a.pressure != null && b.pressure != null) lerp(a.pressure, b.pressure) else null,
    )
}

/** Hourly-rate precipitation (mm/h) from the 15-minute nowcast, if it covers [epochSeconds]. */
private fun Forecast.nowcastAt(epochSeconds: Long): Double? {
    if (nowcast.isEmpty()) return null
    // Each value is the sum over the 15 minutes *preceding* its timestamp.
    val slot = nowcast.firstOrNull { it.time >= epochSeconds } ?: return null
    if (slot.time - epochSeconds > 15 * 60) return null
    return slot.precipitation * 4
}
