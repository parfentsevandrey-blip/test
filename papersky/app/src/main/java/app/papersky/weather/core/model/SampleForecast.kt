package app.papersky.weather.core.model

import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A believable synthetic forecast for widget-picker previews and first-run placeholders — never
 * shown as real data.
 */
object SampleForecast {
    val place = Place(id = "sample", name = "Papersky", latitude = 55.75, longitude = 37.62, timezone = "Europe/Moscow")

    fun build(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Forecast {
        val now = nowMillis / 1000
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        val midnight = today.atStartOfDay(zone).toEpochSecond()
        val offset = zone.rules.getOffset(Instant.ofEpochSecond(now)).totalSeconds
        val startHour = (now / 3600) * 3600 - 3 * 3600

        val hourly = (0 until 24 * 9).map { i ->
            val t = startHour + i * 3600L
            val localHour = ((t + offset) % 86_400) / 3600.0
            val dayIdx = ((t - midnight) / 86_400).toInt()
            val temp = 14 + dayIdx % 3 * 1.5 + 6 * sin((localHour - 9) / 24 * 2 * PI)
            val rainy = dayIdx == 1 && localHour in 13.0..17.0
            val code = when {
                rainy -> 61
                dayIdx == 3 -> 3
                localHour < 6 || localHour > 21 -> 1
                else -> 2
            }
            Hour(
                time = t,
                temperature = temp,
                feelsLike = temp - 1.2,
                code = code,
                precipProbability = if (rainy) 70 else (10 + 10 * cos(localHour)).roundToInt().coerceIn(0, 30),
                precipitation = if (rainy) 0.8 else 0.0,
                cloudCover = if (code >= 3) 90 else if (code == 2) 45 else 15,
                windSpeed = 3.0 + sin(i / 5.0) * 1.5,
                windDirection = 240,
                windGusts = 6.5,
                humidity = 62,
                isDay = localHour in 6.0..19.5,
                uvIndex = if (localHour in 9.0..16.0) 4.0 else 0.0,
                visibility = 24_000.0,
                pressure = 1014.0,
            )
        }
        val daily = (0 until 9).map { d ->
            val date = midnight + d * 86_400L
            Day(
                date = date,
                code = listOf(2, 61, 1, 3, 0, 80, 2, 1, 0)[d],
                tempMax = 20.0 + (d % 3) * 1.5,
                tempMin = 9.0 + (d % 2),
                sunrise = date + 6 * 3600 + 20 * 60,
                sunset = date + 19 * 3600 + 35 * 60,
                precipSum = if (d == 1 || d == 5) 4.2 else 0.0,
                precipProbability = if (d == 1 || d == 5) 70 else 10,
                windMax = 6.0,
                gustMax = 11.0,
                windDirection = 240,
                uvMax = 4.5,
                daylightSeconds = 13.25 * 3600,
            )
        }
        val h = hourly[3]
        return Forecast(
            placeId = place.id,
            latitude = place.latitude,
            longitude = place.longitude,
            timezone = zone.id,
            utcOffsetSeconds = offset,
            fetchedAt = nowMillis,
            current = Current(
                time = now, temperature = h.temperature, feelsLike = h.feelsLike, humidity = h.humidity, isDay = h.isDay,
                code = h.code, cloudCover = h.cloudCover, pressure = h.pressure, windSpeed = h.windSpeed,
                windDirection = h.windDirection, windGusts = h.windGusts, precipitation = 0.0, visibility = h.visibility,
                uvIndex = h.uvIndex, dewPoint = 8.0,
            ),
            hourly = hourly,
            daily = daily,
        )
    }
}
