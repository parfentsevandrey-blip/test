package app.rosa.weather.widget.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.WeatherVisual
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The weather on a widget's own glass — the same scene the app's window shows, from the same
 * moment of the same forecast, so when it rains in the app it rains on the widget. This is the
 * picture of that scene: rain streaks and snow behind the glass, beads of water on it (each a
 * tiny lens showing the sky upside down, with a dark rim, a caustic at its foot and a glint),
 * drops sliding down with wet trails, frost growing in from the frame, mist on a humid day. All of
 * it sits under the widget's content, which stays legible. What moves — falling rain and snow,
 * running drops, lightning — the launcher animates over the picture when it can ([live]).
 *
 * Geometry is in dp; the canvas is pre-scaled. Seeded per widget, so a widget keeps its drops
 * between renders instead of reshuffling them.
 */
internal class WidgetWeather {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    /**
     * The launcher animates falling rain and snow and running drops over this picture (see
     * LiveWeather): the picture then keeps only what stays put.
     */
    var live = false

    /**
     * What is behind the glass: falling rain in three depths, snow from specks to soft flakes.
     * [strength] scales it for how much of the scene a style shows.
     */
    fun behind(canvas: Canvas, rect: RectF, visual: WeatherVisual, light: Argb, strength: Float, seed: Int) {
        if (live) return
        if (visual.rain > 0.05f) streaks(canvas, rect, visual, light, strength, seed)
        if (visual.snow > 0.05f || visual.hail > 0.05f) snow(canvas, rect, visual, strength, seed)
    }

    /**
     * What is on the glass: mist, beads and sliding drops, frost from the frame. [top] and
     * [bottom] are the colours of the scene behind at the top and the foot of the widget — a bead
     * shows them the other way round. [refract] is false where nothing known lies behind (a
     * clear widget over the wallpaper): beads are then only rim and light.
     */
    fun onGlass(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        visual: WeatherVisual,
        frost: Float,
        mist: Float,
        top: Argb,
        bottom: Argb,
        dark: Boolean,
        refract: Boolean,
        seed: Int,
    ) {
        if (mist > 0.05f) mist(canvas, rect, mist, dark, seed)
        if (visual.rain > 0.05f) beads(canvas, rect, visual.rain, top, bottom, dark, refract, seed)
        if (frost > 0.02f) frost(canvas, rect, radius, frost, seed)
    }

    // region Behind the glass

    private fun streaks(canvas: Canvas, rect: RectF, visual: WeatherVisual, light: Argb, strength: Float, seed: Int) {
        val rnd = Random(seed * 17 + 3)
        val w = rect.width()
        val h = rect.height()
        val area = w * h
        // The same slant as the app's rain: falling down and to the right as the wind picks up.
        val slant = 0.12f + visual.wind * 0.32f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFFFFFFFF.toInt()
        for (layer in 0..2) {
            val depth = layer / 2f
            val count = (area / lerp(150f, 900f, depth) * visual.rain).toInt().coerceIn(3, 260)
            paint.strokeWidth = lerp(0.45f, 1.3f, depth)
            // Close to the glass the streaks are out of focus.
            paint.maskFilter = if (depth > 0.9f) BlurMaskFilter(0.7f, BlurMaskFilter.Blur.NORMAL) else null
            val alpha = strength * lerp(0.32f, 0.5f, depth)
            repeat(count) {
                val x = rnd.nextFloat() * (w + h * slant) - h * slant
                val y = rnd.nextFloat() * h
                val len = lerp(5f, 20f, depth) * (0.6f + rnd.nextFloat() * 0.8f) * (0.7f + visual.rain * 0.5f)
                val a = alpha * (0.45f + rnd.nextFloat() * 0.55f)
                // Motion blur of a falling drop: faint where it was, bright where it is.
                paint.shader = LinearGradient(
                    x, y, x + len * slant, y + len,
                    light.withAlpha(0f).value, light.withAlpha(a).value, Shader.TileMode.CLAMP,
                )
                canvas.drawLine(x, y, x + len * slant, y + len, paint)
            }
        }
        paint.maskFilter = null
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    private fun snow(canvas: Canvas, rect: RectF, visual: WeatherVisual, strength: Float, seed: Int) {
        val rnd = Random(seed * 23 + 5)
        val w = rect.width()
        val h = rect.height()
        val amount = max(visual.snow, visual.hail)
        val hail = visual.hail > visual.snow
        // Far: many small, sharp specks.
        paint.shader = null
        repeat((w * h / 260f * amount).toInt().coerceIn(6, 200)) {
            val r = if (hail) 0.7f + rnd.nextFloat() * 0.6f else 0.45f + rnd.nextFloat() * 0.9f
            paint.color = Argb.White.withAlpha(strength * (0.45f + rnd.nextFloat() * 0.5f)).value
            canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, r, paint)
        }
        if (hail) return
        // Near: a few large flakes, soft with depth of field, shaded underneath like a clump.
        repeat((w * h / 2600f * amount).toInt().coerceIn(1, 18)) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = 2.2f + rnd.nextFloat() * 2.8f
            val a = strength * (0.55f + rnd.nextFloat() * 0.35f)
            paint.shader = RadialGradient(
                x, y - r * 0.15f, r,
                intArrayOf(Argb.White.withAlpha(a).value, Argb.White.withAlpha(a * 0.8f).value, 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, r, paint)
            paint.shader = RadialGradient(x, y + r * 0.5f, r * 0.7f, 0x228A93A6, 0x008A93A6, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, paint)
        }
        paint.shader = null
    }

    // endregion

    // region On the glass

    private fun mist(canvas: Canvas, rect: RectF, mist: Float, dark: Boolean, seed: Int) {
        paint.shader = null
        paint.color = (if (dark) Argb.hex(0xC9D2E3) else Argb.White).withAlpha(mist * if (dark) 0.1f else 0.16f).value
        canvas.drawRect(rect, paint)
        // Condensation is a mist of micro-droplets, each catching a speck of light.
        val rnd = Random(seed * 41 + 9)
        repeat((rect.width() * rect.height() / 60f * mist).toInt().coerceAtMost(500)) {
            paint.color = Argb.White.withAlpha(mist * (0.12f + rnd.nextFloat() * 0.18f)).value
            canvas.drawCircle(rnd.nextFloat() * rect.width(), rnd.nextFloat() * rect.height(), 0.25f + rnd.nextFloat() * 0.35f, paint)
        }
    }

    private fun beads(canvas: Canvas, rect: RectF, rain: Float, top: Argb, bottom: Argb, dark: Boolean, refract: Boolean, seed: Int) {
        val rnd = Random(seed * 31 + 7)
        val w = rect.width()
        val h = rect.height()
        val area = w * h
        repeat((area / 190f * (0.25f + rain)).toInt().coerceIn(6, 80)) {
            // Many tiny beads, a few big ones.
            val u = rnd.nextFloat()
            val r = 0.6f + u * u * u * 5.2f * (0.6f + rain * 0.6f)
            bead(canvas, rnd.nextFloat() * w, rnd.nextFloat() * h, r, 1f, top, bottom, dark, refract)
        }
        // Heavier rain runs: a few drops slide down, leaving a wet trail of droplets.
        val sliders = if (live) 0 else (rain * area / 9000f).toInt().coerceIn(if (rain > 0.3f) 1 else 0, 6)
        repeat(sliders) {
            val x = rnd.nextFloat() * w
            val y = h * (0.35f + rnd.nextFloat() * 0.6f)
            val r = 2.4f + rnd.nextFloat() * 1.8f
            val trail = h * (0.15f + rnd.nextFloat() * 0.3f)
            val phase = rnd.nextFloat() * 6.28f
            // The trail: glass wiped clearer, beaded with what the drop left behind.
            paint.shader = LinearGradient(
                x, y - trail, x, y,
                Argb.White.withAlpha(0f).value, Argb.White.withAlpha(if (dark) 0.07f else 0.12f).value, Shader.TileMode.CLAMP,
            )
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = r * 0.9f
            path.reset()
            path.moveTo(x + sin(phase) * r * 0.6f, y - trail)
            path.quadTo(x + sin(phase + 1.7f) * r, y - trail * 0.5f, x, y - r)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.shader = null
            var t = 0.12f
            while (t < 0.9f) {
                val dy = y - trail * (1f - t)
                val dx = x + sin(phase + t * 4f) * r * 0.35f
                bead(canvas, dx, dy, 0.45f + rnd.nextFloat() * 0.6f, 1f, top, bottom, dark, refract)
                t += 0.1f + rnd.nextFloat() * 0.12f
            }
            // The drop itself: heavy at the foot, drawn out above.
            bead(canvas, x, y, r, 1.35f, top, bottom, dark, refract)
        }
    }

    /**
     * A bead of water, a tiny lens: the scene behind it upside down (the light of the horizon at
     * its top, the depth of the sky at its foot), a rim that darkens toward the foot, the sky
     * reflected along its upper edge, light gathered inside the foot, and a glint.
     */
    private fun bead(canvas: Canvas, cx: Float, cy: Float, r: Float, stretch: Float, top: Argb, bottom: Argb, dark: Boolean, refract: Boolean) {
        val oval = RectF(cx - r, cy - r * (2f * stretch - 1f), cx + r, cy + r)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        if (refract) {
            paint.shader = LinearGradient(
                0f, oval.top, 0f, oval.bottom,
                bottom.lerp(Argb.White, 0.18f).value, top.lerp(Argb.White, 0.06f).value, Shader.TileMode.CLAMP,
            )
            canvas.drawOval(oval, paint)
        }
        val rim = if (dark) 0x78020612 else 0x5A1B2433
        paint.shader = RadialGradient(
            cx, cy - r * 0.2f, r * 1.12f,
            intArrayOf(0, 0, rim), floatArrayOf(0f, if (refract) 0.62f else 0.72f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawOval(oval, paint)
        if (r < 0.9f) {
            paint.shader = null
            return
        }
        paint.shader = RadialGradient(cx, cy + r * 0.55f, r * 0.5f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawOval(oval, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(r * 0.12f, 0.3f)
        paint.color = Argb.White.withAlpha(if (dark) 0.28f else 0.42f).value
        val inset = r * 0.14f
        canvas.drawArc(RectF(oval.left + inset, oval.top + inset, oval.right - inset, oval.bottom - inset), 205f, 130f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Argb.White.withAlpha(0.92f).value
        canvas.drawOval(RectF(cx - r * 0.55f, cy - r * 0.58f - (oval.height() - 2 * r) * 0.3f, cx - r * 0.12f, cy - r * 0.3f - (oval.height() - 2 * r) * 0.3f), paint)
    }

    /** Frost from the frame: a white bloom along the edge and fine crystals branching inward. */
    private fun frost(canvas: Canvas, rect: RectF, radius: Float, frost: Float, seed: Int) {
        val band = min(rect.width(), rect.height()) * (0.05f + 0.07f * frost)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = band * 1.3f
        paint.color = Argb.White.withAlpha(0.3f * frost).value
        paint.maskFilter = BlurMaskFilter(band * 0.55f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.maskFilter = null

        val rnd = Random(seed * 53 + 13)
        paint.strokeCap = Paint.Cap.ROUND
        val perimeter = 2 * (rect.width() + rect.height())
        repeat((perimeter / 7f * frost).toInt().coerceIn(6, 160)) {
            // A point on the frame and the way inward from it.
            var d = rnd.nextFloat() * perimeter
            val (x0, y0, inward) = when {
                d < rect.width() -> Triple(d, 0f, 90f)
                d < rect.width() + rect.height() -> { d -= rect.width(); Triple(rect.width(), d, 180f) }
                d < 2 * rect.width() + rect.height() -> { d -= rect.width() + rect.height(); Triple(rect.width() - d, rect.height(), 270f) }
                else -> { d -= 2 * rect.width() + rect.height(); Triple(0f, rect.height() - d, 0f) }
            }
            crystal(canvas, x0, y0, inward + (rnd.nextFloat() - 0.5f) * 70f, band * (0.5f + rnd.nextFloat() * 0.9f), frost, rnd, depth = 0)
        }
        paint.style = Paint.Style.FILL
        // The odd glint of an ice facet.
        repeat((perimeter / 30f * frost).toInt().coerceAtMost(40)) {
            val edge = rnd.nextInt(4)
            val t = rnd.nextFloat()
            val inset = rnd.nextFloat() * band
            val x = when (edge) { 0, 2 -> t * rect.width(); 1 -> rect.width() - inset; else -> inset }
            val y = when (edge) { 1, 3 -> t * rect.height(); 0 -> inset; else -> rect.height() - inset }
            paint.color = Argb.White.withAlpha(0.5f + rnd.nextFloat() * 0.4f).value
            canvas.drawCircle(x, y, 0.35f + rnd.nextFloat() * 0.4f, paint)
        }
    }

    private fun crystal(canvas: Canvas, x: Float, y: Float, angle: Float, length: Float, frost: Float, rnd: Random, depth: Int) {
        val rad = Math.toRadians(angle.toDouble())
        val x1 = x + (cos(rad) * length).toFloat()
        val y1 = y + (sin(rad) * length).toFloat()
        paint.strokeWidth = if (depth == 0) 0.55f else 0.35f
        paint.color = Argb.White.withAlpha(frost * (if (depth == 0) 0.5f else 0.35f)).value
        canvas.drawLine(x, y, x1, y1, paint)
        if (depth < 2) {
            // Feathers: short branches off both sides, like frost on a window.
            val branches = 2 + rnd.nextInt(3)
            for (i in 1..branches) {
                val t = i / (branches + 1f)
                val bx = x + (x1 - x) * t
                val by = y + (y1 - y) * t
                val side = if (i % 2 == 0) 1f else -1f
                crystal(canvas, bx, by, angle + side * (35f + rnd.nextFloat() * 25f), length * (0.25f + rnd.nextFloat() * 0.2f), frost, rnd, depth + 1)
            }
        }
    }

    // endregion

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
