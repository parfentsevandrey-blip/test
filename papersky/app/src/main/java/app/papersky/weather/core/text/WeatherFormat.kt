package app.papersky.weather.core.text

import android.content.Context
import android.text.format.DateFormat
import app.papersky.weather.R
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.MoonPhaseName
import app.papersky.weather.core.model.PrecipUnit
import app.papersky.weather.core.model.PressureUnit
import app.papersky.weather.core.model.TempUnit
import app.papersky.weather.core.model.Units
import app.papersky.weather.core.model.WindUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Unit conversion helpers, kept free of Android types so they are unit-testable. */
object Convert {
    fun temp(celsius: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.Celsius -> celsius
        TempUnit.Fahrenheit -> celsius * 9.0 / 5.0 + 32.0
    }

    fun wind(ms: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.MetersPerSecond -> ms
        WindUnit.KilometersPerHour -> ms * 3.6
        WindUnit.MilesPerHour -> ms * 2.236936
        WindUnit.Knots -> ms * 1.943844
    }

    fun pressure(hpa: Double, unit: PressureUnit): Double = when (unit) {
        PressureUnit.Hectopascal -> hpa
        PressureUnit.MillimetersOfMercury -> hpa * 0.750062
        PressureUnit.InchesOfMercury -> hpa * 0.0295300
    }

    fun precip(mm: Double, unit: PrecipUnit): Double = when (unit) {
        PrecipUnit.Millimeters -> mm
        PrecipUnit.Inches -> mm / 25.4
    }

    /** Rounded value with a true minus sign (U+2212) and no "-0". */
    fun signed(value: Int): String = when {
        value < 0 -> "−${abs(value)}"
        else -> value.toString()
    }

    fun roundTemp(celsius: Double, unit: TempUnit): Int {
        val r = temp(celsius, unit).roundToInt()
        return if (r == 0) 0 else r
    }
}

/**
 * Everything the UI and widgets need to turn metric numbers into localized, unit-aware text.
 * Times are always rendered in the forecast location's zone, not the device zone.
 */
class WeatherFormat(
    private val context: Context,
    val units: Units,
    val zone: ZoneId,
    private val locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
) {
    private val is24h = DateFormat.is24HourFormat(context)
    private val timeFormatter = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
    private val hourFormatter = DateTimeFormatter.ofPattern(if (is24h) "HH" else "h a", locale)
    private val dateFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "d MMMM"), locale)

    fun temp(celsius: Double): String = Convert.signed(Convert.roundTemp(celsius, units.temperature)) + "°"

    fun tempNumber(celsius: Double): String = Convert.signed(Convert.roundTemp(celsius, units.temperature))

    fun tempUnitSymbol(): String = if (units.temperature == TempUnit.Celsius) "°C" else "°F"

    fun tempDelta(celsiusDelta: Double): String {
        val v = if (units.temperature == TempUnit.Fahrenheit) celsiusDelta * 9 / 5 else celsiusDelta
        return "${abs(v).roundToInt()}°"
    }

    fun windNumber(ms: Double): String = Convert.wind(ms, units.wind).roundToInt().toString()

    fun windUnit(): String = context.getString(
        when (units.wind) {
            WindUnit.MetersPerSecond -> R.string.unit_ms
            WindUnit.KilometersPerHour -> R.string.unit_kmh
            WindUnit.MilesPerHour -> R.string.unit_mph
            WindUnit.Knots -> R.string.unit_kn
        },
    )

    fun wind(ms: Double): String = "${windNumber(ms)} ${windUnit()}"

    fun pressureNumber(hpa: Double): String {
        val v = Convert.pressure(hpa, units.pressure)
        return if (units.pressure == PressureUnit.InchesOfMercury) "%.2f".format(locale, v) else v.roundToInt().toString()
    }

    fun pressureUnit(): String = context.getString(
        when (units.pressure) {
            PressureUnit.Hectopascal -> R.string.unit_hpa
            PressureUnit.MillimetersOfMercury -> R.string.unit_mmhg
            PressureUnit.InchesOfMercury -> R.string.unit_inhg
        },
    )

    fun pressure(hpa: Double): String = "${pressureNumber(hpa)} ${pressureUnit()}"

    fun precip(mm: Double): String {
        val v = Convert.precip(mm, units.precipitation)
        val unit = context.getString(if (units.precipitation == PrecipUnit.Millimeters) R.string.unit_mm else R.string.unit_in)
        val number = when {
            units.precipitation == PrecipUnit.Inches -> "%.2f".format(locale, v)
            v < 10 -> "%.1f".format(locale, v)
            else -> v.roundToInt().toString()
        }
        return "$number $unit"
    }

    fun percent(p: Int): String = "$p%"

    fun visibility(meters: Double): String = if (meters >= 10_000) {
        context.getString(R.string.visibility_km, (meters / 1000).roundToInt().toString())
    } else {
        context.getString(R.string.visibility_km, "%.1f".format(locale, meters / 1000))
    }

    fun time(epochSec: Long): String = timeFormatter.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    fun hour(epochSec: Long): String = hourFormatter.format(Instant.ofEpochSecond(epochSec).atZone(zone)).lowercase(locale)

    fun date(epochSec: Long): String = dateFormatter.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    fun localDate(epochSec: Long): LocalDate = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate()

    /** "Today", "Tomorrow" or a weekday name relative to [nowSec] in the forecast zone. */
    fun dayName(epochSec: Long, nowSec: Long, short: Boolean = false): String {
        val day = localDate(epochSec)
        val today = localDate(nowSec)
        return when (day) {
            today -> context.getString(if (short) R.string.today_short else R.string.today)
            today.plusDays(1) -> context.getString(if (short) R.string.tomorrow_short else R.string.tomorrow)
            else -> day.dayOfWeek.getDisplayName(if (short) TextStyle.SHORT_STANDALONE else TextStyle.FULL_STANDALONE, locale)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }

    fun weekdayShort(epochSec: Long): String =
        localDate(epochSec).dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return context.getString(R.string.duration_hm, h.toInt(), m.toInt())
    }

    fun compass(degrees: Int): String {
        val names = context.resources.getStringArray(R.array.compass_points)
        val idx = (((degrees % 360) + 360) % 360 + 22) / 45 % 8
        return names[idx]
    }

    fun condition(condition: Condition): String = context.getString(conditionRes(condition))

    fun moonPhase(phase: Float): String = context.getString(
        when (MoonPhaseName.of(phase)) {
            MoonPhaseName.New -> R.string.moon_new
            MoonPhaseName.WaxingCrescent -> R.string.moon_waxing_crescent
            MoonPhaseName.FirstQuarter -> R.string.moon_first_quarter
            MoonPhaseName.WaxingGibbous -> R.string.moon_waxing_gibbous
            MoonPhaseName.Full -> R.string.moon_full
            MoonPhaseName.WaningGibbous -> R.string.moon_waning_gibbous
            MoonPhaseName.LastQuarter -> R.string.moon_last_quarter
            MoonPhaseName.WaningCrescent -> R.string.moon_waning_crescent
        },
    )

    companion object {
        fun conditionRes(condition: Condition): Int = when (condition) {
            Condition.Clear -> R.string.cond_clear
            Condition.MostlyClear -> R.string.cond_mostly_clear
            Condition.PartlyCloudy -> R.string.cond_partly_cloudy
            Condition.Overcast -> R.string.cond_overcast
            Condition.Fog -> R.string.cond_fog
            Condition.Drizzle -> R.string.cond_drizzle
            Condition.FreezingDrizzle -> R.string.cond_freezing_drizzle
            Condition.Rain -> R.string.cond_rain
            Condition.HeavyRain -> R.string.cond_heavy_rain
            Condition.FreezingRain -> R.string.cond_freezing_rain
            Condition.Snow -> R.string.cond_snow
            Condition.HeavySnow -> R.string.cond_heavy_snow
            Condition.SnowGrains -> R.string.cond_snow_grains
            Condition.RainShowers -> R.string.cond_rain_showers
            Condition.SnowShowers -> R.string.cond_snow_showers
            Condition.Thunderstorm -> R.string.cond_thunderstorm
            Condition.ThunderHail -> R.string.cond_thunder_hail
        }
    }
}
