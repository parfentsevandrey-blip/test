package app.quire.weather.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Where the sky is in its day.
 *
 * The screen used to know one bit — day or night — and wear one of two colours for it. But a sky
 * is not a two-state lamp: six in the morning and noon are different skies, and the data to tell
 * them apart was already on the phone, because the forecast carries today's sunrise and sunset.
 * This is the arithmetic between those two times and the clock, done once, in one place, with no
 * pixels involved — so a test can ask about half past four in the afternoon and get an answer.
 *
 * Nothing here reaches for the clock itself: the moment of asking is handed in. That is the whole
 * testability of it, and it also means the caller decides how often "now" is worth recomputing.
 */
internal class SkyMoment(
    /** 0..1 through the daylight while the sun is up; null when it is down or nobody knows. */
    val daylight: Float?,
    /** 0..1 through the night while the sun is down; null in daylight or when nobody knows. */
    val night: Float?,
    /** The moon, 0 new → 0.5 full → 1 new again. */
    val moonPhase: Float,
    /** 0 deep night → 1 full day, ramping through dawn and dusk instead of switching. */
    val light: Float,
    /** How deep into the golden hour, 0 none → 1 at the horizon crossing itself. */
    val glow: Float,
) {
    companion object {

        /** How long before sunrise the sky starts to pale, and after sunset it keeps a trace. */
        private const val DAWN_BEFORE_MIN = 60f
        private const val DAWN_AFTER_MIN = 35f
        private const val DUSK_BEFORE_MIN = 35f
        private const val DUSK_AFTER_MIN = 50f

        /** How far either side of the horizon crossing the golden tint reaches. */
        private const val GLOW_SPAN_MIN = 45f

        /** One synodic month, and a day the moon was new on. */
        private const val SYNODIC_DAYS = 29.530588853
        private val NEW_MOON = LocalDate.of(2000, 1, 6)

        fun of(
            now: LocalDateTime,
            sunrise: LocalDateTime?,
            sunset: LocalDateTime?,
            day: Boolean,
        ): SkyMoment {
            val phase = moon(now.toLocalDate())
            if (sunrise == null || sunset == null || !sunset.isAfter(sunrise)) {
                // No times to reason from: the provider's one bit is all there is, worn plainly.
                return SkyMoment(
                    daylight = if (day) 0.5f else null,
                    night = if (day) null else 0.5f,
                    moonPhase = phase,
                    light = if (day) 1f else 0f,
                    glow = 0f,
                )
            }

            val sinceRise = minutes(sunrise, now)
            val sinceSet = minutes(sunset, now)
            val dayLen = minutes(sunrise, sunset).coerceAtLeast(1f)
            val nightLen = (24f * 60f - dayLen).coerceAtLeast(1f)

            val up = sinceRise >= 0f && sinceSet < 0f
            val daylight = if (up) (sinceRise / dayLen).coerceIn(0f, 1f) else null
            val night = when {
                up -> null
                // Evening: so far through the night since sunset.
                sinceSet >= 0f -> (sinceSet / nightLen).coerceIn(0f, 1f)
                // Small hours: the same night, approached from its far end.
                else -> (1f - (-sinceRise / nightLen)).coerceIn(0f, 1f)
            }

            // The light ramps rather than switches: up across the hour before sunrise and the
            // half-hour after, down across the last half-hour of sun and the fifty minutes of
            // civil dusk. Between the ramps it is simply day, or simply night.
            val light = when {
                sinceRise < -DAWN_BEFORE_MIN -> 0f
                sinceRise < DAWN_AFTER_MIN ->
                    (sinceRise + DAWN_BEFORE_MIN) / (DAWN_BEFORE_MIN + DAWN_AFTER_MIN)
                sinceSet < -DUSK_BEFORE_MIN -> 1f
                sinceSet < DUSK_AFTER_MIN ->
                    1f - (sinceSet + DUSK_BEFORE_MIN) / (DUSK_BEFORE_MIN + DUSK_AFTER_MIN)
                else -> 0f
            }.coerceIn(0f, 1f)

            // The golden tint peaks at the horizon crossings themselves and is gone within the
            // hour — it is the colour of the sun being low, not a third state of the sky.
            val glow = maxOf(
                1f - kotlin.math.abs(sinceRise) / GLOW_SPAN_MIN,
                1f - kotlin.math.abs(sinceSet) / GLOW_SPAN_MIN,
            ).coerceIn(0f, 1f)

            return SkyMoment(daylight, night, phase, light, glow)
        }

        private fun minutes(from: LocalDateTime, to: LocalDateTime): Float =
            Duration.between(from, to).toMinutes().toFloat()

        /**
         * The moon's phase from the calendar alone.
         *
         * Days since a known new moon, folded by the synodic month. Off by hours at worst, which
         * on a ten-point disc is nothing — the question it answers is "crescent, half or full",
         * not "when exactly to plant".
         */
        private fun moon(date: LocalDate): Float {
            val days = ChronoUnit.DAYS.between(NEW_MOON, date).toDouble()
            val turns = days / SYNODIC_DAYS
            return (((turns % 1.0) + 1.0) % 1.0).toFloat()
        }
    }
}

/**
 * The wash's colour for this moment, mixed from the theme's own containers: night is the
 * secondary container pulled toward black, day is the primary container as it always was, and
 * the golden hour leans both toward the tertiary — so every phase still answers the wallpaper.
 */
internal fun skyColour(scheme: ColorScheme, moment: SkyMoment): Color {
    val nightC = lerp(scheme.secondaryContainer, Color.Black, 0.30f)
    val base = lerp(nightC, scheme.primaryContainer, moment.light)
    return lerp(base, scheme.tertiaryContainer, moment.glow * 0.65f)
}
