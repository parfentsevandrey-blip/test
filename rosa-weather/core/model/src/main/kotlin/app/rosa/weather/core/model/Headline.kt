package app.rosa.weather.core.model

import kotlin.math.roundToInt

enum class PrecipitationKind { Rain, Snow, Sleet, Storm }

/**
 * The single most useful sentence about the weather right now. Chosen by priority so a tiny
 * widget can show "Rain in 15 min" instead of a generic condition label. UI layers localise it.
 */
sealed interface Headline {
    data class PrecipitationStarts(val minutes: Int, val kind: PrecipitationKind) : Headline
    data class PrecipitationEnds(val minutes: Int, val kind: PrecipitationKind) : Headline
    data class PrecipitationLater(val atEpochSeconds: Long, val kind: PrecipitationKind) : Headline
    data class Continuing(val kind: PrecipitationKind) : Headline
    data class TomorrowWarmer(val degreesCelsius: Int) : Headline
    data class TomorrowColder(val degreesCelsius: Int) : Headline
    data class StrongWind(val gustsMs: Double) : Headline
    data class HighUv(val index: Int) : Headline
    data class Sunrise(val atEpochSeconds: Long) : Headline
    data class Sunset(val atEpochSeconds: Long) : Headline
    data class FeelsLike(val apparentCelsius: Double) : Headline
    data class Steady(val condition: WeatherCondition) : Headline
}

object Headlines {
    private const val WET_MM_PER_15_MIN = 0.05
    private const val WET_MM_PER_HOUR = 0.2

    fun pick(forecast: Forecast, moment: ForecastMoment): Headline {
        val now = moment.epochSeconds
        precipitationHeadline(forecast, moment)?.let { return it }

        if (moment.windGusts >= 15.0) return Headline.StrongWind(moment.windGusts)

        val today = forecast.dayAt(now)
        val sunset = today?.sunset
        val sunrise = today?.sunrise
        if (sunset != null && sunset > now && sunset - now <= 75 * 60) return Headline.Sunset(sunset)
        if (sunrise != null && sunrise > now && sunrise - now <= 75 * 60) return Headline.Sunrise(sunrise)

        if (moment.isDay && moment.uvIndex >= 6) return Headline.HighUv(moment.uvIndex.roundToInt())

        val days = forecast.daysFrom(now)
        if (days.size >= 2) {
            val delta = (days[1].temperatureMax - days[0].temperatureMax).roundToInt()
            if (delta >= 5) return Headline.TomorrowWarmer(delta)
            if (delta <= -5) return Headline.TomorrowColder(-delta)
        }

        if (kotlin.math.abs(moment.apparentTemperature - moment.temperature) >= 3) {
            return Headline.FeelsLike(moment.apparentTemperature)
        }
        return Headline.Steady(moment.condition)
    }

    private fun precipitationHeadline(forecast: Forecast, moment: ForecastMoment): Headline? {
        val now = moment.epochSeconds
        val kind = kindFor(moment.condition, moment.temperature)
        val wetNow = moment.precipitation >= WET_MM_PER_HOUR && moment.condition.isPrecipitation

        val upcoming = forecast.nowcast.filter { it.time > now && it.time - now <= 2 * 3600 }
        if (upcoming.isNotEmpty()) {
            if (wetNow) {
                val dry = upcoming.firstOrNull { it.precipitation < WET_MM_PER_15_MIN }
                if (dry != null) return Headline.PrecipitationEnds(minutesUntil(now, dry.time - 15 * 60), kind)
            } else {
                val wet = upcoming.firstOrNull { it.precipitation >= WET_MM_PER_15_MIN }
                if (wet != null) {
                    val code = wet.weatherCode?.let(WeatherCondition::fromWmo)
                    val wetKind = if (code != null && code.isPrecipitation) kindFor(code, moment.temperature) else kind
                    return Headline.PrecipitationStarts(minutesUntil(now, wet.time - 15 * 60), wetKind)
                }
            }
        }
        if (wetNow) return Headline.Continuing(kind)

        val laterHour = forecast.hoursFrom(now).drop(1).take(12).firstOrNull {
            it.precipitationProbability >= 55 && it.precipitation >= WET_MM_PER_HOUR
        }
        if (laterHour != null) {
            val laterCondition = WeatherCondition.fromWmo(laterHour.weatherCode)
            // Hourly values describe the *preceding* hour; never announce a start in the past.
            val nextQuarter = (now / 900 + 1) * 900
            val start = (laterHour.time - 3600).coerceAtLeast(nextQuarter)
            return Headline.PrecipitationLater(start, kindFor(laterCondition, laterHour.temperature))
        }
        return null
    }

    private fun minutesUntil(now: Long, at: Long): Int = ((at - now) / 60.0).roundToInt().coerceAtLeast(1)

    fun kindFor(condition: WeatherCondition, temperature: Double): PrecipitationKind = when (condition.family) {
        WeatherCondition.Family.Snow -> PrecipitationKind.Snow
        WeatherCondition.Family.Ice -> PrecipitationKind.Sleet
        WeatherCondition.Family.Storm -> PrecipitationKind.Storm
        else -> when {
            temperature <= -1 -> PrecipitationKind.Snow
            temperature <= 1.5 -> PrecipitationKind.Sleet
            else -> PrecipitationKind.Rain
        }
    }
}
