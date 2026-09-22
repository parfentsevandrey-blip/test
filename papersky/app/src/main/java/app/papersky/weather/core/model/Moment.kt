package app.papersky.weather.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Weather at a single instant. Built either from live "current" conditions or by interpolating the
 * hourly forecast, which is what keeps widgets truthful between downloads: the hourly slot for
 * "now" is always used once the live observation has aged out.
 */
data class WeatherMoment(
    val epochSec: Long,
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val cloudCover: Int,
    val precipitation: Double,
    val precipProbability: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val humidity: Int,
    val uvIndex: Double,
    val visibility: Double?,
    val pressure: Double,
    val dewPoint: Double?,
    val sunrise: Long?,
    val sunset: Long?,
    val isLive: Boolean,
) {
    val condition: Condition get() = Condition.fromWmo(code)
    val precip: Precipitation get() = Precipitation.from(code, precipitation)

    /** 0 = deep night, 1 = full day, with a smooth civil-twilight ramp around sunrise/sunset. */
    val daylight: Float get() = Astro.daylight(epochSec, sunrise, sunset, isDay)

    /** Position of the sun along its arc, 0 at sunrise and 1 at sunset; outside 0..1 at night. */
    val sunProgress: Float get() = Astro.sunProgress(epochSec, sunrise, sunset, isDay)

    val moonPhase: Float get() = Astro.moonPhase(epochSec)
}

private const val LIVE_WINDOW_SEC = 45 * 60

fun Forecast.momentAt(epochSec: Long, preferLive: Boolean = true): WeatherMoment? {
    val day = dayAt(epochSec)
    val nextDay = daily.getOrNull(dayIndexAt(epochSec) + 1)
    // Sunrise/sunset relevant to this instant: after today's sunset we look at tomorrow's sunrise.
    val sunrise = day?.sunrise?.takeIf { it > 0 }
    val sunset = day?.sunset?.takeIf { it > 0 }

    if (preferLive && abs(epochSec - current.time) <= LIVE_WINDOW_SEC) {
        val c = current
        val hour = hourly.getOrNull(hourIndexAt(epochSec))
        return WeatherMoment(
            epochSec = epochSec,
            temperature = c.temperature,
            feelsLike = c.feelsLike,
            code = c.code,
            isDay = c.isDay,
            cloudCover = c.cloudCover,
            precipitation = c.precipitation,
            precipProbability = hour?.precipProbability ?: 0,
            windSpeed = c.windSpeed,
            windDirection = c.windDirection,
            windGusts = c.windGusts,
            humidity = c.humidity,
            uvIndex = c.uvIndex ?: hour?.uvIndex ?: 0.0,
            visibility = c.visibility ?: hour?.visibility,
            pressure = c.pressure,
            dewPoint = c.dewPoint,
            sunrise = sunrise,
            sunset = sunset,
            isLive = true,
        ).withNightSun(nextDay)
    }

    if (hourly.isEmpty()) return null
    val i = hourIndexAt(epochSec)
    val a = hourly[i]
    val b = hourly.getOrNull(i + 1) ?: a
    val f = if (b.time > a.time) ((epochSec - a.time).toDouble() / (b.time - a.time)).coerceIn(0.0, 1.0) else 0.0
    fun lerp(x: Double, y: Double) = x + (y - x) * f
    fun lerpI(x: Int, y: Int) = lerp(x.toDouble(), y.toDouble()).roundToInt()
    val near = if (f < 0.5) a else b
    return WeatherMoment(
        epochSec = epochSec,
        temperature = lerp(a.temperature, b.temperature),
        feelsLike = lerp(a.feelsLike, b.feelsLike),
        code = near.code,
        isDay = near.isDay,
        cloudCover = lerpI(a.cloudCover, b.cloudCover),
        precipitation = lerp(a.precipitation, b.precipitation),
        precipProbability = lerpI(a.precipProbability, b.precipProbability),
        windSpeed = lerp(a.windSpeed, b.windSpeed),
        windDirection = lerpAngle(a.windDirection, b.windDirection, f),
        windGusts = lerp(a.windGusts, b.windGusts),
        humidity = lerpI(a.humidity, b.humidity),
        uvIndex = lerp(a.uvIndex, b.uvIndex),
        visibility = a.visibility?.let { va -> b.visibility?.let { lerp(va, it) } ?: va },
        pressure = lerp(a.pressure, b.pressure),
        dewPoint = null,
        sunrise = sunrise,
        sunset = sunset,
        isLive = false,
    ).withNightSun(nextDay)
}

/**
 * After sunset the next relevant sunrise is tomorrow's; keeping both lets the night sky progress
 * (and the moon travel) across the whole night.
 */
private fun WeatherMoment.withNightSun(nextDay: Day?): WeatherMoment {
    val set = sunset ?: return this
    // Keep today's times through dusk so the twilight ramp stays continuous.
    if (epochSec > set + 3600 && nextDay != null && nextDay.sunrise > 0) {
        return copy(sunrise = nextDay.sunrise, sunset = nextDay.sunset)
    }
    return this
}

private fun lerpAngle(a: Int, b: Int, f: Double): Int {
    var d = ((b - a) % 360 + 540) % 360 - 180
    if (d == -180) d = 180
    return ((a + d * f).roundToInt() % 360 + 360) % 360
}

object Astro {
    private const val SYNODIC_MONTH_DAYS = 29.530588853
    private const val KNOWN_NEW_MOON_EPOCH = 947_182_440L // 2000-01-06 18:14 UTC
    private const val TWILIGHT_SEC = 40 * 60f

    /** 0 = new moon, 0.5 = full moon. */
    fun moonPhase(epochSec: Long): Float {
        val days = (epochSec - KNOWN_NEW_MOON_EPOCH) / 86_400.0
        val cycles = days / SYNODIC_MONTH_DAYS
        return (cycles - floor(cycles)).toFloat()
    }

    fun moonIllumination(phase: Float): Float = ((1 - cos(2 * PI * phase)) / 2).toFloat()

    fun daylight(t: Long, sunrise: Long?, sunset: Long?, isDayFallback: Boolean): Float {
        if (sunrise == null || sunset == null || sunset <= sunrise) return if (isDayFallback) 1f else 0f
        // Distance (seconds) into the day from the nearest edge; negative at night.
        val inside = minOf(t - sunrise, sunset - t).toFloat()
        return smoothstep(-TWILIGHT_SEC, TWILIGHT_SEC, inside)
    }

    fun sunProgress(t: Long, sunrise: Long?, sunset: Long?, isDayFallback: Boolean): Float {
        if (sunrise == null || sunset == null || sunset <= sunrise) return if (isDayFallback) 0.5f else -0.5f
        return ((t - sunrise).toDouble() / (sunset - sunrise)).toFloat()
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

enum class MoonPhaseName { New, WaxingCrescent, FirstQuarter, WaxingGibbous, Full, WaningGibbous, LastQuarter, WaningCrescent;

    companion object {
        fun of(phase: Float): MoonPhaseName {
            val p = ((phase % 1f) + 1f) % 1f
            return when {
                p < 0.033f || p >= 0.967f -> New
                p < 0.216f -> WaxingCrescent
                p < 0.284f -> FirstQuarter
                p < 0.466f -> WaxingGibbous
                p < 0.534f -> Full
                p < 0.716f -> WaningGibbous
                p < 0.784f -> LastQuarter
                else -> WaningCrescent
            }
        }
    }
}
