package app.rosa.weather.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class TemperatureUnit { Celsius, Fahrenheit }

@Serializable
enum class WindUnit { MetersPerSecond, KilometersPerHour, MilesPerHour, Knots, Beaufort }

@Serializable
enum class PressureUnit { MillimetersOfMercury, Hectopascal, InchesOfMercury }

@Serializable
enum class PrecipitationUnit { Millimeters, Inches }

@Serializable
enum class DistanceUnit { Kilometers, Miles }

@Serializable
data class Units(
    val temperature: TemperatureUnit = TemperatureUnit.Celsius,
    val wind: WindUnit = WindUnit.MetersPerSecond,
    val pressure: PressureUnit = PressureUnit.MillimetersOfMercury,
    val precipitation: PrecipitationUnit = PrecipitationUnit.Millimeters,
    val distance: DistanceUnit = DistanceUnit.Kilometers,
) {
    fun temperature(celsius: Double): Double = when (temperature) {
        TemperatureUnit.Celsius -> celsius
        TemperatureUnit.Fahrenheit -> celsius * 9.0 / 5.0 + 32.0
    }

    fun wind(metersPerSecond: Double): Double = when (wind) {
        WindUnit.MetersPerSecond -> metersPerSecond
        WindUnit.KilometersPerHour -> metersPerSecond * 3.6
        WindUnit.MilesPerHour -> metersPerSecond * 2.236_936
        WindUnit.Knots -> metersPerSecond * 1.943_844
        WindUnit.Beaufort -> beaufort(metersPerSecond).toDouble()
    }

    fun pressure(hectopascal: Double): Double = when (pressure) {
        PressureUnit.Hectopascal -> hectopascal
        PressureUnit.MillimetersOfMercury -> hectopascal * 0.750_061_683
        PressureUnit.InchesOfMercury -> hectopascal * 0.029_529_983
    }

    fun precipitation(mm: Double): Double = when (precipitation) {
        PrecipitationUnit.Millimeters -> mm
        PrecipitationUnit.Inches -> mm / 25.4
    }

    fun distance(meters: Double): Double = when (distance) {
        DistanceUnit.Kilometers -> meters / 1000.0
        DistanceUnit.Miles -> meters / 1609.344
    }

    /** Rounded temperature, never "-0". */
    fun roundedTemperature(celsius: Double): Int = temperature(celsius).roundToInt().let { if (it == 0) 0 else it }

    companion object {
        /** Sensible defaults per region: Russia and most of Europe use m/s & mmHg / hPa. */
        fun forCountry(countryCode: String?): Units = when (countryCode?.uppercase()) {
            "US", "LR", "MM" -> Units(
                TemperatureUnit.Fahrenheit, WindUnit.MilesPerHour, PressureUnit.InchesOfMercury,
                PrecipitationUnit.Inches, DistanceUnit.Miles,
            )
            "GB" -> Units(TemperatureUnit.Celsius, WindUnit.MilesPerHour, PressureUnit.Hectopascal, distance = DistanceUnit.Miles)
            "RU", "BY", "KZ", "UA", "KG", "UZ", "AM", "AZ", "GE", "MD", "TJ", "TM" -> Units()
            null -> Units()
            else -> Units(pressure = PressureUnit.Hectopascal, wind = WindUnit.KilometersPerHour)
        }

        fun beaufort(metersPerSecond: Double): Int {
            val limits = doubleArrayOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)
            val idx = limits.indexOfFirst { metersPerSecond < it }
            return if (idx < 0) 12 else idx
        }
    }
}

/** Compass point index 0..15 (N, NNE, NE, …) for a meteorological wind direction. */
fun compassPoint(degrees: Int): Int = (((degrees % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16
