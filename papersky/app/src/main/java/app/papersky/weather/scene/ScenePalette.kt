package app.papersky.weather.scene

import android.content.Context
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Colours of one paper diorama. Stored as a flat ARGB array so palettes can be mixed channel-wise
 * every frame without allocation-heavy objects.
 */
class ScenePalette private constructor(internal val c: IntArray) {
    val skyTop get() = c[SKY_TOP]
    val skyBottom get() = c[SKY_BOTTOM]
    val glow get() = c[GLOW]
    val sun get() = c[SUN]
    val sunRay get() = c[SUN_RAY]
    val moon get() = c[MOON]
    val star get() = c[STAR]
    val cloud get() = c[CLOUD]
    val cloudShade get() = c[CLOUD_SHADE]
    val hillFar get() = c[HILL_FAR]
    val hillMid get() = c[HILL_MID]
    val hillNear get() = c[HILL_NEAR]
    val snowCap get() = c[SNOW_CAP]
    val house get() = c[HOUSE]
    val roof get() = c[ROOF]
    val window get() = c[WINDOW]
    val tree get() = c[TREE]
    val precip get() = c[PRECIP]
    val paper get() = c[PAPER]
    val paperInk get() = c[PAPER_INK]
    val accent get() = c[ACCENT]
    val tape get() = c[TAPE]
    val shadow get() = c[SHADOW]

    /** Sky colour behind most text (upper-middle of the gradient). */
    val skyMid: Int get() = ColorMath.lerp(skyTop, skyBottom, 0.35f)

    /** True when light ink reads better than dark ink on the sky (picked by WCAG contrast). */
    val isDarkSky: Boolean get() = ColorMath.contrast(INK_LIGHT, skyMid) > ColorMath.contrast(INK_DARK, skyMid)

    /** Ink colour for text drawn directly on the sky. */
    val onSky: Int get() = if (isDarkSky) INK_LIGHT else INK_DARK

    val onSkySoft: Int get() = ColorMath.withAlpha(onSky, 0.72f)

    val isDarkPaper: Boolean get() = ColorMath.luminance(paper) < 0.3f

    /** This palette with the paper, ink, tape and accent of [other]. */
    fun withPaperOf(other: ScenePalette): ScenePalette = copyWith(
        PAPER to other.paper, PAPER_INK to other.paperInk, TAPE to other.tape, ACCENT to other.accent,
    )

    fun copyWith(vararg pairs: Pair<Int, Int>): ScenePalette {
        val n = c.copyOf()
        for ((k, v) in pairs) n[k] = v
        return ScenePalette(n)
    }

    override fun equals(other: Any?) = other is ScenePalette && other.c.contentEquals(c)
    override fun hashCode() = c.contentHashCode()

    companion object {
        const val SKY_TOP = 0; const val SKY_BOTTOM = 1; const val GLOW = 2; const val SUN = 3
        const val SUN_RAY = 4; const val MOON = 5; const val STAR = 6; const val CLOUD = 7
        const val CLOUD_SHADE = 8; const val HILL_FAR = 9; const val HILL_MID = 10; const val HILL_NEAR = 11
        const val SNOW_CAP = 12; const val HOUSE = 13; const val ROOF = 14; const val WINDOW = 15
        const val TREE = 16; const val PRECIP = 17; const val PAPER = 18; const val PAPER_INK = 19
        const val ACCENT = 20; const val TAPE = 21; const val SHADOW = 22
        private const val SIZE = 23

        const val INK_DARK = 0xFF1F2530.toInt()
        const val INK_LIGHT = 0xFFF8F1E4.toInt()

        fun of(vararg colors: Long): ScenePalette {
            require(colors.size == SIZE) { "Palette needs $SIZE colours, got ${colors.size}" }
            return ScenePalette(IntArray(SIZE) { colors[it].toInt() })
        }

        fun mix(parts: List<Pair<ScenePalette, Float>>): ScenePalette {
            val total = parts.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0f } ?: return parts.first().first
            val out = IntArray(SIZE)
            for (k in 0 until SIZE) {
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for ((p, w) in parts) {
                    if (w <= 0f) continue
                    val col = p.c[k]
                    a += ColorMath.a(col) * w; r += ColorMath.r(col) * w
                    g += ColorMath.g(col) * w; b += ColorMath.b(col) * w
                }
                out[k] = ColorMath.argb((a / total).toInt(), (r / total).toInt(), (g / total).toInt(), (b / total).toInt())
            }
            return ScenePalette(out)
        }

        fun lerp(x: ScenePalette, y: ScenePalette, t: Float): ScenePalette =
            ScenePalette(IntArray(SIZE) { ColorMath.lerp(x.c[it], y.c[it], t) })
    }
}

/** Colour themes offered for widgets (and previewed in the app). */
@Serializable
enum class PaletteMode { Auto, Linen, Ink, Riso, Moss, Wallpaper }

/**
 * Hand-mixed gouache palettes. Every mood has a day and a night sheet; clear weather also gets
 * golden-hour and twilight sheets so dawn and dusk glow.
 */
object Palettes {
    //                                skyTop      skyBottom   glow        sun         sunRay      moon        star        cloud       cloudShade  hillFar     hillMid     hillNear    snowCap     house       roof        window      tree        precip      paper       paperInk    accent      tape        shadow
    val ClearDay = ScenePalette.of(0xFF7FB6D8, 0xFFF3E4C8, 0x66FFF1C2, 0xFFF3A23F, 0xFFF8C76A, 0xFFF6EEDC, 0xFFFFF7E0, 0xFFFFFBF3, 0xFFEADCC6, 0xFFB3CCB3, 0xFF86AD88, 0xFF4F7D5A, 0xFFFFFFFF, 0xFFF2E3C7, 0xFFD0603F, 0xFF6F8194, 0xFF3F6A4B, 0xFF46688A, 0xFFFCF6EA, 0xFF2A2724, 0xFFE0773C, 0xFFF1B49A, 0x552B1D12)
    val GoldenDay = ScenePalette.of(0xFF98A5CF, 0xFFF8C48E, 0x88FFD396, 0xFFF08848, 0xFFF6B068, 0xFFF8EEDA, 0xFFFFF4DA, 0xFFFFEAD4, 0xFFE8B69A, 0xFFC9A88D, 0xFFA07B65, 0xFF6C5346, 0xFFFFF6EE, 0xFFF3D8B6, 0xFFC2543A, 0xFFFFD07A, 0xFF594437, 0xFF6A5A7A, 0xFFFDF1E2, 0xFF33241C, 0xFFE2683A, 0xFFF4B28E, 0x663A2012)
    val Twilight = ScenePalette.of(0xFF3C4A80, 0xFFE8A0A0, 0x66F4A6A0, 0xFFE9744F, 0xFFF09A72, 0xFFF7EAD2, 0xFFFFF3DA, 0xFFE8C4CF, 0xFFB98CA6, 0xFF8B7EAE, 0xFF65598C, 0xFF3E3763, 0xFFE9E2F2, 0xFFD9C2C9, 0xFF9C4A55, 0xFFFFC870, 0xFF2F2B4D, 0xFF4C4670, 0xFFF8EEE8, 0xFF2A2233, 0xFFE56F6A, 0xFFEFA8B5, 0x662A1633)
    val ClearNight = ScenePalette.of(0xFF0F1630, 0xFF2A3766, 0x33A9B8FF, 0xFFE9744F, 0xFFF09A72, 0xFFF5EBD3, 0xFFFFF4D6, 0xFF3A4670, 0xFF26304F, 0xFF2A3A63, 0xFF1F2C4E, 0xFF141E38, 0xFFCFD8EA, 0xFF3B4466, 0xFF5A3C55, 0xFFFFC56A, 0xFF0F172B, 0xFF9FB2D8, 0xFF232A45, 0xFFF3EADA, 0xFFF2B35B, 0xFF6F79B8, 0x99000000)
    val GreyDay = ScenePalette.of(0xFF8E9BA6, 0xFFD6D5CF, 0x22FFFFFF, 0xFFE8C99A, 0xFFEBD5B0, 0xFFF1ECE2, 0xFFFFFFFF, 0xFFEAE7E1, 0xFFBAB8B5, 0xFFA8B5AC, 0xFF7C9185, 0xFF52695C, 0xFFFFFFFF, 0xFFE3DCCF, 0xFFA4574A, 0xFFF6D08A, 0xFF40584A, 0xFF3E5064, 0xFFF7F4EE, 0xFF262A2E, 0xFF5F86AD, 0xFFB4C7D9, 0x55202428)
    val GreyNight = ScenePalette.of(0xFF1B2029, 0xFF363C4A, 0x22B0B8C8, 0xFFB5A58A, 0xFFB5A58A, 0xFFE5E0D5, 0xFFE8E4DA, 0xFF40475A, 0xFF2A2F3D, 0xFF2E3644, 0xFF232A37, 0xFF181D27, 0xFFC9CFDA, 0xFF3A404E, 0xFF5B4046, 0xFFFFC064, 0xFF121720, 0xFFA9B8CC, 0xFF262B35, 0xFFEDE8E0, 0xFF8FB0D6, 0xFF55627A, 0x99000000)
    val StormDay = ScenePalette.of(0xFF3F3A57, 0xFF8D8A96, 0x22FFF3B0, 0xFFD9C08A, 0xFFD9C08A, 0xFFEDE6D8, 0xFFFFFFFF, 0xFF6E6A80, 0xFF4A465C, 0xFF5E6B70, 0xFF45545A, 0xFF2D3A40, 0xFFE7E9EE, 0xFFCFC8C0, 0xFF7E4A4A, 0xFFFFC76A, 0xFF24302F, 0xFF2B3140, 0xFFF1EEF3, 0xFF221F2B, 0xFFE9C15A, 0xFFBDB2D6, 0x77140F1C)
    val StormNight = ScenePalette.of(0xFF15131F, 0xFF3A3548, 0x22FFF3B0, 0xFFB5A58A, 0xFFB5A58A, 0xFFE1DACB, 0xFFE8E4DA, 0xFF3C3850, 0xFF26233A, 0xFF2A3036, 0xFF1F252A, 0xFF12171B, 0xFFBFC4CE, 0xFF33333F, 0xFF4E3441, 0xFFFFBE5C, 0xFF0B0F12, 0xFF9AA3BF, 0xFF221F2E, 0xFFEEEAF4, 0xFFF2CF63, 0xFF5D5580, 0xAA000000)
    val SnowDay = ScenePalette.of(0xFFB8C8DD, 0xFFF1F2F4, 0x44FFFFFF, 0xFFF0C27A, 0xFFF5D8A4, 0xFFF6F2EA, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD5DCE6, 0xFFE2E8F0, 0xFFCBD5E2, 0xFFADBCD0, 0xFFFFFFFF, 0xFFEDE3D6, 0xFFB0564A, 0xFFF7D28C, 0xFF4F6B67, 0xFFFFFFFF, 0xFFFAFBFD, 0xFF2B3140, 0xFF6F93C0, 0xFFC5D3E8, 0x44283246)
    val SnowNight = ScenePalette.of(0xFF1D2640, 0xFF46506E, 0x33C8D6FF, 0xFFD9B98A, 0xFFD9B98A, 0xFFF3EEE2, 0xFFFFF7E6, 0xFF5A6482, 0xFF3E4763, 0xFF5B6685, 0xFF475170, 0xFF343D59, 0xFFDDE4F2, 0xFF4B5372, 0xFF6B4356, 0xFFFFC96E, 0xFF1D2A33, 0xFFF2F5FF, 0xFF28304A, 0xFFF0EEF5, 0xFFAFC4E8, 0xFF6C7DAA, 0x99000000)
    val FogDay = ScenePalette.of(0xFFC3C2BC, 0xFFE6E3DB, 0x33FFFFFF, 0xFFEBD2A6, 0xFFEEDDB8, 0xFFF3EFE6, 0xFFFFFFFF, 0xFFF1EFE9, 0xFFCFCBC3, 0xFFCFD0C6, 0xFFB4B9AE, 0xFF96A094, 0xFFFFFFFF, 0xFFE8E1D4, 0xFFAF6A55, 0xFFF4D08F, 0xFF6D7A6D, 0xFF6E7880, 0xFFF8F6F1, 0xFF2D2E2C, 0xFFA3845E, 0xFFD8CAB2, 0x44252521)
    val FogNight = ScenePalette.of(0xFF2A2D33, 0xFF474B53, 0x22FFFFFF, 0xFFB5A58A, 0xFFB5A58A, 0xFFE6E1D6, 0xFFE8E4DA, 0xFF53575F, 0xFF3C4047, 0xFF464A51, 0xFF383C42, 0xFF2A2D33, 0xFFC9CCD2, 0xFF41444B, 0xFF5B4441, 0xFFFFC369, 0xFF1D2024, 0xFFB5BCC6, 0xFF2B2E34, 0xFFEEEBE4, 0xFFD2B383, 0xFF62656E, 0x99000000)

    // Fixed themes.
    val RisoDay = ScenePalette.of(0xFF6FA8DC, 0xFFFDE9EF, 0x55FFE800, 0xFFFF48B0, 0xFFFFE800, 0xFFFFF3C4, 0xFFFFE800, 0xFFFFF6F8, 0xFFFFB3D9, 0xFF8CC3E8, 0xFF0078BF, 0xFF00597F, 0xFFFFFFFF, 0xFFFFE800, 0xFFFF48B0, 0xFF0078BF, 0xFF004F7A, 0xFF0078BF, 0xFFFFF7F0, 0xFF1A2B6D, 0xFFFF48B0, 0xFFFFE800, 0x551A2B6D)
    val RisoNight = ScenePalette.of(0xFF1A2B6D, 0xFF5B3E8E, 0x44FF48B0, 0xFFFF48B0, 0xFFFFE800, 0xFFFFE800, 0xFFFFE800, 0xFF6B4C9A, 0xFF3C2E73, 0xFF3D4FA0, 0xFF2A3783, 0xFF16215A, 0xFFFFD6EC, 0xFFFF48B0, 0xFFFFE800, 0xFFFFE800, 0xFF0E1545, 0xFFFFB3D9, 0xFF1F2A66, 0xFFFFF1F6, 0xFFFF48B0, 0xFFFFE800, 0x99000000)
    val MossDay = ScenePalette.of(0xFFA9C7A0, 0xFFEFE9D2, 0x44FFF6D0, 0xFFE3B04B, 0xFFEDCB7A, 0xFFF6EEDC, 0xFFFFF7E0, 0xFFF7F4E6, 0xFFD6D7BE, 0xFF9DB98E, 0xFF6F9464, 0xFF3F6440, 0xFFFFFFFF, 0xFFEBDDBE, 0xFFA8643F, 0xFF55624E, 0xFF2F4E31, 0xFF4E6E5A, 0xFFF6F2E3, 0xFF22301F, 0xFFB8733E, 0xFFD9C38E, 0x55202A1A)
    val MossNight = ScenePalette.of(0xFF101E17, 0xFF2C4A3A, 0x33C8FFD6, 0xFFE3B04B, 0xFFEDCB7A, 0xFFF2EAD2, 0xFFF4F0D8, 0xFF2F4A3D, 0xFF1F3329, 0xFF28453A, 0xFF1C342B, 0xFF11231C, 0xFFD6E2D6, 0xFF344A3F, 0xFF6B4A33, 0xFFFFCB6B, 0xFF0A1711, 0xFFB3CFBE, 0xFF1D2C24, 0xFFEFEBDD, 0xFFE3B04B, 0xFF4F6E5C, 0x99000000)

    /** Weights of the four time-of-day sheets for a daylight level and sun position. */
    fun timeWeights(daylight: Float, sunProgress: Float): FloatArray {
        val d = daylight.coerceIn(0f, 1f)
        val tw = 1f - abs(2f * d - 1f)
        val night = (1f - d) * (1f - tw)
        val dayish = d * (1f - tw)
        val edge = minOf(sunProgress, 1f - sunProgress)
        val golden = 1f - smoothstep(0.02f, 0.2f, edge)
        return floatArrayOf(dayish * (1f - golden), dayish * golden, tw, night)
    }

    /**
     * Sky and landscape blend continuously, but paper must stay clearly light or dark — a half-way
     * grey sheet would make ink unreadable — so it comes from the pure day or night sheet.
     */
    fun forState(s: SceneState): ScenePalette {
        val sky = blend(s)
        val paperSide = if (s.daylight >= 0.45f) s.copy(daylight = 1f, sunProgress = 0.5f) else s.copy(daylight = 0f, sunProgress = -0.5f)
        return sky.withPaperOf(blend(paperSide))
    }

    private fun blend(s: SceneState): ScenePalette {
        val tw = timeWeights(s.daylight, s.sunProgress)
        val clear = ScenePalette.mix(
            listOf(ClearDay to tw[0], GoldenDay to tw[1], Twilight to tw[2], ClearNight to tw[3]),
        )
        val warmth = (tw[1] + tw[2]).coerceIn(0f, 1f) * 0.22f
        fun mood(day: ScenePalette, night: ScenePalette): ScenePalette =
            ScenePalette.lerp(ScenePalette.lerp(night, day, s.daylight), clear, warmth)

        var rem = 1f
        val wStorm = s.thunder.coerceIn(0f, 1f) * rem; rem -= wStorm
        val wSnow = (s.snow * 1.3f).coerceIn(0f, 1f) * rem; rem -= wSnow
        val wFog = s.fog.coerceIn(0f, 1f) * rem; rem -= wFog
        val grey = maxOf(smoothstep(0.45f, 0.95f, s.cloudCover), (s.rain + s.drizzle * 0.8f).coerceIn(0f, 1f) * 0.95f)
        val wGrey = grey * rem; rem -= wGrey
        val wClear = rem

        val parts = buildList {
            if (wClear > 0.001f) add(clear to wClear)
            if (wGrey > 0.001f) add(mood(GreyDay, GreyNight) to wGrey)
            if (wFog > 0.001f) add(mood(FogDay, FogNight) to wFog)
            if (wSnow > 0.001f) add(mood(SnowDay, SnowNight) to wSnow)
            if (wStorm > 0.001f) add(mood(StormDay, StormNight) to wStorm)
        }
        return ScenePalette.mix(parts)
    }

    fun resolve(mode: PaletteMode, s: SceneState, context: Context? = null): ScenePalette = when (mode) {
        PaletteMode.Auto -> forState(s)
        PaletteMode.Linen -> forState(s.copy(daylight = 1f, sunProgress = 0.5f))
        PaletteMode.Ink -> forState(s.copy(daylight = 0f, sunProgress = -0.5f))
        PaletteMode.Riso -> fixed(RisoDay, RisoNight, s.daylight)
        PaletteMode.Moss -> fixed(MossDay, MossNight, s.daylight)
        PaletteMode.Wallpaper -> context?.let { fixed(wallpaper(it, night = false), wallpaper(it, night = true), s.daylight) }
            ?: forState(s)
    }

    private fun fixed(day: ScenePalette, night: ScenePalette, daylight: Float): ScenePalette =
        ScenePalette.lerp(night, day, daylight).withPaperOf(if (daylight >= 0.45f) day else night)

    /** Material You: build a paper scene from the system's wallpaper-derived tonal palettes. */
    fun wallpaper(context: Context, night: Boolean): ScenePalette {
        fun col(id: Int) = context.getColor(id).toLong() and 0xFFFFFFFFL
        val a1 = intArrayOf(android.R.color.system_accent1_50, android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300, android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600, android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900)
        val a2 = intArrayOf(android.R.color.system_accent2_50, android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300, android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600, android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900)
        val a3 = intArrayOf(android.R.color.system_accent3_50, android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300, android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600, android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900)
        val n1 = intArrayOf(android.R.color.system_neutral1_10, android.R.color.system_neutral1_50, android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900)
        return if (!night) {
            ScenePalette.of(
                col(a1[3]), col(a1[0]), 0x55FFFFFF, col(a3[3]), col(a3[1]), col(n1[1]), col(n1[0]), col(n1[0]), col(a1[1]),
                col(a2[2]), col(a2[4]), col(a2[6]), 0xFFFFFFFF, col(n1[1]), col(a3[5]), col(a3[2]), col(a2[7]), col(a1[6]),
                col(n1[0]), col(n1[5]), col(a3[5]), col(a3[1]), 0x44000000,
            )
        } else {
            ScenePalette.of(
                col(a1[9]), col(a1[7]), 0x33FFFFFF, col(a3[4]), col(a3[2]), col(n1[1]), col(n1[0]), col(a1[7]), col(a1[8]),
                col(a2[6]), col(a2[7]), col(a2[9]), col(n1[2]), col(a2[7]), col(a3[7]), col(a3[2]), col(a2[9]), col(a1[2]),
                col(n1[5]), col(n1[1]), col(a3[3]), col(a3[7]), 0x99000000,
            )
        }
    }
}
