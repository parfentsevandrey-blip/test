package app.rosa.weather.core.designsystem.glyph

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withTranslation
import app.rosa.weather.core.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hand-built, resolution-independent weather iconography drawn straight onto an
 * [android.graphics.Canvas]. The exact same glyphs render in Compose (via `nativeCanvas`) and in
 * widget bitmaps, so the app and the home screen share one visual vocabulary.
 *
 * Two tones, mirroring iOS 26 widget rendering modes:
 *  - [Tone.Color]: soft, luminous, gently 3D (gradients + contact shadows);
 *  - [Tone.Mono]: a single ink with knocked-out gaps between overlapping shapes, for the clear,
 *    tinted and paper widget styles.
 *
 * Not thread-safe: keep one instance per render thread / composable.
 */
class WeatherGlyphPainter {
    enum class Tone { Color, Mono }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val path = Path()
    private val tmp = Path()

    private var left = 0f
    private var top = 0f
    private var s = 1f
    private var tone = Tone.Color
    private var ink = Color.WHITE
    private var onLight = false

    /**
     * @param time seconds, animates rays, drops and flakes when > 0 (pass 0 for static renders).
     * @param moonPhase 0..1 (0 new, 0.5 full) used for night glyphs.
     */
    fun draw(
        canvas: Canvas,
        condition: WeatherCondition,
        isDay: Boolean,
        bounds: RectF,
        tone: Tone = Tone.Color,
        monoColor: Int = Color.WHITE,
        moonPhase: Double = 0.3,
        time: Float = 0f,
        onLightBackground: Boolean = false,
    ) {
        s = min(bounds.width(), bounds.height())
        if (s <= 1f) return
        left = bounds.centerX() - s / 2
        top = bounds.centerY() - s / 2
        this.tone = tone
        ink = monoColor
        onLight = onLightBackground

        val layer = if (tone == Tone.Mono) canvas.saveLayer(bounds, null) else canvas.save()
        when (condition) {
            WeatherCondition.Clear -> if (isDay) sun(canvas, 0.5f, 0.5f, 0.2f, time) else moon(canvas, 0.5f, 0.5f, 0.25f, moonPhase, stars = true)
            WeatherCondition.MostlyClear -> {
                celestial(canvas, isDay, 0.43f, 0.41f, 0.19f, moonPhase, time)
                cloud(canvas, 0.63f, 0.64f, 0.44f, CloudShade.Light)
            }
            WeatherCondition.PartlyCloudy -> {
                celestial(canvas, isDay, 0.37f, 0.35f, 0.17f, moonPhase, time)
                cloud(canvas, 0.55f, 0.57f, 0.62f, CloudShade.Light)
            }
            WeatherCondition.Overcast -> {
                cloud(canvas, 0.63f, 0.40f, 0.5f, CloudShade.Back)
                cloud(canvas, 0.46f, 0.57f, 0.66f, CloudShade.Light)
            }
            WeatherCondition.Fog, WeatherCondition.RimeFog -> {
                cloud(canvas, 0.5f, 0.34f, 0.58f, CloudShade.Back)
                fog(canvas)
            }
            WeatherCondition.Drizzle -> precipitationCloud(canvas, CloudShade.Light) { drizzle(canvas, time) }
            WeatherCondition.FreezingDrizzle -> precipitationCloud(canvas, CloudShade.Light) {
                drizzle(canvas, time)
                flakes(canvas, count = 1, time = time, offset = 0.18f)
            }
            WeatherCondition.LightRain -> precipitationCloud(canvas, CloudShade.Light) { drops(canvas, 2, time) }
            WeatherCondition.Rain -> precipitationCloud(canvas, CloudShade.Mid) { drops(canvas, 3, time) }
            WeatherCondition.HeavyRain -> precipitationCloud(canvas, CloudShade.Dark) { drops(canvas, 4, time) }
            WeatherCondition.FreezingRain -> precipitationCloud(canvas, CloudShade.Mid) {
                drops(canvas, 2, time)
                flakes(canvas, count = 1, time = time, offset = 0.12f)
            }
            WeatherCondition.LightSnow, WeatherCondition.SnowGrains -> precipitationCloud(canvas, CloudShade.Light) { flakes(canvas, 2, time) }
            WeatherCondition.Snow -> precipitationCloud(canvas, CloudShade.Light) { flakes(canvas, 3, time) }
            WeatherCondition.HeavySnow -> precipitationCloud(canvas, CloudShade.Mid) { flakes(canvas, 4, time) }
            WeatherCondition.RainShowers, WeatherCondition.HeavyShowers -> {
                celestial(canvas, isDay, 0.34f, 0.28f, 0.14f, moonPhase, time)
                precipitationCloud(canvas, if (condition == WeatherCondition.HeavyShowers) CloudShade.Mid else CloudShade.Light) {
                    drops(canvas, if (condition == WeatherCondition.HeavyShowers) 3 else 2, time)
                }
            }
            WeatherCondition.SnowShowers -> {
                celestial(canvas, isDay, 0.34f, 0.28f, 0.14f, moonPhase, time)
                precipitationCloud(canvas, CloudShade.Light) { flakes(canvas, 2, time) }
            }
            WeatherCondition.Thunderstorm -> precipitationCloud(canvas, CloudShade.Dark) {
                drops(canvas, 2, time, skipMiddle = true)
                bolt(canvas)
            }
            WeatherCondition.ThunderstormHail -> precipitationCloud(canvas, CloudShade.Dark) {
                hail(canvas, time)
                bolt(canvas)
            }
        }
        canvas.restoreToCount(layer)
    }

    private fun x(v: Float) = left + v * s
    private fun y(v: Float) = top + v * s
    private fun d(v: Float) = v * s

    private fun celestial(canvas: Canvas, isDay: Boolean, cx: Float, cy: Float, r: Float, phase: Double, time: Float) {
        if (isDay) sun(canvas, cx, cy, r, time) else moon(canvas, cx, cy, r * 1.15f, phase, stars = false)
    }

    private inline fun precipitationCloud(canvas: Canvas, shade: CloudShade, below: () -> Unit) {
        below()
        cloud(canvas, 0.5f, 0.40f, 0.68f, shade)
    }

    // region Sun & moon

    private fun sun(canvas: Canvas, cx: Float, cy: Float, r: Float, time: Float) {
        val px = x(cx)
        val py = y(cy)
        val pr = d(r)
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(px, py, pr * 2.3f, intArrayOf(0x66FFD36B, 0x22FFB347, 0x00FFB347), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, pr * 2.3f, fill)
        }
        // Rays: soft rounded capsules, slowly turning when animated.
        stroke.shader = null
        stroke.strokeWidth = pr * 0.24f
        stroke.color = if (tone == Tone.Color) 0xFFFFC857.toInt() else ink
        stroke.alpha = if (tone == Tone.Color) 230 else 220
        val rotation = time * 0.12f
        for (i in 0 until 8) {
            val a = rotation + i * (PI / 4).toFloat()
            val inner = pr * 1.42f
            val outer = pr * (if (i % 2 == 0) 1.82f else 1.7f)
            canvas.drawLine(px + cos(a) * inner, py + sin(a) * inner, px + cos(a) * outer, py + sin(a) * outer, stroke)
        }
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(
                px - pr * 0.35f, py - pr * 0.4f, pr * 1.5f,
                intArrayOf(0xFFFFF8D2.toInt(), 0xFFFFD25E.toInt(), 0xFFFF9F2E.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            fill.shader = null
            fill.color = ink
        }
        canvas.drawCircle(px, py, pr, fill)
        fill.shader = null
    }

    private fun moon(canvas: Canvas, cx: Float, cy: Float, r: Float, phase: Double, stars: Boolean) {
        val px = x(cx)
        val py = y(cy)
        val pr = d(r)
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(px, py, pr * 2f, intArrayOf(0x40C9CCFF, 0x00C9CCFF), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, pr * 2f, fill)
        }
        // Earthshine: the unlit disc stays faintly visible.
        fill.shader = null
        fill.color = if (tone == Tone.Color) 0x33D6DAFF else ink
        if (tone == Tone.Mono) fill.alpha = 60
        canvas.drawCircle(px, py, pr, fill)

        litMoonPath(px, py, pr, phase)
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(
                px - pr * 0.3f, py - pr * 0.35f, pr * 1.6f,
                intArrayOf(0xFFFFFDF2.toInt(), 0xFFE9E6FA.toInt(), 0xFFC4C1E6.toInt()),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            fill.color = ink
        }
        canvas.drawPath(path, fill)
        fill.shader = null

        if (stars) {
            sparkle(canvas, x(0.2f), y(0.24f), d(0.055f))
            sparkle(canvas, x(0.8f), y(0.72f), d(0.04f))
        }
    }

    /**
     * Builds the illuminated part of the moon: a half disc combined with the terminator ellipse
     * (subtracted for crescents, added for gibbous phases). Northern-hemisphere orientation.
     */
    private fun litMoonPath(px: Float, py: Float, pr: Float, phase: Double) {
        path.reset()
        // Keep a readable crescent: an astronomically exact new moon would be an invisible icon.
        val p = phase.coerceIn(0.12, 0.88)
        val waxing = p < 0.5
        val k = cos(2 * PI * p).toFloat() // 1 new … -1 full
        val halfRect = RectF(px - pr, py - pr, px + pr, py + pr)
        path.addArc(halfRect, if (waxing) -90f else 90f, 180f)
        path.close()
        val rx = abs(k) * pr
        tmp.reset()
        tmp.addOval(RectF(px - rx, py - pr, px + rx, py + pr), Path.Direction.CW)
        val crescent = k > 0
        path.op(tmp, if (crescent) Path.Op.DIFFERENCE else Path.Op.UNION)
    }

    private fun sparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r)
        path.quadTo(cx, cy, cx + r, cy)
        path.quadTo(cx, cy, cx, cy + r)
        path.quadTo(cx, cy, cx - r, cy)
        path.quadTo(cx, cy, cx, cy - r)
        path.close()
        fill.shader = null
        fill.color = if (tone == Tone.Color) 0xFFF4F1FF.toInt() else ink
        canvas.drawPath(path, fill)
    }

    // endregion

    // region Clouds

    private enum class CloudShade(val top: Int, val bottom: Int, val monoAlpha: Int) {
        Light(0xFFFFFFFF.toInt(), 0xFFD9E1EE.toInt(), 255),
        Back(0xFFD5DBE6.toInt(), 0xFFA9B3C4.toInt(), 150),
        Mid(0xFFE3E8F0.toInt(), 0xFFA8B2C3.toInt(), 255),
        Dark(0xFFA5AEC0.toInt(), 0xFF677084.toInt(), 255),
    }

    private fun cloudPath(cx: Float, cy: Float, w: Float) {
        path.reset()
        val base = RectF(x(cx - w / 2), y(cy), x(cx + w / 2), y(cy + w * 0.3f))
        path.addRoundRect(base, d(w * 0.15f), d(w * 0.15f), Path.Direction.CW)
        fun bump(bx: Float, by: Float, r: Float) {
            tmp.reset()
            tmp.addCircle(x(bx), y(by), d(r), Path.Direction.CW)
            path.op(tmp, Path.Op.UNION)
        }
        bump(cx - w * 0.23f, cy + w * 0.06f, w * 0.2f)
        bump(cx + w * 0.02f, cy - w * 0.03f, w * 0.28f)
        bump(cx + w * 0.28f, cy + w * 0.1f, w * 0.17f)
    }

    private fun cloud(canvas: Canvas, cx: Float, cy: Float, w: Float, shade: CloudShade) {
        cloudPath(cx, cy, w)
        if (tone == Tone.Mono) {
            // Knock out a gap so the cloud reads cleanly over the sun / drops beneath it.
            clear.style = Paint.Style.STROKE
            clear.strokeWidth = d(0.07f)
            canvas.drawPath(path, clear)
            fill.shader = null
            fill.color = ink
            fill.alpha = shade.monoAlpha
            canvas.drawPath(path, fill)
            return
        }
        // Soft contact shadow gives the glyph a gentle 3D presence.
        shadow.color = 0x38203050
        shadow.maskFilter = BlurMaskFilter(d(0.045f), BlurMaskFilter.Blur.NORMAL)
        canvas.withTranslation(0f, d(0.035f)) { drawPath(path, shadow) }
        fill.color = 0xFFFFFFFF.toInt()
        // On pale skies white clouds vanish: deepen the underside and add a hairline edge.
        val bottom = if (onLight) blend(shade.bottom, 0xFF7C879C.toInt(), 0.5f) else shade.bottom
        fill.shader = LinearGradient(0f, y(cy - w * 0.32f), 0f, y(cy + w * 0.3f), shade.top, bottom, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        if (onLight) {
            stroke.shader = null
            stroke.color = 0x332A3550
            stroke.strokeWidth = d(0.012f)
            canvas.drawPath(path, stroke)
        }
        // Rim light along the top edge: a hint of the glass language.
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.shader = LinearGradient(0f, y(cy - w * 0.32f), 0f, y(cy + w * 0.05f), 0xB0FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        stroke.strokeWidth = d(0.012f)
        stroke.alpha = 255
        canvas.drawPath(path, stroke)
        stroke.shader = null
        fill.shader = null
    }

    // endregion

    // region Precipitation

    private fun drops(canvas: Canvas, count: Int, time: Float, skipMiddle: Boolean = false) {
        val xs = when (count) {
            1 -> floatArrayOf(0.5f)
            2 -> floatArrayOf(0.38f, 0.6f)
            3 -> floatArrayOf(0.3f, 0.5f, 0.7f)
            else -> floatArrayOf(0.24f, 0.41f, 0.58f, 0.75f)
        }
        xs.forEachIndexed { i, bx ->
            if (skipMiddle && xs.size > 2 && i == xs.size / 2) return@forEachIndexed
            val phase = if (time > 0f) ((time * 1.4f + i * 0.37f) % 1f) else (i % 2) * 0.35f
            val by = 0.73f + phase * 0.12f
            val alpha = if (time > 0f) (1f - phase).coerceIn(0f, 1f) else 1f
            drop(canvas, bx - (by - 0.73f) * 0.25f, by, 0.075f, alpha)
        }
    }

    private fun drop(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Float) {
        val px = x(cx)
        val py = y(cy)
        val r = d(size) * 0.55f
        path.reset()
        path.moveTo(px + r * 0.55f, py - r * 2.1f)
        path.cubicTo(px + r * 0.9f, py - r * 0.9f, px + r * 1.1f, py - r * 0.2f, px + r, py + r * 0.25f)
        path.cubicTo(px + r * 0.9f, py + r * 1.2f, px - r * 0.9f, py + r * 1.25f, px - r, py + r * 0.2f)
        path.cubicTo(px - r * 1.0f, py - r * 0.4f, px - r * 0.2f, py - r * 1.2f, px + r * 0.55f, py - r * 2.1f)
        path.close()
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = LinearGradient(px, py - r * 2f, px, py + r, 0xFFA8E2FF.toInt(), 0xFF3F8CF5.toInt(), Shader.TileMode.CLAMP)
            fill.alpha = (alpha * 255).toInt()
        } else {
            fill.shader = null
            fill.color = ink
            fill.alpha = (alpha * 230).toInt()
        }
        canvas.drawPath(path, fill)
        fill.shader = null
        fill.alpha = 255
    }

    private fun drizzle(canvas: Canvas, time: Float) {
        val points = arrayOf(0.34f to 0.76f, 0.5f to 0.84f, 0.66f to 0.76f, 0.42f to 0.92f, 0.58f to 0.92f)
        fill.shader = null
        points.forEachIndexed { i, (px, py) ->
            val dy = if (time > 0f) ((time * 0.9f + i * 0.21f) % 1f) * 0.05f else 0f
            fill.color = if (tone == Tone.Color) 0xFF8CCBFF.toInt() else ink
            canvas.drawCircle(x(px), y(py + dy), d(0.028f), fill)
        }
    }

    private fun flakes(canvas: Canvas, count: Int, time: Float, offset: Float = 0f) {
        val xs = when (count) {
            1 -> floatArrayOf(0.5f + offset)
            2 -> floatArrayOf(0.38f, 0.62f)
            3 -> floatArrayOf(0.3f, 0.5f, 0.7f)
            else -> floatArrayOf(0.25f, 0.42f, 0.58f, 0.75f)
        }
        stroke.shader = null
        stroke.color = if (tone == Tone.Color) (if (onLight) 0xFF7FA9E8.toInt() else 0xFFF4F9FF.toInt()) else ink
        stroke.alpha = 255
        xs.forEachIndexed { i, fx ->
            val sway = if (time > 0f) sin(time * 1.3f + i) * 0.02f else 0f
            val fy = 0.78f + (i % 2) * 0.1f + if (time > 0f) ((time * 0.25f + i * 0.3f) % 1f) * 0.05f else 0f
            flake(canvas, fx + sway, fy, 0.06f, time * 0.4f + i)
        }
    }

    private fun flake(canvas: Canvas, cx: Float, cy: Float, r: Float, rotation: Float) {
        val px = x(cx)
        val py = y(cy)
        val pr = d(r)
        stroke.strokeWidth = pr * 0.32f
        if (tone == Tone.Color) {
            shadow.color = 0x5580B8FF
            shadow.maskFilter = BlurMaskFilter(pr * 0.6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(px, py, pr, shadow)
        }
        for (i in 0 until 3) {
            val a = rotation + i * (PI / 3).toFloat()
            canvas.drawLine(px - cos(a) * pr, py - sin(a) * pr, px + cos(a) * pr, py + sin(a) * pr, stroke)
        }
    }

    private fun hail(canvas: Canvas, time: Float) {
        val points = arrayOf(0.33f to 0.8f, 0.5f to 0.9f, 0.67f to 0.8f)
        points.forEachIndexed { i, (hx, hy) ->
            val dy = if (time > 0f) ((time * 1.6f + i * 0.4f) % 1f) * 0.06f else 0f
            if (tone == Tone.Color) {
                fill.color = 0xFFFFFFFF.toInt()
                fill.shader = RadialGradient(x(hx) - d(0.01f), y(hy + dy) - d(0.01f), d(0.05f), 0xFFFFFFFF.toInt(), 0xFFB9D8F5.toInt(), Shader.TileMode.CLAMP)
            } else {
                fill.shader = null
                fill.color = ink
            }
            canvas.drawCircle(x(hx), y(hy + dy), d(0.042f), fill)
        }
        fill.shader = null
    }

    private fun bolt(canvas: Canvas) {
        path.reset()
        path.moveTo(x(0.55f), y(0.52f))
        path.lineTo(x(0.39f), y(0.76f))
        path.lineTo(x(0.5f), y(0.76f))
        path.lineTo(x(0.43f), y(0.97f))
        path.lineTo(x(0.64f), y(0.68f))
        path.lineTo(x(0.53f), y(0.68f))
        path.lineTo(x(0.61f), y(0.52f))
        path.close()
        if (tone == Tone.Mono) {
            clear.style = Paint.Style.STROKE
            clear.strokeWidth = d(0.06f)
            canvas.drawPath(path, clear)
        }
        fill.pathEffect = CornerPathEffect(d(0.02f))
        if (tone == Tone.Color) {
            shadow.color = 0x88FFC43D.toInt()
            shadow.maskFilter = BlurMaskFilter(d(0.05f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, shadow)
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = LinearGradient(0f, y(0.52f), 0f, y(0.97f), 0xFFFFF4A8.toInt(), 0xFFFFA92E.toInt(), Shader.TileMode.CLAMP)
        } else {
            fill.shader = null
            fill.color = ink
        }
        canvas.drawPath(path, fill)
        fill.pathEffect = null
        fill.shader = null
    }

    private fun fog(canvas: Canvas) {
        stroke.shader = null
        stroke.color = if (tone == Tone.Color) 0xFFE9EDF4.toInt() else ink
        stroke.strokeWidth = d(0.065f)
        val lines = arrayOf(Triple(0.2f, 0.72f, 0.64f), Triple(0.3f, 0.8f, 0.78f), Triple(0.24f, 0.62f, 0.92f))
        lines.forEachIndexed { i, (x0, x1, ly) ->
            stroke.alpha = if (tone == Tone.Color) 235 - i * 30 else 220 - i * 40
            canvas.drawLine(x(x0), y(ly), x(x1), y(ly), stroke)
        }
        stroke.alpha = 255
    }

    // endregion

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
