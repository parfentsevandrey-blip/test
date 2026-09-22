package app.papersky.weather.core.text

import app.papersky.weather.R
import app.papersky.weather.core.model.Astro
import app.papersky.weather.core.model.Condition
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Hour
import app.papersky.weather.core.model.MoonPhaseName
import app.papersky.weather.core.model.momentAt
import kotlin.math.abs

enum class PrecipKind { Rain, Drizzle, Snow, Storm, Sleet }

/**
 * Small, human observations about the forecast — the handwritten note in the app and the
 * rotating whisper line on widgets. Pure logic; [NoteText] turns them into words.
 */
sealed interface Note {
    data class PrecipSoon(val kind: PrecipKind, val minutes: Int?, val at: Long) : Note
    data class PrecipEnds(val kind: PrecipKind, val at: Long) : Note
    data class PrecipAllDay(val kind: PrecipKind) : Note
    data class StormLater(val at: Long) : Note
    data class Gusts(val speed: Double) : Note
    data class UvPeak(val uv: Double, val at: Long) : Note
    data class Frost(val min: Double) : Note
    data class Tomorrow(val delta: Double) : Note
    data object Foggy : Note
    data object StarryNight : Note
    data object FullMoon : Note
    data object DryDay : Note
    data class Sunrise(val at: Long) : Note
    data class Sunset(val at: Long) : Note
    data class FeelsLike(val value: Double) : Note
}

object Narrator {
    private const val HOUR = 3600L

    fun notes(f: Forecast, now: Long): List<Note> {
        val out = mutableListOf<Note>()
        val moment = f.momentAt(now) ?: return out
        val upcoming = f.hoursFrom(now, 24)
        val wetNow = moment.precipitation >= 0.1 || moment.condition.isWet || moment.condition.isSnowy

        // 1. Precipitation starting soon — minute-level nowcast first, then hourly.
        if (!wetNow) {
            val soonSlot = f.nowcast.firstOrNull { it.time >= now - 15 * 60 && it.precipitation >= 0.1 }
            val soonHour = upcoming.drop(1).take(12).firstOrNull { it.isWet() }
            when {
                soonSlot != null && soonSlot.time - now <= 3 * HOUR -> {
                    val kind = soonHour?.kind() ?: kindFor(moment.code, moment.temperature)
                    val minutes = ((soonSlot.time - now) / 60).toInt().coerceAtLeast(0)
                    out += Note.PrecipSoon(kind, if (minutes <= 60) roundTo5(minutes) else null, soonSlot.time)
                }
                soonHour != null -> out += Note.PrecipSoon(soonHour.kind(), null, soonHour.time)
            }
        } else {
            // 2. Currently wet: when does it end?
            val dryHour = upcoming.drop(1).firstOrNull { !it.isWet() }
            val kind = kindFor(moment.code, moment.temperature)
            if (dryHour == null) out += Note.PrecipAllDay(kind) else out += Note.PrecipEnds(kind, dryHour.time)
        }

        // 3. Thunder later today.
        if (!moment.condition.isStormy) {
            upcoming.take(12).firstOrNull { Condition.fromWmo(it.code).isStormy }?.let { out += Note.StormLater(it.time) }
        }

        // 4. Strong gusts in the next 12 hours.
        val gust = upcoming.take(12).maxOfOrNull { it.windGusts } ?: 0.0
        if (gust >= 13.5) out += Note.Gusts(gust)

        // 5. UV peak still ahead today.
        val today = f.dayAt(now)
        if (today != null && today.uvMax >= 6 && moment.daylight > 0.5f) {
            val peak = upcoming.filter { it.time < today.sunset }.maxByOrNull { it.uvIndex }
            if (peak != null && peak.uvIndex >= 6 && peak.time >= now - HOUR) out += Note.UvPeak(peak.uvIndex, peak.time)
        }

        // 6. Frost tonight when it's still mild now.
        val night = upcoming.filter { !it.isDay }
        val nightMin = night.minOfOrNull { it.temperature }
        if (nightMin != null && nightMin <= 0.0 && moment.temperature >= 2.0) out += Note.Frost(nightMin)

        // 7. Tomorrow noticeably warmer/colder.
        val todayIdx = f.dayIndexAt(now)
        val tomorrow = f.daily.getOrNull(todayIdx + 1)
        if (today != null && tomorrow != null) {
            val delta = tomorrow.tempMax - today.tempMax
            if (abs(delta) >= 4.0) out += Note.Tomorrow(delta)
        }

        // 8. Atmosphere.
        if (moment.condition == Condition.Fog || (moment.visibility ?: 20_000.0) < 1_000) out += Note.Foggy
        if (moment.daylight < 0.2f && moment.cloudCover < 25 && !wetNow) out += Note.StarryNight
        if (moment.daylight < 0.5f && MoonPhaseName.of(Astro.moonPhase(now)) == MoonPhaseName.Full) out += Note.FullMoon

        val feelsGap = moment.feelsLike - moment.temperature
        if (abs(feelsGap) >= 4.0) out += Note.FeelsLike(moment.feelsLike)

        if (!wetNow && out.none { it is Note.PrecipSoon } && today != null) {
            val restOfDay = upcoming.filter { it.time < today.date + 24 * HOUR }
            if (restOfDay.size >= 3 && restOfDay.none { it.isWet() }) out += Note.DryDay
        }

        // 9. Sun times are always true and make a gentle filler.
        val nextSunrise = f.daily.map { it.sunrise }.firstOrNull { it > now }
        val nextSunset = f.daily.map { it.sunset }.firstOrNull { it > now }
        if (nextSunset != null && (nextSunrise == null || nextSunset < nextSunrise)) {
            out += Note.Sunset(nextSunset)
            nextSunrise?.let { out += Note.Sunrise(it) }
        } else {
            nextSunrise?.let { out += Note.Sunrise(it) }
            nextSunset?.let { out += Note.Sunset(it) }
        }
        return out
    }

    fun headline(f: Forecast, now: Long): Note? = notes(f, now).firstOrNull()

    private fun Hour.isWet(): Boolean {
        val c = Condition.fromWmo(code)
        return (precipitation >= 0.1 && precipProbability >= 35) || ((c.isWet || c.isSnowy) && precipProbability >= 45)
    }

    private fun Hour.kind(): PrecipKind = kindFor(code, temperature)

    fun kindFor(code: Int, temperature: Double): PrecipKind {
        val c = Condition.fromWmo(code)
        return when {
            c.isStormy -> PrecipKind.Storm
            c.isSnowy -> PrecipKind.Snow
            c == Condition.FreezingRain || c == Condition.FreezingDrizzle -> PrecipKind.Sleet
            c == Condition.Drizzle -> PrecipKind.Drizzle
            temperature <= 0.5 && !c.isWet -> PrecipKind.Snow
            else -> PrecipKind.Rain
        }
    }

    private fun roundTo5(minutes: Int) = ((minutes + 2) / 5 * 5).coerceAtLeast(5)
}

/** Localized wording for [Note]s. */
object NoteText {
    fun resolve(note: Note, fmt: WeatherFormat, res: android.content.res.Resources): String = when (note) {
        is Note.PrecipSoon -> if (note.minutes != null) {
            res.getQuantityString(soonMinutesRes(note.kind), note.minutes, note.minutes)
        } else {
            res.getString(soonAtRes(note.kind), fmt.time(note.at))
        }
        is Note.PrecipEnds -> res.getString(endsRes(note.kind), fmt.time(note.at))
        is Note.PrecipAllDay -> res.getString(
            when (note.kind) {
                PrecipKind.Snow -> R.string.note_snow_all_day
                else -> R.string.note_rain_all_day
            },
        )
        is Note.StormLater -> res.getString(R.string.note_storm_later, fmt.time(note.at))
        is Note.Gusts -> res.getString(R.string.note_gusts, fmt.wind(note.speed))
        is Note.UvPeak -> res.getString(R.string.note_uv_peak, note.uv.toInt().toString(), fmt.time(note.at))
        is Note.Frost -> res.getString(R.string.note_frost, fmt.temp(note.min))
        is Note.Tomorrow -> res.getString(
            if (note.delta > 0) R.string.note_tomorrow_warmer else R.string.note_tomorrow_colder,
            fmt.tempDelta(note.delta),
        )
        Note.Foggy -> res.getString(R.string.note_fog)
        Note.StarryNight -> res.getString(R.string.note_stars)
        Note.FullMoon -> res.getString(R.string.note_full_moon)
        Note.DryDay -> res.getString(R.string.note_dry_day)
        is Note.Sunrise -> res.getString(R.string.note_sunrise, fmt.time(note.at))
        is Note.Sunset -> res.getString(R.string.note_sunset, fmt.time(note.at))
        is Note.FeelsLike -> res.getString(R.string.note_feels_like, fmt.temp(note.value))
    }

    private fun soonMinutesRes(kind: PrecipKind) = when (kind) {
        PrecipKind.Snow -> R.plurals.note_snow_in_minutes
        PrecipKind.Storm -> R.plurals.note_storm_in_minutes
        else -> R.plurals.note_rain_in_minutes
    }

    private fun soonAtRes(kind: PrecipKind) = when (kind) {
        PrecipKind.Snow -> R.string.note_snow_at
        PrecipKind.Storm -> R.string.note_storm_later
        PrecipKind.Drizzle -> R.string.note_drizzle_at
        PrecipKind.Sleet -> R.string.note_sleet_at
        PrecipKind.Rain -> R.string.note_rain_at
    }

    private fun endsRes(kind: PrecipKind) = when (kind) {
        PrecipKind.Snow -> R.string.note_snow_ends
        PrecipKind.Storm -> R.string.note_storm_ends
        else -> R.string.note_rain_ends
    }
}
