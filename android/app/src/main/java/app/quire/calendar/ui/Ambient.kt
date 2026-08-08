package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens

/**
 * The ground the world sits on: two very soft pools of colour behind everything.
 *
 * They are driven by where the user is — the month under the focus, the zoom
 * level — rather than by a clock. An idle screen therefore holds perfectly
 * still and costs nothing, while moving through the calendar makes the
 * background shift underneath at its own slower rate, which is what reads as
 * depth. A background that breathes on a timer would repaint sixty times a
 * second forever to say nothing.
 */
class Ambient(context: Context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var warm: RadialGradient? = null
    private var cool: RadialGradient? = null
    private var vignette: RadialGradient? = null
    private var builtFor = 0
    private var builtWidth = 0f
    private var builtHeight = 0f

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; warm = null; cool = null; vignette = null }

    private fun build(w: Float, h: Float) {
        val key = palette.accent xor palette.canvas
        if (warm != null && key == builtFor && w == builtWidth && h == builtHeight) return
        builtFor = key
        builtWidth = w
        builtHeight = h
        val radius = maxOf(w, h) * 0.85f
        warm = RadialGradient(
            0f,
            0f,
            radius,
            intArrayOf(
                Tokens.withAlpha(palette.accent, if (palette.dark) 0.17f else 0.10f),
                Tokens.withAlpha(palette.accent, 0f),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        // A vignette keeps the corners from competing with the grid.
        vignette = RadialGradient(
            w / 2f,
            h / 2f,
            maxOf(w, h) * 0.72f,
            intArrayOf(
                Tokens.withAlpha(palette.ink, 0f),
                Tokens.withAlpha(palette.ink, if (palette.dark) 0.30f else 0.055f),
            ),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        cool = RadialGradient(
            0f,
            0f,
            radius * 0.9f,
            intArrayOf(
                Tokens.withAlpha(palette.ink, if (palette.dark) 0.10f else 0.055f),
                Tokens.withAlpha(palette.ink, 0f),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /**
     * [focus] is the continuous month index and [zoom] the level; both are used
     * as parallax input so the pools lag behind the grid rather than track it.
     */
    fun draw(
        canvas: Canvas,
        w: Float,
        h: Float,
        zoom: Float,
        focus: Float,
        tiltX: Float = 0f,
        tiltY: Float = 0f,
    ) {
        canvas.drawColor(palette.canvas)
        build(w, h)

        val drift = (focus - Math.floor(focus.toDouble() / 12.0).toFloat() * 12f) / 12f
        val depth = 1f - (zoom - 1f).coerceIn(0f, 1f) * 0.6f

        // The pools are the farthest layer, so they answer the tilt the least.
        val parallaxX = tiltX * dp(9f)
        val parallaxY = tiltY * dp(7f)

        paint.shader = warm
        var restore = canvas.save()
        canvas.translate(
            w * (0.16f + drift * 0.24f) + parallaxX,
            h * 0.14f - dp(12f) * (1f - zoom.coerceIn(0f, 1f)) + parallaxY,
        )
        paint.alpha = (255 * depth).toInt()
        canvas.drawPaint(paint)
        canvas.restoreToCount(restore)

        paint.shader = cool
        restore = canvas.save()
        canvas.translate(
            w * (0.94f - drift * 0.18f) + parallaxX * 0.7f,
            h * (0.82f + zoom * 0.03f) + parallaxY * 0.7f,
        )
        canvas.drawPaint(paint)
        canvas.restoreToCount(restore)

        paint.alpha = 255
        paint.shader = vignette
        canvas.drawPaint(paint)

        paint.shader = null
        paint.alpha = 255
    }
}
