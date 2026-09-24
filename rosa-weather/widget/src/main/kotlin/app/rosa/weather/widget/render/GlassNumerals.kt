package app.rosa.weather.widget.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import app.rosa.weather.core.model.Argb
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The colours of a glass temperature: its body from [top] to [bottom], the light caught on its
 * upper edges ([rim]), the colour gathered in its lower edges ([foot]), the shadow it casts on the
 * pane and a diagonal [sheen]. A zero alpha leaves that part out.
 */
data class GlassInk(
    val top: Int,
    val bottom: Int,
    val rim: Int,
    val foot: Int,
    val shadow: Int,
    val sheen: Int,
)

/**
 * The widget's temperature drawn as a piece of glass, the Canvas counterpart of the app's liquid
 * numerals. It is plain 2D drawing into the widget bitmap, so it costs nothing between renders.
 * Edges come from the glyphs themselves: the part of the text that a small shift uncovers is a
 * crescent along one side of every stroke. Like the pane, the digits catch the sky's [WidgetLight]:
 * lit on the side of the sun or moon, in its colour, brighter in real sunlight.
 */
internal object GlassNumerals {
    /** Below this size an edge would be a pixel wide and only blur the digits: draw them flat. */
    private const val MIN_SIZE = 26f

    fun draw(
        canvas: Canvas,
        text: String,
        left: Float,
        baseline: Float,
        base: TextPaint,
        ink: GlassInk,
        strongShadow: Boolean,
        light: WidgetLight = WidgetLight.Resting,
    ) {
        val size = base.textSize
        val paint = TextPaint(base)
        val glyphs = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val box = RectF(left + glyphs.left, baseline + glyphs.top, left + glyphs.right, baseline + glyphs.bottom)
        val pad = size * 0.16f
        val bounds = RectF(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad)

        // The shadow the glass casts on the pane; darker where the widget sits on bare wallpaper.
        paint.color = if (strongShadow) 0x70000000 else ink.shadow
        paint.maskFilter = BlurMaskFilter(size * if (strongShadow) 0.05f else 0.06f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, left, baseline + size * 0.04f, paint)
        paint.maskFilter = null

        if (size < MIN_SIZE) {
            paint.color = ink.top or 0xFF000000.toInt()
            canvas.drawText(text, left, baseline, paint)
            return
        }

        val layer = canvas.saveLayer(bounds, null)
        // The body clears towards the top and gathers colour towards its foot. (An opaque paint
        // colour, or the shadow's alpha would thin the gradient.)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(0f, box.top, 0f, box.bottom, ink.top, ink.bottom, Shader.TileMode.CLAMP)
        canvas.drawText(text, left, baseline, paint)
        paint.shader = null

        // Lit along the edges facing the light, gathering colour along those facing away.
        val e = size * 0.017f
        val lx = cos(light.overall)
        val ly = sin(light.overall)
        edge(canvas, text, left, baseline, paint, bounds, lit(ink.rim, light), dx = -lx * e, dy = -ly * e)
        edge(canvas, text, left, baseline, paint, bounds, ink.foot, dx = lx * e, dy = ly * e)

        if (ink.sheen ushr 24 != 0) {
            // A band of light across the upper half, kept inside the glyphs.
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            paint.color = 0xFFFFFFFF.toInt()
            paint.shader = LinearGradient(
                box.left, box.top, box.left + box.height() * 0.8f, box.bottom,
                intArrayOf(0, ink.sheen, 0),
                floatArrayOf(0.22f, 0.36f, 0.52f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.xfermode = null
        }
        canvas.restoreToCount(layer)
    }

    /**
     * Paints the sliver of every stroke that shifting the text by ([dx], [dy]) uncovers, i.e. the
     * edges facing away from the shift, softened so it fades into the body.
     */
    private fun edge(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint, bounds: RectF, color: Int, dx: Float, dy: Float) {
        if (color ushr 24 == 0) return
        val layer = canvas.saveLayer(bounds, null)
        paint.color = color
        canvas.drawText(text, x, y, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        paint.color = 0xFF000000.toInt()
        paint.maskFilter = BlurMaskFilter(hypot(dx, dy) * 0.56f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, x + dx, y + dy, paint)
        paint.maskFilter = null
        paint.xfermode = null
        canvas.restoreToCount(layer)
    }

    /** The rim takes the light's colour, and brightens in real sunlight. */
    private fun lit(rim: Int, light: WidgetLight): Int {
        if (rim ushr 24 == 0) return rim
        val alpha = ((rim ushr 24) / 255f * (1f + 0.2f * light.power)).coerceAtMost(1f)
        return Argb(rim or 0xFF000000.toInt()).lerp(light.color, light.share).withAlpha(alpha).value
    }
}
