package app.rosa.weather.widget.render.calendar

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import app.rosa.weather.core.model.Argb
import app.rosa.weather.widget.render.WidgetLight
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a month looks: the colours its painting is made of and what the glass over it catches. The
 * sky runs [sky] from zenith to horizon; the painted sun or moon sits at [bodyX], [bodyY] (0..1 of
 * the scene) and lights the glass ([light]); [accent] marks today and the weekends; [dark] says
 * light type reads over it.
 */
internal data class MonthArt(
    val month: Int,
    val sky: List<Argb>,
    val accent: Argb,
    val dark: Boolean,
    val bodyX: Float,
    val bodyY: Float,
    val bodyPower: Float,
    val bodyColor: Argb,
    val isMoon: Boolean = false,
) {
    /** The zenith, the horizon and the light's glow, for palettes built from this painting. */
    val zenith: Argb get() = sky.first()
    val horizon: Argb get() = sky.last()

    /** The painted sun or moon as the glass sees it, from the centre of a [w] × [h] pane. */
    fun light(w: Float, h: Float): WidgetLight = WidgetLight(
        angle = atan2(bodyY * h - h / 2f, bodyX * w - w / 2f),
        power = bodyPower,
        color = Argb.White.lerp(bodyColor, 0.65f),
        sky = zenith,
    )

    companion object {
        fun of(month: Int): MonthArt = ARTS[(month - 1).mod(12)]

        private val ARTS = listOf(
            // January: a snowbound pine forest in the blue hour, a young moon, stars.
            MonthArt(1, hex(0x0B1433, 0x1B2A5C, 0x3B4D8A, 0x8393C9), Argb.hex(0x9CC2FF), dark = true, bodyX = 0.52f, bodyY = 0.1f, bodyPower = 0.35f, bodyColor = Argb.hex(0xDCE4FF), isMoon = true),
            // February: a birch grove in snow at dawn, pink over the drifts.
            MonthArt(2, hex(0x5E7DBE, 0x9FB0DF, 0xEBC3CC, 0xFFE0C6), Argb.hex(0xFF8FA6), dark = false, bodyX = 0.3f, bodyY = 0.5f, bodyPower = 0.6f, bodyColor = Argb.hex(0xFFD7B8)),
            // March: the thaw — high sun, snow going grey, puddles of sky.
            MonthArt(3, hex(0x3F7FD0, 0x78AAE6, 0xBCD6F1, 0xE9F0F2), Argb.hex(0x2FA38F), dark = false, bodyX = 0.52f, bodyY = 0.1f, bodyPower = 0.9f, bodyColor = Argb.hex(0xFFF3D6)),
            // April: green hills after rain, a rainbow.
            MonthArt(4, hex(0x5E9BD6, 0x9CC7EB, 0xD6ECF2, 0xF1F5E4), Argb.hex(0x3FAE55), dark = false, bodyX = 0.2f, bodyY = 0.16f, bodyPower = 0.7f, bodyColor = Argb.hex(0xFFF1CF)),
            // May: orchards in blossom, petals on the wind.
            MonthArt(5, hex(0x7AB2E4, 0xB6D6EF, 0xF4D8E4, 0xFFF1E2), Argb.hex(0xE8618C), dark = false, bodyX = 0.5f, bodyY = 0.12f, bodyPower = 0.85f, bodyColor = Argb.hex(0xFFEBC9)),
            // June: a flowering meadow, an oak, fair-weather clouds.
            MonthArt(6, hex(0x2F86DE, 0x6DAEEE, 0xBADBF5, 0xE8F4F7), Argb.hex(0x2E7FD9), dark = false, bodyX = 0.52f, bodyY = 0.09f, bodyPower = 1f, bodyColor = Argb.hex(0xFFF6DE)),
            // July: wheat at sunset, poplars, the first fireflies.
            MonthArt(7, hex(0x5F6CB8, 0xC98CA8, 0xFFB98A, 0xFFE0A0), Argb.hex(0xFFA23A), dark = true, bodyX = 0.36f, bodyY = 0.52f, bodyPower = 0.8f, bodyColor = Argb.hex(0xFFC77A)),
            // August: a starry night over the stubble, the Milky Way, the Perseids.
            MonthArt(8, hex(0x080D26, 0x141D4A, 0x28316B, 0x474C84), Argb.hex(0xC4B4FF), dark = true, bodyX = 0.52f, bodyY = 0.1f, bodyPower = 0.25f, bodyColor = Argb.hex(0xD8DEFF), isMoon = true),
            // September: golden birches, a warm, clear light.
            MonthArt(9, hex(0x568FD2, 0x8FB9E6, 0xD4E1EB, 0xF6E8CF), Argb.hex(0xE6A21E), dark = false, bodyX = 0.52f, bodyY = 0.12f, bodyPower = 0.85f, bodyColor = Argb.hex(0xFFE7B5)),
            // October: maples ablaze in the mist, leaves falling.
            MonthArt(10, hex(0x7F7FAA, 0xCF9C8E, 0xEFBF9C, 0xF7DABD), Argb.hex(0xF26A2E), dark = true, bodyX = 0.3f, bodyY = 0.4f, bodyPower = 0.45f, bodyColor = Argb.hex(0xFFC89A)),
            // November: bare trees in fog, the first snow on the ground.
            MonthArt(11, hex(0x566477, 0x8793A3, 0xB5BCC5, 0xD3D6D9), Argb.hex(0x86B3D6), dark = true, bodyX = 0.5f, bodyY = 0.2f, bodyPower = 0f, bodyColor = Argb.White),
            // December: firs under snow, warm lights, a full moon.
            MonthArt(12, hex(0x0D1636, 0x1C2A5B, 0x33457E, 0x5B6BA5), Argb.hex(0xFF8A70), dark = true, bodyX = 0.52f, bodyY = 0.1f, bodyPower = 0.4f, bodyColor = Argb.hex(0xE8EEFF), isMoon = true),
        )

        private fun hex(vararg colors: Long) = colors.map { Argb.hex(it) }
    }
}

/**
 * Paints a month's scene into a rect of any proportions, in dp: sky, the sun or moon, hills in
 * three planes paling into the distance, the season's trees and ground, and what moves in the air.
 * Deterministic: the same month always paints the same picture at the same size.
 */
internal class SeasonScene {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /** Whether the season's falling things are left to the live tiles that animate them. */
    private var moving = false

    fun draw(canvas: Canvas, rect: RectF, art: MonthArt, live: Boolean = false) {
        moving = live
        val w = rect.width()
        val h = rect.height()
        canvas.withTranslation(rect.left, rect.top) {
            val r = Random(art.month * 7919)
            sky(this, w, h, art)
            when (art.month) {
                1 -> january(this, w, h, art, r)
                2 -> february(this, w, h, art, r)
                3 -> march(this, w, h, art, r)
                4 -> april(this, w, h, art, r)
                5 -> may(this, w, h, art, r)
                6 -> june(this, w, h, art, r)
                7 -> july(this, w, h, art, r)
                8 -> august(this, w, h, art, r)
                9 -> september(this, w, h, art, r)
                10 -> october(this, w, h, art, r)
                11 -> november(this, w, h, art, r)
                else -> december(this, w, h, art, r)
            }
        }
        paint.reset()
        paint.isAntiAlias = true
    }

    // region Months

    private fun january(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        stars(c, w, h * 0.55f, 0.9f, r)
        moon(c, w, h, art, crescent = 0.35f)
        hill(c, w, h, 0.5f, 0.05f, 1.3f, Argb.hex(0x6C7CB8), Argb.hex(0x4B5A94), r, haze = art.horizon, hazeAmount = 0.35f)
        pines(c, w, h, baseline = 0.55f, count = 16, height = 0.14f, Argb.hex(0x24315E), snow = Argb.hex(0xC9D5F5), r, haze = art.horizon, hazeAmount = 0.3f)
        hill(c, w, h, 0.6f, 0.05f, 1.8f, Argb.hex(0x3E4B80), Argb.hex(0x2D3866), r)
        pines(c, w, h, baseline = 0.66f, count = 9, height = 0.24f, Argb.hex(0x16203F), snow = Argb.hex(0xDDE6FA), r)
        ground(c, w, h, 0.7f, Argb.hex(0xCBD6F4), Argb.hex(0x8C9AD0), r, wave = 0.025f)
        snowShadows(c, w, h, 0.7f, Argb.hex(0x7684BD), r)
        snowfall(c, w, h, 80, 1f, r)
    }

    private fun february(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.07f, halo = 0.55f)
        hill(c, w, h, 0.52f, 0.04f, 1.1f, Argb.hex(0xC0BDE0), Argb.hex(0xA7A6D6), r, haze = art.horizon, hazeAmount = 0.45f)
        birches(c, w, h, baseline = 0.6f, count = 11, height = 0.3f, crown = null, r, haze = art.horizon, hazeAmount = 0.35f)
        hill(c, w, h, 0.62f, 0.04f, 1.6f, Argb.hex(0xE6E3F6), Argb.hex(0xC8C6E8), r)
        birches(c, w, h, baseline = 0.7f, count = 6, height = 0.5f, crown = null, r)
        ground(c, w, h, 0.72f, Argb.hex(0xF7F3FB), Argb.hex(0xCFCBEA), r, wave = 0.03f)
        snowShadows(c, w, h, 0.72f, Argb.hex(0xB9B3DE), r)
        drift(c, w, h, r)
        snowfall(c, w, h, 45, 0.7f, r)
    }

    private fun march(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.06f, halo = 0.45f)
        clouds(c, w, h, 3, 0.22f, Argb.hex(0xFFFFFF), 0.75f, r)
        hill(c, w, h, 0.52f, 0.04f, 1.2f, Argb.hex(0xA8BCCB), Argb.hex(0x93A8B9), r, haze = art.horizon, hazeAmount = 0.4f)
        birches(c, w, h, baseline = 0.58f, count = 10, height = 0.24f, crown = Argb.hex(0xB9CF9A).withAlpha(0.35f), r, haze = art.horizon, hazeAmount = 0.35f)
        hill(c, w, h, 0.62f, 0.05f, 1.7f, Argb.hex(0x84946F), Argb.hex(0x6C7A58), r)
        snowPatches(c, w, h, 0.6f, 0.68f, Argb.hex(0xEEF3F4), 7, r)
        birches(c, w, h, baseline = 0.72f, count = 5, height = 0.42f, crown = Argb.hex(0xC4D8A2).withAlpha(0.45f), r)
        ground(c, w, h, 0.74f, Argb.hex(0x8E9C68), Argb.hex(0x6B6A4C), r, wave = 0.02f)
        snowPatches(c, w, h, 0.76f, 1f, Argb.hex(0xE9EFF1), 9, r)
        puddles(c, w, h, art, r)
    }

    private fun april(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        rainbow(c, w, h)
        sun(c, w, h, art, size = 0.05f, halo = 0.4f)
        clouds(c, w, h, 4, 0.2f, Argb.hex(0xFFFFFF), 0.85f, r)
        hill(c, w, h, 0.54f, 0.05f, 1.2f, Argb.hex(0xA9CFA3), Argb.hex(0x92C28C), r, haze = art.horizon, hazeAmount = 0.35f)
        crowns(c, w, h, baseline = 0.6f, count = 9, height = 0.16f, listOf(Argb.hex(0x9ED06E), Argb.hex(0x7FBE5A), Argb.hex(0xB9DE8A)), r, haze = art.horizon, hazeAmount = 0.3f)
        hill(c, w, h, 0.64f, 0.05f, 1.6f, Argb.hex(0x7FC064), Argb.hex(0x5E9E4B), r)
        crowns(c, w, h, baseline = 0.72f, count = 4, height = 0.28f, listOf(Argb.hex(0x8ACB62), Argb.hex(0x67AE4B), Argb.hex(0xA9DA7C)), r)
        ground(c, w, h, 0.74f, Argb.hex(0x7EC45E), Argb.hex(0x4C8D3C), r, wave = 0.02f)
        flowers(c, w, h, 0.76f, 40, listOf(Argb.hex(0xFFFFFF), Argb.hex(0xFFE066)), r)
        rain(c, w, h, 26, r)
    }

    private fun may(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.06f, halo = 0.5f)
        clouds(c, w, h, 2, 0.18f, Argb.hex(0xFFF8FB), 0.7f, r)
        hill(c, w, h, 0.54f, 0.05f, 1.3f, Argb.hex(0xBBD7B4), Argb.hex(0xA3C89C), r, haze = art.horizon, hazeAmount = 0.4f)
        crowns(c, w, h, baseline = 0.6f, count = 9, height = 0.15f, listOf(Argb.hex(0xF7C3D2), Argb.hex(0xFBE1E8), Argb.hex(0xE8A5BC)), r, haze = art.horizon, hazeAmount = 0.3f)
        hill(c, w, h, 0.65f, 0.05f, 1.7f, Argb.hex(0x8DC77A), Argb.hex(0x67A956), r)
        crowns(c, w, h, baseline = 0.74f, count = 4, height = 0.3f, listOf(Argb.hex(0xF9C9D6), Argb.hex(0xFFFFFF), Argb.hex(0xF2A7BF), Argb.hex(0xFDE3EA)), r)
        ground(c, w, h, 0.76f, Argb.hex(0x88C76B), Argb.hex(0x5A9C4A), r, wave = 0.02f)
        flowers(c, w, h, 0.78f, 55, listOf(Argb.hex(0xFFE066), Argb.hex(0xFFFFFF), Argb.hex(0xFFF3A0)), r)
        petals(c, w, h, 40, r)
    }

    private fun june(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.055f, halo = 0.5f)
        clouds(c, w, h, 4, 0.26f, Argb.hex(0xFFFFFF), 0.95f, r)
        hill(c, w, h, 0.56f, 0.04f, 1.1f, Argb.hex(0x8DB9CB), Argb.hex(0x7CAFC2), r, haze = art.horizon, hazeAmount = 0.35f)
        hill(c, w, h, 0.62f, 0.05f, 1.5f, Argb.hex(0x74B25E), Argb.hex(0x5E9E4A), r)
        oak(c, w * 0.72f, h * 0.64f, h * 0.36f, r)
        ground(c, w, h, 0.72f, Argb.hex(0x80C45E), Argb.hex(0x4A8A3A), r, wave = 0.025f)
        flowers(c, w, h, 0.74f, 80, listOf(Argb.hex(0xFFFFFF), Argb.hex(0xFFE066), Argb.hex(0x6C8CFF), Argb.hex(0xE8514A), Argb.hex(0xFFFFFF)), r)
    }

    private fun july(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.09f, halo = 0.8f)
        hill(c, w, h, 0.56f, 0.03f, 1.0f, Argb.hex(0xC98E86), Argb.hex(0xB37A73), r, haze = art.horizon, hazeAmount = 0.45f)
        poplars(c, w, h, baseline = 0.6f, count = 7, height = 0.26f, Argb.hex(0x6A4A5C), r, haze = art.horizon, hazeAmount = 0.3f)
        hill(c, w, h, 0.62f, 0.03f, 1.4f, Argb.hex(0xE0A34E), Argb.hex(0xC8883A), r)
        field(c, w, h, 0.64f, Argb.hex(0xF0C060), Argb.hex(0xB07633), r)
        fireflies(c, w, h, 0.62f, 22, Argb.hex(0xFFE9A0), r)
    }

    private fun august(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        milkyWay(c, w, h, r)
        stars(c, w, h * 0.7f, 1.3f, r)
        meteors(c, w, h, r)
        moon(c, w, h, art, crescent = 0.25f)
        hill(c, w, h, 0.62f, 0.03f, 1.1f, Argb.hex(0x252C58), Argb.hex(0x1D2349), r)
        hill(c, w, h, 0.68f, 0.03f, 1.5f, Argb.hex(0x171D40), Argb.hex(0x121733), r)
        haystacks(c, w, h, 0.72f, Argb.hex(0x0E122B), r)
        ground(c, w, h, 0.74f, Argb.hex(0x151B3C), Argb.hex(0x0B0F26), r, wave = 0.015f)
        fireflies(c, w, h, 0.7f, 14, Argb.hex(0xD9F59A), r)
    }

    private fun september(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.06f, halo = 0.55f)
        clouds(c, w, h, 2, 0.18f, Argb.hex(0xFFFFFF), 0.6f, r)
        hill(c, w, h, 0.54f, 0.04f, 1.2f, Argb.hex(0xAFBDC6), Argb.hex(0x9CAAB6), r, haze = art.horizon, hazeAmount = 0.4f)
        birches(c, w, h, baseline = 0.6f, count = 11, height = 0.22f, crown = Argb.hex(0xE9B83E), r, haze = art.horizon, hazeAmount = 0.35f)
        hill(c, w, h, 0.64f, 0.04f, 1.6f, Argb.hex(0xB6A049), Argb.hex(0x947F35), r)
        birches(c, w, h, baseline = 0.74f, count = 5, height = 0.44f, crown = Argb.hex(0xF4C84A), r)
        ground(c, w, h, 0.75f, Argb.hex(0xBBA65A), Argb.hex(0x7C6A35), r, wave = 0.02f)
        litter(c, w, h, 0.76f, 60, listOf(Argb.hex(0xF2C94C), Argb.hex(0xE5A93A), Argb.hex(0xFFE08A)), r)
        leaves(c, w, h, 16, listOf(Argb.hex(0xF4C84A), Argb.hex(0xE8A93A), Argb.hex(0xFFD86A)), r)
    }

    private fun october(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        sun(c, w, h, art, size = 0.08f, halo = 0.75f)
        hill(c, w, h, 0.52f, 0.04f, 1.2f, Argb.hex(0xC19C98), Argb.hex(0xB18C8A), r, haze = art.horizon, hazeAmount = 0.5f)
        crowns(c, w, h, baseline = 0.58f, count = 10, height = 0.16f, listOf(Argb.hex(0xD9794A), Argb.hex(0xC9573A), Argb.hex(0xE89A55)), r, haze = art.horizon, hazeAmount = 0.45f)
        fog(c, w, h, 0.56f, 0.35f, art.horizon)
        hill(c, w, h, 0.64f, 0.04f, 1.6f, Argb.hex(0x9C5534), Argb.hex(0x7A3C25), r)
        crowns(c, w, h, baseline = 0.74f, count = 4, height = 0.34f, listOf(Argb.hex(0xE8632B), Argb.hex(0xF28C38), Argb.hex(0xC83F2A), Argb.hex(0xF6A24A)), r)
        ground(c, w, h, 0.75f, Argb.hex(0xA3532C), Argb.hex(0x5E2E1C), r, wave = 0.02f)
        litter(c, w, h, 0.76f, 90, listOf(Argb.hex(0xE8632B), Argb.hex(0xF28C38), Argb.hex(0xC83F2A), Argb.hex(0xF6B04A)), r)
        leaves(c, w, h, 22, listOf(Argb.hex(0xE8632B), Argb.hex(0xF28C38), Argb.hex(0xD24A2A), Argb.hex(0xF6B04A)), r)
    }

    private fun november(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        hill(c, w, h, 0.52f, 0.04f, 1.2f, Argb.hex(0x9DA6B1), Argb.hex(0x8E97A3), r, haze = art.horizon, hazeAmount = 0.5f)
        bareTrees(c, w, h, baseline = 0.58f, count = 9, height = 0.2f, Argb.hex(0x6D7582), r, haze = art.horizon, hazeAmount = 0.45f)
        fog(c, w, h, 0.55f, 0.45f, art.horizon)
        hill(c, w, h, 0.64f, 0.04f, 1.5f, Argb.hex(0x5F6773), Argb.hex(0x4B525D), r)
        bareTrees(c, w, h, baseline = 0.74f, count = 4, height = 0.42f, Argb.hex(0x2F343C), r)
        ground(c, w, h, 0.75f, Argb.hex(0x6E675C), Argb.hex(0x48433C), r, wave = 0.02f)
        snowPatches(c, w, h, 0.75f, 1f, Argb.hex(0xDCE0E6), 14, r)
        litter(c, w, h, 0.78f, 18, listOf(Argb.hex(0xA0643A), Argb.hex(0x8B5A34)), r)
        fog(c, w, h, 0.78f, 0.25f, Argb.hex(0xD9DCE0))
        snowfall(c, w, h, 24, 0.6f, r)
    }

    private fun december(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        stars(c, w, h * 0.5f, 0.8f, r)
        moon(c, w, h, art, crescent = 0f)
        hill(c, w, h, 0.54f, 0.04f, 1.2f, Argb.hex(0x5C6DA7), Argb.hex(0x4A5A93), r, haze = art.horizon, hazeAmount = 0.3f)
        pines(c, w, h, baseline = 0.6f, count = 14, height = 0.16f, Argb.hex(0x223058), snow = Argb.hex(0xD3DDF7), r, haze = art.horizon, hazeAmount = 0.25f)
        hill(c, w, h, 0.66f, 0.04f, 1.6f, Argb.hex(0x3A4A80), Argb.hex(0x2A366A), r)
        lights(c, w, h, r)
        pines(c, w, h, baseline = 0.72f, count = 7, height = 0.3f, Argb.hex(0x12203D), snow = Argb.hex(0xE8EEFF), r)
        ground(c, w, h, 0.74f, Argb.hex(0xD6DEF6), Argb.hex(0x98A6D6), r, wave = 0.025f)
        snowShadows(c, w, h, 0.74f, Argb.hex(0x7D8BC4), r)
        snowfall(c, w, h, 110, 1.1f, r)
    }

    // endregion

    // region Sky and lights

    private fun sky(c: Canvas, w: Float, h: Float, art: MonthArt) {
        paint.shader = LinearGradient(0f, 0f, 0f, h * 0.78f, art.sky.map { it.value }.toIntArray(), floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun sun(c: Canvas, w: Float, h: Float, art: MonthArt, size: Float, halo: Float) {
        val x = art.bodyX * w
        val y = art.bodyY * h
        val s = min(w, h)
        val r = s * size
        paint.shader = RadialGradient(
            x, y, max(w, h) * halo,
            intArrayOf(art.bodyColor.withAlpha(0.55f).value, art.bodyColor.withAlpha(0.18f).value, 0),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(x, y, r * 1.6f, intArrayOf(Argb.White.value, art.bodyColor.value, art.bodyColor.withAlpha(0f).value), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.6f, paint)
        paint.shader = null
    }

    private fun moon(c: Canvas, w: Float, h: Float, art: MonthArt, crescent: Float) {
        val x = art.bodyX * w
        val y = art.bodyY * h
        val r = min(w, h) * 0.045f
        paint.shader = RadialGradient(x, y, r * 9f, intArrayOf(art.bodyColor.withAlpha(0.28f).value, art.bodyColor.withAlpha(0.06f).value, 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 9f, paint)
        paint.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(Argb.White.value, art.bodyColor.value), null, Shader.TileMode.CLAMP)
        if (crescent > 0f) {
            // The lit limb only: the moon minus a disc shifted into the dark.
            val lit = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
            val shade = Path().apply { addCircle(x + r * (1f - crescent) * 1.1f, y - r * 0.15f, r * 0.95f, Path.Direction.CW) }
            lit.op(shade, Path.Op.DIFFERENCE)
            c.drawPath(lit, paint)
        } else {
            c.drawCircle(x, y, r, paint)
            // Maria: a few soft, darker seas.
            paint.shader = null
            paint.color = art.sky[2].withAlpha(0.18f).value
            c.drawCircle(x - r * 0.25f, y - r * 0.15f, r * 0.32f, paint)
            c.drawCircle(x + r * 0.2f, y + r * 0.25f, r * 0.22f, paint)
            c.drawCircle(x + r * 0.3f, y - r * 0.3f, r * 0.15f, paint)
        }
        paint.shader = null
    }

    private fun stars(c: Canvas, w: Float, h: Float, density: Float, r: Random) {
        val n = (w * h / 380f * density).toInt().coerceIn(12, 260)
        paint.shader = null
        repeat(n) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val size = 0.3f + r.nextFloat() * r.nextFloat() * 1.1f
            paint.color = Argb.White.withAlpha(0.3f + r.nextFloat() * 0.6f).value
            c.drawCircle(x, y, size, paint)
            if (size > 1.05f) {
                // The brightest twinkle with four short rays.
                paint.color = Argb.White.withAlpha(0.35f).value
                c.drawRect(x - size * 3f, y - 0.2f, x + size * 3f, y + 0.2f, paint)
                c.drawRect(x - 0.2f, y - size * 3f, x + 0.2f, y + size * 3f, paint)
            }
        }
    }

    private fun milkyWay(c: Canvas, w: Float, h: Float, r: Random) {
        c.withRotation(-28f, w / 2, h * 0.3f) {
            val band = RectF(-w * 0.3f, h * 0.18f, w * 1.3f, h * 0.42f)
            paint.shader = LinearGradient(0f, band.top, 0f, band.bottom, intArrayOf(0, Argb.hex(0xB9B6F0).withAlpha(0.22f).value, Argb.hex(0xE7D9FF).withAlpha(0.3f).value, Argb.hex(0xB9B6F0).withAlpha(0.2f).value, 0), null, Shader.TileMode.CLAMP)
            paint.maskFilter = BlurMaskFilter(min(w, h) * 0.04f, BlurMaskFilter.Blur.NORMAL)
            drawRect(band, paint)
            paint.maskFilter = null
            paint.shader = null
            // Dense dust of faint stars along the band.
            repeat((w * 1.4f).toInt().coerceIn(60, 400)) {
                val x = band.left + r.nextFloat() * band.width()
                val y = band.centerY() + (r.nextFloat() + r.nextFloat() - 1f) * band.height() * 0.45f
                paint.color = Argb.White.withAlpha(0.2f + r.nextFloat() * 0.45f).value
                drawCircle(x, y, 0.25f + r.nextFloat() * 0.45f, paint)
            }
        }
    }

    private fun meteors(c: Canvas, w: Float, h: Float, r: Random) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat(3) { i ->
            val x = w * (0.18f + i * 0.28f + r.nextFloat() * 0.1f)
            val y = h * (0.08f + r.nextFloat() * 0.25f)
            val len = min(w, h) * (0.12f + r.nextFloat() * 0.1f)
            val dx = len * 0.85f
            val dy = len * 0.5f
            paint.shader = LinearGradient(x, y, x - dx, y - dy, Argb.White.withAlpha(0.9f).value, Argb.hex(0xB9C4FF).withAlpha(0f).value, Shader.TileMode.CLAMP)
            paint.strokeWidth = 1.1f
            c.drawLine(x, y, x - dx, y - dy, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun clouds(c: Canvas, w: Float, h: Float, count: Int, top: Float, color: Argb, alpha: Float, r: Random) {
        val s = min(w, h)
        paint.shader = null
        repeat(count) { i ->
            val cx = w * (0.08f + (i + 0.2f + r.nextFloat() * 0.6f) / count * 0.9f)
            val base = h * top * (0.45f + r.nextFloat() * 0.7f)
            val cw = w * (0.13f + r.nextFloat() * 0.1f)
            // Cumulus: puffs heaped on a flat base, lit from above, grey beneath.
            val puffs = 5 + r.nextInt(3)
            val shade = color.lerp(Argb.hex(0x8C9CB8), 0.4f)
            paint.maskFilter = BlurMaskFilter(s * 0.004f, BlurMaskFilter.Blur.NORMAL)
            for (pass in 0..1) {
                repeat(puffs) { p ->
                    val t = p / (puffs - 1f)
                    val bx = cx - cw / 2 + t * cw
                    val bulge = sin(t * PI.toFloat())
                    val br = cw * (0.13f + 0.13f * bulge) * (0.85f + r.nextFloat() * 0.3f)
                    val by = base - br * (0.6f + bulge * 0.7f)
                    paint.color = (if (pass == 0) shade else color).withAlpha(alpha * (if (pass == 0) 0.9f else 1f)).value
                    c.drawCircle(bx, by + (if (pass == 0) br * 0.25f else 0f), br, paint)
                }
            }
            paint.maskFilter = null
        }
    }

    private fun rainbow(c: Canvas, w: Float, h: Float) {
        val cx = w * 0.62f
        val cy = h * 0.66f
        val radius = max(w, h) * 0.52f
        val bands = listOf(0xE85A5A, 0xF29A4A, 0xF5D65A, 0x7CC96A, 0x5AA8E8, 0x7A6CD6)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.035f
        paint.maskFilter = BlurMaskFilter(radius * 0.02f, BlurMaskFilter.Blur.NORMAL)
        bands.forEachIndexed { i, rgb ->
            paint.color = Argb.hex(rgb.toLong()).withAlpha(0.28f).value
            val rr = radius - i * paint.strokeWidth * 0.9f
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), 190f, 160f, false, paint)
        }
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
    }

    private fun lights(c: Canvas, w: Float, h: Float, r: Random) {
        // Windows and garlands far off in the snow: warm bokeh along the valley.
        val colors = listOf(0xFFD27A, 0xFF9E5E, 0xFF6B6B, 0xFFE9B0, 0x9AD7FF)
        val s = min(w, h)
        repeat(26) {
            val x = r.nextFloat() * w
            val y = h * (0.6f + r.nextFloat() * 0.08f)
            val rr = s * (0.006f + r.nextFloat() * 0.018f)
            val color = Argb.hex(colors[r.nextInt(colors.size)].toLong())
            paint.shader = RadialGradient(x, y, rr * 3f, intArrayOf(color.withAlpha(0.95f).value, color.withAlpha(0.35f).value, 0), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rr * 3f, paint)
        }
        paint.shader = null
    }

    private fun fog(c: Canvas, w: Float, h: Float, y: Float, alpha: Float, color: Argb) {
        paint.maskFilter = BlurMaskFilter(h * 0.04f, BlurMaskFilter.Blur.NORMAL)
        paint.shader = null
        for (i in 0 until 3) {
            paint.color = color.withAlpha(alpha * (0.6f + i * 0.2f)).value
            val top = h * (y + i * 0.04f)
            c.drawRect(-w * 0.1f, top, w * 1.1f, top + h * 0.06f, paint)
        }
        paint.maskFilter = null
    }

    // endregion

    // region Land

    /** A range of hills: smooth, seeded ridges with a gradient from the crest down. */
    private fun hill(
        c: Canvas, w: Float, h: Float, base: Float, amp: Float, freq: Float, top: Argb, bottom: Argb, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val phase = r.nextFloat() * 6.28f
        val phase2 = r.nextFloat() * 6.28f
        path.reset()
        path.moveTo(0f, h)
        val steps = 48
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val y = h * (base - amp * (0.6f * sin(t * PI.toFloat() * 2f * freq + phase) + 0.4f * sin(t * PI.toFloat() * 5.3f * freq + phase2)))
            path.lineTo(t * w, y)
        }
        path.lineTo(w, h)
        path.close()
        val t = if (haze != null) top.lerp(haze, hazeAmount) else top
        val b = if (haze != null) bottom.lerp(haze, hazeAmount * 0.6f) else bottom
        paint.shader = LinearGradient(0f, h * (base - amp), 0f, h, t.value, b.value, Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
    }

    private fun ground(c: Canvas, w: Float, h: Float, top: Float, light: Argb, dark: Argb, r: Random, wave: Float) {
        val phase = r.nextFloat() * 6.28f
        path.reset()
        path.moveTo(0f, h)
        for (i in 0..40) {
            val t = i / 40f
            path.lineTo(t * w, h * (top - wave * sin(t * 6.28f * 0.8f + phase)))
        }
        path.lineTo(w, h)
        path.close()
        paint.shader = LinearGradient(0f, h * top, 0f, h, light.value, dark.value, Shader.TileMode.CLAMP)
        c.drawPath(path, paint)
        paint.shader = null
    }

    private fun snowShadows(c: Canvas, w: Float, h: Float, top: Float, color: Argb, r: Random) {
        paint.maskFilter = BlurMaskFilter(h * 0.02f, BlurMaskFilter.Blur.NORMAL)
        repeat(5) {
            val x = r.nextFloat() * w
            val y = h * (top + 0.06f + r.nextFloat() * (0.9f - top))
            paint.color = color.withAlpha(0.28f).value
            c.drawOval(RectF(x - w * 0.18f, y - h * 0.012f, x + w * 0.18f, y + h * 0.018f), paint)
        }
        paint.maskFilter = null
    }

    private fun drift(c: Canvas, w: Float, h: Float, r: Random) {
        // Snow blown across the drifts: long, faint streaks low over the ground.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat(14) {
            val x = r.nextFloat() * w
            val y = h * (0.66f + r.nextFloat() * 0.3f)
            val len = w * (0.08f + r.nextFloat() * 0.14f)
            paint.shader = LinearGradient(x, y, x + len, y - len * 0.08f, Argb.White.withAlpha(0f).value, Argb.White.withAlpha(0.5f).value, Shader.TileMode.MIRROR)
            paint.strokeWidth = 0.7f + r.nextFloat() * 0.6f
            c.drawLine(x, y, x + len, y - len * 0.08f, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun snowPatches(c: Canvas, w: Float, h: Float, from: Float, to: Float, color: Argb, count: Int, r: Random) {
        // Snow lying in patches: each a cluster of flat lobes, smaller and fainter toward the horizon.
        paint.maskFilter = BlurMaskFilter(min(w, h) * 0.003f, BlurMaskFilter.Blur.NORMAL)
        repeat(count) {
            val depth = r.nextFloat()
            val x = r.nextFloat() * w
            val y = h * (from + depth * (to - from))
            val size = w * (0.02f + depth * 0.06f) * (0.6f + r.nextFloat() * 0.6f)
            paint.color = color.withAlpha(0.55f + depth * 0.4f).value
            repeat(4 + r.nextInt(3)) {
                val lx = x + (r.nextFloat() - 0.5f) * size * 1.6f
                val ly = y + (r.nextFloat() - 0.5f) * size * 0.16f
                val lw = size * (0.35f + r.nextFloat() * 0.45f)
                c.drawOval(RectF(lx - lw, ly - lw * 0.1f, lx + lw, ly + lw * 0.12f), paint)
            }
        }
        paint.maskFilter = null
    }

    private fun puddles(c: Canvas, w: Float, h: Float, art: MonthArt, r: Random) {
        repeat(4) {
            val x = r.nextFloat() * w
            val y = h * (0.8f + r.nextFloat() * 0.16f)
            val pw = w * (0.06f + r.nextFloat() * 0.08f)
            val oval = RectF(x - pw, y - pw * 0.12f, x + pw, y + pw * 0.12f)
            paint.shader = LinearGradient(0f, oval.top, 0f, oval.bottom, art.sky[1].value, art.sky[2].value, Shader.TileMode.CLAMP)
            c.drawOval(oval, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.6f
            paint.color = Argb.White.withAlpha(0.5f).value
            c.drawArc(oval, 200f, 70f, false, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun field(c: Canvas, w: Float, h: Float, top: Float, light: Argb, dark: Argb, r: Random) {
        ground(c, w, h, top, light, dark, r, wave = 0.012f)
        // Rows of wheat running to the horizon, and stalks catching the low sun.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat((w * 1.6f).toInt().coerceIn(80, 520)) {
            val x = r.nextFloat() * w
            val depth = r.nextFloat()
            val y = h * (top + 0.02f + depth * depth * (1f - top))
            val len = h * (0.01f + depth * 0.04f)
            paint.strokeWidth = 0.4f + depth * 0.8f
            paint.color = (if (r.nextBoolean()) light.lerp(Argb.White, 0.35f) else dark).withAlpha(0.25f + depth * 0.45f).value
            c.drawLine(x, y, x + len * 0.15f, y - len, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun haystacks(c: Canvas, w: Float, h: Float, base: Float, color: Argb, r: Random) {
        paint.shader = null
        paint.color = color.value
        repeat(3) { i ->
            val x = w * (0.18f + i * 0.3f + r.nextFloat() * 0.08f)
            val sw = w * (0.05f + r.nextFloat() * 0.03f)
            val y = h * (base + r.nextFloat() * 0.03f)
            c.drawOval(RectF(x - sw, y - sw * 1.1f, x + sw, y + sw * 0.6f), paint)
            c.drawRect(x - sw, y - sw * 0.1f, x + sw, y + sw * 0.2f, paint)
        }
    }

    // endregion

    // region Trees

    private fun pines(
        c: Canvas, w: Float, h: Float, baseline: Float, count: Int, height: Float, color: Argb, snow: Argb, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val body = if (haze != null) color.lerp(haze, hazeAmount) else color
        val cap = if (haze != null) snow.lerp(haze, hazeAmount * 0.8f) else snow
        repeat(count) {
            val x = w * (it + r.nextFloat() * 0.8f) / count
            val th = h * height * (0.65f + r.nextFloat() * 0.5f)
            val y = h * baseline + r.nextFloat() * h * 0.02f
            val tw = th * 0.42f
            // Tiers of a fir, widest at the foot, each dusted with snow along its upper edge.
            val tiers = 4
            for (t in 0 until tiers) {
                val k = t / tiers.toFloat()
                val tierTop = y - th * (1f - k * 0.78f)
                val tierBottom = y - th * (0.62f - k * 0.62f)
                val half = tw * (0.35f + k * 0.65f) / 2f
                path.reset()
                path.moveTo(x, tierTop)
                path.lineTo(x + half, tierBottom)
                path.quadTo(x, tierBottom - th * 0.03f, x - half, tierBottom)
                path.close()
                paint.color = body.value
                c.drawPath(path, paint)
                path.reset()
                path.moveTo(x, tierTop)
                path.lineTo(x + half * 0.55f, tierTop + (tierBottom - tierTop) * 0.55f)
                path.quadTo(x, tierTop + (tierBottom - tierTop) * 0.4f, x - half * 0.7f, tierTop + (tierBottom - tierTop) * 0.62f)
                path.close()
                paint.color = cap.withAlpha(0.9f).value
                c.drawPath(path, paint)
            }
            paint.color = body.lerp(Argb.Black, 0.3f).value
            c.drawRect(x - tw * 0.04f, y - th * 0.05f, x + tw * 0.04f, y + h * 0.01f, paint)
        }
    }

    private fun birches(
        c: Canvas, w: Float, h: Float, baseline: Float, count: Int, height: Float, crown: Argb?, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val bark = Argb.hex(0xF3F0EA).let { if (haze != null) it.lerp(haze, hazeAmount) else it }
        val mark = Argb.hex(0x3E3A40).let { if (haze != null) it.lerp(haze, hazeAmount) else it }
        val twig = Argb.hex(0x6E6478).let { if (haze != null) it.lerp(haze, hazeAmount) else it }
        repeat(count) {
            val x = w * (it + r.nextFloat() * 0.8f) / count
            val th = h * height * (0.7f + r.nextFloat() * 0.45f)
            val y = h * baseline + r.nextFloat() * h * 0.02f
            val lean = (r.nextFloat() - 0.5f) * th * 0.08f
            val trunk = max(0.8f, th * 0.022f)
            // A bare crown is a haze of fine twigs, then a few boughs, then the white trunk.
            if (crown == null) {
                paint.maskFilter = BlurMaskFilter(max(0.6f, th * 0.04f), BlurMaskFilter.Blur.NORMAL)
                paint.color = twig.withAlpha(0.32f).value
                c.drawOval(RectF(x + lean - th * 0.16f, y - th * 1.02f, x + lean + th * 0.16f, y - th * 0.42f), paint)
                paint.maskFilter = null
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = max(0.35f, trunk * 0.3f)
            paint.color = twig.withAlpha(0.6f).value
            repeat(4) { b ->
                val t = 0.55f + b * 0.1f
                val bx = x + lean * t
                val by = y - th * t
                val side = if (b % 2 == 0) 1f else -1f
                val bl = th * (0.12f - b * 0.015f)
                path.reset()
                path.moveTo(bx, by)
                path.quadTo(bx + side * bl * 0.7f, by - bl * 0.5f, bx + side * bl, by + bl * 0.15f)
                c.drawPath(path, paint)
            }
            paint.strokeWidth = trunk
            paint.color = bark.value
            c.drawLine(x, y, x + lean, y - th, paint)
            paint.style = Paint.Style.FILL
            // Black lenticels across the bark.
            paint.color = mark.withAlpha(0.8f).value
            repeat(5) { m ->
                val t = 0.1f + m * 0.17f + r.nextFloat() * 0.05f
                val mx = x + lean * t
                c.drawRect(mx - trunk * 0.5f, y - th * t, mx + trunk * 0.3f, y - th * t + max(0.5f, trunk * 0.35f), paint)
            }
            if (crown != null) {
                val cc = if (haze != null) crown.lerp(haze, hazeAmount) else crown
                foliage(c, x + lean, y - th * 0.72f, th * 0.26f, th * 0.3f, listOf(cc, cc.lerp(Argb.White, 0.25f), cc.lerp(Argb.Black, 0.15f)), r)
            }
        }
    }

    /** Round crowns: deciduous trees, young in April, in blossom in May, ablaze in October. */
    private fun crowns(
        c: Canvas, w: Float, h: Float, baseline: Float, count: Int, height: Float, colors: List<Argb>, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val tones = colors.map { if (haze != null) it.lerp(haze, hazeAmount) else it }
        val bark = Argb.hex(0x5A4638).let { if (haze != null) it.lerp(haze, hazeAmount) else it }
        repeat(count) {
            val x = w * (it + r.nextFloat() * 0.8f) / count
            val th = h * height * (0.7f + r.nextFloat() * 0.5f)
            val y = h * baseline + r.nextFloat() * h * 0.02f
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = max(0.8f, th * 0.05f)
            paint.color = bark.value
            c.drawLine(x, y, x, y - th * 0.55f, paint)
            paint.strokeWidth = max(0.5f, th * 0.025f)
            c.drawLine(x, y - th * 0.35f, x + th * 0.14f, y - th * 0.58f, paint)
            c.drawLine(x, y - th * 0.4f, x - th * 0.12f, y - th * 0.62f, paint)
            paint.style = Paint.Style.FILL
            foliage(c, x, y - th * 0.68f, th * 0.36f, th * 0.3f, tones, r)
        }
    }

    private fun foliage(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, tones: List<Argb>, r: Random) {
        // Many small clusters heaped into a crown: shaded below, lit on top, a few loose leaves.
        val base = tones.first()
        val dark = base.lerp(Argb.Black, 0.28f)
        val light = tones.last().lerp(Argb.White, 0.18f)
        paint.color = dark.value
        c.drawOval(RectF(cx - rx * 0.92f, cy - ry * 0.7f, cx + rx * 0.92f, cy + ry * 0.92f), paint)
        val clusters = 26
        repeat(clusters) {
            val a = r.nextFloat() * 6.28f
            val d = sqrt(r.nextFloat()) * 0.82f
            val bx = cx + cos(a) * rx * d
            val by = cy + sin(a) * ry * d
            // Height in the crown decides the light: 0 at the foot, 1 at the top.
            val lit = ((cy + ry - by) / (2 * ry)).coerceIn(0f, 1f)
            val tone = tones[r.nextInt(tones.size)]
            paint.color = dark.lerp(tone, 0.55f + lit * 0.45f).lerp(light, max(0f, lit - 0.55f) * 0.9f).value
            c.drawCircle(bx, by, min(rx, ry) * (0.2f + r.nextFloat() * 0.16f), paint)
        }
        repeat(10) {
            val a = r.nextFloat() * 6.28f
            val d = 0.85f + r.nextFloat() * 0.25f
            paint.color = tones[r.nextInt(tones.size)].lerp(Argb.White, 0.1f).value
            c.drawCircle(cx + cos(a) * rx * d, cy + sin(a) * ry * d, max(0.5f, min(rx, ry) * 0.07f), paint)
        }
    }

    private fun oak(c: Canvas, x: Float, y: Float, th: Float, r: Random) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Argb.hex(0x4A3A2C).value
        paint.strokeWidth = max(1.5f, th * 0.07f)
        c.drawLine(x, y, x, y - th * 0.5f, paint)
        paint.strokeWidth = max(1f, th * 0.035f)
        c.drawLine(x, y - th * 0.4f, x + th * 0.25f, y - th * 0.62f, paint)
        c.drawLine(x, y - th * 0.45f, x - th * 0.22f, y - th * 0.66f, paint)
        paint.style = Paint.Style.FILL
        // Its shadow on the grass.
        paint.maskFilter = BlurMaskFilter(th * 0.05f, BlurMaskFilter.Blur.NORMAL)
        paint.color = Argb.hex(0x2F5A22).withAlpha(0.45f).value
        c.drawOval(RectF(x - th * 0.5f, y - th * 0.02f, x + th * 0.3f, y + th * 0.06f), paint)
        paint.maskFilter = null
        foliage(c, x, y - th * 0.7f, th * 0.5f, th * 0.36f, listOf(Argb.hex(0x5C9A45), Argb.hex(0x75B254), Argb.hex(0x4A8338), Argb.hex(0x8DC565)), r)
    }

    private fun poplars(
        c: Canvas, w: Float, h: Float, baseline: Float, count: Int, height: Float, color: Argb, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val tone = if (haze != null) color.lerp(haze, hazeAmount) else color
        paint.color = tone.value
        repeat(count) {
            val x = w * (it + r.nextFloat() * 0.8f) / count
            val th = h * height * (0.7f + r.nextFloat() * 0.5f)
            val y = h * baseline + r.nextFloat() * h * 0.015f
            val tw = th * 0.16f
            c.drawOval(RectF(x - tw, y - th, x + tw, y - th * 0.05f), paint)
            c.drawRect(x - tw * 0.08f, y - th * 0.1f, x + tw * 0.08f, y, paint)
        }
    }

    private fun bareTrees(
        c: Canvas, w: Float, h: Float, baseline: Float, count: Int, height: Float, color: Argb, r: Random,
        haze: Argb? = null, hazeAmount: Float = 0f,
    ) {
        val tone = if (haze != null) color.lerp(haze, hazeAmount) else color
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = tone.value
        repeat(count) {
            val x = w * (it + r.nextFloat() * 0.8f) / count
            val th = h * height * (0.7f + r.nextFloat() * 0.45f)
            val y = h * baseline + r.nextFloat() * h * 0.02f
            branch(c, x, y, -90f + (r.nextFloat() - 0.5f) * 8f, th * 0.42f, max(0.6f, th * 0.045f), 0, r)
        }
        paint.style = Paint.Style.FILL
    }

    private fun branch(c: Canvas, x: Float, y: Float, angle: Float, length: Float, width: Float, depth: Int, r: Random) {
        val rad = Math.toRadians(angle.toDouble())
        val x1 = x + (cos(rad) * length).toFloat()
        val y1 = y + (sin(rad) * length).toFloat()
        paint.strokeWidth = width
        c.drawLine(x, y, x1, y1, paint)
        if (depth >= 4 || length < 2f) return
        val spread = 22f + r.nextFloat() * 14f
        branch(c, x1, y1, angle - spread, length * (0.62f + r.nextFloat() * 0.12f), width * 0.62f, depth + 1, r)
        branch(c, x1, y1, angle + spread, length * (0.62f + r.nextFloat() * 0.12f), width * 0.62f, depth + 1, r)
        if (depth < 2 && r.nextFloat() < 0.5f) branch(c, x1, y1, angle + (r.nextFloat() - 0.5f) * 12f, length * 0.55f, width * 0.55f, depth + 1, r)
    }

    // endregion

    // region In the air and on the ground

    private fun snowfall(c: Canvas, w: Float, h: Float, count: Int, size: Float, r: Random) {
        if (moving) return
        val n = (count * (w * h) / (320f * 250f)).toInt().coerceIn(count / 3, count * 3)
        repeat(n) {
            val depth = r.nextFloat()
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val rr = (0.4f + depth * depth * 1.6f) * size
            paint.maskFilter = if (depth > 0.8f) BlurMaskFilter(rr * 0.6f, BlurMaskFilter.Blur.NORMAL) else null
            paint.color = Argb.White.withAlpha(0.45f + depth * 0.45f).value
            c.drawCircle(x, y, rr, paint)
        }
        paint.maskFilter = null
    }

    private fun rain(c: Canvas, w: Float, h: Float, count: Int, r: Random) {
        if (moving) return
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        repeat(count) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h * 0.8f
            val len = h * (0.03f + r.nextFloat() * 0.04f)
            paint.strokeWidth = 0.5f + r.nextFloat() * 0.4f
            paint.shader = LinearGradient(x, y, x + len * 0.15f, y + len, Argb.White.withAlpha(0f).value, Argb.White.withAlpha(0.45f).value, Shader.TileMode.CLAMP)
            c.drawLine(x, y, x + len * 0.15f, y + len, paint)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun flowers(c: Canvas, w: Float, h: Float, top: Float, count: Int, colors: List<Argb>, r: Random) {
        val n = (count * w / 320f).toInt().coerceIn(count / 3, count * 2)
        repeat(n) {
            val depth = r.nextFloat()
            val x = r.nextFloat() * w
            val y = h * (top + 0.02f + depth * (0.98f - top))
            val rr = 0.5f + depth * 1.6f
            val color = colors[r.nextInt(colors.size)]
            paint.color = color.withAlpha(0.7f + depth * 0.3f).value
            c.drawCircle(x, y, rr, paint)
            if (rr > 1.2f) {
                paint.color = Argb.hex(0xFFD23A).withAlpha(0.9f).value
                c.drawCircle(x, y, rr * 0.35f, paint)
            }
        }
    }

    private fun litter(c: Canvas, w: Float, h: Float, top: Float, count: Int, colors: List<Argb>, r: Random) {
        val n = (count * w / 320f).toInt().coerceIn(count / 3, count * 2)
        repeat(n) {
            val depth = r.nextFloat()
            val x = r.nextFloat() * w
            val y = h * (top + 0.02f + depth * (0.98f - top))
            val size = 0.8f + depth * 2.2f
            leaf(c, x, y, size, r.nextFloat() * 360f, colors[r.nextInt(colors.size)].withAlpha(0.6f + depth * 0.4f))
        }
    }

    private fun leaves(c: Canvas, w: Float, h: Float, count: Int, colors: List<Argb>, r: Random) {
        if (moving) return
        val n = (count * (w * h) / (320f * 250f)).toInt().coerceIn(count / 2, count * 3)
        repeat(n) {
            val depth = r.nextFloat()
            val size = 1.5f + depth * 3.2f
            leaf(c, r.nextFloat() * w, r.nextFloat() * h, size, r.nextFloat() * 360f, colors[r.nextInt(colors.size)].withAlpha(0.75f + depth * 0.25f))
        }
    }

    private fun leaf(c: Canvas, x: Float, y: Float, size: Float, angle: Float, color: Argb) {
        c.withRotation(angle, x, y) {
            path.reset()
            path.moveTo(x - size, y)
            path.quadTo(x - size * 0.2f, y - size * 0.62f, x + size, y)
            path.quadTo(x - size * 0.2f, y + size * 0.62f, x - size, y)
            path.close()
            paint.color = color.value
            drawPath(path, paint)
            paint.color = color.lerp(Argb.Black, 0.3f).withAlpha(0.5f).value
            drawRect(x - size, y - 0.15f, x + size * 0.8f, y + 0.15f, paint)
        }
    }

    private fun petals(c: Canvas, w: Float, h: Float, count: Int, r: Random) {
        if (moving) return
        val colors = listOf(Argb.hex(0xFBD3DF), Argb.hex(0xF7B8CB), Argb.hex(0xFFFFFF))
        val n = (count * (w * h) / (320f * 250f)).toInt().coerceIn(count / 2, count * 3)
        repeat(n) {
            val depth = r.nextFloat()
            val size = 1f + depth * 2.2f
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            // Drawn in the same order as before: the angle first, then the colour.
            val angle = r.nextFloat() * 360f
            paint.color = colors[r.nextInt(colors.size)].withAlpha(0.7f + depth * 0.3f).value
            c.withRotation(angle, x, y) {
                drawOval(RectF(x - size, y - size * 0.6f, x + size, y + size * 0.6f), paint)
            }
        }
    }

    private fun fireflies(c: Canvas, w: Float, h: Float, top: Float, count: Int, color: Argb, r: Random) {
        if (moving) return
        val n = (count * w / 320f).toInt().coerceIn(count / 2, count * 2)
        repeat(n) {
            val x = r.nextFloat() * w
            val y = h * (top + r.nextFloat() * (0.97f - top))
            val rr = 0.7f + r.nextFloat() * 0.9f
            paint.shader = RadialGradient(x, y, rr * 5f, intArrayOf(color.withAlpha(0.95f).value, color.withAlpha(0.3f).value, 0), floatArrayOf(0f, 0.22f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, rr * 5f, paint)
        }
        paint.shader = null
    }

    // endregion
}
