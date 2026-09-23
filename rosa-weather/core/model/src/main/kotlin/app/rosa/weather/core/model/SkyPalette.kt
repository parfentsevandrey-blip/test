package app.rosa.weather.core.model

/**
 * The colour story of the sky at a given sun elevation and weather. One palette feeds the AGSL
 * sky in the app, the Canvas-rendered widgets and the UI accents, so the whole product shifts
 * mood together — apricot at dawn, milky lilac in snow, ink-indigo at night.
 *
 * Colours were picked as a filmic, slightly warm grade rather than "physically correct" blues:
 * the goal is cosy, not a weather-station readout.
 */
data class SkyPalette(
    val zenith: Argb,
    val horizon: Argb,
    val glow: Argb,
    val sun: Argb,
    val cloudLight: Argb,
    val cloudShade: Argb,
    val ink: Argb,
    val inkSoft: Argb,
    val accent: Argb,
    val warm: Argb,
    val cool: Argb,
) {
    /** True when the sky is bright enough that text must switch to dark ink. */
    val isLight: Boolean get() = ink.luminance < 0.2

    companion object {
        private class Key(val elevation: Double, val zenith: Long, val horizon: Long, val glow: Long, val sun: Long)

        // Sun elevation (deg) → clear-sky colours.
        private val clearKeys = listOf(
            Key(-18.0, 0x05081A, 0x121637, 0x1C1B45, 0xC9D2FF),
            Key(-12.0, 0x0A0F2E, 0x221F55, 0x3B2C66, 0xC9D2FF),
            Key(-7.0, 0x141D4C, 0x4A3F86, 0x9C6696, 0xFFC9C0),
            Key(-3.0, 0x233A7E, 0x9B6B9E, 0xF0928A, 0xFFB387),
            Key(0.0, 0x3558A2, 0xE88C7A, 0xFFB077, 0xFFC27A),
            Key(4.0, 0x4577BF, 0xF4B08A, 0xFFD39A, 0xFFE0A8),
            Key(10.0, 0x3E82D6, 0xB7D3EE, 0xFFE9C4, 0xFFF2D6),
            Key(25.0, 0x2C76D8, 0x9BCBF6, 0xFFF6E3, 0xFFFBF0),
            Key(60.0, 0x1E6ACF, 0x8CC3F7, 0xFFFFFF, 0xFFFFFF),
        )

        fun of(sunElevation: Double, visual: WeatherVisual, moonIllumination: Double = 0.5): SkyPalette {
            val (a, b, t) = bracket(sunElevation)
            var zenith = Argb.hex(a.zenith).lerp(Argb.hex(b.zenith), t)
            var horizon = Argb.hex(a.horizon).lerp(Argb.hex(b.horizon), t)
            var glow = Argb.hex(a.glow).lerp(Argb.hex(b.glow), t)
            val sun = Argb.hex(a.sun).lerp(Argb.hex(b.sun), t)

            val daylight = ((sunElevation + 8.0) / 18.0).coerceIn(0.0, 1.0).toFloat()
            val night = 1f - daylight

            // Moonlight gently lifts a clear night sky.
            if (night > 0f) {
                val lift = (moonIllumination.toFloat() * 0.18f * night * (1f - visual.cloudCover))
                zenith = zenith.lerp(Argb.hex(0x1B2A5C), lift)
                horizon = horizon.lerp(Argb.hex(0x33407A), lift)
            }

            // Overcast: warm greys by day, deep slate by night. Rain & storms push darker/cooler.
            val overcast = (visual.cloudCover - 0.45f).coerceAtLeast(0f) / 0.55f
            val dark = visual.cloudDarkness
            val greyDay = Argb.hex(0x8C95A3).lerp(Argb.hex(0x4B5566), dark)
            val greyDayLow = Argb.hex(0xC7C9CC).lerp(Argb.hex(0x7C8592), dark)
            val greyNight = Argb.hex(0x151A26).lerp(Argb.hex(0x0B0D14), dark)
            val greyNightLow = Argb.hex(0x2A2E3D).lerp(Argb.hex(0x1A1C26), dark)
            val cloudZenith = greyNight.lerp(greyDay, daylight)
            val cloudHorizon = greyNightLow.lerp(greyDayLow, daylight)
            zenith = zenith.lerp(cloudZenith, overcast * 0.9f)
            horizon = horizon.lerp(cloudHorizon, overcast * 0.85f)
            glow = glow.lerp(cloudHorizon, overcast * 0.7f)

            // Snow: lilac-white milk. Fog: flat pearl.
            if (visual.snow > 0f) {
                val snowTop = Argb.hex(0x1C2238).lerp(Argb.hex(0xAEB6CC), daylight)
                val snowLow = Argb.hex(0x3A3F58).lerp(Argb.hex(0xE9ECF4), daylight)
                zenith = zenith.lerp(snowTop, visual.snow * 0.7f)
                horizon = horizon.lerp(snowLow, visual.snow * 0.75f)
            }
            if (visual.fog > 0f) {
                val pearl = Argb.hex(0x2A2C34).lerp(Argb.hex(0xD6D3CE), daylight)
                zenith = zenith.lerp(pearl, visual.fog * 0.6f)
                horizon = horizon.lerp(pearl, visual.fog * 0.85f)
                glow = glow.lerp(pearl, visual.fog * 0.6f)
            }
            if (visual.lightning > 0f) {
                zenith = zenith.lerp(Argb.hex(0x1D1830), 0.5f * daylight + 0.3f)
                horizon = horizon.lerp(Argb.hex(0x46405C), 0.4f)
            }

            val cloudLight = Argb.hex(0x2F3448).lerp(Argb.hex(0xFFF8F0), daylight)
                .lerp(glow, 0.25f * (1f - overcast))
                .lerp(Argb.hex(0x6C7280), dark * 0.6f)
            val cloudShade = Argb.hex(0x0D1020).lerp(Argb.hex(0x9AA3B5), daylight)
                .lerp(Argb.hex(0x3A4150), dark * 0.7f)

            val mid = zenith.lerp(horizon, 0.55f)
            val light = mid.luminance > 0.42
            val ink = if (light) Argb.hex(0x1B2030) else Argb.hex(0xFFFBF5)
            val inkSoft = if (light) Argb.hex(0x1B2030).withAlpha(0.66f) else Argb.hex(0xFFFBF5).withAlpha(0.72f)

            val warm = Argb.hex(0xFFB26B).lerp(sun, 0.3f)
            val cool = Argb.hex(0x7FB6FF).lerp(zenith, 0.2f)
            val accent = when {
                visual.lightning > 0f -> Argb.hex(0xC7B3FF)
                visual.snow > 0.3f -> Argb.hex(0xB9D4FF)
                visual.rain > 0.3f -> Argb.hex(0x8CC8F2)
                night > 0.6f -> Argb.hex(0xC6CCFF)
                sunElevation < 8 -> Argb.hex(0xFFB48A)
                else -> Argb.hex(0xFFD37A)
            }
            return SkyPalette(zenith, horizon, glow, sun, cloudLight, cloudShade, ink, inkSoft, accent, warm, cool)
        }

        private fun bracket(elevation: Double): Triple<Key, Key, Float> {
            if (elevation <= clearKeys.first().elevation) return Triple(clearKeys.first(), clearKeys.first(), 0f)
            if (elevation >= clearKeys.last().elevation) return Triple(clearKeys.last(), clearKeys.last(), 0f)
            val hi = clearKeys.indexOfFirst { it.elevation >= elevation }
            val a = clearKeys[hi - 1]
            val b = clearKeys[hi]
            val t = ((elevation - a.elevation) / (b.elevation - a.elevation)).toFloat()
            // Smoothstep keeps the twilight transitions from looking linear.
            return Triple(a, b, t * t * (3 - 2 * t))
        }
    }
}

/** Temperature → colour scale for range bars and curves (cold violet → mint → amber → coral). */
object TemperatureScale {
    private val stops = listOf(
        -30.0 to 0x8E7CFF,
        -15.0 to 0x7FA6FF,
        -3.0 to 0x7ED8F2,
        5.0 to 0x8EE6C4,
        14.0 to 0xD8E98A,
        22.0 to 0xFFD27A,
        29.0 to 0xFFA56B,
        36.0 to 0xFF6F6B,
    )

    fun colorFor(celsius: Double): Argb {
        if (celsius <= stops.first().first) return Argb.hex(stops.first().second.toLong())
        if (celsius >= stops.last().first) return Argb.hex(stops.last().second.toLong())
        val hi = stops.indexOfFirst { it.first >= celsius }
        val (t0, c0) = stops[hi - 1]
        val (t1, c1) = stops[hi]
        return Argb.hex(c0.toLong()).lerp(Argb.hex(c1.toLong()), ((celsius - t0) / (t1 - t0)).toFloat())
    }
}
