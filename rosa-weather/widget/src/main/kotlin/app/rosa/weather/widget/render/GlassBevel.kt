package app.rosa.weather.widget.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import app.rosa.weather.core.model.Argb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The widget pane's glass edge, shaded pixel by pixel the way the app's glass shader shades its
 * bevel: the pane's rim curves down over [width] like the edge of a thick slab, and the scene's
 * [WidgetLight] falls on that curve. What that gives, all the way round and never in patches:
 *
 *  - a razor catch-light at the very edge, brightest where the rim faces the sun, moon or sky and
 *    never quite gone, so the pane always reads as one piece of glass;
 *  - a second, finer streak just inside it along the lit side: the signature of thick glass;
 *  - Fresnel: the edge reflects the sky, so it glows faintly at the rim, more along the top;
 *  - thickness: the far side of the bevel falls into a soft shadow;
 *  - a whisper of dispersion inside the lit rim.
 *
 * A launcher gives a widget no shader to run, so this is computed once per update into a bitmap
 * at the canvas's own resolution, and only for the band along the edge.
 */
internal object GlassBevel {
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, rect: RectF, radius: Float, light: WidgetLight, dark: Boolean, strength: Float) {
        @Suppress("DEPRECATION")
        val scale = canvas.matrix.mapRadius(1f).coerceIn(1f, 4f)
        val w = (rect.width() * scale).roundToInt()
        val h = (rect.height() * scale).roundToInt()
        if (w < 8 || h < 8) return
        val bitmap = createBitmap(w, h)
        // Only the band along the edge is shaded: along the top and bottom, then down the sides.
        val band = min((width(w / scale / 2f, h / scale / 2f) * scale).toInt() + 2, min(w, h) / 2)
        for ((x0, y0, sw, sh) in listOf(
            intArrayOf(0, 0, w, band),
            intArrayOf(0, h - band, w, band),
            intArrayOf(0, band, band, h - 2 * band),
            intArrayOf(w - band, band, band, h - 2 * band),
        )) {
            if (sw <= 0 || sh <= 0) continue
            val pixels = IntArray(sw * sh)
            shade(pixels, x0, y0, sw, sh, w, h, scale, radius, light, dark, strength)
            bitmap.setPixels(pixels, 0, sw, x0, y0, sw, sh)
        }
        canvas.drawBitmap(bitmap, null, rect, filter)
        bitmap.recycle()
    }

    /** Width of the curved edge, dp: a thick slab's, a little less on the smallest widgets. */
    private fun width(hx: Float, hy: Float) = min(9f, min(hx, hy) * 0.14f).coerceAtLeast(4f)

    /** Shades the [sw] × [sh] pixels at ([x0], [y0]) of a [w] × [h] pane into [out]. */
    private fun shade(
        out: IntArray, x0: Int, y0: Int, sw: Int, sh: Int, w: Int, h: Int, s: Float,
        radius: Float, light: WidgetLight, dark: Boolean, strength: Float,
    ) {
        val hx = w / s / 2f
        val hy = h / s / 2f
        val r = radius.coerceIn(0f, min(hx, hy))
        val bevel = width(hx, hy)

        val share = light.share
        val sx = cos(light.angle)
        val sy = sin(light.angle)
        val rx = cos(WidgetLight.RESTING_ANGLE)
        val ry = sin(WidgetLight.RESTING_ANGLE)
        val sun = Argb.White.lerp(light.color, share)
        val cyan = Argb.hex(0x8FEAFF)
        val pink = Argb.hex(0xFFB3DE)
        // Light glass shows its highlights less and its depth more; smoked glass the other way round.
        val lift = strength * (0.85f + 0.35f * light.power) * (if (dark) 1f else 0.8f)
        val depth = strength * (if (dark) 0.8f else 1.15f)

        for (j in y0 until y0 + sh) {
            for (i in x0 until x0 + sw) {
                val px = (i + 0.5f) / s - hx
                val py = (j + 0.5f) / s - hy
                val qx = abs(px) - (hx - r)
                val qy = abs(py) - (hy - r)
                val sdf = hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - r
                val inside = -sdf
                if (sdf * s > 0.5f || inside > bevel) continue
                // The outward normal of the outline here.
                val nx: Float
                val ny: Float
                if (qx > 0f && qy > 0f) {
                    val l = hypot(qx, qy)
                    nx = qx / l * sign(px)
                    ny = qy / l * sign(py)
                } else if (qx > qy) {
                    nx = sign(px)
                    ny = 0f
                } else {
                    nx = 0f
                    ny = sign(py)
                }
                val cover = (0.5f - sdf * s).coerceIn(0f, 1f)
                // x runs 1 at the rim to 0 where the curve meets the flat face; hz is its height there.
                val x = 1f - inside / bevel
                val hz = sqrt(max(0f, 1f - x * x))
                val depthPx = inside * s

                val fs = nx * sx + ny * sy
                val fr = nx * rx + ny * ry
                val lobe = share * pos(fs).cube() + (1f - share) * pos(fr).cube()
                val along = share * pos(fs).pow4() + (1f - share) * pos(fr).pow4()
                val tight = share * pos(fs).pow8() + (1f - share) * pos(fr).pow8()
                val away = share * pos(-fs).cube() + (1f - share) * pos(-fr).cube()
                val env = 0.5f - 0.5f * ny

                // Highlights: white where the sky gives them, the light's own colour where it does.
                var lit = 0f
                var white = 0f
                val rim = gauss(depthPx - 0.7f, 0.75f)
                white += rim * (0.14f + 0.24f * env + 0.2f * away)
                lit += rim * (0.45f * along + 0.45f * tight)
                lit += gauss(x - 0.8f, 0.055f) * 0.36f * along
                lit += gauss(x - 0.8f, 0.2f) * 0.07f * lobe
                white += (1f - hz).cube() * (0.08f + 0.2f * env) * (0.7f + 0.3f * lobe)
                val fringeCyan = gauss(depthPx - 1.9f, 0.7f) * 0.12f * tight
                val fringePink = gauss(depthPx - 2.8f, 0.7f) * 0.08f * tight

                // Depth: the far side of the curve in shadow, and a little the lower edge.
                val shadowBand = smooth(0.12f, 0.7f, x) * (1f - smooth(0.86f, 1f, x))
                val shade = shadowBand * (0.2f * away + 0.06f * (1f - env)) * depth

                val a1 = (white + lit + fringeCyan + fringePink) * lift
                var cr = (white + lit * sun.red / 255f + fringeCyan * cyan.red / 255f + fringePink * pink.red / 255f) * lift
                var cg = (white + lit * sun.green / 255f + fringeCyan * cyan.green / 255f + fringePink * pink.green / 255f) * lift
                var cb = (white + lit * sun.blue / 255f + fringeCyan * cyan.blue / 255f + fringePink * pink.blue / 255f) * lift
                var alpha = a1.coerceAtMost(1f)
                if (a1 > 1f) {
                    cr /= a1
                    cg /= a1
                    cb /= a1
                }
                // Highlights over the shadow.
                val d = shade.coerceIn(0f, 1f)
                alpha += d * (1f - alpha)
                alpha *= cover
                if (alpha > 0.002f) {
                    val k = cover / alpha
                    out[(j - y0) * sw + (i - x0)] = (to255(alpha) shl 24) or (to255(cr * k) shl 16) or (to255(cg * k) shl 8) or to255(cb * k)
                }
            }
        }
    }

    private fun sign(v: Float) = if (v < 0f) -1f else 1f

    private fun pos(v: Float) = if (v > 0f) v else 0f

    private fun Float.cube() = this * this * this

    private fun Float.pow4(): Float {
        val q = this * this
        return q * q
    }

    private fun Float.pow8(): Float {
        val q = pow4()
        return q * q
    }

    private fun gauss(v: Float, width: Float): Float {
        val z = v / width
        return exp(-z * z)
    }

    private fun smooth(from: Float, to: Float, v: Float): Float {
        val t = ((v - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun to255(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
