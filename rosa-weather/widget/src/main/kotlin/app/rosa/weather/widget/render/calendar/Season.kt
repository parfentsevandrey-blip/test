package app.rosa.weather.widget.render.calendar

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * The year week by week, as it goes in the middle of Russia: how much snow lies, how far autumn
 * has coloured the leaves and how many still hang on, when the orchards flower, the rivers freeze
 * and open, the mist rises. Every value moves smoothly from week to week, so each of the 52 weeks
 * is a little further on than the one before — October's first week still red and gold, its last
 * bare and white with frost.
 */
internal data class Season(
    /** 1..52: the week of the year. */
    val week: Int,
    /** Ground under snow, 0..1. */
    val snow: Float,
    /** Deciduous crowns in leaf, 0..1: bare in winter, a green haze in April, full in summer, falling in October. */
    val leaves: Float,
    /** The leaves' colour: 0 green, about ⅓ gold, ⅔ red, 1 brown. */
    val autumn: Float,
    /** Young spring green, 0..1. */
    val fresh: Float,
    /** Orchards in flower, 0..1. */
    val blossom: Float,
    /** Grass green and growing, 0..1; 0 is last year's straw. */
    val grass: Float,
    /** Rivers and lakes frozen, 0..1. */
    val ice: Float,
    /** Hoarfrost and rime on the ground and the trees, 0..1. */
    val frost: Float,
    /** Mist, 0..1. */
    val mist: Float,
    /** Rye ripening, 0..1. */
    val ripe: Float,
    /** New Year's lights, 0..1. */
    val festive: Float,
    /** The sun's strength over the day, 0..1: low in December, high in June. */
    val sun: Float,
) {
    /** Where this week falls among [month]'s weeks: 0 its first, 1 its last. */
    fun within(month: Int): Float {
        val (first, last) = SeasonClock.weeksOf(month)
        return if (last <= first) 0.5f else ((week - first).toFloat() / (last - first)).coerceIn(0f, 1f)
    }

    /** A broad-leaved crown's tones, dark to light, for this week: fresh green, summer, gold, red, brown. */
    fun crown(): IntArray = if (fresh > 0f && autumn <= 0f) blend(SUMMER, SPRING, fresh) else along(autumn, CROWNS)

    /** A birch's small leaves: fresh, then green, then gold, and last a faded brown — birches never go red. */
    fun birchLeaves(): IntArray = if (fresh > 0f && autumn <= 0f) blend(BIRCH_SUMMER, BIRCH_SPRING, fresh) else along(autumn, BIRCHES)

    /** Grass, dark to light: last year's straw greening in spring, lush in summer, drying to rust. */
    fun grassTones(): IntArray = when {
        fresh > 0f && week < 26 -> blend(STRAW, blend(LUSH, YOUNG, fresh), grass)
        else -> along(1f - grass, MEADOWS)
    }

    companion object {
        private val SPRING = tones(0x3E6A2A, 0x5E8E36, 0x86B24A, 0xB2D46A, 0xDDEFA0)
        private val SUMMER = tones(0x0E2410, 0x1C3C1A, 0x2E5A26, 0x4C7C34, 0x76A24A)
        private val CROWNS = listOf(
            0f to SUMMER,
            0.18f to tones(0x2E3A14, 0x55621E, 0x86862A, 0xB8A83A, 0xE0CC62),
            0.4f to tones(0x6A3A0E, 0xA8641A, 0xDC9A2E, 0xF6C44A, 0xFFE490),
            0.66f to tones(0x6E1E14, 0xA8321E, 0xD2502A, 0xEE7A36, 0xFFB04E),
            1f to tones(0x3A2414, 0x5E3A1E, 0x7E5230, 0x9E6E44, 0xBC8E5E),
        )
        private val BIRCH_SPRING = tones(0x6E8A4A, 0x8EAA5A, 0xB2CC78, 0xD2E6A0, 0xEEF6C8)
        private val BIRCH_SUMMER = tones(0x2E5A24, 0x44762E, 0x62943C, 0x86B252, 0xB2CE7A)
        private val BIRCHES = listOf(
            0f to BIRCH_SUMMER,
            0.18f to tones(0x5A6A22, 0x86882E, 0xB0A63A, 0xD6C452, 0xF0E08A),
            0.4f to tones(0x7A4A12, 0xB8741E, 0xE0A232, 0xF6C84E, 0xFFE68A),
            0.7f to tones(0x7A4A16, 0xA86A24, 0xC88A34, 0xE0A64C, 0xF2C878),
            1f to tones(0x5A3E22, 0x7E5A34, 0x9E7646, 0xBA9260, 0xD2B084),
        )
        private val YOUNG = tones(0x3E6A24, 0x5E9234, 0x86B846, 0xAED65E, 0xD2EC8A)
        private val LUSH = tones(0x2A5A1E, 0x467E2E, 0x6CA23E, 0x98C654, 0xC8E27A)
        private val STRAW = tones(0x5A4A30, 0x7A6444, 0x9A805A, 0xB49C72, 0xCCB88E)
        private val MEADOWS = listOf(
            0f to LUSH,
            0.3f to tones(0x4E6424, 0x6E8430, 0x96A040, 0xBEB85A, 0xDCD080),
            0.6f to tones(0x5E5A24, 0x7E7A30, 0xA69A40, 0xC8B45A, 0xE2CC80),
            1f to STRAW,
        )

        private fun tones(vararg rgb: Long) = IntArray(rgb.size) { Tone.of(rgb[it]) }

        private fun blend(a: IntArray, b: IntArray, t: Float) = IntArray(a.size) { Tone.mix(a[it], b[it], t) }

        private fun along(t: Float, stops: List<Pair<Float, IntArray>>): IntArray {
            val k = t.coerceIn(0f, 1f)
            val i = stops.indexOfLast { it.first <= k }.coerceIn(0, stops.size - 2)
            val (a, ta) = stops[i]
            val (b, tb) = stops[i + 1]
            return blend(ta, tb, Tone.smooth(0f, 1f, (k - a) / (b - a)))
        }
    }
}

/** The weeks of the year and the season in each: the calendar's clock. */
internal object SeasonClock {
    const val WEEKS = 52

    /** 1..52: the week [date] falls in, counted from 1 January; the year's last days join week 52. */
    fun weekOf(date: LocalDate): Int = min(WEEKS, (date.dayOfYear - 1) / 7 + 1)

    /** The week a page of [month] shows: today's on this month, the middle of any other. */
    fun weekFor(month: YearMonth, today: LocalDate): Int =
        if (YearMonth.from(today) == month) weekOf(today) else weekOf(month.atDay(15))

    /** The first and last weeks with days of [month] (in a non-leap year). */
    fun weeksOf(month: Int): Pair<Int, Int> {
        val m = YearMonth.of(2025, month)
        return weekOf(m.atDay(1)) to weekOf(m.atEndOfMonth())
    }

    fun of(week: Int): Season {
        val w = week.coerceIn(1, WEEKS).toFloat()
        return Season(
            week = w.toInt(),
            snow = SNOW(w),
            leaves = LEAVES(w),
            autumn = AUTUMN(w),
            fresh = FRESH(w),
            blossom = BLOSSOM(w),
            grass = GRASS(w),
            ice = ICE(w),
            frost = FROST(w),
            mist = MIST(w),
            ripe = RIPE(w),
            festive = FESTIVE(w),
            sun = SUN(w),
        )
    }

    /** A value by week: keyframes joined by smooth steps, held before the first and after the last. */
    private class Curve(vararg keys: Pair<Int, Float>) {
        private val weeks = keys.map { it.first.toFloat() }
        private val values = keys.map { it.second }

        operator fun invoke(week: Float): Float {
            if (week <= weeks.first()) return values.first()
            if (week >= weeks.last()) return values.last()
            val i = weeks.indexOfLast { it <= week }
            return values[i] + (values[i + 1] - values[i]) * Tone.smooth(0f, 1f, (week - weeks[i]) / (weeks[i + 1] - weeks[i]))
        }
    }

    // The snow lies from late November to March, going in patches through the thaw.
    private val SNOW = Curve(1 to 1f, 9 to 1f, 10 to 0.9f, 11 to 0.7f, 12 to 0.45f, 13 to 0.22f, 14 to 0.06f, 15 to 0f, 45 to 0f, 46 to 0.3f, 47 to 0.6f, 48 to 0.85f, 49 to 1f)

    // A green haze on the birches in late April, full crowns by June, the leaves falling through October.
    private val LEAVES = Curve(1 to 0f, 15 to 0f, 16 to 0.08f, 17 to 0.2f, 18 to 0.4f, 19 to 0.62f, 20 to 0.82f, 21 to 0.95f, 22 to 1f, 36 to 1f, 37 to 0.96f, 38 to 0.9f, 39 to 0.8f, 40 to 0.66f, 41 to 0.46f, 42 to 0.28f, 43 to 0.12f, 44 to 0.03f, 45 to 0f)

    // The first yellow strands in late August, gold in September, red and then brown in October.
    private val AUTUMN = Curve(1 to 0f, 33 to 0f, 34 to 0.04f, 35 to 0.09f, 36 to 0.17f, 37 to 0.28f, 38 to 0.4f, 39 to 0.5f, 40 to 0.6f, 41 to 0.7f, 42 to 0.8f, 43 to 0.9f, 44 to 1f)

    private val FRESH = Curve(1 to 0f, 15 to 0f, 16 to 1f, 21 to 1f, 23 to 0.7f, 25 to 0.35f, 27 to 0f)

    // Bird cherry and apple in flower from mid-May, the petals falling by June.
    private val BLOSSOM = Curve(1 to 0f, 18 to 0f, 19 to 0.45f, 20 to 1f, 21 to 0.75f, 22 to 0.3f, 23 to 0f)

    private val GRASS = Curve(1 to 0f, 13 to 0f, 14 to 0.1f, 15 to 0.28f, 16 to 0.5f, 17 to 0.72f, 18 to 0.9f, 19 to 1f, 30 to 1f, 31 to 0.92f, 33 to 0.82f, 35 to 0.72f, 37 to 0.6f, 39 to 0.48f, 41 to 0.35f, 43 to 0.2f, 45 to 0.08f, 46 to 0f)

    // Ice goes out in early April and sets again at the end of November.
    private val ICE = Curve(1 to 1f, 10 to 1f, 11 to 0.85f, 12 to 0.6f, 13 to 0.3f, 14 to 0.1f, 15 to 0f, 46 to 0f, 47 to 0.15f, 48 to 0.45f, 49 to 0.75f, 50 to 0.92f, 51 to 1f)

    // Rime on everything in the Epiphany frosts of mid-January; the first frosts at the end of October.
    private val FROST = Curve(1 to 0.7f, 2 to 0.75f, 3 to 1f, 4 to 0.7f, 5 to 0.55f, 8 to 0.5f, 9 to 0.3f, 11 to 0.15f, 13 to 0f, 41 to 0f, 42 to 0.1f, 43 to 0.3f, 44 to 0.65f, 45 to 0.45f, 46 to 0.5f, 48 to 0.55f, 52 to 0.6f)

    private val MIST = Curve(1 to 0.1f, 13 to 0.1f, 14 to 0.35f, 15 to 0.3f, 16 to 0.15f, 20 to 0.05f, 32 to 0.05f, 33 to 0.15f, 34 to 0.35f, 35 to 0.4f, 37 to 0.2f, 39 to 0.25f, 40 to 0.35f, 41 to 0.5f, 42 to 0.7f, 43 to 0.55f, 44 to 0.4f, 46 to 0.3f, 48 to 0.2f, 52 to 0.1f)

    private val RIPE = Curve(1 to 0f, 26 to 0f, 27 to 0.25f, 28 to 0.5f, 29 to 0.72f, 30 to 0.9f, 31 to 1f)

    private val FESTIVE = Curve(1 to 0.85f, 2 to 0.35f, 3 to 0f, 50 to 0f, 51 to 0.55f, 52 to 1f)

    private val SUN = Curve(1 to 0.08f, 4 to 0.15f, 8 to 0.32f, 12 to 0.55f, 16 to 0.75f, 21 to 0.92f, 25 to 1f, 29 to 0.95f, 33 to 0.82f, 37 to 0.62f, 41 to 0.42f, 45 to 0.22f, 49 to 0.1f, 52 to 0.06f)
}
