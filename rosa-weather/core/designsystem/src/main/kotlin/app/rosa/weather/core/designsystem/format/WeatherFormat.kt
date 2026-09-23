package app.rosa.weather.core.designsystem.format

import android.content.Context
import android.text.format.DateFormat
import app.rosa.weather.core.designsystem.R
import app.rosa.weather.core.model.AirLevel
import app.rosa.weather.core.model.DistanceUnit
import app.rosa.weather.core.model.Headline
import app.rosa.weather.core.model.PrecipitationKind
import app.rosa.weather.core.model.PrecipitationUnit
import app.rosa.weather.core.model.PressureUnit
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WeatherCondition
import app.rosa.weather.core.model.WindUnit
import app.rosa.weather.core.model.compassPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Locale- and unit-aware formatting shared by the app UI and the widget renderer, so a value
 * reads identically everywhere ("−3°", "4 м/с", "757 мм рт. ст.").
 */
class WeatherFormat(
    private val context: Context,
    val units: Units,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    private val is24Hour = DateFormat.is24HourFormat(context)
    private val timeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale)
    private val hourFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "H" else "h a", locale)

    fun withZone(zone: ZoneId) = WeatherFormat(context, units, zone)

    /** "12°", "−3°" — typographic minus, no "−0". */
    fun temperature(celsius: Double): String = signed(units.roundedTemperature(celsius)) + "°"

    fun temperatureNumber(celsius: Double): String = signed(units.roundedTemperature(celsius))

    fun temperatureDelta(celsiusDelta: Int): String {
        val converted = if (units.temperature == app.rosa.weather.core.model.TemperatureUnit.Fahrenheit) {
            (celsiusDelta * 9 / 5.0).roundToInt()
        } else {
            celsiusDelta
        }
        return "${abs(converted)}°"
    }

    fun condition(condition: WeatherCondition, isDay: Boolean = true): String = context.getString(
        when (condition) {
            WeatherCondition.Clear -> if (isDay) R.string.condition_clear_day else R.string.condition_clear
            WeatherCondition.MostlyClear -> R.string.condition_mostly_clear
            WeatherCondition.PartlyCloudy -> R.string.condition_partly_cloudy
            WeatherCondition.Overcast -> R.string.condition_overcast
            WeatherCondition.Fog -> R.string.condition_fog
            WeatherCondition.RimeFog -> R.string.condition_rime_fog
            WeatherCondition.Drizzle -> R.string.condition_drizzle
            WeatherCondition.FreezingDrizzle -> R.string.condition_freezing_drizzle
            WeatherCondition.LightRain -> R.string.condition_light_rain
            WeatherCondition.Rain -> R.string.condition_rain
            WeatherCondition.HeavyRain -> R.string.condition_heavy_rain
            WeatherCondition.FreezingRain -> R.string.condition_freezing_rain
            WeatherCondition.LightSnow -> R.string.condition_light_snow
            WeatherCondition.Snow -> R.string.condition_snow
            WeatherCondition.HeavySnow -> R.string.condition_heavy_snow
            WeatherCondition.SnowGrains -> R.string.condition_snow_grains
            WeatherCondition.RainShowers -> R.string.condition_rain_showers
            WeatherCondition.HeavyShowers -> R.string.condition_heavy_showers
            WeatherCondition.SnowShowers -> R.string.condition_snow_showers
            WeatherCondition.Thunderstorm -> R.string.condition_thunderstorm
            WeatherCondition.ThunderstormHail -> R.string.condition_thunderstorm_hail
        },
    )

    fun windValue(metersPerSecond: Double): String = units.wind(metersPerSecond).roundToInt().toString()

    fun windUnit(): String = context.getString(
        when (units.wind) {
            WindUnit.MetersPerSecond -> R.string.unit_ms
            WindUnit.KilometersPerHour -> R.string.unit_kmh
            WindUnit.MilesPerHour -> R.string.unit_mph
            WindUnit.Knots -> R.string.unit_knots
            WindUnit.Beaufort -> R.string.unit_beaufort
        },
    )

    fun wind(metersPerSecond: Double): String = "${windValue(metersPerSecond)} ${windUnit()}"

    fun pressureValue(hpa: Double): String = when (units.pressure) {
        PressureUnit.InchesOfMercury -> "%.2f".format(locale, units.pressure(hpa))
        else -> units.pressure(hpa).roundToInt().toString()
    }

    fun pressureUnit(): String = context.getString(
        when (units.pressure) {
            PressureUnit.MillimetersOfMercury -> R.string.unit_mmhg
            PressureUnit.Hectopascal -> R.string.unit_hpa
            PressureUnit.InchesOfMercury -> R.string.unit_inhg
        },
    )

    fun pressure(hpa: Double): String = "${pressureValue(hpa)} ${pressureUnit()}"

    fun precipitationValue(mm: Double): String {
        val v = units.precipitation(mm)
        return when {
            v == 0.0 -> "0"
            v < 10 -> "%.1f".format(locale, v)
            else -> v.roundToInt().toString()
        }
    }

    fun precipitationUnit(): String = context.getString(
        if (units.precipitation == PrecipitationUnit.Inches) R.string.unit_in else R.string.unit_mm,
    )

    fun precipitation(mm: Double): String = "${precipitationValue(mm)} ${precipitationUnit()}"

    fun visibility(meters: Double): String {
        val v = units.distance(meters)
        val unit = context.getString(if (units.distance == DistanceUnit.Miles) R.string.unit_mi else R.string.unit_km)
        val value = if (v < 10) "%.1f".format(locale, v) else v.roundToInt().toString()
        return "$value $unit"
    }

    fun percent(value: Int): String = "$value%"

    fun compass(degrees: Int): String = context.resources.getStringArray(R.array.compass_points)[compassPoint(degrees)]

    fun time(epochSeconds: Long): String = timeFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    fun hour(epochSeconds: Long): String = hourFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    fun localDate(epochSeconds: Long): LocalDate = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

    /** "Today", "Tomorrow", or a short weekday ("Fri"). */
    fun dayLabel(dayEpochSeconds: Long, nowEpochSeconds: Long, short: Boolean = true): String {
        val day = localDate(dayEpochSeconds)
        val today = localDate(nowEpochSeconds)
        return when (day) {
            today -> context.getString(R.string.today)
            today.plusDays(1) -> context.getString(R.string.tomorrow)
            else -> weekday(day, short)
        }
    }

    fun weekday(date: LocalDate, short: Boolean = true): String =
        date.dayOfWeek.getDisplayName(if (short) TextStyle.SHORT_STANDALONE else TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
            .trimEnd('.')

    fun dayOfMonth(dayEpochSeconds: Long): String = DateTimeFormatter.ofPattern("d MMM", locale)
        .format(localDate(dayEpochSeconds)).trimEnd('.')

    fun minutes(minutes: Int): String = if (minutes < 60) {
        context.resources.getQuantityString(R.plurals.minutes_short, minutes, minutes)
    } else if (minutes % 60 == 0) {
        context.getString(R.string.hours_short, minutes / 60)
    } else {
        context.getString(R.string.hours_minutes_short, minutes / 60, minutes % 60)
    }

    fun updated(fetchedAtEpochSeconds: Long, nowEpochSeconds: Long): String {
        val minutes = ((nowEpochSeconds - fetchedAtEpochSeconds) / 60).toInt()
        return when {
            minutes < 2 -> context.getString(R.string.updated_just_now)
            minutes < 6 * 60 -> context.getString(R.string.updated_ago, minutes(minutes))
            else -> context.getString(R.string.updated_at, time(fetchedAtEpochSeconds))
        }
    }

    fun kind(kind: PrecipitationKind): String = context.getString(
        when (kind) {
            PrecipitationKind.Rain -> R.string.kind_rain
            PrecipitationKind.Snow -> R.string.kind_snow
            PrecipitationKind.Sleet -> R.string.kind_sleet
            PrecipitationKind.Storm -> R.string.kind_storm
        },
    )

    fun headline(headline: Headline, nowEpochSeconds: Long): String = when (headline) {
        is Headline.PrecipitationStarts -> context.getString(R.string.headline_starts, kind(headline.kind), minutes(headline.minutes))
        is Headline.PrecipitationEnds -> context.getString(R.string.headline_ends, kind(headline.kind), minutes(headline.minutes))
        is Headline.PrecipitationLater -> context.getString(R.string.headline_later, kind(headline.kind), time(headline.atEpochSeconds))
        is Headline.Continuing -> context.getString(R.string.headline_continuing, kind(headline.kind))
        is Headline.TomorrowWarmer -> context.getString(R.string.headline_tomorrow_warmer, temperatureDelta(headline.degreesCelsius))
        is Headline.TomorrowColder -> context.getString(R.string.headline_tomorrow_colder, temperatureDelta(headline.degreesCelsius))
        is Headline.StrongWind -> context.getString(R.string.headline_strong_wind, wind(headline.gustsMs))
        is Headline.HighUv -> context.getString(R.string.headline_high_uv, headline.index)
        is Headline.Sunrise -> context.getString(R.string.headline_sunrise, time(headline.atEpochSeconds))
        is Headline.Sunset -> context.getString(R.string.headline_sunset, time(headline.atEpochSeconds))
        is Headline.FeelsLike -> context.getString(R.string.headline_feels_like, temperature(headline.apparentCelsius))
        is Headline.Steady -> condition(headline.condition)
    }

    fun uvLevel(uv: Double): String = context.getString(
        when {
            uv < 3 -> R.string.uv_low
            uv < 6 -> R.string.uv_moderate
            uv < 8 -> R.string.uv_high
            uv < 11 -> R.string.uv_very_high
            else -> R.string.uv_extreme
        },
    )

    fun airLevel(level: AirLevel): String = context.getString(
        when (level) {
            AirLevel.Good -> R.string.air_good
            AirLevel.Fair -> R.string.air_fair
            AirLevel.Moderate -> R.string.air_moderate
            AirLevel.Poor -> R.string.air_poor
            AirLevel.VeryPoor -> R.string.air_very_poor
            AirLevel.ExtremelyPoor -> R.string.air_extremely_poor
            AirLevel.Unknown -> R.string.air_unknown
        },
    )

    fun moonPhase(phase: Double): String = context.getString(
        when {
            phase < 0.03 || phase > 0.97 -> R.string.moon_new
            phase < 0.22 -> R.string.moon_waxing_crescent
            phase < 0.28 -> R.string.moon_first_quarter
            phase < 0.47 -> R.string.moon_waxing_gibbous
            phase < 0.53 -> R.string.moon_full
            phase < 0.72 -> R.string.moon_waning_gibbous
            phase < 0.78 -> R.string.moon_last_quarter
            else -> R.string.moon_waning_crescent
        },
    )

    fun highLow(max: Double, min: Double): String = context.getString(R.string.high_low, temperature(max), temperature(min))

    fun now(): String = context.getString(R.string.now)

    fun currentLocation(): String = context.getString(R.string.current_location)

    private fun signed(value: Int): String = if (value < 0) "−${abs(value)}" else value.toString()

    companion object {
        fun zoneOf(timezone: String?, utcOffsetSeconds: Int): ZoneId =
            runCatching { ZoneId.of(timezone) }.getOrElse { ZoneOffset.ofTotalSeconds(utcOffsetSeconds) }
    }
}
