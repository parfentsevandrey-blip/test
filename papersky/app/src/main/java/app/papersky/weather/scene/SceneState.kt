package app.papersky.weather.scene

import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.model.momentAt

/**
 * Everything the paper diorama needs, as continuous numbers. Continuous on purpose: the app
 * animates between states (e.g. while scrubbing the hourly ribbon) by interpolating each field.
 */
data class SceneState(
    val daylight: Float = 1f,
    val sunProgress: Float = 0.5f,
    val nightProgress: Float = 0.5f,
    val cloudCover: Float = 0.2f,
    val rain: Float = 0f,
    val drizzle: Float = 0f,
    val snow: Float = 0f,
    val hail: Float = 0f,
    val thunder: Float = 0f,
    val fog: Float = 0f,
    /** Horizontal wind component in m/s, positive = blowing to the right (east). */
    val windX: Float = 1f,
    val windSpeed: Float = 2f,
    val temperature: Float = 15f,
    val moonPhase: Float = 0.5f,
    val rainbow: Float = 0f,
    /** Snow lying on the ground (roofs, hills, trees). */
    val snowGround: Float = 0f,
    val seed: Int = 7,
) {
    fun lerp(to: SceneState, t: Float): SceneState {
        fun l(a: Float, b: Float) = a + (b - a) * t
        return SceneState(
            daylight = l(daylight, to.daylight),
            sunProgress = l(sunProgress, to.sunProgress),
            nightProgress = l(nightProgress, to.nightProgress),
            cloudCover = l(cloudCover, to.cloudCover),
            rain = l(rain, to.rain),
            drizzle = l(drizzle, to.drizzle),
            snow = l(snow, to.snow),
            hail = l(hail, to.hail),
            thunder = l(thunder, to.thunder),
            fog = l(fog, to.fog),
            windX = l(windX, to.windX),
            windSpeed = l(windSpeed, to.windSpeed),
            temperature = l(temperature, to.temperature),
            moonPhase = to.moonPhase,
            rainbow = l(rainbow, to.rainbow),
            snowGround = l(snowGround, to.snowGround),
            seed = to.seed,
        )
    }

    companion object {
        fun seedFor(placeId: String): Int = placeId.hashCode() and 0x7FFFFFFF

        fun from(m: WeatherMoment, seed: Int, forecast: Forecast? = null): SceneState {
            val p = m.precip
            val c = m.condition
            val dirRad = Math.toRadians(m.windDirection.toDouble())
            // Meteorological direction is where the wind comes FROM; it blows toward dir + 180°.
            val windX = (-kotlin.math.sin(dirRad) * m.windSpeed).toFloat()
            val visibility = m.visibility ?: 20_000.0
            val fog = maxOf(
                if (c == Condition.Fog) 0.85f else 0f,
                1f - smoothstep(700f, 6_000f, visibility.toFloat()),
            )

            val rise = m.sunrise
            val set = m.sunset
            val nightProgress = if (rise != null && set != null && set > rise) {
                val nightLen = (86_400 - (set - rise)).coerceAtLeast(3_600).toFloat()
                when {
                    m.epochSec < rise -> 1f - (rise - m.epochSec) / nightLen
                    m.epochSec > set -> (m.epochSec - set) / nightLen
                    else -> 0f
                }.coerceIn(0f, 1f)
            } else 0.5f

            val snowGround = forecast?.let { snowOnGround(it, m) } ?: if (c.isSnowy) 0.8f else 0f

            return SceneState(
                daylight = m.daylight,
                sunProgress = m.sunProgress,
                nightProgress = nightProgress,
                cloudCover = (m.cloudCover / 100f).coerceIn(0f, 1f),
                rain = p.rain,
                drizzle = p.drizzle,
                snow = p.snow,
                hail = p.hail,
                thunder = if (c.isStormy) 1f else 0f,
                fog = fog.coerceIn(0f, 1f),
                windX = windX,
                windSpeed = m.windSpeed.toFloat(),
                temperature = m.temperature.toFloat(),
                moonPhase = m.moonPhase,
                rainbow = forecast?.let { rainbowChance(it, m) } ?: 0f,
                snowGround = snowGround,
                seed = seed,
            )
        }

        fun of(forecast: Forecast, epochSec: Long, preferLive: Boolean = true): SceneState? {
            val m = forecast.momentAt(epochSec, preferLive) ?: return null
            return from(m, seedFor(forecast.placeId), forecast)
        }

        /** After a shower, with a low sun behind the viewer and the sky opening up: a rainbow. */
        private fun rainbowChance(f: Forecast, m: WeatherMoment): Float {
            if (m.daylight < 0.9f || m.precipitation > 0.05 || m.cloudCover > 80) return 0f
            val lowSun = m.sunProgress < 0.3f || m.sunProgress > 0.7f
            if (!lowSun) return 0f
            val i = f.hourIndexAt(m.epochSec)
            val recentRain = (1..2).any { k -> (f.hourly.getOrNull(i - k)?.precipitation ?: 0.0) >= 0.3 }
            return if (recentRain) 1f else 0f
        }

        /** Snow stays on the ground while it's cold after snowfall in the last day or so. */
        private fun snowOnGround(f: Forecast, m: WeatherMoment): Float {
            if (m.condition.isSnowy) return 1f
            if (m.temperature > 2.5) return 0f
            val i = f.hourIndexAt(m.epochSec)
            val snowed = (1..30).any { k ->
                val h = f.hourly.getOrNull(i - k) ?: return@any false
                Condition.fromWmo(h.code).isSnowy && h.precipitation > 0.05
            }
            return if (snowed || m.temperature < -3) 0.9f else 0f
        }
    }
}
