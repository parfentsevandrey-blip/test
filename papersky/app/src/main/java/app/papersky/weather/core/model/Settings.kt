package app.papersky.weather.core.model

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class TempUnit { Celsius, Fahrenheit }

@Serializable
enum class WindUnit { MetersPerSecond, KilometersPerHour, MilesPerHour, Knots }

@Serializable
enum class PressureUnit { Hectopascal, MillimetersOfMercury, InchesOfMercury }

@Serializable
enum class PrecipUnit { Millimeters, Inches }

@Serializable
enum class MotionLevel { Full, Gentle, Still }

/**
 * The app's theme (DESIGN_DOCTRINE §16): a room with a fire burning and the weather in the window,
 * the living sky over the mountains, or Ophelia's river.
 */
@Serializable
enum class AppTheme { Hearth, Sky, Ophelia }

@Serializable
data class Units(
    val temperature: TempUnit = TempUnit.Celsius,
    val wind: WindUnit = WindUnit.MetersPerSecond,
    val pressure: PressureUnit = PressureUnit.Hectopascal,
    val precipitation: PrecipUnit = PrecipUnit.Millimeters,
) {
    companion object {
        /** Sensible regional defaults for a first launch. */
        fun defaultsFor(locale: Locale): Units = when (locale.country.uppercase()) {
            "US", "LR", "MM" -> Units(TempUnit.Fahrenheit, WindUnit.MilesPerHour, PressureUnit.InchesOfMercury, PrecipUnit.Inches)
            "GB" -> Units(TempUnit.Celsius, WindUnit.MilesPerHour, PressureUnit.Hectopascal, PrecipUnit.Millimeters)
            "RU", "BY", "KZ", "UA", "UZ", "KG", "TJ", "AM", "AZ", "GE", "MD" ->
                Units(TempUnit.Celsius, WindUnit.MetersPerSecond, PressureUnit.MillimetersOfMercury, PrecipUnit.Millimeters)
            else -> Units(TempUnit.Celsius, WindUnit.KilometersPerHour, PressureUnit.Hectopascal, PrecipUnit.Millimeters)
        }
    }
}

@Serializable
data class UserSettings(
    val units: Units? = null,
    val updateIntervalMinutes: Int = 30,
    val haptics: Boolean = true,
    val hapticStrength: Float = 1f,
    val motion: MotionLevel = MotionLevel.Full,
    val tiltParallax: Boolean = true,
    /** Houses in silhouette on the meadow of the print. */
    val village: Boolean = true,
    val theme: AppTheme = AppTheme.Hearth,
    val selectedPlaceId: String = Place.HERE,
    val backgroundLocation: Boolean = false,
    val onboarded: Boolean = false,
) {
    fun resolvedUnits(locale: Locale = Locale.getDefault()): Units = units ?: Units.defaultsFor(locale)

    companion object {
        val IntervalChoices = listOf(15, 30, 60, 120, 180)
    }
}
