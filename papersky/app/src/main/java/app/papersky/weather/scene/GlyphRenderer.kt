package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.papersky.weather.core.model.Condition
import kotlin.math.sin

/** Engraved weather and detail icons. */
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

/** Inks for glyphs (DESIGN_DOCTRINE §7): hairlines in the sheet's ink, fills only for the lights. */
data class GlyphColors(
    /** Disc of the sun and its rays: the sheet's pigment. */
    val sun: Int,
    val moon: Int,
    /** Outlines: clouds, instruments, arrows. */
    val line: Int,
    /** A breath of ink inside outlines. */
    val wash: Int,
    val rain: Int,
    val snow: Int,
    val bolt: Int,
    val accent: Int,
) {
    companion object {
        fun from(p: ScenePalette, onPaper: Boolean, darkOverride: Boolean? = null): GlyphColors {
            val bg = if (onPaper) p.paper else p.skyMid
            val dark = darkOverride ?: (ColorMath.luminance(bg) < 0.35f)
            val line = when {
                darkOverride == true -> 0xFFF4EEE2.toInt()
                onPaper -> p.paperInk
                else -> p.onSky
            }
            return GlyphColors(
                sun = if (dark) 0xFFE0A15A.toInt() else 0xFFC0563A.toInt(),
                moon = if (dark) 0xFFEDE4CC.toInt() else line,
                line = line,
                wash = ColorMath.withAlpha(line, if (dark) 0.1f else 0.06f),
                rain = if (dark) 0xFFA3B8CE.toInt() else 0xFF3F5E80.toInt(),
                snow = if (dark) 0xFFF4F6FA.toInt() else 0xFF6F88A6.toInt(),
                bolt = 0xFFD4A445.toInt(),
                accent = if (dark) 0xFFCDAE7A.toInt() else 0xFFB04A32.toInt(),
            )
        }
    }
}

/**
 * Draws a [Glyph] into a square, engraved (DESIGN_DOCTRINE §7): one hairline weight (1.25 of 24
 * units), round ends, no shadows; only the sun, the moon and lightning are filled. The sun behind
 * a cloud is cut away by the cloud's outline, so glyphs sit equally well on paper and on the sky.
 * [time] animates rays, drops and flakes in the app; widgets pass 0.
 */
class GlyphRenderer {
    /** What to draw: the whole glyph, only its static body, or only the parts that move. */
    enum class Pass { All, Base, Motion }

    private var pass = Pass.All
    private val base get() = pass != Pass.Motion
    private val motion get() = pass != Pass.Base

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private val cloudPath = Path()
    private val placed = Path()
    private val matrix = Matrix()
    private var cloudPathUnit = -1f

    fun draw(canvas: Canvas, glyph: Glyph, left: Float, top: Float, size: Float, c: GlyphColors, time: Float = 0f, rotation: Float = 0f, pass: Pass = Pass.All) {
        this.pass = pass
        if (pass == Pass.Motion && glyph !in ANIMATED) return
        val u = size / 24f
        line.strokeWidth = STROKE * u
        canvas.save()
        canvas.translate(left, top)
        when (glyph) {
            Glyph.Sun -> sun(canvas, u, 12f, 12f, 1f, c, time)
            Glyph.Moon -> moon(canvas, u, 12f, 12f, 1f, c, time)
            Glyph.SunCloud -> { cloudOver(canvas, u, 2.5f, 0.5f, 0.82f, c) { sun(canvas, u, 9f, 9.5f, 0.78f, c, time) }; cloud(canvas, u, 2.5f, 0.5f, 0.82f, c) }
            Glyph.MoonCloud -> { cloudOver(canvas, u, 2.5f, 0.5f, 0.82f, c) { moon(canvas, u, 9.5f, 9f, 0.78f, c, time) }; cloud(canvas, u, 2.5f, 0.5f, 0.82f, c) }
            Glyph.Cloud -> cloud(canvas, u, -0.7f, -0.6f, 1f, c)
            Glyph.Overcast -> {
                cloudOver(canvas, u, 1.5f, 1.5f, 0.88f, c) { cloud(canvas, u, -3f, -3.5f, 0.72f, c, alpha = 0.5f) }
                cloud(canvas, u, 1.5f, 1.5f, 0.88f, c)
            }
            Glyph.Fog -> fog(canvas, u, c, time)
            Glyph.Drizzle -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); drops(canvas, u, c, time, 3, short = true) }
            Glyph.Rain -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); drops(canvas, u, c, time, 3, short = false) }
            Glyph.HeavyRain -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); drops(canvas, u, c, time, 5, short = false) }
            Glyph.Sleet -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); drops(canvas, u, c, time, 2, short = false); flakes(canvas, u, c, time, 1, offset = 0) }
            Glyph.Snow -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); flakes(canvas, u, c, time, 3, offset = 0) }
            Glyph.Hail -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); hail(canvas, u, c, time) }
            Glyph.Thunder -> { cloud(canvas, u, -0.7f, -3.5f, 0.95f, c); bolt(canvas, u, c, time) }
            Glyph.Wind -> arrow(canvas, u, c, rotation)
            Glyph.Drop -> drop(canvas, u, c)
            Glyph.Umbrella -> umbrella(canvas, u, c)
            Glyph.Uv -> sun(canvas, u, 12f, 12f, 0.9f, c, time)
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

    private fun sun(canvas: Canvas, u: Float, cx: Float, cy: Float, s: Float, c: GlyphColors, t: Float) {
        if (motion) {
            canvas.save()
            canvas.translate(cx * u, cy * u)
            canvas.rotate(t * 10f)
            line.color = c.sun
            line.strokeWidth = 1.05f * u
            for (i in 0 until 12) {
                val outer = if (i % 2 == 0) 9.6f else 8.4f
                canvas.drawLine(0f, -6.8f * u * s, 0f, -outer * u * s, line)
                canvas.rotate(30f)
            }
            line.strokeWidth = STROKE * u
            canvas.restore()
        }
        if (!base) return
        fill.color = c.sun
        canvas.drawCircle(cx * u, cy * u, 4.6f * u * s, fill)
    }

    private fun moon(canvas: Canvas, u: Float, cx: Float, cy: Float, s: Float, c: GlyphColors, t: Float) {
        if (motion) {
            val tw = if (t == 0f) 1f else 0.55f + 0.45f * sin(t * 2.1f)
            star(canvas, (cx + 7.2f * s) * u, (cy - 5.6f * s) * u, 1.9f * u * s, ColorMath.withAlpha(c.moon, tw))
        }
        if (!base) return
        path.reset()
        path.addCircle(cx * u, cy * u, 6.6f * u * s, Path.Direction.CW)
        val bite = Path().apply { addCircle((cx + 3.4f * s) * u, (cy - 2.4f * s) * u, 5.6f * u * s, Path.Direction.CW) }
        path.op(bite, Path.Op.DIFFERENCE)
        fill.color = c.moon
        canvas.drawPath(path, fill)
    }

    /** A four-pointed hairline star. */
    private fun star(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        line.color = color
        line.strokeWidth = r * 0.45f
        canvas.drawLine(x, y - r, x, y + r, line)
        canvas.drawLine(x - r, y, x + r, y, line)
    }

    private fun ensureCloud(u: Float) {
        if (u == cloudPathUnit) return
        cloudPathUnit = u
        cloudPath.reset()
        cloudPath.addCircle(8.2f * u, 15f * u, 3.9f * u, Path.Direction.CW)
        cloudPath.addCircle(13f * u, 11.8f * u, 5.4f * u, Path.Direction.CW)
        cloudPath.addCircle(17.6f * u, 15.2f * u, 3.5f * u, Path.Direction.CW)
        val base = Path().apply { addRoundRect(RectF(4.3f * u, 14.2f * u, 21.1f * u, 18.9f * u), 2.35f * u, 2.35f * u, Path.Direction.CW) }
        cloudPath.op(base, Path.Op.UNION)
    }

    /** The cloud outline placed at (dx, dy) and scaled by s about its centre. */
    private fun placedCloud(u: Float, dx: Float, dy: Float, s: Float): Path {
        ensureCloud(u)
        matrix.reset()
        matrix.setScale(s, s, 12.7f * u, 15f * u)
        matrix.postTranslate(dx * u, dy * u)
        cloudPath.transform(matrix, placed)
        return placed
    }

    /** Draws [behind] with the cloud's silhouette cut out of it. */
    private inline fun cloudOver(canvas: Canvas, u: Float, dx: Float, dy: Float, s: Float, c: GlyphColors, behind: () -> Unit) {
        canvas.save()
        canvas.clipOutPath(placedCloud(u, dx, dy, s))
        behind()
        canvas.restore()
    }

    private fun cloud(canvas: Canvas, u: Float, dx: Float, dy: Float, s: Float, c: GlyphColors, alpha: Float = 1f) {
        if (!base) return
        val p = placedCloud(u, dx, dy, s)
        fill.color = ColorMath.scaleAlpha(c.wash, alpha)
        canvas.drawPath(p, fill)
        line.color = ColorMath.scaleAlpha(c.line, alpha)
        line.strokeWidth = STROKE * u
        canvas.drawPath(p, line)
    }

    private fun drops(canvas: Canvas, u: Float, c: GlyphColors, t: Float, count: Int, short: Boolean) {
        if (!motion) return
        line.color = c.rain
        line.strokeWidth = 1.15f * u
        val len = if (short) 1.8f else 3.4f
        for (i in 0 until count) {
            val x = 12f + (i - (count - 1) / 2f) * (if (count > 3) 3f else if (count == 2) 5f else 4f)
            val phase = if (t == 0f) (i % 2) * 0.35f else fract(t * 1.2f + i * 0.37f)
            val y = 17.4f + phase * 2.8f
            val a = if (t == 0f) 1f else ((1f - phase) * 1.2f).coerceIn(0f, 1f)
            line.alpha = (a * 255).toInt()
            canvas.drawLine((x + 0.7f) * u, y * u, (x - 0.5f) * u, (y + len) * u, line)
        }
        line.alpha = 255
    }

    private fun flakes(canvas: Canvas, u: Float, c: GlyphColors, t: Float, count: Int, offset: Int) {
        if (!motion) return
        line.color = c.snow
        line.strokeWidth = 0.9f * u
        for (i in 0 until count) {
            val x = 12f + (i + offset - (count + offset - 1) / 2f) * 4.4f
            val y = 19f + (if (t == 0f) (i % 2) * 1.4f else sin(t * 1.4f + i) * 1.1f + 0.7f)
            canvas.save()
            canvas.translate(x * u, y * u)
            canvas.rotate(t * 30f + i * 20f)
            for (k in 0 until 3) {
                canvas.drawLine(-1.6f * u, 0f, 1.6f * u, 0f, line)
                canvas.rotate(60f)
            }
            canvas.restore()
        }
    }

    private fun hail(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        if (!motion) return
        fill.color = c.snow
        for (i in 0 until 3) {
            val phase = if (t == 0f) i * 0.3f else fract(t * 1.4f + i * 0.33f)
            canvas.drawCircle((8f + i * 4f) * u, (18.2f + phase * 3f) * u, 1.1f * u, fill)
        }
    }

    private fun bolt(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        if (!motion) return
        val flick = if (t == 0f) 1f else if (fract(t * 0.6f) < 0.12f) 0.35f else 1f
        path.reset()
        path.moveTo(13f * u, 14.2f * u)
        path.lineTo(9.8f * u, 18.8f * u)
        path.lineTo(12.2f * u, 18.8f * u)
        path.lineTo(10.6f * u, 22.4f * u)
        path.lineTo(14.8f * u, 17.4f * u)
        path.lineTo(12.4f * u, 17.4f * u)
        path.lineTo(14.2f * u, 14.2f * u)
        path.close()
        fill.color = ColorMath.withAlpha(c.bolt, flick)
        canvas.drawPath(path, fill)
    }

    private fun fog(canvas: Canvas, u: Float, c: GlyphColors, t: Float) {
        cloud(canvas, u, -0.7f, -4.5f, 0.85f, c)
        if (!motion) return
        line.strokeWidth = STROKE * u
        line.color = c.line
        val rows = floatArrayOf(16.2f, 19.2f, 22.2f)
        val lens = floatArrayOf(13f, 16f, 10f)
        for (i in rows.indices) {
            val shift = if (t == 0f) 0f else sin(t * 0.8f + i * 1.3f) * 1.1f
            line.alpha = if (i == 1) 255 else 170
            val x0 = 12f - lens[i] / 2 + shift + (i - 1) * 1.2f
            canvas.drawLine(x0 * u, rows[i] * u, (x0 + lens[i]) * u, rows[i] * u, line)
        }
        line.alpha = 255
    }

    private fun arrow(canvas: Canvas, u: Float, c: GlyphColors, rotation: Float) {
        if (!base) return
        canvas.save()
        canvas.rotate(rotation, 12f * u, 12f * u)
        line.color = c.line
        canvas.drawLine(12f * u, 20.5f * u, 12f * u, 4f * u, line)
        canvas.drawLine(8.2f * u, 7.8f * u, 12f * u, 4f * u, line)
        canvas.drawLine(15.8f * u, 7.8f * u, 12f * u, 4f * u, line)
        // Fletching at the tail.
        canvas.drawLine(12f * u, 17f * u, 9.6f * u, 19.4f * u, line)
        canvas.drawLine(12f * u, 17f * u, 14.4f * u, 19.4f * u, line)
        canvas.restore()
    }

    private fun drop(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        path.reset()
        path.moveTo(12f * u, 3.5f * u)
        path.cubicTo(15.6f * u, 9f * u, 18f * u, 12f * u, 18f * u, 15f * u)
        path.cubicTo(18f * u, 18.5f * u, 15.3f * u, 21f * u, 12f * u, 21f * u)
        path.cubicTo(8.7f * u, 21f * u, 6f * u, 18.5f * u, 6f * u, 15f * u)
        path.cubicTo(6f * u, 12f * u, 8.4f * u, 9f * u, 12f * u, 3.5f * u)
        path.close()
        fill.color = ColorMath.withAlpha(c.rain, 0.16f)
        canvas.drawPath(path, fill)
        line.color = c.rain
        canvas.drawPath(path, line)
    }

    private fun umbrella(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        path.reset()
        rect.set(3.5f * u, 4.5f * u, 20.5f * u, 20f * u)
        path.arcTo(rect, 180f, 180f, true)
        var x = 20.5f
        repeat(3) {
            path.quadTo((x - 1.4f) * u, 11f * u, (x - 2.83f) * u, 12.25f * u); x -= 2.83f
            path.quadTo((x - 1.4f) * u, 11f * u, (x - 2.83f) * u, 12.25f * u); x -= 2.83f
        }
        path.close()
        fill.color = ColorMath.withAlpha(c.rain, 0.14f)
        canvas.drawPath(path, fill)
        line.color = c.line
        canvas.drawPath(path, line)
        canvas.drawLine(12f * u, 12.2f * u, 12f * u, 19f * u, line)
        rect.set(12f * u, 17f * u, 15f * u, 21f * u)
        canvas.drawArc(rect, 180f, -180f, false, line)
    }

    private fun horizonSun(canvas: Canvas, u: Float, c: GlyphColors, up: Boolean) {
        if (!base) return
        canvas.save()
        canvas.clipRect(0f, 0f, 24f * u, 15.6f * u)
        fill.color = c.sun
        canvas.drawCircle(12f * u, 16f * u, 4.8f * u, fill)
        line.color = c.sun
        line.strokeWidth = 1.2f * u
        for (i in 0..4) {
            canvas.save()
            canvas.rotate(-90f + (i - 2) * 36f, 12f * u, 16f * u)
            canvas.drawLine(12f * u, 9.4f * u, 12f * u, 7.2f * u, line)
            canvas.restore()
        }
        canvas.restore()
        line.strokeWidth = STROKE * u
        line.color = c.line
        canvas.drawLine(3f * u, 16.8f * u, 21f * u, 16.8f * u, line)
        val ay = if (up) 20.2f else 22.8f
        val dir = if (up) -1f else 1f
        canvas.drawLine(12f * u, (ay - dir * 1.4f) * u, 12f * u, (ay + dir * 1.4f) * u, line)
        canvas.drawLine(12f * u, (ay + dir * 1.4f) * u, 10.5f * u, (ay + dir * 0.1f) * u, line)
        canvas.drawLine(12f * u, (ay + dir * 1.4f) * u, 13.5f * u, (ay + dir * 0.1f) * u, line)
    }

    private fun gauge(canvas: Canvas, u: Float, c: GlyphColors, rotation: Float) {
        if (!base) return
        line.color = c.line
        rect.set(3.5f * u, 5.5f * u, 20.5f * u, 22.5f * u)
        canvas.drawArc(rect, 150f, 240f, false, line)
        canvas.save()
        canvas.rotate(rotation, 12f * u, 14f * u)
        line.color = c.accent
        canvas.drawLine(12f * u, 14f * u, 12f * u, 7.5f * u, line)
        canvas.restore()
        fill.color = c.line
        canvas.drawCircle(12f * u, 14f * u, 1.3f * u, fill)
    }

    private fun eye(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        path.reset()
        path.moveTo(3f * u, 12f * u)
        path.quadTo(12f * u, 3.5f * u, 21f * u, 12f * u)
        path.quadTo(12f * u, 20.5f * u, 3f * u, 12f * u)
        path.close()
        line.color = c.line
        canvas.drawPath(path, line)
        canvas.drawCircle(12f * u, 12f * u, 3.2f * u, line)
        fill.color = c.line
        canvas.drawCircle(12f * u, 12f * u, 1.2f * u, fill)
    }

    private fun thermo(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        line.color = c.line
        rect.set(10f * u, 3f * u, 14f * u, 16.4f * u)
        canvas.drawRoundRect(rect, 2f * u, 2f * u, line)
        canvas.drawCircle(12f * u, 18.2f * u, 3.4f * u, line)
        fill.color = c.accent
        canvas.drawCircle(12f * u, 18.2f * u, 2f * u, fill)
        line.color = c.accent
        canvas.drawLine(12f * u, 9f * u, 12f * u, 16.5f * u, line)
    }

    private fun refresh(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        line.color = c.line
        rect.set(5f * u, 5f * u, 19f * u, 19f * u)
        canvas.drawArc(rect, -60f, 290f, false, line)
        canvas.drawLine(19f * u, 4f * u, 19f * u, 9.4f * u, line)
        canvas.drawLine(19f * u, 9.4f * u, 13.8f * u, 9.4f * u, line)
    }

    private fun pin(canvas: Canvas, u: Float, c: GlyphColors) {
        if (!base) return
        path.reset()
        path.moveTo(12f * u, 21.5f * u)
        path.cubicTo(6.8f * u, 15.4f * u, 5.6f * u, 12.2f * u, 5.6f * u, 9.8f * u)
        path.cubicTo(5.6f * u, 6f * u, 8.5f * u, 3f * u, 12f * u, 3f * u)
        path.cubicTo(15.5f * u, 3f * u, 18.4f * u, 6f * u, 18.4f * u, 9.8f * u)
        path.cubicTo(18.4f * u, 12.2f * u, 17.2f * u, 15.4f * u, 12f * u, 21.5f * u)
        path.close()
        line.color = c.line
        canvas.drawPath(path, line)
        fill.color = c.accent
        canvas.drawCircle(12f * u, 9.8f * u, 2.3f * u, fill)
    }

    companion object {
        /** Hairline weight in 24ths of the glyph (§7). */
        const val STROKE = 1.25f

        /** Glyphs with moving parts (DESIGN_DOCTRINE §7). */
        val ANIMATED = setOf(
            Glyph.Sun, Glyph.Moon, Glyph.SunCloud, Glyph.MoonCloud, Glyph.Fog, Glyph.Drizzle, Glyph.Rain,
            Glyph.HeavyRain, Glyph.Sleet, Glyph.Snow, Glyph.Hail, Glyph.Thunder, Glyph.Uv,
        )

        /** The glyph baked into a bitmap; with [Pass.Base] its moving parts are left out. */
        fun bitmap(glyph: Glyph, sizePx: Int, colors: GlyphColors, rotation: Float = 0f, pass: Pass = Pass.All): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            GlyphRenderer().draw(Canvas(bmp), glyph, 0f, 0f, sizePx.toFloat(), colors, 0f, rotation, pass)
            return bmp
        }
    }
}
