package app.rosa.weather.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deterministic, plausible forecasts for widget-picker previews, the widget studio before the
 * first sync, UI previews and tests.
 */
object SampleForecast {
    enum class Scenario { SunnyMild, RainyAfternoon, SnowyCold, StormyWarm, FoggyMorning, ClearNight }

    fun create(
        scenario: Scenario = Scenario.RainyAfternoon,
        nowEpochSeconds: Long = 1_758_628_800L, // 2025-09-23 12:00 UTC
        latitude: Double = 55.7558,
        longitude: Double = 37.6173,
        utcOffsetSeconds: Int = 3 * 3600,
        placeId: String = "sample",
    ): Forecast {
        val (base, amplitude, code) = when (scenario) {
            Scenario.SunnyMild -> Triple(18.0, 6.0, 1)
            Scenario.RainyAfternoon -> Triple(12.0, 4.0, 61)
            Scenario.SnowyCold -> Triple(-7.0, 3.0, 73)
            Scenario.StormyWarm -> Triple(26.0, 5.0, 95)
            Scenario.FoggyMorning -> Triple(6.0, 4.0, 45)
            Scenario.ClearNight -> Triple(9.0, 5.0, 0)
        }
        val localMidnight = (nowEpochSeconds + utcOffsetSeconds) / 86_400 * 86_400 - utcOffsetSeconds
        val startHour = (nowEpochSeconds / 3600 - 3) * 3600

        fun tempAt(t: Long, dayShift: Double = 0.0): Double {
            val localHour = ((t + utcOffsetSeconds) % 86_400) / 3600.0
            // Diurnal curve peaking around 15:00 local.
            return base + dayShift + amplitude * cos((localHour - 15.0) / 24.0 * 2 * PI)
        }

        val hourly = (0 until 72).map { i ->
            val t = startHour + i * 3600L
            val dayIndex = ((t - localMidnight) / 86_400).toInt()
            val shift = sin(dayIndex * 1.3) * 2.5
            val localHour = (((t + utcOffsetSeconds) % 86_400) / 3600).toInt()
            val wetWindow = when (scenario) {
                Scenario.RainyAfternoon -> localHour in 13..19 && dayIndex == 0
                Scenario.StormyWarm -> localHour in 16..20
                Scenario.SnowyCold -> localHour in 6..14
                else -> false
            }
            val hourCode = when {
                wetWindow -> code
                scenario == Scenario.FoggyMorning && localHour in 4..10 -> 45
                scenario == Scenario.ClearNight -> 0
                localHour in 10..17 && scenario != Scenario.SunnyMild -> 2
                else -> if (scenario == Scenario.SunnyMild) 0 else 1
            }
            val precip = if (wetWindow) 0.6 + 1.4 * sin((localHour - 12) / 8.0 * PI).coerceAtLeast(0.0) else 0.0
            val temperature = tempAt(t, shift) - if (wetWindow) 1.5 else 0.0
            val sunElevation = Astronomy.sun(t, latitude, longitude).elevation
            HourlyPoint(
                time = t,
                temperature = temperature,
                apparentTemperature = temperature - 1.8,
                precipitationProbability = if (wetWindow) 70 + (i % 3) * 10 else (i * 7) % 20,
                precipitation = precip,
                weatherCode = hourCode,
                cloudCover = when (hourCode) { 0 -> 5; 1 -> 25; 2 -> 55; 45 -> 90; else -> 95 },
                windSpeed = 3.0 + 2.0 * sin(i / 5.0) + if (scenario == Scenario.StormyWarm) 6 else 0,
                windDirection = (200 + i * 5) % 360,
                windGusts = 6.0 + 3.0 * sin(i / 4.0) + if (scenario == Scenario.StormyWarm) 10 else 0,
                uvIndex = max(0.0, sunElevation / 9.0),
                isDay = sunElevation > -0.83,
                humidity = if (wetWindow) 92 else 64 + (i % 10),
                visibility = if (hourCode == 45) 400.0 else 24_000.0,
                pressure = 1012.0 + sin(i / 12.0) * 4,
            )
        }

        val daily = (0 until 10).map { d ->
            val dayStart = localMidnight + d * 86_400L
            val shift = sin(d * 1.3) * 2.5
            val dayCode = when {
                d == 0 -> code
                d % 4 == 1 -> 3
                d % 4 == 2 -> 61
                d % 5 == 3 -> 0
                else -> 2
            }
            val sunriseLocal = 6.5 + sin(d / 10.0) * 0.1
            DailyPoint(
                time = dayStart,
                weatherCode = if (scenario == Scenario.SnowyCold && dayCode == 61) 71 else dayCode,
                temperatureMax = base + shift + amplitude,
                temperatureMin = base + shift - amplitude,
                sunrise = dayStart + (sunriseLocal * 3600).roundToInt(),
                sunset = dayStart + ((19.1 - d * 0.05) * 3600).roundToInt(),
                precipitationSum = if (dayCode >= 51) 3.4 + d else 0.0,
                precipitationProbabilityMax = if (dayCode >= 51) 80 else 10 + d * 3,
                windSpeedMax = 6.0 + d % 3,
                windDirectionDominant = (180 + d * 20) % 360,
                uvIndexMax = if (scenario == Scenario.SnowyCold) 1.0 else 4.0 + d % 3,
                daylightDuration = 12.5 * 3600,
            )
        }

        val nowcast = (0 until 8).map { i ->
            val t = (nowEpochSeconds / 900 + 1 + i) * 900
            val rainSoon = scenario == Scenario.RainyAfternoon && i >= 2
            NowcastPoint(time = t, precipitation = if (rainSoon) 0.2 + i * 0.05 else 0.0, weatherCode = if (rainSoon) 61 else null)
        }

        val nowTemp = tempAt(nowEpochSeconds)
        val sun = Astronomy.sun(nowEpochSeconds, latitude, longitude)
        return Forecast(
            placeId = placeId,
            latitude = latitude,
            longitude = longitude,
            timezone = "Europe/Moscow",
            utcOffsetSeconds = utcOffsetSeconds,
            fetchedAt = nowEpochSeconds - 600,
            current = CurrentConditions(
                time = nowEpochSeconds - 600,
                temperature = nowTemp,
                apparentTemperature = nowTemp - 2.0,
                humidity = 71,
                dewPoint = nowTemp - 5,
                weatherCode = when (scenario) {
                    Scenario.RainyAfternoon -> 3
                    Scenario.FoggyMorning -> 45
                    else -> code
                },
                isDay = sun.elevation > -0.83,
                cloudCover = when (scenario) {
                    Scenario.SunnyMild, Scenario.ClearNight -> 8
                    Scenario.FoggyMorning -> 90
                    else -> 92
                },
                pressure = 1013.0,
                windSpeed = if (scenario == Scenario.StormyWarm) 11.0 else 4.2,
                windGusts = if (scenario == Scenario.StormyWarm) 19.0 else 8.5,
                windDirection = 220,
                precipitation = if (scenario == Scenario.SnowyCold || scenario == Scenario.StormyWarm) 0.4 else 0.0,
                uvIndex = max(0.0, sun.elevation / 9.0),
                visibility = if (scenario == Scenario.FoggyMorning) 350.0 else 24_000.0,
            ),
            hourly = hourly,
            daily = daily,
            nowcast = nowcast,
            air = AirQuality(time = nowEpochSeconds, europeanAqi = 28, usAqi = 41, pm25 = 7.5, pm10 = 14.0),
        )
    }
}
