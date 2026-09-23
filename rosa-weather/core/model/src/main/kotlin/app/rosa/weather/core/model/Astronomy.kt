package app.rosa.weather.core.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Horizontal coordinates, degrees. Azimuth is measured clockwise from north. */
data class SkyPosition(val elevation: Double, val azimuth: Double)

/**
 * @property phase 0 = new moon, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter.
 * @property illumination illuminated fraction of the disc, 0..1.
 */
data class MoonPhase(val phase: Double, val illumination: Double) {
    val isWaxing: Boolean get() = phase < 0.5
}

/**
 * Low-precision solar & lunar ephemeris (≈1° accuracy), plenty for placing the sun and moon
 * in the animated sky and deciding on day/twilight/night palettes. Based on the formulas from
 * the Astronomical Almanac as popularised by NOAA and suncalc.
 */
object Astronomy {
    private const val RAD = PI / 180.0
    private const val J2000 = 2_451_545.0
    private const val OBLIQUITY = 23.4397 * RAD
    private const val SYNODIC_MONTH = 29.530_588_853
    private const val KNOWN_NEW_MOON_JD = 2_451_550.259_7

    fun julianDay(epochSeconds: Long): Double = epochSeconds / 86_400.0 + 2_440_587.5

    fun sun(epochSeconds: Long, latitude: Double, longitude: Double): SkyPosition {
        val d = julianDay(epochSeconds) - J2000
        val g = normalizeDegrees(357.529 + 0.985_600_28 * d) * RAD
        val q = normalizeDegrees(280.459 + 0.985_647_36 * d)
        val lambda = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * RAD
        val epsilon = (23.439 - 0.000_000_36 * d) * RAD
        val ra = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val dec = asin(sin(epsilon) * sin(lambda))
        return horizontal(d, latitude, longitude, ra, dec)
    }

    fun moon(epochSeconds: Long, latitude: Double, longitude: Double): SkyPosition {
        val d = julianDay(epochSeconds) - J2000
        val l = (218.316 + 13.176_396 * d) * RAD
        val m = (134.963 + 13.064_993 * d) * RAD
        val f = (93.272 + 13.229_350 * d) * RAD
        val lambda = l + 6.289 * RAD * sin(m)
        val beta = 5.128 * RAD * sin(f)
        val ra = atan2(sin(lambda) * cos(OBLIQUITY) - tan(beta) * sin(OBLIQUITY), cos(lambda))
        val dec = asin(sin(beta) * cos(OBLIQUITY) + cos(beta) * sin(OBLIQUITY) * sin(lambda))
        return horizontal(d, latitude, longitude, ra, dec)
    }

    fun moonPhase(epochSeconds: Long): MoonPhase {
        val age = (julianDay(epochSeconds) - KNOWN_NEW_MOON_JD) / SYNODIC_MONTH
        val phase = age - floor(age)
        val illumination = (1 - cos(2 * PI * phase)) / 2
        return MoonPhase(phase, illumination)
    }

    private fun horizontal(d: Double, latitude: Double, longitude: Double, ra: Double, dec: Double): SkyPosition {
        val lat = latitude * RAD
        val siderealTime = normalizeDegrees(280.160_47 + 360.985_623_5 * d + longitude) * RAD
        val h = siderealTime - ra
        val elevation = asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(h))
        val azimuth = atan2(-cos(dec) * sin(h), sin(dec) * cos(lat) - cos(dec) * cos(h) * sin(lat))
        return SkyPosition(elevation / RAD, normalizeDegrees(azimuth / RAD))
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}

/** Coarse phase of the day, derived from the true solar elevation. */
enum class DayPhase {
    Night, AstronomicalTwilight, BlueHour, GoldenHour, Day;

    val isDaylight: Boolean get() = this == GoldenHour || this == Day

    companion object {
        fun fromSunElevation(elevation: Double): DayPhase = when {
            elevation < -12 -> Night
            elevation < -6 -> AstronomicalTwilight
            elevation < -0.8 -> BlueHour
            elevation < 8 -> GoldenHour
            else -> Day
        }
    }
}
