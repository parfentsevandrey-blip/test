package app.papersky.weather.core.model

/** Human-scale weather conditions derived from WMO weather interpretation codes. */
enum class Condition {
    Clear,
    MostlyClear,
    PartlyCloudy,
    Overcast,
    Fog,
    Drizzle,
    FreezingDrizzle,
    Rain,
    HeavyRain,
    FreezingRain,
    Snow,
    HeavySnow,
    SnowGrains,
    RainShowers,
    SnowShowers,
    Thunderstorm,
    ThunderHail;

    val isWet: Boolean
        get() = this in setOf(Drizzle, FreezingDrizzle, Rain, HeavyRain, FreezingRain, RainShowers, Thunderstorm, ThunderHail)

    val isSnowy: Boolean
        get() = this in setOf(Snow, HeavySnow, SnowGrains, SnowShowers)

    val isStormy: Boolean get() = this == Thunderstorm || this == ThunderHail

    companion object {
        fun fromWmo(code: Int): Condition = when (code) {
            0 -> Clear
            1 -> MostlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45, 48 -> Fog
            51, 53, 55 -> Drizzle
            56, 57 -> FreezingDrizzle
            61, 63 -> Rain
            65 -> HeavyRain
            66, 67 -> FreezingRain
            71, 73 -> Snow
            75 -> HeavySnow
            77 -> SnowGrains
            80, 81, 82 -> RainShowers
            85, 86 -> SnowShowers
            95 -> Thunderstorm
            96, 99 -> ThunderHail
            else -> if (code > 3) Overcast else Clear
        }
    }
}

/**
 * How much of each kind of precipitation is falling, 0..1 each. Separate channels (instead of a
 * single enum) let scenes cross-fade smoothly from rain to snow while scrubbing through time.
 */
data class Precipitation(
    val rain: Float = 0f,
    val drizzle: Float = 0f,
    val snow: Float = 0f,
    val hail: Float = 0f,
) {
    val total: Float get() = (rain + drizzle + snow + hail).coerceAtMost(1f)

    companion object {
        val None = Precipitation()

        /**
         * Channels from a WMO code, boosted by the measured amount in mm/h so a "slight rain" code
         * with a real downpour still looks heavy.
         */
        fun from(code: Int, mmPerHour: Double): Precipitation {
            val measured = when {
                mmPerHour <= 0.0 -> 0f
                else -> (0.25f + (mmPerHour / 6.0).toFloat()).coerceAtMost(1f)
            }
            fun level(base: Float) = maxOf(base, measured)
            return when (code) {
                51 -> Precipitation(drizzle = level(0.35f))
                53 -> Precipitation(drizzle = level(0.55f))
                55 -> Precipitation(drizzle = level(0.8f))
                56, 57 -> Precipitation(drizzle = level(0.5f), hail = 0.15f)
                61 -> Precipitation(rain = level(0.35f))
                63 -> Precipitation(rain = level(0.6f))
                65 -> Precipitation(rain = level(0.95f))
                66 -> Precipitation(rain = level(0.4f), hail = 0.2f)
                67 -> Precipitation(rain = level(0.7f), hail = 0.3f)
                71 -> Precipitation(snow = level(0.35f))
                73 -> Precipitation(snow = level(0.6f))
                75 -> Precipitation(snow = level(0.95f))
                77 -> Precipitation(snow = 0.3f, hail = 0.2f)
                80 -> Precipitation(rain = level(0.4f))
                81 -> Precipitation(rain = level(0.65f))
                82 -> Precipitation(rain = level(1f))
                85 -> Precipitation(snow = level(0.5f))
                86 -> Precipitation(snow = level(0.9f))
                95 -> Precipitation(rain = level(0.75f))
                96 -> Precipitation(rain = level(0.7f), hail = 0.35f)
                99 -> Precipitation(rain = level(0.85f), hail = 0.6f)
                else -> None
            }
        }
    }
}
