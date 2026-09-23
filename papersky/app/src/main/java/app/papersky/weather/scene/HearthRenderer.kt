package app.papersky.weather.scene

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The room by the fire (DESIGN_DOCTRINE §16): a warm plaster wall, a stone chimney-piece with an
 * arched firebox, an oak mantel with candles and books, logs on a bed of embers, and a window
 * where the weather goes on outside (painted by [PaperSceneRenderer]).
 *
 * Split like the prints: the room and the window frame are static and cached by the app; the
 * fire, its sparks and the light it throws are drawn every frame. The flames come from a GPU
 * shader where one can run ([Shaders.FIRE]); elsewhere — widget bitmaps, thumbnails, older
 * phones — from layered tongues of flame drawn here.
 */
class HearthRenderer(private val density: Float) {
    private val dp = density

    /** Where everything stands, in px, for one canvas size. */
    class Geometry(
        val w: Float,
        val h: Float,
        /** The glass, arched at the top. */
        val window: RectF,
        val windowPath: Path,
        val arch: Float,
        val frame: Float,
        val mantel: RectF,
        val surround: RectF,
        val opening: RectF,
        val openingPath: Path,
        /** Where flames burn, inside the opening. */
        val fire: RectF,
        /** Top of the logs. */
        val logs: Float,
        val floor: Float,
        val scale: Float,
    )

    private var cached: Geometry? = null

    fun geometry(w: Float, h: Float): Geometry {
        cached?.let { if (it.w == w && it.h == h) return it }
        val landscape = w >= h * 0.95f
        val s = (min(w, h) / (dp * 300f)).coerceIn(0.4f, 1.4f)
        val window: RectF
        val mantel: RectF
        val surround: RectF
        val opening: RectF
        val floor: Float
        if (landscape) {
            window = RectF(w * 0.57f, h * 0.1f, w * 0.94f, h * 0.64f)
            mantel = RectF(w * 0.03f, h * 0.34f, w * 0.53f, h * 0.34f + 10 * dp * s)
            surround = RectF(w * 0.06f, mantel.bottom, w * 0.5f, h * 0.9f)
            opening = RectF(w * 0.13f, h * 0.45f, w * 0.43f, h * 0.87f)
            floor = h * 0.9f
        } else {
            window = RectF(w * 0.55f, h * 0.095f, w * 0.93f, h * 0.44f)
            mantel = RectF(w * 0.07f, h * 0.495f, w * 0.93f, h * 0.495f + 13 * dp * s)
            surround = RectF(w * 0.11f, mantel.bottom, w * 0.89f, h * 0.84f)
            opening = RectF(w * 0.24f, h * 0.56f, w * 0.76f, h * 0.815f)
            floor = h * 0.84f
        }
        val arch = min(window.height() * 0.34f, window.width() * 0.5f)
        val windowPath = archPath(window, arch)
        val openingPath = archPath(opening, opening.height() * 0.2f)
        val logs = opening.bottom - opening.height() * 0.2f
        val fire = RectF(opening.left + opening.width() * 0.06f, opening.top + opening.height() * 0.05f, opening.right - opening.width() * 0.06f, logs + opening.height() * 0.06f)
        return Geometry(w, h, window, windowPath, arch, 6 * dp * s, mantel, surround, opening, openingPath, fire, logs, floor, s).also { cached = it }
    }

    private fun archPath(r: RectF, rise: Float): Path = Path().apply {
        moveTo(r.left, r.bottom)
        lineTo(r.left, r.top + rise)
        arcTo(RectF(r.left, r.top, r.right, r.top + rise * 2), 180f, 180f, false)
        lineTo(r.right, r.bottom)
        close()
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = PaperGrain.shader() }
    private val paperPaint by lazy { Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = MaterialTextures.shader(MaterialTextures.paper) } }
    private val firePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.PLUS }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val sprite = SpritePaint()
    private val tmpPath = Path()
    private val tmpRect = RectF()
    private val srcRect = Rect()
    private val dstRect = Rect()

    /** How hard the fire burns: a roaring fire in the frost, embers and a small flame in the heat. */
    fun intensity(s: SceneState): Float {
        val cold = 1f - smoothstep(-6f, 24f, s.temperature)
        val cosy = (s.rain + s.snow + s.drizzle).coerceIn(0f, 1f) * 0.12f + (1f - s.daylight) * 0.06f
        return (0.4f + 0.72f * cold + cosy).coerceIn(0.35f, 1.2f)
    }

    /** The draught in the chimney leans the flames with the wind outside. */
    fun lean(s: SceneState) = (s.windX / 22f).coerceIn(-0.45f, 0.45f)

    /** Firelight flickering: a few incommensurate beats, never quite repeating. */
    fun flicker(t: Float): Float =
        0.84f + 0.07f * sin(t * 7.3f) + 0.05f * sin(t * 13.1f + 1.3f) + 0.04f * sin(t * 23.7f + 0.4f) + 0.03f * sin(t * 2.1f + 2.2f)

    // ============================================================================================
    // Static: the room
    // ============================================================================================

    /** The wall, the chimney-piece, the mantel and its things, the firebox, the logs, the hearthstone. */
    fun drawRoom(canvas: Canvas, g: Geometry, s: SceneState, p: ScenePalette, extraBelow: Float = 0f) {
        val w = g.w
        val bottom = g.h + extraBelow
        // Plaster, darker up in the shadows, warmer where the fire reaches.
        shaded.shader = LinearGradient(0f, 0f, 0f, g.surround.bottom, p.skyTop, p.skyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, bottom, shaded)
        shaded.shader = null
        // Wallpaper: a quiet stripe, a shade darker every other band, with a hairline between.
        val stripe = 18 * dp * g.scale
        var sx = 0f
        var band = 0
        while (sx < w) {
            if (band % 2 == 1) {
                fill.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.045f)
                canvas.drawRect(sx, 0f, sx + stripe, g.surround.bottom, fill)
            }
            lines.strokeWidth = 0.6f * dp
            lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.035f)
            canvas.drawLine(sx, 0f, sx, g.surround.bottom, lines)
            sx += stripe
            band++
        }
        paperPaint.alpha = 70
        canvas.drawRect(0f, 0f, w, bottom, paperPaint)
        grainPaint.alpha = 60
        canvas.drawRect(0f, 0f, w, bottom, grainPaint)
        // A picture rail running round the room.
        lines.strokeWidth = 1.5f * dp * g.scale
        lines.color = ColorMath.withAlpha(ColorMath.darken(p.skyTop, 0.25f), 0.6f)
        canvas.drawLine(0f, g.window.top - 14 * dp * g.scale, w, g.window.top - 14 * dp * g.scale, lines)
        lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.05f)
        canvas.drawLine(0f, g.window.top - 12.5f * dp * g.scale, w, g.window.top - 12.5f * dp * g.scale, lines)

        // The window's reveal: the wall turns into a deep embrasure around the frame.
        tmpRect.set(g.window.left - g.frame * 2.2f, g.window.top - g.frame * 2.2f, g.window.right + g.frame * 2.2f, g.window.bottom + g.frame * 1.5f)
        fill.color = ColorMath.withAlpha(ColorMath.darken(p.skyTop, 0.3f), 0.55f)
        canvas.drawPath(archPath(tmpRect, min(tmpRect.height() * 0.34f, tmpRect.width() * 0.5f)), fill)

        drawChimneyPiece(canvas, g, p)
        drawFirebox(canvas, g, p)
        drawLogs(canvas, g, s, p)
        drawMantel(canvas, g, p)
        drawHearthstone(canvas, g, p, bottom)
    }

    private fun drawChimneyPiece(canvas: Canvas, g: Geometry, p: ScenePalette) {
        val r = g.surround
        canvas.save()
        canvas.clipRect(r)
        canvas.clipOutPath(g.openingPath)
        fill.color = ColorMath.darken(p.hillMid, 0.35f)
        canvas.drawRect(r, fill)
        // Dressed stone laid in courses, each block its own shade, mortar between them.
        val course = 15 * dp * g.scale
        val gap = 1.6f * dp * g.scale
        var y = r.top
        var row = 0
        while (y < r.bottom) {
            var x = r.left - (if (row % 2 == 0) 0f else course * 1.3f)
            var k = 0
            while (x < r.right) {
                val bw = course * (2f + rand(row * 31 + k, 881) * 1.4f)
                val tone = rand(row * 31 + k, 883)
                tmpRect.set(x + gap / 2, y + gap / 2, x + bw - gap / 2, y + course - gap / 2)
                fill.color = ColorMath.lerp(ColorMath.lerp(p.hillFar, p.hillMid, 0.35f), ColorMath.lerp(p.hillFar, p.hillMid, 0.85f), tone)
                canvas.drawRoundRect(tmpRect, 1.5f * dp, 1.5f * dp, fill)
                // Light on the top arris, shade on the bottom one.
                lines.strokeWidth = 1f * dp
                lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.1f)
                canvas.drawLine(tmpRect.left + 2 * dp, tmpRect.top + 0.6f * dp, tmpRect.right - 2 * dp, tmpRect.top + 0.6f * dp, lines)
                lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.14f)
                canvas.drawLine(tmpRect.left + 2 * dp, tmpRect.bottom - 0.6f * dp, tmpRect.right - 2 * dp, tmpRect.bottom - 0.6f * dp, lines)
                x += bw
                k++
            }
            y += course
            row++
        }
        paperPaint.alpha = 90
        canvas.drawRect(r, paperPaint)
        // The stone blackened by smoke just above the opening.
        shaded.shader = RadialGradient(g.opening.centerX(), g.opening.top, g.opening.width() * 0.55f, intArrayOf(0x66000000, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(r, shaded)
        shaded.shader = null
        canvas.restore()
        // A soft shadow where the chimney-piece stands proud of the wall.
        shaded.shader = LinearGradient(r.right, 0f, r.right + 10 * dp * g.scale, 0f, 0x33000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(r.right, r.top, r.right + 10 * dp * g.scale, r.bottom, shaded)
        shaded.shader = LinearGradient(r.left, 0f, r.left - 10 * dp * g.scale, 0f, 0x33000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(r.left - 10 * dp * g.scale, r.top, r.left, r.bottom, shaded)
        shaded.shader = null
    }

    private fun drawFirebox(canvas: Canvas, g: Geometry, p: ScenePalette) {
        val o = g.opening
        canvas.save()
        canvas.clipPath(g.openingPath)
        shaded.shader = LinearGradient(0f, o.top, 0f, o.bottom, ColorMath.darken(p.water, 0.3f), ColorMath.lerp(p.water, p.window, 0.12f), Shader.TileMode.CLAMP)
        canvas.drawRect(o, shaded)
        shaded.shader = null
        // Firebricks at the back, warmed by the fire.
        val course = 9 * dp * g.scale
        lines.strokeWidth = 0.8f * dp
        lines.color = ColorMath.withAlpha(ColorMath.lerp(p.water, p.window, 0.35f), 0.22f)
        var y = o.bottom - course
        var row = 0
        while (y > o.top) {
            canvas.drawLine(o.left, y, o.right, y, lines)
            var x = o.left + (if (row % 2 == 0) 0f else course * 1.2f)
            while (x < o.right) {
                canvas.drawLine(x, y, x, y + course, lines)
                x += course * 2.4f
            }
            y -= course
            row++
        }
        // The throat of the chimney: black at the top of the arch.
        shaded.shader = LinearGradient(0f, o.top, 0f, o.top + o.height() * 0.45f, 0xEE000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(o, shaded)
        shaded.shader = null
        // The sides of the firebox, deep in shadow.
        shaded.shader = LinearGradient(o.left, 0f, o.left + o.width() * 0.16f, 0f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(o.left, o.top, o.left + o.width() * 0.16f, o.bottom, shaded)
        shaded.shader = LinearGradient(o.right, 0f, o.right - o.width() * 0.16f, 0f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(o.right - o.width() * 0.16f, o.top, o.right, o.bottom, shaded)
        shaded.shader = null
        canvas.restore()
    }

    private val logSpecs = floatArrayOf(
        // cx, cy (fraction of the opening below the logs line), length, thickness, angle
        0.5f, 0.35f, 0.74f, 0.13f, 0f,
        0.36f, 0.62f, 0.52f, 0.12f, 9f,
        0.63f, 0.66f, 0.54f, 0.125f, -11f,
    )

    private fun drawLogs(canvas: Canvas, g: Geometry, s: SceneState, p: ScenePalette) {
        val o = g.opening
        val bedH = o.bottom - g.logs
        canvas.save()
        canvas.clipPath(g.openingPath)
        // The bed of embers under the logs.
        for (i in 0 until 26) {
            val x = o.left + o.width() * (0.12f + 0.76f * rand(i, 891))
            val y = o.bottom - bedH * (0.08f + 0.3f * rand(i, 893))
            val r = (3f + 4f * rand(i, 895)) * dp * g.scale
            fill.color = ColorMath.lerp(0xFF1A0E09.toInt(), 0xFF3A1A0C.toInt(), rand(i, 897))
            canvas.drawCircle(x, y, r, fill)
        }
        drawLog(canvas, g, p, 0)
        canvas.restore()
    }

    /** One log of [logSpecs] (0 = the back log, 1 and 2 = the two crossed in front). */
    private fun drawLog(canvas: Canvas, g: Geometry, p: ScenePalette, index: Int) {
        val o = g.opening
        val bedH = o.bottom - g.logs
        run {
            val k = index * 5
            val cx = o.left + o.width() * logSpecs[k]
            val cy = g.logs + bedH * logSpecs[k + 1]
            val len = o.width() * logSpecs[k + 2]
            val th = o.height() * logSpecs[k + 3]
            canvas.save()
            canvas.rotate(logSpecs[k + 4], cx, cy)
            tmpRect.set(cx - len / 2, cy - th / 2, cx + len / 2, cy + th / 2)
            // Bark: rounded, lit from the flames above, charred below.
            shaded.shader = LinearGradient(0f, tmpRect.top, 0f, tmpRect.bottom, intArrayOf(ColorMath.lighten(p.tree, 0.12f), p.tree, 0xFF120906.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(tmpRect, th / 2, th / 2, shaded)
            shaded.shader = null
            lines.strokeWidth = 0.8f * dp
            lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.35f)
            for (b in 0 until 4) {
                val yy = tmpRect.top + th * (0.25f + 0.17f * b)
                canvas.drawLine(tmpRect.left + th * 0.6f, yy, tmpRect.right - th * 0.6f - rand(k + b, 901) * len * 0.2f, yy + (rand(k + b, 903) - 0.5f) * 2 * dp, lines)
            }
            // The sawn end: pale wood with its rings.
            val ex = tmpRect.right - th * 0.3f
            tmpRect.set(ex - th * 0.32f, cy - th / 2, ex + th * 0.32f, cy + th / 2)
            fill.color = ColorMath.lerp(0xFFB88A5E.toInt(), p.tree, 0.45f)
            canvas.drawOval(tmpRect, fill)
            stroke.strokeWidth = 0.7f * dp
            stroke.color = ColorMath.withAlpha(ColorMath.darken(p.tree, 0.2f), 0.6f)
            for (ring in 1..3) {
                val f = ring / 4f
                tmpRect.set(ex - th * 0.32f * f, cy - th / 2 * f, ex + th * 0.32f * f, cy + th / 2 * f)
                canvas.drawOval(tmpRect, stroke)
            }
            canvas.restore()
        }
    }

    private var frontLogs: android.graphics.Bitmap? = null
    private var frontKey = 0L

    /** The two front logs, drawn once into a bitmap so they can lie over the flames every frame. */
    private fun frontLogs(g: Geometry, p: ScenePalette): android.graphics.Bitmap? {
        val o = g.opening
        val key = (o.width().toLong() shl 40) xor (o.height().toLong() shl 20) xor p.tree.toLong() xor (o.left.toLong() shl 8)
        if (frontLogs != null && key == frontKey) return frontLogs
        val bw = o.width().roundToInt().coerceAtLeast(1)
        val bh = o.height().roundToInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-o.left, -o.top)
        drawLog(c, g, p, 1)
        drawLog(c, g, p, 2)
        frontLogs = bmp
        frontKey = key
        return bmp
    }

    private fun drawMantel(canvas: Canvas, g: Geometry, p: ScenePalette) {
        val m = g.mantel
        // Its shadow on the stone below.
        shaded.shader = LinearGradient(0f, m.bottom, 0f, m.bottom + 12 * dp * g.scale, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(g.surround.left, m.bottom, g.surround.right, m.bottom + 12 * dp * g.scale, shaded)
        shaded.shader = LinearGradient(0f, m.top, 0f, m.bottom, ColorMath.lighten(p.hillNear, 0.1f), ColorMath.darken(p.hillNear, 0.15f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(m, 2 * dp, 2 * dp, shaded)
        shaded.shader = null
        // Grain of the oak.
        lines.strokeWidth = 0.7f * dp
        lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.18f)
        for (i in 0 until 3) {
            val yy = m.top + m.height() * (0.3f + 0.2f * i)
            canvas.drawLine(m.left + 6 * dp, yy, m.right - 6 * dp, yy + (rand(i, 911) - 0.5f) * 2 * dp, lines)
        }
        lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.14f)
        lines.strokeWidth = 1f * dp
        canvas.drawLine(m.left + 2 * dp, m.top + 0.7f * dp, m.right - 2 * dp, m.top + 0.7f * dp, lines)

        // Two candles at one end, a few books at the other.
        val u = 1.2f * dp * g.scale
        for (c in 0..1) {
            val cx = m.left + m.width() * (0.1f + 0.06f * c)
            val ch = (22f - 7f * c) * u
            tmpRect.set(cx - 3.2f * u, m.top - 3 * u, cx + 3.2f * u, m.top)
            fill.color = ColorMath.darken(0xFFB08A4E.toInt(), 0.1f)
            canvas.drawRoundRect(tmpRect, 1.2f * u, 1.2f * u, fill)
            tmpRect.set(cx - 2.3f * u, m.top - 3 * u - ch, cx + 2.3f * u, m.top - 3 * u)
            shaded.shader = LinearGradient(tmpRect.left, 0f, tmpRect.right, 0f, 0xFFF3E6CC.toInt(), 0xFFC9B593.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(tmpRect, shaded)
            shaded.shader = null
            lines.color = 0xFF2A1D14.toInt()
            lines.strokeWidth = 0.7f * u
            canvas.drawLine(cx, tmpRect.top, cx, tmpRect.top - 2 * u, lines)
        }
        val spines = intArrayOf(0xFF6B2E2A.toInt(), 0xFF2F4A3D.toInt(), 0xFF3B3A5A.toInt(), 0xFF7A5A34.toInt())
        var bx = m.right - m.width() * 0.2f
        for (i in spines.indices) {
            val bw = (5f + 3f * rand(i, 921)) * u
            val bh = (18f + 7f * rand(i, 923)) * u
            canvas.save()
            if (i == spines.lastIndex) canvas.rotate(-14f, bx, m.top)
            tmpRect.set(bx, m.top - bh, bx + bw, m.top)
            fill.color = ColorMath.lerp(spines[i], p.hillNear, 0.3f)
            canvas.drawRect(tmpRect, fill)
            lines.color = ColorMath.withAlpha(0xFFD9B872.toInt(), 0.5f)
            lines.strokeWidth = 0.6f * u
            canvas.drawLine(bx + 1 * u, m.top - bh * 0.8f, bx + bw - 1 * u, m.top - bh * 0.8f, lines)
            canvas.restore()
            bx += bw + 0.6f * u
        }
    }

    private fun drawHearthstone(canvas: Canvas, g: Geometry, p: ScenePalette, bottom: Float) {
        val u = dp * g.scale
        val left = g.surround.left - 14 * u
        val right = g.surround.right + 14 * u
        val slab = g.floor + 26 * u
        // Oak floorboards, the rows widening as they come towards us.
        val oak = ColorMath.lerp(ColorMath.darken(p.skyBottom, 0.35f), p.tree, 0.35f)
        shaded.shader = LinearGradient(0f, g.floor, 0f, g.floor + 220 * u, ColorMath.darken(oak, 0.2f), oak, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, g.floor + 4 * u, g.w, bottom, shaded)
        shaded.shader = null
        lines.strokeWidth = 0.9f * dp
        var y = g.floor + 10 * u
        var gap = 8 * u
        var row = 0
        while (y < bottom) {
            lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.28f)
            canvas.drawLine(0f, y, g.w, y, lines)
            lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.05f)
            canvas.drawLine(0f, y + 1.2f * dp, g.w, y + 1.2f * dp, lines)
            // Butt joints, staggered from row to row.
            lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.22f)
            var x = rand(row, 1101) * 90 * u
            while (x < g.w) {
                canvas.drawLine(x, y, x, y + gap, lines)
                x += (90f + 70f * rand(row * 7 + x.toInt(), 1103)) * u
            }
            y += gap
            gap = min(gap * 1.12f, 22 * u)
            row++
        }
        // A threadbare rug in front of the fire.
        val rug = RectF(left + 22 * u, slab + 14 * u, right - 22 * u, slab + 14 * u + 70 * u)
        if (rug.top < bottom) {
            fill.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.25f)
            canvas.drawRect(rug.left - 1 * u, rug.top + 2 * u, rug.right + 1 * u, rug.bottom + 3 * u, fill)
            fill.color = 0xFF5E2723.toInt()
            canvas.drawRect(rug, fill)
            stroke.strokeWidth = 2.2f * u
            stroke.color = 0xFFB08A4E.toInt()
            tmpRect.set(rug); tmpRect.inset(6 * u, 6 * u)
            canvas.drawRect(tmpRect, stroke)
            stroke.strokeWidth = 0.9f * u
            stroke.color = ColorMath.withAlpha(0xFFB08A4E.toInt(), 0.6f)
            tmpRect.inset(5 * u, 5 * u)
            canvas.drawRect(tmpRect, stroke)
            // Its fringe.
            lines.color = ColorMath.withAlpha(0xFFD8C7A2.toInt(), 0.55f)
            lines.strokeWidth = 0.8f * u
            var fx = rug.left + 2 * u
            while (fx < rug.right) {
                canvas.drawLine(fx, rug.top, fx, rug.top - 3 * u, lines)
                canvas.drawLine(fx, rug.bottom, fx, rug.bottom + 3 * u, lines)
                fx += 3 * u
            }
            grainPaint.alpha = 90
            canvas.drawRect(rug, grainPaint)
        }
        // The hearthstone: a slab of the same stone, its front edge catching the firelight.
        shaded.shader = LinearGradient(0f, g.floor, 0f, slab, ColorMath.lighten(p.moss, 0.06f), ColorMath.darken(p.moss, 0.08f), Shader.TileMode.CLAMP)
        canvas.drawRect(left, g.floor, right, slab, shaded)
        shaded.shader = LinearGradient(0f, slab, 0f, slab + 7 * u, ColorMath.darken(p.moss, 0.25f), ColorMath.darken(p.moss, 0.45f), Shader.TileMode.CLAMP)
        canvas.drawRect(left, slab, right, slab + 7 * u, shaded)
        shaded.shader = null
        grainPaint.alpha = 70
        canvas.drawRect(left, g.floor, right, slab + 7 * u, grainPaint)
        lines.strokeWidth = 0.8f * dp
        lines.color = ColorMath.withAlpha(0xFF000000.toInt(), 0.3f)
        for (j in 1..2) {
            val jx = left + (right - left) * (j / 3f + (rand(j, 1105) - 0.5f) * 0.08f)
            canvas.drawLine(jx, g.floor, jx - 3 * u, slab + 7 * u, lines)
        }
        lines.strokeWidth = 1f * dp
        lines.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), 0.14f)
        canvas.drawLine(left, slab + 0.5f * dp, right, slab + 0.5f * dp, lines)
        canvas.drawLine(left, g.floor + 0.5f * dp, right, g.floor + 0.5f * dp, lines)
        // Its shadow on the boards.
        shaded.shader = LinearGradient(0f, slab + 7 * u, 0f, slab + 16 * u, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(left, slab + 7 * u, right, slab + 16 * u, shaded)
        shaded.shader = null
    }

    /** The window frame, its bars and sill, over whatever the outdoor print painted in the glass. */
    fun drawWindowFrame(canvas: Canvas, g: Geometry, s: SceneState, p: ScenePalette, frost: Float) {
        val r = g.window
        val wood = ColorMath.lerp(p.hillNear, p.skyTop, 0.25f)
        // Frost creeping in from the corners of the panes.
        if (frost > 0.02f) {
            val bmp = MaterialTextures.frost
            val size = (min(r.width(), r.height()) * 0.45f).roundToInt()
            srcRect.set(0, 0, bmp.width, bmp.height)
            bitmapPaint.alpha = (255 * (frost * 0.6f).coerceIn(0f, 0.6f)).roundToInt()
            canvas.save()
            canvas.clipPath(g.windowPath)
            canvas.save(); canvas.translate(r.left, r.bottom); canvas.scale(1f, -1f)
            dstRect.set(0, 0, size, size); canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint); canvas.restore()
            canvas.save(); canvas.translate(r.right, r.bottom); canvas.scale(-1f, -1f)
            dstRect.set(0, 0, size, size); canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint); canvas.restore()
            canvas.restore()
            bitmapPaint.alpha = 255
        }
        // The frame, then the bars: one mullion, a transom where the arch begins.
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.color = ColorMath.darken(wood, 0.25f)
        stroke.strokeWidth = g.frame * 1.6f
        canvas.drawPath(g.windowPath, stroke)
        stroke.color = wood
        stroke.strokeWidth = g.frame
        canvas.drawPath(g.windowPath, stroke)
        lines.strokeWidth = g.frame * 0.55f
        lines.color = wood
        lines.strokeCap = Paint.Cap.BUTT
        canvas.drawLine(r.centerX(), r.top, r.centerX(), r.bottom, lines)
        canvas.drawLine(r.left, r.top + g.arch, r.right, r.top + g.arch, lines)
        canvas.drawLine(r.left, r.top + g.arch + (r.height() - g.arch) * 0.5f, r.right, r.top + g.arch + (r.height() - g.arch) * 0.5f, lines)
        lines.strokeCap = Paint.Cap.ROUND
        stroke.strokeCap = Paint.Cap.ROUND
        // The sill.
        tmpRect.set(r.left - g.frame * 2f, r.bottom + g.frame * 0.3f, r.right + g.frame * 2f, r.bottom + g.frame * 1.8f)
        shaded.shader = LinearGradient(0f, tmpRect.top, 0f, tmpRect.bottom, ColorMath.lighten(wood, 0.12f), ColorMath.darken(wood, 0.12f), Shader.TileMode.CLAMP)
        canvas.drawRect(tmpRect, shaded)
        shaded.shader = LinearGradient(0f, tmpRect.bottom, 0f, tmpRect.bottom + g.frame * 2f, 0x44000000, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawRect(tmpRect.left, tmpRect.bottom, tmpRect.right, tmpRect.bottom + g.frame * 2f, shaded)
        shaded.shader = null
        // Snow gathering on the outside ledge, seen through the bottom of the glass.
        if (s.snowGround > 0.1f) {
            canvas.save()
            canvas.clipPath(g.windowPath)
            fill.color = ColorMath.withAlpha(p.snowCap, s.snowGround * 0.9f)
            tmpRect.set(r.left - 4 * dp, r.bottom - 7 * dp * g.scale, r.right + 4 * dp, r.bottom + 6 * dp)
            canvas.drawRoundRect(tmpRect, 6 * dp, 6 * dp, fill)
            canvas.restore()
        }
    }

    // ============================================================================================
    // Every frame: the fire and its light
    // ============================================================================================

    /**
     * The flames and sparks in the firebox at [time]. With a [shader] on a hardware canvas the
     * flames are the GPU fire; otherwise they are tongues of flame drawn here.
     */
    fun drawFire(canvas: Canvas, g: Geometry, s: SceneState, p: ScenePalette, time: Float, flare: Float, shader: RuntimeShader?) {
        val power = intensity(s)
        val lean = lean(s)
        canvas.save()
        canvas.clipPath(g.openingPath)
        drawEmbers(canvas, g, time, power + flare * 0.5f)
        val f = g.fire
        // The back of the firebox, lit by the fire and breathing with it.
        val lit = (power + flare * 0.6f).coerceAtMost(1.4f) * flicker(time)
        sprite.tint(0xFFFF7A2A.toInt(), 0.3f * lit)
        sprite.draw(canvas, SceneSprites.glow, f.centerX(), g.logs - f.height() * 0.22f, f.width() * 0.75f)
        sprite.tint(0xFFFFB050.toInt(), 0.22f * lit)
        sprite.draw(canvas, SceneSprites.glow, f.centerX(), g.logs - f.height() * 0.08f, f.width() * 0.4f)
        if (shader != null && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shader.setFloatUniform("size", f.width(), f.height())
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("intensity", power)
            shader.setFloatUniform("lean", lean)
            shader.setFloatUniform("flare", flare)
            firePaint.shader = shader
            canvas.save()
            canvas.translate(f.left, f.top)
            canvas.drawRect(0f, 0f, f.width(), f.height(), firePaint)
            canvas.restore()
            firePaint.shader = null
        } else {
            drawFlames(canvas, g, time, power + flare * 0.6f, lean)
        }
        // The front logs lie across the roots of the flames; their cracks glow.
        frontLogs(g, p)?.let { canvas.drawBitmap(it, g.opening.left, g.opening.top, null) }
        drawCracks(canvas, g, time, power + flare * 0.5f)
        drawSparks(canvas, g, time, power + flare)
        canvas.restore()
    }

    /** Glowing splits in the charred undersides of the front logs, breathing with the fire. */
    private fun drawCracks(canvas: Canvas, g: Geometry, t: Float, power: Float) {
        val o = g.opening
        val bedH = o.bottom - g.logs
        lines.strokeWidth = 1.1f * dp * g.scale
        for (i in 0 until 7) {
            val x = o.left + o.width() * (0.2f + 0.6f * rand(i, 971))
            val y = g.logs + bedH * (0.5f + 0.35f * rand(i, 973))
            val len = o.width() * (0.03f + 0.05f * rand(i, 975))
            val pulse = 0.5f + 0.5f * sin(t * (1.3f + rand(i, 977)) + i * 1.7f)
            lines.color = ColorMath.withAlpha(0xFFFF7A2A.toInt(), (0.35f + 0.5f * pulse) * power.coerceAtMost(1f))
            canvas.drawLine(x, y, x + len, y + (rand(i, 979) - 0.5f) * 3 * dp, lines)
        }
    }

    /** Hot cracks in the logs and embers breathing under them. */
    private fun drawEmbers(canvas: Canvas, g: Geometry, t: Float, power: Float) {
        val o = g.opening
        val bedH = o.bottom - g.logs
        for (i in 0 until 16) {
            val x = o.left + o.width() * (0.14f + 0.72f * rand(i, 931))
            val y = o.bottom - bedH * (0.05f + 0.55f * rand(i, 933))
            val pulse = 0.55f + 0.45f * sin(t * (1.1f + rand(i, 935) * 1.7f) + i * 2.1f)
            sprite.tint(ColorMath.lerp(0xFFFF4A10.toInt(), 0xFFFFA040.toInt(), rand(i, 937)), (0.35f + 0.5f * pulse) * power.coerceAtMost(1f))
            sprite.draw(canvas, SceneSprites.glow, x, y, (5f + 7f * rand(i, 939)) * dp * g.scale)
        }
    }

    private val flameGradients = arrayOfNulls<Shader>(3)
    private var flameKey = Float.NaN
    private val flameBlur = android.graphics.BlurMaskFilter(3.5f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    private val coreBlur = android.graphics.BlurMaskFilter(3f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)

    /** Canvas flames: tongues rising from the logs in three heats — a red body, orange, a pale core. */
    private fun drawFlames(canvas: Canvas, g: Geometry, t: Float, power: Float, lean: Float) {
        val f = g.fire
        if (flameKey != f.top + f.bottom * 7f) {
            flameKey = f.top + f.bottom * 7f
            flameGradients[0] = LinearGradient(0f, f.bottom, 0f, f.top, intArrayOf(0xEEE0461A.toInt(), 0xAAC2300E.toInt(), 0x00801808), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            flameGradients[1] = LinearGradient(0f, f.bottom, 0f, f.top + f.height() * 0.3f, intArrayOf(0xF2FF9A2E.toInt(), 0xAAFF7418.toInt(), 0x00FF5A10), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            flameGradients[2] = LinearGradient(0f, f.bottom, 0f, f.top + f.height() * 0.55f, intArrayOf(0xFFFFF2C4.toInt(), 0xCCFFD274.toInt(), 0x00FFB040), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        val base = g.logs + (f.bottom - g.logs) * 0.9f
        val reach = f.height() * (0.3f + 0.6f * power.coerceIn(0.2f, 1.5f))
        val n = 13
        for (pass in 0..2) {
            firePaint.shader = flameGradients[pass]
            firePaint.maskFilter = if (pass == 2) coreBlur else flameBlur
            val scale = when (pass) { 0 -> 1f; 1 -> 0.7f; else -> 0.4f }
            for (i in 0 until n) {
                // The pale core is a few broad tongues, not a crown of spikes.
                if (pass == 2 && i % 2 == 1) continue
                val u = (i + 0.5f) / n
                val cx = f.left + f.width() * (0.1f + 0.8f * u) + (rand(i, 941) - 0.5f) * f.width() * 0.04f
                val centre = 1f - abs(u - 0.5f) * 1.5f
                val flick = sin(t * (5.1f + i * 0.73f) + i * 1.9f) * 0.5f + sin(t * (8.3f + i * 0.41f) + i) * 0.3f + sin(t * (13.7f + i * 0.3f)) * 0.2f
                val hgt = reach * scale * (0.35f + 0.65f * centre) * (0.55f + 0.45f * rand(i, 943)) * (0.8f + 0.2f * flick) * 1.25f
                val bw = f.width() / n * (if (pass == 2) 3.4f else 2.1f) * scale
                val sway = (sin(t * (2.3f + i * 0.2f) + i * 1.3f) * 0.12f + lean * 0.6f) * hgt
                tmpPath.reset()
                tmpPath.moveTo(cx - bw / 2, base)
                tmpPath.cubicTo(cx - bw * 0.62f, base - hgt * 0.35f, cx + sway * 0.4f - bw * 0.2f, base - hgt * 0.75f, cx + sway, base - hgt)
                tmpPath.cubicTo(cx + sway * 0.4f + bw * 0.22f, base - hgt * 0.72f, cx + bw * 0.62f, base - hgt * 0.35f, cx + bw / 2, base)
                tmpPath.close()
                canvas.drawPath(tmpPath, firePaint)
            }
        }
        firePaint.shader = null
        firePaint.maskFilter = null
    }

    /** Sparks flung up from the fire, cooling from yellow to red as they rise and wink out. */
    private fun drawSparks(canvas: Canvas, g: Geometry, t: Float, power: Float) {
        val o = g.opening
        val n = (8 + power * 10).toInt()
        for (i in 0 until n) {
            val life = 1.4f + 1.4f * rand(i, 951)
            val phase = t / life + rand(i, 953)
            val cycle = floor(phase).toInt()
            val ph = phase - cycle
            if (rand(i * 17 + cycle, 955) > 0.75f) continue
            val x0 = o.left + o.width() * (0.25f + 0.5f * rand(i * 17 + cycle, 957))
            val y0 = g.logs
            val rise = o.height() * (0.55f + 0.35f * rand(i * 17 + cycle, 959))
            val x = x0 + sin(ph * 6f + i) * 6 * dp * g.scale + (rand(i * 17 + cycle, 961) - 0.5f) * ph * 30 * dp * g.scale
            val y = y0 - rise * ph
            val a = (1f - ph) * smoothstep(0f, 0.08f, ph)
            val col = ColorMath.lerp(0xFFFFE7A0.toInt(), 0xFFFF5A1A.toInt(), ph)
            sprite.tint(col, 0.6f * a)
            sprite.draw(canvas, SceneSprites.glow, x, y, 4.5f * dp * g.scale)
            fill.color = ColorMath.withAlpha(col, a)
            canvas.drawCircle(x, y, 0.9f * dp * g.scale, fill)
        }
    }

    private var glowKey = 0L
    private var roomGlow: Shader? = null
    private var floorGlow: Shader? = null
    private var glassGlow: Shader? = null

    /**
     * The light the fire throws, flickering: a warm pool over the chimney-piece and the wall, on
     * the hearthstone, a reflection in the lower panes, and the candle flames on the mantel.
     */
    fun drawGlow(canvas: Canvas, g: Geometry, s: SceneState, p: ScenePalette, time: Float, flare: Float, still: Boolean = false) {
        val power = intensity(s) + flare * 0.4f
        val fl = if (still) 1f else flicker(time)
        val key = (g.w.toLong() shl 32) xor g.h.toLong() xor (p.window.toLong() shl 5)
        if (roomGlow == null || key != glowKey) {
            glowKey = key
            val c = g.opening.centerX()
            val cy = g.opening.centerY() + g.opening.height() * 0.15f
            val warm = p.window
            roomGlow = RadialGradient(c, cy, max(g.w, g.h * 0.6f) * 0.9f, intArrayOf(ColorMath.withAlpha(warm, 0.55f), ColorMath.withAlpha(warm, 0.18f), ColorMath.withAlpha(warm, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            floorGlow = RadialGradient(c, g.floor, g.opening.width() * 0.9f, intArrayOf(ColorMath.withAlpha(warm, 0.6f), ColorMath.withAlpha(warm, 0f)), null, Shader.TileMode.CLAMP)
            glassGlow = LinearGradient(0f, g.window.bottom, 0f, g.window.bottom - g.window.height() * 0.5f, ColorMath.withAlpha(warm, 0.35f), ColorMath.withAlpha(warm, 0f), Shader.TileMode.CLAMP)
        }
        val night = 1f - s.daylight.coerceIn(0f, 1f) * 0.55f
        canvas.save()
        canvas.clipOutPath(g.windowPath)
        shaded.shader = roomGlow
        shaded.alpha = (255 * (0.55f * power * fl * night).coerceIn(0f, 1f)).roundToInt()
        canvas.drawRect(0f, 0f, g.w, g.h, shaded)
        shaded.shader = floorGlow
        shaded.alpha = (255 * (0.6f * power * fl).coerceIn(0f, 1f)).roundToInt()
        canvas.drawRect(0f, g.opening.bottom, g.w, g.h, shaded)
        canvas.restore()
        // The fire mirrored faintly in the lower panes.
        canvas.save()
        canvas.clipPath(g.windowPath)
        shaded.shader = glassGlow
        shaded.alpha = (255 * (0.5f * power * fl * night).coerceIn(0f, 1f)).roundToInt()
        canvas.drawRect(g.window, shaded)
        canvas.restore()
        shaded.shader = null
        shaded.alpha = 255
        // Candle flames.
        val m = g.mantel
        val u = 1.2f * dp * g.scale
        for (c in 0..1) {
            val cx = m.left + m.width() * (0.1f + 0.06f * c)
            val top = m.top - 3 * u - (22f - 7f * c) * u - 2 * u
            val sway = if (still) 0f else sin(time * (3.1f + c) + c * 2f) * 0.8f * u
            val hgt = (7f + (if (still) 0f else sin(time * (9.3f + c * 1.7f)) * 1.2f)) * u
            sprite.tint(p.window, 0.55f * (if (still) 1f else 0.85f + 0.15f * sin(time * 11f + c)))
            sprite.draw(canvas, SceneSprites.glow, cx, top - hgt * 0.4f, 16 * u)
            tmpPath.reset()
            tmpPath.moveTo(cx - 1.6f * u, top)
            tmpPath.quadTo(cx - 1.9f * u, top - hgt * 0.55f, cx + sway, top - hgt)
            tmpPath.quadTo(cx + 1.9f * u, top - hgt * 0.55f, cx + 1.6f * u, top)
            tmpPath.close()
            fill.color = 0xFFFFD27A.toInt()
            canvas.drawPath(tmpPath, fill)
            fill.color = 0xFFFFF6DC.toInt()
            canvas.drawCircle(cx, top - hgt * 0.25f, 0.9f * u, fill)
        }
    }

    // ============================================================================================
    // One pass: thumbnails and widgets
    // ============================================================================================

    private val outdoor by lazy { PaperSceneRenderer(density) }

    /** The whole room in one pass onto any canvas, with [outdoorPalette] for the view outside. */
    fun draw(canvas: Canvas, w: Float, h: Float, s: SceneState, p: ScenePalette, outdoorPalette: ScenePalette, time: Float, village: Boolean, frost: Float, extraBelow: Float = 0f, particles: Boolean = true) {
        if (w <= 1f || h <= 1f) return
        val g = geometry(w, h)
        drawRoom(canvas, g, s, p, extraBelow)
        canvas.save()
        canvas.clipPath(g.windowPath)
        canvas.translate(g.window.left, g.window.top)
        outdoor.draw(
            canvas, g.window.width(), g.window.height(), s, outdoorPalette,
            PaperSceneRenderer.Options(time = time, horizon = 0.62f, detail = 0.8f, vignette = 0.3f, village = village, staticBolt = s.thunder > 0.5f, glass = true, particles = particles),
        )
        canvas.restore()
        drawWindowFrame(canvas, g, s, p, frost)
        drawFire(canvas, g, s, p, time, 0f, null)
        drawGlow(canvas, g, s, p, time, 0f, still = true)
        // The corners of the room fall into dusk.
        shaded.shader = RadialGradient(w * 0.45f, h * 0.55f, max(w, h) * 0.75f, intArrayOf(0, 0, 0x88000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h + extraBelow, shaded)
        shaded.shader = null
    }
}
