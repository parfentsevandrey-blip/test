package app.rosa.weather.core.model

/**
 * Human-level weather condition derived from a WMO 4677 weather interpretation code, as
 * returned by Open-Meteo. The enum is intentionally coarse: presentation layers pick labels,
 * glyphs and scene parameters from it.
 */
enum class WeatherCondition(val family: Family) {
    Clear(Family.Clear),
    MostlyClear(Family.Clear),
    PartlyCloudy(Family.Cloudy),
    Overcast(Family.Cloudy),
    Fog(Family.Fog),
    RimeFog(Family.Fog),
    Drizzle(Family.Rain),
    FreezingDrizzle(Family.Ice),
    LightRain(Family.Rain),
    Rain(Family.Rain),
    HeavyRain(Family.Rain),
    FreezingRain(Family.Ice),
    LightSnow(Family.Snow),
    Snow(Family.Snow),
    HeavySnow(Family.Snow),
    SnowGrains(Family.Snow),
    RainShowers(Family.Rain),
    HeavyShowers(Family.Rain),
    SnowShowers(Family.Snow),
    Thunderstorm(Family.Storm),
    ThunderstormHail(Family.Storm);

    enum class Family { Clear, Cloudy, Fog, Rain, Ice, Snow, Storm }

    val isPrecipitation: Boolean
        get() = family == Family.Rain || family == Family.Snow || family == Family.Ice || family == Family.Storm

    companion object {
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0 -> Clear
            1 -> MostlyClear
            2 -> PartlyCloudy
            3 -> Overcast
            45 -> Fog
            48 -> RimeFog
            51, 53, 55 -> Drizzle
            56, 57 -> FreezingDrizzle
            61 -> LightRain
            63 -> Rain
            65 -> HeavyRain
            66, 67 -> FreezingRain
            71 -> LightSnow
            73 -> Snow
            75 -> HeavySnow
            77 -> SnowGrains
            80 -> RainShowers
            81, 82 -> HeavyShowers
            85, 86 -> SnowShowers
            95 -> Thunderstorm
            96, 99 -> ThunderstormHail
            else -> if (code in 4..49) Fog else Overcast
        }
    }
}

/**
 * Continuous scene parameters (all 0..1) that drive both the in-app animated sky and the
 * widget renderer, so the two always agree on what "this weather" looks like.
 */
data class WeatherVisual(
    val cloudCover: Float,
    val cloudDarkness: Float,
    val rain: Float,
    val snow: Float,
    val fog: Float,
    val lightning: Float,
    val hail: Float,
    val wind: Float,
) {
    val precipitation: Float get() = maxOf(rain, snow, hail)

    companion object {
        val ClearDay = WeatherVisual(0.05f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f)

        fun from(
            condition: WeatherCondition,
            cloudCoverPercent: Int,
            precipitationMm: Double = 0.0,
            windSpeedMs: Double = 0.0,
            humidity: Int = 50,
        ): WeatherVisual {
            val clouds = (cloudCoverPercent / 100f).coerceIn(0f, 1f)
            val amount = (precipitationMm / 6.0).toFloat().coerceIn(0f, 1f)
            fun rainOf(base: Float) = (base + amount * 0.5f).coerceIn(0f, 1f)
            val wind = (windSpeedMs / 18.0).toFloat().coerceIn(0f, 1f)
            val mist = ((humidity - 85) / 15f).coerceIn(0f, 1f) * 0.25f
            return when (condition) {
                WeatherCondition.Clear -> WeatherVisual(minOf(clouds, 0.12f), 0f, 0f, 0f, mist, 0f, 0f, wind)
                WeatherCondition.MostlyClear -> WeatherVisual(clouds.coerceIn(0.12f, 0.35f), 0.05f, 0f, 0f, mist, 0f, 0f, wind)
                WeatherCondition.PartlyCloudy -> WeatherVisual(clouds.coerceIn(0.35f, 0.65f), 0.12f, 0f, 0f, mist, 0f, 0f, wind)
                WeatherCondition.Overcast -> WeatherVisual(clouds.coerceAtLeast(0.85f), 0.35f, 0f, 0f, mist, 0f, 0f, wind)
                WeatherCondition.Fog -> WeatherVisual(0.7f, 0.2f, 0f, 0f, 0.85f, 0f, 0f, wind)
                WeatherCondition.RimeFog -> WeatherVisual(0.7f, 0.15f, 0f, 0.05f, 0.95f, 0f, 0f, wind)
                WeatherCondition.Drizzle -> WeatherVisual(0.9f, 0.35f, rainOf(0.2f), 0f, 0.25f, 0f, 0f, wind)
                WeatherCondition.FreezingDrizzle -> WeatherVisual(0.9f, 0.35f, rainOf(0.2f), 0.1f, 0.3f, 0f, 0f, wind)
                WeatherCondition.LightRain -> WeatherVisual(0.9f, 0.4f, rainOf(0.35f), 0f, 0.15f, 0f, 0f, wind)
                WeatherCondition.Rain -> WeatherVisual(0.95f, 0.5f, rainOf(0.6f), 0f, 0.15f, 0f, 0f, wind)
                WeatherCondition.HeavyRain -> WeatherVisual(1f, 0.65f, rainOf(0.9f), 0f, 0.2f, 0f, 0f, wind)
                WeatherCondition.FreezingRain -> WeatherVisual(0.95f, 0.5f, rainOf(0.55f), 0.15f, 0.2f, 0f, 0.1f, wind)
                WeatherCondition.LightSnow -> WeatherVisual(0.9f, 0.25f, 0f, 0.35f, 0.15f, 0f, 0f, wind)
                WeatherCondition.Snow -> WeatherVisual(0.95f, 0.3f, 0f, 0.65f, 0.2f, 0f, 0f, wind)
                WeatherCondition.HeavySnow -> WeatherVisual(1f, 0.4f, 0f, 0.95f, 0.35f, 0f, 0f, wind)
                WeatherCondition.SnowGrains -> WeatherVisual(0.9f, 0.3f, 0f, 0.4f, 0.2f, 0f, 0.1f, wind)
                WeatherCondition.RainShowers -> WeatherVisual(0.75f, 0.45f, rainOf(0.5f), 0f, 0.1f, 0f, 0f, wind)
                WeatherCondition.HeavyShowers -> WeatherVisual(0.85f, 0.6f, rainOf(0.85f), 0f, 0.15f, 0f, 0f, wind)
                WeatherCondition.SnowShowers -> WeatherVisual(0.8f, 0.3f, 0f, 0.6f, 0.15f, 0f, 0f, wind)
                WeatherCondition.Thunderstorm -> WeatherVisual(1f, 0.8f, rainOf(0.75f), 0f, 0.1f, 1f, 0f, maxOf(wind, 0.4f))
                WeatherCondition.ThunderstormHail -> WeatherVisual(1f, 0.85f, rainOf(0.6f), 0f, 0.1f, 1f, 0.7f, maxOf(wind, 0.5f))
            }
        }
    }
}
