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
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
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
 *  - [Tone.Color]: glass pictograms — frosted clouds lit from within, a glowing glass orb of a
 *    sun, a pearl moon, raindrops as glass beads, crystal flakes; each with rim light, a specular
 *    highlight, a soft cast shadow and a whisper of dispersion;
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
        val rotation = time * 0.12f
        if (tone == Tone.Mono) {
            stroke.shader = null
            stroke.strokeWidth = pr * 0.24f
            stroke.color = ink
            stroke.alpha = 220
            for (i in 0 until 8) {
                val a = rotation + i * (PI / 4).toFloat()
                val outer = pr * (if (i % 2 == 0) 1.82f else 1.7f)
                canvas.drawLine(px + cos(a) * pr * 1.42f, py + sin(a) * pr * 1.42f, px + cos(a) * outer, py + sin(a) * outer, stroke)
            }
            fill.shader = null
            fill.color = ink
            canvas.drawCircle(px, py, pr, fill)
            return
        }
        // Halo of warm light around the orb.
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = RadialGradient(px, py, pr * 2.5f, intArrayOf(0x70FFD36B, 0x26FFB347, 0x00FFB347), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(px, py, pr * 2.5f, fill)
        // Rays: glass shards, bright at the root and thinning into light at the tip.
        stroke.strokeWidth = pr * 0.21f
        stroke.alpha = 255
        for (i in 0 until 8) {
            val a = rotation + i * (PI / 4).toFloat()
            val inner = pr * 1.4f
            val outer = pr * (if (i % 2 == 0) 1.84f else 1.68f)
            val x0 = px + cos(a) * inner
            val y0 = py + sin(a) * inner
            val x1 = px + cos(a) * outer
            val y1 = py + sin(a) * outer
            stroke.shader = LinearGradient(x0, y0, x1, y1, 0xFFFFD66E.toInt(), 0x66FFB347, Shader.TileMode.CLAMP)
            canvas.drawLine(x0, y0, x1, y1, stroke)
        }
        stroke.shader = null
        // The orb: a glowing glass marble.
        fill.shader = RadialGradient(
            px - pr * 0.3f, py - pr * 0.35f, pr * 1.45f,
            intArrayOf(0xFFFFFBE0.toInt(), 0xFFFFD866.toInt(), 0xFFFFA23A.toInt(), 0xFFF0802A.toInt()),
            floatArrayOf(0f, 0.45f, 0.85f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(px, py, pr, fill)
        // Light caught inside the lower rim, as in any lit sphere of glass.
        fill.shader = RadialGradient(px + pr * 0.15f, py + pr * 0.75f, pr * 0.7f, 0x66FFF3C4, 0x00FFF3C4, Shader.TileMode.CLAMP)
        canvas.drawCircle(px, py, pr, fill)
        // Rim: a warmer edge toward the bottom-right gives the sphere depth.
        stroke.strokeWidth = pr * 0.07f
        stroke.shader = LinearGradient(px - pr, py - pr, px + pr, py + pr, 0x00E07020, 0x99E0701F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(px, py, pr * 0.965f, stroke)
        stroke.shader = null
        specular(canvas, px - pr * 0.36f, py - pr * 0.42f, pr * 0.34f, pr * 0.17f, -38f, 0.95f)
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
        // Earthshine: the unlit disc stays faintly visible (a cool grey on a pale sky).
        fill.shader = null
        fill.color = if (tone == Tone.Color) (if (onLight) 0x2E4A5580 else 0x33D6DAFF) else ink
        if (tone == Tone.Mono) fill.alpha = 60
        canvas.drawCircle(px, py, pr, fill)

        litMoonPath(px, py, pr, phase)
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(
                px - pr * 0.3f, py - pr * 0.35f, pr * 1.6f,
                intArrayOf(0xFFFFFDF2.toInt(), 0xFFE9E6FA.toInt(), if (onLight) 0xFFA9A4D6.toInt() else 0xFFC4C1E6.toInt()),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, fill)
            if (onLight) {
                // A pale pearl on a pale sky needs an edge to exist at all.
                fill.shader = null
                stroke.strokeWidth = maxOf(pr * 0.05f, 0.8f)
                stroke.color = 0x665A6394
                canvas.drawPath(path, stroke)
            }
            // Pearl surface: faint maria, a lit limb and a glint, all inside the lit part.
            canvas.withClip(path) {
                fill.shader = null
                fill.color = 0x1C5A5F8C
                drawCircle(px + pr * 0.25f, py - pr * 0.2f, pr * 0.22f, fill)
                drawCircle(px - pr * 0.15f, py + pr * 0.35f, pr * 0.16f, fill)
                drawCircle(px + pr * 0.42f, py + pr * 0.3f, pr * 0.1f, fill)
                stroke.strokeWidth = pr * 0.09f
                stroke.color = 0xFFFFFFFF.toInt()
                stroke.shader = LinearGradient(px - pr, py - pr, px + pr * 0.4f, py + pr * 0.4f, 0xCCFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
                drawPath(path, stroke)
                stroke.shader = null
                specular(this, px - pr * 0.38f, py - pr * 0.4f, pr * 0.28f, pr * 0.13f, -40f, 0.75f)
            }
        } else {
            fill.color = ink
            canvas.drawPath(path, fill)
        }
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
        if (tone == Tone.Color) {
            fill.color = 0xFFFFFFFF.toInt()
            // On a pale sky the star keeps its white heart but gets a violet tip to show against it.
            fill.shader = RadialGradient(cx, cy, r, 0xFFFFFFFF.toInt(), if (onLight) 0xFF8C84D0.toInt() else 0xFFCFC8FF.toInt(), Shader.TileMode.CLAMP)
        } else {
            fill.shader = null
            fill.color = ink
        }
        canvas.drawPath(path, fill)
        fill.shader = null
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
        val top = y(cy - w * 0.32f)
        val bottom = y(cy + w * 0.3f)
        val box = RectF(x(cx - w * 0.55f), top - d(0.02f), x(cx + w * 0.55f), bottom + d(0.02f))
        // Clouds further back get less light: their glass reads fainter.
        val lit = if (shade == CloudShade.Back) 0.55f else if (shade == CloudShade.Dark) 0.7f else 1f

        // Cast shadow: soft and cool, so the glass floats.
        shadow.color = if (onLight) 0x42324060 else 0x36101830
        shadow.maskFilter = BlurMaskFilter(d(0.05f), BlurMaskFilter.Blur.NORMAL)
        canvas.withTranslation(0f, d(0.045f)) { drawPath(path, shadow) }

        // Frosted body; on pale skies the underside deepens so white glass doesn't vanish.
        val underside = if (onLight) blend(shade.bottom, 0xFF7C879C.toInt(), 0.5f) else shade.bottom
        fill.color = 0xFFFFFFFF.toInt()
        fill.shader = LinearGradient(0f, top, 0f, bottom, shade.top, underside, Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)

        canvas.withClip(path) {
            // Light entering through the upper lobes and glowing inside the glass.
            fill.shader = RadialGradient(
                x(cx - w * 0.06f), y(cy - w * 0.14f), d(w * 0.5f),
                ((0x9A * lit).toInt() shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
            )
            drawRect(box, fill)
            // Volume: the base turns away from the light.
            fill.shader = LinearGradient(
                0f, y(cy + w * 0.04f), 0f, bottom,
                0x00000000, (0x38 shl 24) or (blend(underside, 0xFF2E3850.toInt(), 0.55f) and 0xFFFFFF), Shader.TileMode.CLAMP,
            )
            drawRect(box, fill)
            // A whisper of dispersion along the base, cool to warm.
            fill.shader = LinearGradient(
                x(cx - w * 0.5f), 0f, x(cx + w * 0.5f), 0f,
                intArrayOf(0x307FE6FF, 0x00FFFFFF, 0x30FFA6D8), null, Shader.TileMode.CLAMP,
            )
            drawRect(box.left, bottom - d(w * 0.07f), box.right, bottom, fill)
        }

        if (onLight) {
            stroke.shader = null
            stroke.color = 0x332A3550
            stroke.strokeWidth = d(0.012f)
            canvas.drawPath(path, stroke)
        }
        // Rim light along the upper contour, fading as the edge turns down.
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.shader = LinearGradient(0f, top, 0f, y(cy + w * 0.08f), ((0xE8 * lit).toInt() shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        stroke.strokeWidth = d(0.016f)
        stroke.alpha = 255
        canvas.drawPath(path, stroke)
        stroke.shader = null
        // Specular on the big lobe.
        specular(canvas, x(cx - w * 0.1f), y(cy - w * 0.19f), d(w * 0.13f), d(w * 0.045f), -24f, 0.9f * lit)
        fill.shader = null
    }

    /** A soft elliptical highlight with a hot core: the glint that makes a shape read as glass. */
    private fun specular(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, angle: Float, strength: Float) {
        if (strength <= 0.01f || rx < 0.6f) return
        canvas.withRotation(angle, cx, cy) {
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = RadialGradient(cx, cy, rx, ((0xE0 * strength).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            scale(1f, ry / rx, cx, cy)
            drawCircle(cx, cy, rx, fill)
        }
        fill.shader = null
        fill.color = ((0xF0 * strength).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        canvas.drawCircle(cx + rx * 0.55f, cy + ry * 0.35f, maxOf(ry * 0.32f, 0.5f), fill)
        fill.color = 0xFFFFFFFF.toInt()
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
            // A glass bead: bright body, a darker refracting edge, a glint and a caustic.
            val a = (alpha * 255).toInt()
            fill.color = 0xFFFFFFFF.toInt()
            fill.shader = LinearGradient(px, py - r * 2f, px, py + r, 0xFFB4E8FF.toInt(), 0xFF3A84F0.toInt(), Shader.TileMode.CLAMP)
            fill.alpha = a
            canvas.drawPath(path, fill)
            stroke.shader = LinearGradient(px - r, 0f, px + r, 0f, 0x00FFFFFF, 0x8C1F5FC8.toInt(), Shader.TileMode.CLAMP)
            stroke.strokeWidth = r * 0.28f
            stroke.alpha = a
            canvas.drawPath(path, stroke)
            stroke.shader = null
            stroke.alpha = 255
            fill.shader = null
            fill.color = 0xFFFFFFFF.toInt()
            fill.alpha = (alpha * 220).toInt()
            canvas.drawOval(RectF(px - r * 0.62f, py - r * 0.75f, px - r * 0.22f, py - r * 0.05f), fill)
            fill.alpha = (alpha * 120).toInt()
            canvas.drawCircle(px + r * 0.35f, py + r * 0.5f, r * 0.16f, fill)
        } else {
            fill.shader = null
            fill.color = ink
            fill.alpha = (alpha * 230).toInt()
            canvas.drawPath(path, fill)
        }
        fill.shader = null
        fill.alpha = 255
    }

    private fun drizzle(canvas: Canvas, time: Float) {
        val points = arrayOf(0.34f to 0.76f, 0.5f to 0.84f, 0.66f to 0.76f, 0.42f to 0.92f, 0.58f to 0.92f)
        fill.shader = null
        points.forEachIndexed { i, (px, py) ->
            val dy = if (time > 0f) ((time * 0.9f + i * 0.21f) % 1f) * 0.05f else 0f
            val bx = x(px)
            val by = y(py + dy)
            val br = d(0.028f)
            if (tone == Tone.Color) {
                fill.color = 0xFFFFFFFF.toInt()
                fill.shader = RadialGradient(bx - br * 0.35f, by - br * 0.35f, br * 1.3f, 0xFFE4F6FF.toInt(), 0xFF5AA6F5.toInt(), Shader.TileMode.CLAMP)
                canvas.drawCircle(bx, by, br, fill)
                fill.shader = null
                fill.color = 0xE6FFFFFF.toInt()
                canvas.drawCircle(bx - br * 0.38f, by - br * 0.38f, br * 0.28f, fill)
            } else {
                fill.color = ink
                canvas.drawCircle(bx, by, br, fill)
            }
        }
        fill.shader = null
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
            // Crystal: white at the heart, icy at the tips.
            val tipColor = if (onLight) 0xFF5E8FD8.toInt() else 0xFFB7DBFF.toInt()
            stroke.shader = RadialGradient(px, py, pr * 1.05f, if (onLight) 0xFF8DB4EE.toInt() else 0xFFFFFFFF.toInt(), tipColor, Shader.TileMode.CLAMP)
        }
        for (i in 0 until 3) {
            val a = rotation + i * (PI / 3).toFloat()
            canvas.drawLine(px - cos(a) * pr, py - sin(a) * pr, px + cos(a) * pr, py + sin(a) * pr, stroke)
        }
        if (tone == Tone.Color) {
            stroke.shader = null
            fill.shader = null
            fill.color = 0xF2FFFFFF.toInt()
            canvas.drawCircle(px, py, pr * 0.22f, fill)
        }
    }

    private fun hail(canvas: Canvas, time: Float) {
        val points = arrayOf(0.33f to 0.8f, 0.5f to 0.9f, 0.67f to 0.8f)
        points.forEachIndexed { i, (hx, hy) ->
            val dy = if (time > 0f) ((time * 1.6f + i * 0.4f) % 1f) * 0.06f else 0f
            if (tone == Tone.Color) {
                fill.color = 0xFFFFFFFF.toInt()
                fill.shader = RadialGradient(x(hx) - d(0.01f), y(hy + dy) - d(0.01f), d(0.05f), 0xFFFFFFFF.toInt(), 0xFFB9D8F5.toInt(), Shader.TileMode.CLAMP)
                canvas.drawCircle(x(hx), y(hy + dy), d(0.042f), fill)
                stroke.shader = null
                stroke.color = 0x803A78C8.toInt()
                stroke.strokeWidth = d(0.008f)
                canvas.drawCircle(x(hx), y(hy + dy), d(0.038f), stroke)
                fill.shader = null
                fill.color = 0xF0FFFFFF.toInt()
                canvas.drawCircle(x(hx) - d(0.014f), y(hy + dy) - d(0.014f), d(0.011f), fill)
            } else {
                fill.shader = null
                fill.color = ink
                canvas.drawCircle(x(hx), y(hy + dy), d(0.042f), fill)
            }
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
        if (tone == Tone.Color) {
            // A glossy facet catching the light along the leading edge.
            stroke.alpha = 255
            stroke.pathEffect = CornerPathEffect(d(0.02f))
            stroke.shader = LinearGradient(x(0.4f), y(0.52f), x(0.62f), y(0.9f), 0xE6FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
            stroke.strokeWidth = d(0.012f)
            canvas.drawPath(path, stroke)
            stroke.shader = null
            stroke.pathEffect = null
        }
        fill.pathEffect = null
        fill.shader = null
    }

    private fun fog(canvas: Canvas) {
        val lines = arrayOf(Triple(0.2f, 0.72f, 0.64f), Triple(0.3f, 0.8f, 0.78f), Triple(0.24f, 0.62f, 0.92f))
        if (tone == Tone.Mono) {
            stroke.shader = null
            stroke.color = ink
            stroke.strokeWidth = d(0.065f)
            lines.forEachIndexed { i, (x0, x1, ly) ->
                stroke.alpha = 220 - i * 40
                canvas.drawLine(x(x0), y(ly), x(x1), y(ly), stroke)
            }
            stroke.alpha = 255
            return
        }
        // Bars of frosted glass: dense in the middle, thinning out at the ends, lit along the top.
        stroke.alpha = 255
        lines.forEachIndexed { i, (x0, x1, ly) ->
            val fade = 1f - i * 0.14f
            stroke.strokeWidth = d(0.065f)
            stroke.shader = LinearGradient(
                x(x0), 0f, x(x1), 0f,
                intArrayOf(blendAlpha(0xFFDCE2EC.toInt(), 0.55f * fade), blendAlpha(0xFFF3F6FA.toInt(), 0.95f * fade), blendAlpha(0xFFDCE2EC.toInt(), 0.55f * fade)),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawLine(x(x0), y(ly), x(x1), y(ly), stroke)
            stroke.strokeWidth = d(0.012f)
            stroke.shader = LinearGradient(x(x0), 0f, x(x1), 0f, intArrayOf(0x00FFFFFF, blendAlpha(0xFFFFFFFF.toInt(), 0.9f * fade), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            canvas.drawLine(x(x0 + 0.02f), y(ly - 0.018f), x(x1 - 0.02f), y(ly - 0.018f), stroke)
        }
        stroke.shader = null
    }

    private fun blendAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0xFFFFFF)

    // endregion

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) + ((b shr shift and 0xFF) - (a shr shift and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
