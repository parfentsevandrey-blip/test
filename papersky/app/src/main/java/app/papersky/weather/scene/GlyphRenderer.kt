package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.papersky.weather.core.model.Condition
import kotlin.math.sin

/** Paper-cut weather and detail icons. */
enum class Glyph {
    Sun, Moon, SunCloud, MoonCloud, Cloud, Overcast, Fog, Drizzle, Rain, HeavyRain, Sleet, Snow, Thunder, Hail,
    Wind, Drop, Umbrella, Uv, Sunrise, Sunset, Gauge, Eye, Thermo, Refresh, Pin;

    companion object {
        fun of(condition: Condition, isDay: Boolean): Glyph = when (condition) {
            Condition.Clear -> if (isDay) Sun else Moon
            Condition.MostlyClear, Condition.PartlyCloudy -> if (isDay) SunCloud else MoonCloud
            Condition.Overcast -> Overcast
            Condition.Fog -> Fog
            Condition.Drizzle -> Drizzle
            Condition.FreezingDrizzle, Condition.FreezingRain -> Sleet
            Condition.Rain, Condition.RainShowers -> Rain
            Condition.HeavyRain -> HeavyRain
            Condition.Snow, Condition.HeavySnow, Condition.SnowShowers -> Snow
            Condition.SnowGrains -> Hail
            Condition.Thunderstorm -> Thunder
            Condition.ThunderHail -> Thunder
        }
    }
}

/** Colours for glyphs; [forPaper] keeps white things (snow, clouds) readable on light paper. */
data class GlyphColors(
    val sun: Int,
    val sunRay: Int,
    val moon: Int,
    val cloud: Int,
    val cloudShade: Int,
    val rain: Int,
    val snow: Int,
    val bolt: Int,
    val ink: Int,
    val shadow: Int,
) {
    companion object {
        fun from(p: ScenePalette, onPaper: Boolean, darkOverride: Boolean? = null): GlyphColors {
            val bg = if (onPaper) p.paper else p.skyMid
            val darkBg = darkOverride ?: (ColorMath.luminance(bg) < 0.35f)
            val ink = when {
                darkOverride == true -> 0xFFFFFFFF.toInt()
                onPaper -> p.paperInk
                else -> p.onSky
            }
            val cloud = if (!darkBg) ColorMath.lerp(0xFFFFFFFF.toInt(), p.cloudShade, 0.12f) else ColorMath.lerp(p.cloud, 0xFFFFFFFF.toInt(), 0.35f)
            val cloudShade = if (!darkBg) ColorMath.lerp(p.cloudShade, 0xFF8C96A3.toInt(), 0.35f) else ColorMath.lerp(p.cloudShade, p.cloud, 0.3f)
            return GlyphColors(
                sun = 0xFFF3A23F.toInt(),
                sunRay = 0xFFF7C261.toInt(),
                moon = 0xFFF4E6C4.toInt(),
                cloud = cloud,
                cloudShade = cloudShade,
                rain = if (darkBg) 0xFF9CC4EC.toInt() else 0xFF3F77B3.toInt(),
                snow = if (darkBg) 0xFFFFFFFF.toInt() else 0xFF7FA3CF.toInt(),
                bolt = 0xFFF6C744.toInt(),
                ink = ink,
                shadow = if (darkBg) 0x66000000 else ColorMath.withAlpha(p.shadow, 0.28f),
            )
        }
    }
}

/**
 * Draws a [Glyph] into a square. Built on a 24-unit grid so every icon shares proportions.
 * [time] animates rays, drops and flakes in the app; widgets pass 0.
 */
class GlyphRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private val cloudPath = Path()
    private var cloudPathUnit = -1f

    fun draw(canvas: Canvas, glyph: Glyph, left: Float, top: Float, size: Float, c: GlyphColors, time: Float = 0f, rotation: Float = 0f) {
        val u = size / 24f
        canvas.save()
        canvas.translate(left, top)
        when (glyph) {
            Glyph.Sun -> sun(canvas, u, 12f, 12f, 1f, c, time)
            Glyph.Moon -> moon(canvas, u, 12f, 12f, 1f, c, time)
            Glyph.SunCloud -> { sun(canvas, u, 8.5f, 8.5f, 0.72f, c, time); cloud(canvas, u, 4f, 7f, 0.78f, c, bob(time)) }
            Glyph.MoonCloud -> { moon(canvas, u, 9f, 8f, 0.72f, c, time); cloud(canvas, u, 4f, 7f, 0.78f, c, bob(time)) }
            Glyph.Cloud -> cloud(canvas, u, 1f, 3f, 1f, c, bob(time))
            Glyph.Overcast -> {
                cloud(canvas, u, -1f, -1.5f, 0.7f, c.copy(cloud = c.cloudShade, cloudShade = ColorMath.darken(c.cloudShade, 0.15f)), -bob(time))
                cloud(canvas, u, 2.5f, 3.5f, 0.9f, c, bob(time))
            }
            Glyph.Fog -> fog(canvas, u, c, time)
            Glyph.Drizzle -> { cloud(canvas, u, 1f, -2f, 0.95f, c, 0f); drops(canvas, u, c, time, 3, short = true) }
            Glyph.Rain -> { cloud(canvas, u, 1f, -2f, 0.95f, c, 0f); drops(canvas, u, c, time, 3, short = false) }
            Glyph.HeavyRain -> { cloud(canvas, u, 1f, -2f, 0.95f, c.copy(cloud = ColorMath.lerp(c.cloud, c.cloudShade, 0.4f)), 0f); drops(canvas, u, c, time, 5, short = false) }
            Glyph.Sleet -> { cloud(canvas, u, 1f, -2f, 0.95f, c, 0f); drops(canvas, u, c, time, 2, short = false); flakes(canvas, u, c, time, 2, offset = 1) }
            Glyph.Snow -> { cloud(canvas, u, 1f, -2f, 0.95f, c, 0f); flakes(canvas, u, c, time, 3, offset = 0) }
            Glyph.Hail -> { cloud(canvas, u, 1f, -2f, 0.95f, c, 0f); hail(canvas, u, c, time) }
            Glyph.Thunder -> {
                cloud(canvas, u, 1f, -2f, 0.95f, c.copy(cloud = ColorMath.lerp(c.cloud, c.cloudShade, 0.55f)), 0f)
                bolt(canvas, u, c, time)
            }
            Glyph.Wind -> arrow(canvas, u, c, rotation)
            Glyph.Drop -> drop(canvas, u, c)
            Glyph.Umbrella -> umbrella(canvas, u, c)
            Glyph.Uv -> sun(canvas, u, 12f, 12f, 0.85f, c.copy(sun = 0xFFE9724A.toInt()), time)
            Glyph.Sunrise -> horizonSun(canvas, u, c, up = true)
            Glyph.Sunset -> horizonSun(canvas, u, c, up = false)
            Glyph.Gauge -> gauge(canvas, u, c, rotation)
            Glyph.Eye -> eye(canvas, u, c)
            Glyph.Thermo -> thermo(canvas, u, c)
            Glyph.Refresh -> refresh(canvas, u, c)
            Glyph.Pin -> pin(canvas, u, c)
        }
        canvas.restore()
    }

    private fun bob(t: Float) = if (t == 0f) 0f else sin(t * 1.4f) * 0.35f

    private fun shadowOn(color: Int, u: Float) {
        fill.setShadowLayer(1.1f * u, 0f, 0.6f * u, color)
    }

    private fun sun(canvas: Canvas, u: Float, cx: Float, cy: Float, s: Float, c: GlyphColors, t: Float) {
        canvas.save()
        canvas.translate(cx * u, cy * u)
        canvas.rotate(t * 18f)
        fill.color = c.sunRay
        for (i in 0 until 8) {
            val w = 1.9f * u * s
            rect.set(-w / 2, -10.6f * u * s, w / 2, -7.2f * u * s)
            canvas.drawRoundRect(rect, w / 2, w / 2, fill)
            canvas.rotate(45f)
        }
        canvas.restore()
        fill.color = c.sun
        shadowOn(c.shadow, u)
        canvas.drawCircle(cx * u, cy * u, 5.4f * u * s, fill)
        fill.clearShadowLayer()
        fill.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.28f)
        canvas.drawCircle((cx - 1.1f * s) * u, (cy - 1.1f * s) * u, 3.1f * u * s, fill)
    }

    private fun moon(canvas: Canvas, u: Float, cx: Float, cy: Float, s: Float, c: GlyphColors, t: Float) {
        path.reset()
        path.addCircle(cx * u, cy * u, 7f * u * s, Path.Direction.CW)
        val bite = Path().apply { addCircle((cx + 3.6f * s) * u, (cy - 2.6f * s) * u, 6.2f * u * s, Path.Direction.CW) }
        path.op(bite, Path.Op.DIFFERENCE)
        fill.color = c.moon
        shadowOn(c.shadow, u)
        canvas.drawPath(path, fill)
        fill.clearShadowLayer()
        val tw = if (t == 0f) 1f else 0.6f + 0.4f * sin(t * 2.3f)
        sparkle(canvas, (cx + 7.5f * s) * u, (cy - 5.5f * s) * u, 1.8f * u * s, ColorMath.withAlpha(c.moon, tw))
        sparkle(canvas, (cx + 5f * s) * u, (cy + 5.8f * s) * u, 1.1f * u * s, ColorMath.withAlpha(c.moon, 1.6f - tw))
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        path.reset()
        path.moveTo(x, y - r * 1.6f)
        path.quadTo(x, y, x + r * 1.6f, y)
        path.quadTo(x, y, x, y + r * 1.6f)
        path.quadTo(x, y, x - r * 1.6f, y)
        path.quadTo(x, y, x, y - r * 1.6f)
        canvas.drawPath(path, fill)
    }

    private fun ensureCloud(u: Float) {
        if (u == cloudPathUnit) return
        cloudPathUnit = u
        cloudPath.reset()
        cloudPath.addCircle(7.5f * u, 14.5f * u, 4.3f * u, Path.Direction.CW)
        cloudPath.addCircle(12.5f * u, 11.5f * u, 5.8f * u, Path.Direction.CW)
        cloudPath.addCircle(17.3f * u, 14.2f * u, 4.3f * u, Path.Direction.CW)
        val base = Path().apply { addRoundRect(RectF(3.2f * u, 13.5f * u, 21.5f * u, 19f * u), 2f * u, 2f * u, Path.Direction.CW) }
        cloudPath.op(base, Path.Op.UNION)
        val clip = Path().apply { addRect(0f, 0f, 24f * u, 19f * u, Path.Direction.CW) }
        cloudPath.op(clip, Path.Op.INTERSECT)
    }

    private fun cloud(canvas: Canvas, u: Float, dx: Float, dy: Float, s: Float, c: GlyphColors, bob: Float) {
        ensureCloud(u)
        canvas.save()
        canvas.translate(dx * u, (dy + bob) * u)
        canvas.scale(s, s, 12f * u, 14f * u)
        fill.color = c.cloudShade
        canvas.save(); canvas.translate(0.7f * u, 1f * u); canvas.drawPath(cloudPath, fill); canvas.restore()
        fill.color = c.cloud
        shadowOn(c.shadow, u)
        canvas.drawPath(cloudPath, fill)
        fill.clearShadowLayer()
        line.color = ColorMath.withAlpha(c.ink, 0.14f)
        line.strokeWidth = 0.6f * u
        canvas.drawPath(cloudPath, line)
        canvas.restore()
    }

    private fun drops(canvas: Canvas, u: Float, c: GlyphColors, t: Float, count: Int, short: Boolean) {
        line.color = c.rain
        line.strokeWidth = (if (short) 1.6f else 1.8f) * u
        val len = if (short) 1.6f else 3.6f
        for (i in 0 until count) {
            val x = 12f + (i - (count - 1) / 2f) * (if (count > 3) 3.2f else 4.2f)
            val phase = if (t == 0f) (i % 2) * 0.35f else fract(t * 1.3f + i * 0.37f)
            val y = 18.5f + phase * 3f
            val a = if (t == 0f) 1f else (1f - phase) * 1.1f
            line.alpha = (a.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawLine((x + 0.8f) * u, y * u, (x - 0.4f) * u, (y + len) * u, line)
        }
        line.alpha = 255
    }

    private fun flakes(canvas: Canvas, u: Float, c: GlyphColors, t: Float, count: Int, offset: Int) {
        line.color = c.snow
        line.strokeWidth = 1.1f * u
        for (i in 0 until count) {
            val x = 12f + (i + offset - (count + offset - 1) / 2f) * 4.6f
            val y = 19.5f + (if (t == 0f) (i % 2) * 1.6f else sin(t * 1.6f + i) * 1.2f + 0.8f)
            canvas.save()
            canvas.translate(x * u, y * u)
            canvas.rotate(t * 40f + i * 20f)
            for (k in 0 until 3) {
                canvas.drawLine(-1.7f * u, 0f, 1.7f * u, 0f, line)
                canvas.rotate(60f)
            }
            canvas.restore()
        }
    }

    private fun hail(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        fill.color = c.snow
        for (i in 0 until 3) {
            val phase = if (t == 0f) i * 0.3f else fract(t * 1.5f + i * 0.33f)
            canvas.drawCircle((8f + i * 4f) * u, (18.5f + phase * 3.5f) * u, 1.3f * u, fill)
        }
    }

    private fun bolt(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        val flick = if (t == 0f) 1f else if (fract(t * 0.6f) < 0.12f) 0.4f else 1f
        path.reset()
        path.moveTo(13f * u, 14f * u)
        path.lineTo(9f * u, 19.5f * u)
        path.lineTo(12f * u, 19.5f * u)
        path.lineTo(10.2f * u, 23.5f * u)
        path.lineTo(15.5f * u, 17.5f * u)
        path.lineTo(12.4f * u, 17.5f * u)
        path.lineTo(14.6f * u, 14f * u)
        path.close()
        fill.color = ColorMath.withAlpha(c.bolt, flick)
        shadowOn(c.shadow, u)
        canvas.drawPath(path, fill)
        fill.clearShadowLayer()
    }

    private fun fog(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        cloud(canvas, u, 1f, -3f, 0.85f, c, 0f)
        line.strokeWidth = 1.8f * u
        val rows = floatArrayOf(15.5f, 18.8f, 22f)
        val lens = floatArrayOf(14f, 17f, 11f)
        for (i in rows.indices) {
            val shift = if (t == 0f) 0f else sin(t * 0.8f + i * 1.3f) * 1.2f
            line.color = ColorMath.withAlpha(ColorMath.lerp(c.cloudShade, c.ink, 0.25f), 0.85f)
            val x0 = 12f - lens[i] / 2 + shift + (i - 1) * 1.2f
            canvas.drawLine(x0 * u, rows[i] * u, (x0 + lens[i]) * u, rows[i] * u, line)
        }
    }

    private fun arrow(canvas: Canvas, u: Float, c: GlyphColors, rotation: Float) {
        canvas.save()
        canvas.rotate(rotation, 12f * u, 12f * u)
        path.reset()
        path.moveTo(12f * u, 3f * u)
        path.lineTo(17.5f * u, 11f * u)
        path.lineTo(13.4f * u, 10f * u)
        path.lineTo(13.4f * u, 20f * u)
        path.lineTo(10.6f * u, 20f * u)
        path.lineTo(10.6f * u, 10f * u)
        path.lineTo(6.5f * u, 11f * u)
        path.close()
        fill.color = c.ink
        canvas.drawPath(path, fill)
        canvas.restore()
    }

    private fun drop(canvas: Canvas, u: Float, c: GlyphColors) {
        path.reset()
        path.moveTo(12f * u, 3f * u)
        path.cubicTo(16f * u, 9f * u, 18.5f * u, 12f * u, 18.5f * u, 15f * u)
        path.cubicTo(18.5f * u, 18.8f * u, 15.5f * u, 21.5f * u, 12f * u, 21.5f * u)
        path.cubicTo(8.5f * u, 21.5f * u, 5.5f * u, 18.8f * u, 5.5f * u, 15f * u)
        path.cubicTo(5.5f * u, 12f * u, 8f * u, 9f * u, 12f * u, 3f * u)
        path.close()
        fill.color = c.rain
        canvas.drawPath(path, fill)
        fill.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.45f)
        canvas.drawCircle(9.5f * u, 15.5f * u, 1.6f * u, fill)
    }

    private fun umbrella(canvas: Canvas, u: Float, c: GlyphColors) {
        path.reset()
        rect.set(3f * u, 4f * u, 21f * u, 20f * u)
        path.arcTo(rect, 180f, 180f, true)
        var x = 21f
        repeat(3) { path.quadTo((x - 1.5f) * u, 10.5f * u, (x - 3f) * u, 12f * u); x -= 3f; path.quadTo((x - 1.5f) * u, 10.5f * u, (x - 3f) * u, 12f * u); x -= 3f }
        path.close()
        fill.color = c.rain
        canvas.drawPath(path, fill)
        line.color = c.ink
        line.strokeWidth = 1.5f * u
        canvas.drawLine(12f * u, 12f * u, 12f * u, 19f * u, line)
        rect.set(12f * u, 17f * u, 15f * u, 21f * u)
        canvas.drawArc(rect, 180f, -180f, false, line)
    }

    private fun horizonSun(canvas: Canvas, u: Float, c: GlyphColors, up: Boolean) {
        canvas.save()
        canvas.clipRect(0f, 0f, 24f * u, 16f * u)
        fill.color = c.sun
        canvas.drawCircle(12f * u, 16f * u, 5.5f * u, fill)
        fill.color = c.sunRay
        for (i in 0..4) {
            canvas.save()
            canvas.rotate(-90f + (i - 2) * 38f, 12f * u, 16f * u)
            rect.set(18.5f * u, 15.2f * u, 21.5f * u, 16.8f * u)
            canvas.drawRoundRect(rect, 0.8f * u, 0.8f * u, fill)
            canvas.restore()
        }
        canvas.restore()
        line.color = c.ink
        line.strokeWidth = 1.6f * u
        canvas.drawLine(3f * u, 17f * u, 21f * u, 17f * u, line)
        val ay = if (up) 20f else 23f
        val dir = if (up) -1f else 1f
        canvas.drawLine(12f * u, (ay - dir * 1.5f) * u, 12f * u, (ay + dir * 1.5f) * u, line)
        canvas.drawLine(12f * u, (ay + dir * 1.5f) * u, 10.3f * u, (ay + dir * 0.1f) * u, line)
        canvas.drawLine(12f * u, (ay + dir * 1.5f) * u, 13.7f * u, (ay + dir * 0.1f) * u, line)
    }

    private fun gauge(canvas: Canvas, u: Float, c: GlyphColors, rotation: Float) {
        line.color = c.ink
        line.strokeWidth = 1.8f * u
        rect.set(3f * u, 5f * u, 21f * u, 23f * u)
        canvas.drawArc(rect, 150f, 240f, false, line)
        canvas.save()
        canvas.rotate(rotation, 12f * u, 14f * u)
        fill.color = c.sun
        path.reset()
        path.moveTo(12f * u, 6.5f * u)
        path.lineTo(13.4f * u, 14f * u)
        path.lineTo(10.6f * u, 14f * u)
        path.close()
        canvas.drawPath(path, fill)
        canvas.restore()
        fill.color = c.ink
        canvas.drawCircle(12f * u, 14f * u, 1.8f * u, fill)
    }

    private fun eye(canvas: Canvas, u: Float, c: GlyphColors) {
        path.reset()
        path.moveTo(2.5f * u, 12f * u)
        path.quadTo(12f * u, 2.5f * u, 21.5f * u, 12f * u)
        path.quadTo(12f * u, 21.5f * u, 2.5f * u, 12f * u)
        path.close()
        fill.color = c.cloud
        canvas.drawPath(path, fill)
        line.color = c.ink
        line.strokeWidth = 1.4f * u
        canvas.drawPath(path, line)
        fill.color = c.rain
        canvas.drawCircle(12f * u, 12f * u, 3.6f * u, fill)
        fill.color = c.ink
        canvas.drawCircle(12f * u, 12f * u, 1.6f * u, fill)
    }

    private fun thermo(canvas: Canvas, u: Float, c: GlyphColors) {
        line.color = c.ink
        line.strokeWidth = 1.4f * u
        rect.set(9.5f * u, 2.5f * u, 14.5f * u, 17f * u)
        canvas.drawRoundRect(rect, 2.5f * u, 2.5f * u, line)
        fill.color = 0xFFE0703C.toInt()
        canvas.drawCircle(12f * u, 18f * u, 4f * u, fill)
        rect.set(11f * u, 8f * u, 13f * u, 17f * u)
        canvas.drawRoundRect(rect, 1f * u, 1f * u, fill)
    }

    private fun refresh(canvas: Canvas, u: Float, c: GlyphColors) {
        line.color = c.ink
        line.strokeWidth = 2f * u
        rect.set(5f * u, 5f * u, 19f * u, 19f * u)
        canvas.drawArc(rect, -60f, 290f, false, line)
        path.reset()
        path.moveTo(19.5f * u, 3.5f * u)
        path.lineTo(19.8f * u, 9.5f * u)
        path.lineTo(14f * u, 8.6f * u)
        path.close()
        fill.color = c.ink
        canvas.drawPath(path, fill)
    }

    private fun pin(canvas: Canvas, u: Float, c: GlyphColors) {
        path.reset()
        path.moveTo(12f * u, 22f * u)
        path.cubicTo(6f * u, 15f * u, 5f * u, 12f * u, 5f * u, 9.5f * u)
        path.cubicTo(5f * u, 5.4f * u, 8.2f * u, 2.5f * u, 12f * u, 2.5f * u)
        path.cubicTo(15.8f * u, 2.5f * u, 19f * u, 5.4f * u, 19f * u, 9.5f * u)
        path.cubicTo(19f * u, 12f * u, 18f * u, 15f * u, 12f * u, 22f * u)
        path.close()
        fill.color = 0xFFE0703C.toInt()
        shadowOn(c.shadow, u)
        canvas.drawPath(path, fill)
        fill.clearShadowLayer()
        fill.color = 0xFFFFF6E8.toInt()
        canvas.drawCircle(12f * u, 9.5f * u, 2.8f * u, fill)
    }

    companion object {
        fun bitmap(glyph: Glyph, sizePx: Int, colors: GlyphColors, rotation: Float = 0f): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            GlyphRenderer().draw(Canvas(bmp), glyph, 0f, 0f, sizePx.toFloat(), colors, 0f, rotation)
            return bmp
        }
    }
}
