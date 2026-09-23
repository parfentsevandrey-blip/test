package app.papersky.weather.scene

import android.content.Context
import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * Colours of one print. Stored as a flat ARGB array so palettes can be mixed channel-wise
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
 * Muted pigment palettes, as in a woodblock print (DESIGN_DOCTRINE §5). Every mood has a day and a
 * night sheet; clear weather also gets golden-hour and twilight sheets so dawn and dusk glow. The
 * paper is ivory by day and charcoal by night in every mood, tinted by a few per cent at most.
 */
object Palettes {
    //                                skyTop      skyBottom   glow        sun         sunRay      moon        star        cloud       cloudShade  hillFar     hillMid     hillNear    snowCap     house       roof        window      tree        precip      paper       paperInk    accent      tape        shadow
    val ClearDay = ScenePalette.of(0xFF8DAAC0, 0xFFECE3D2, 0x55FFF0D0, 0xFFF1DDB0, 0xFFF6E7C4, 0xFFEFE8D6, 0xFFFFF8EA, 0xFFF7F4EE, 0xFFD9D6D0, 0xFFA6B5BC, 0xFF7E948B, 0xFF4A5D53, 0xFFFBFBF8, 0xFF3F5048, 0xFF34443C, 0xFFF2C77E, 0xFF3A4C43, 0xFF52708E, 0xFFF4EFE6, 0xFF22201D, 0xFFB04A32, 0xFFD9CDB8, 0x552B2118)
    val GoldenDay = ScenePalette.of(0xFFA3A6BC, 0xFFF0CFA8, 0x77F6C89A, 0xFFD9653B, 0xFFEDA77A, 0xFFF3E8D2, 0xFFFFF3DC, 0xFFF6E4D2, 0xFFD9B8A2, 0xFFC0A89C, 0xFF957C72, 0xFF5B4A45, 0xFFFCF3EA, 0xFF4E3E39, 0xFF43342F, 0xFFF4C27A, 0xFF4A3B36, 0xFF6A5A78, 0xFFF5EDE2, 0xFF26201B, 0xFFB04A32, 0xFFE4C9AE, 0x662B1810)
    val Twilight = ScenePalette.of(0xFF4D5680, 0xFFD9A9A3, 0x55E7A7A0, 0xFFD8674A, 0xFFE89C84, 0xFFF3E6CF, 0xFFFFF1DA, 0xFFD9BDC2, 0xFFA88C9E, 0xFF8C86A3, 0xFF625C7C, 0xFF3A3652, 0xFFE6E0EE, 0xFF312E46, 0xFF2A273D, 0xFFF4C37A, 0xFF2E2B44, 0xFF4C4670, 0xFFF3ECE6, 0xFF25212A, 0xFFB04A32, 0xFFE4C1C4, 0x662A1633)
    val ClearNight = ScenePalette.of(0xFF10162A, 0xFF2C3654, 0x33A9B6E0, 0xFFD8674A, 0xFFE89C84, 0xFFEFE8D6, 0xFFF6F0E0, 0xFF394360, 0xFF262E47, 0xFF2D3854, 0xFF212A42, 0xFF151C2E, 0xFFCCD4E4, 0xFF10162A, 0xFF0E1322, 0xFFF0BE70, 0xFF111726, 0xFF9FB0CC, 0xFF1C2029, 0xFFECE5D6, 0xFFCDAE7A, 0xFF5A6280, 0x99000000)
    val GreyDay = ScenePalette.of(0xFF9AA3AA, 0xFFDAD8D2, 0x22FFFFFF, 0xFFEFE3C8, 0xFFEFE3C8, 0xFFF0ECE2, 0xFFFFFFFF, 0xFFECEAE5, 0xFFBDBBB7, 0xFFAAB2B0, 0xFF83908A, 0xFF57655D, 0xFFFFFFFF, 0xFF46544C, 0xFF3C4942, 0xFFF3CB85, 0xFF43514A, 0xFF4A5E72, 0xFFF2F0EB, 0xFF22211F, 0xFFB04A32, 0xFFC9CFD4, 0x55202428)
    val GreyNight = ScenePalette.of(0xFF1B1F27, 0xFF363B47, 0x22B0B8C8, 0xFFB5A58A, 0xFFB5A58A, 0xFFE5E0D5, 0xFFE8E4DA, 0xFF414756, 0xFF2B303C, 0xFF2F3642, 0xFF242A35, 0xFF181D26, 0xFFC9CFDA, 0xFF141820, 0xFF11151C, 0xFFEDBB6A, 0xFF151A22, 0xFFA9B8CC, 0xFF1E2127, 0xFFEAE5DB, 0xFFCDAE7A, 0xFF55627A, 0x99000000)
    val StormDay = ScenePalette.of(0xFF4A4858, 0xFF8F8E96, 0x22FFF3B0, 0xFFD9C9A0, 0xFFD9C9A0, 0xFFEDE6D8, 0xFFFFFFFF, 0xFF75737F, 0xFF504E5C, 0xFF626D71, 0xFF48555A, 0xFF2F3B40, 0xFFE7E9EE, 0xFF28333A, 0xFF222C32, 0xFFF2C46E, 0xFF27323A, 0xFF2E3544, 0xFFEFEDEC, 0xFF221F24, 0xFFB04A32, 0xFFBDB2D6, 0x77140F1C)
    val StormNight = ScenePalette.of(0xFF16151E, 0xFF393648, 0x22FFF3B0, 0xFFB5A58A, 0xFFB5A58A, 0xFFE1DACB, 0xFFE8E4DA, 0xFF3D3A4E, 0xFF28253A, 0xFF2B3136, 0xFF20262B, 0xFF13181C, 0xFFBFC4CE, 0xFF0F1316, 0xFF0C1013, 0xFFEDB85E, 0xFF0E1215, 0xFF9AA3BF, 0xFF1E1D24, 0xFFECE8EF, 0xFFCDAE7A, 0xFF5D5580, 0xAA000000)
    val SnowDay = ScenePalette.of(0xFFB3C1D1, 0xFFF0F0EF, 0x44FFFFFF, 0xFFF1E2C2, 0xFFF4E8CF, 0xFFF6F2EA, 0xFFFFFFFF, 0xFFFBFBFA, 0xFFD3D9E0, 0xFFDCE2E8, 0xFFC3CDD7, 0xFF9EACBA, 0xFFFFFFFF, 0xFF6B7887, 0xFF5E6B7A, 0xFFF3CF8A, 0xFF56646B, 0xFFFFFFFF, 0xFFF4F4F1, 0xFF232629, 0xFFB04A32, 0xFFC5D3E8, 0x44283246)
    val SnowNight = ScenePalette.of(0xFF1C2438, 0xFF444D66, 0x33C8D6FF, 0xFFD9B98A, 0xFFD9B98A, 0xFFF0ECE2, 0xFFFFF7E6, 0xFF586078, 0xFF3D455C, 0xFF58627E, 0xFF454E68, 0xFF333B53, 0xFFDDE3EE, 0xFF1F2536, 0xFF1A1F2E, 0xFFF1C272, 0xFF1C2433, 0xFFF2F4FA, 0xFF202536, 0xFFEEECF2, 0xFFCDAE7A, 0xFF6C7DAA, 0x99000000)
    val FogDay = ScenePalette.of(0xFFC3C2BC, 0xFFE4E1D9, 0x33FFFFFF, 0xFFEDE0C4, 0xFFEFE4CC, 0xFFF3EFE6, 0xFFFFFFFF, 0xFFEFEDE8, 0xFFCFCBC3, 0xFFCECFC7, 0xFFB3B8AE, 0xFF8F978D, 0xFFFFFFFF, 0xFF7A8178, 0xFF70776E, 0xFFF3CE8C, 0xFF6C7469, 0xFF6E7880, 0xFFF3F1EC, 0xFF262624, 0xFFB04A32, 0xFFD8CAB2, 0x44252521)
    val FogNight = ScenePalette.of(0xFF2A2C31, 0xFF464950, 0x22FFFFFF, 0xFFB5A58A, 0xFFB5A58A, 0xFFE6E1D6, 0xFFE8E4DA, 0xFF52555C, 0xFF3C3F45, 0xFF45484F, 0xFF383B41, 0xFF2A2D32, 0xFFC9CCD2, 0xFF1F2126, 0xFF1B1D21, 0xFFEDBF6A, 0xFF1E2024, 0xFFB5BCC6, 0xFF212226, 0xFFEDEAE4, 0xFFCDAE7A, 0xFF62656E, 0x99000000)

    // Fixed themes.
    val RisoDay = ScenePalette.of(0xFF6F86B4, 0xFFF4EEE6, 0x33F0B8A0, 0xFFD5553B, 0xFFE9927C, 0xFFF6EEDD, 0xFFFFFFFF, 0xFFF8F4EE, 0xFFC9D0E2, 0xFFA9B5D2, 0xFF6F83B3, 0xFF2F4A86, 0xFFFFFFFF, 0xFF263D72, 0xFFD5553B, 0xFFF2C07A, 0xFF243A6B, 0xFF2F4A86, 0xFFF7F3EC, 0xFF1F2B54, 0xFFD5553B, 0xFFE9C9C0, 0x551A2B6D)
    val RisoNight = ScenePalette.of(0xFF17224A, 0xFF3B4675, 0x33D5553B, 0xFFD5553B, 0xFFE9927C, 0xFFF3E9D8, 0xFFF6E7DE, 0xFF3D4878, 0xFF2A3460, 0xFF34437A, 0xFF273467, 0xFF18224A, 0xFFE8D8D8, 0xFF121A3A, 0xFFD5553B, 0xFFF2C07A, 0xFF111838, 0xFFE9C0B6, 0xFF1B2244, 0xFFF3EDE6, 0xFFE07A5F, 0xFF5D6CA0, 0x99000000)
    val MossDay = ScenePalette.of(0xFFA9BAA3, 0xFFECE6D3, 0x44FFF6D0, 0xFFE7CF9A, 0xFFEEDCB2, 0xFFF4EEDC, 0xFFFFF7E0, 0xFFF5F3E8, 0xFFD2D3BE, 0xFFA3B69A, 0xFF778F6E, 0xFF455E45, 0xFFFFFFFF, 0xFF3A503A, 0xFF324632, 0xFFF2CB86, 0xFF334A34, 0xFF4E6E5A, 0xFFF3F0E4, 0xFF1F2A1E, 0xFFA2632F, 0xFFD9C38E, 0x55202A1A)
    val MossNight = ScenePalette.of(0xFF101C16, 0xFF2A4436, 0x33C8FFD6, 0xFFE3B04B, 0xFFEDCB7A, 0xFFF2EAD2, 0xFFF4F0D8, 0xFF2F4A3D, 0xFF1F3329, 0xFF28453A, 0xFF1C342B, 0xFF11231C, 0xFFD6E2D6, 0xFF0D1B15, 0xFF0B1712, 0xFFF0C36A, 0xFF0C1812, 0xFFB3CFBE, 0xFF1B2721, 0xFFEDEADC, 0xFFD6B070, 0xFF4F6E5C, 0x99000000)

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
