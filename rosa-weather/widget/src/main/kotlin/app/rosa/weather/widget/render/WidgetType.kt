package app.rosa.weather.widget.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import app.rosa.weather.core.designsystem.R
import kotlin.math.min

/**
 * Typefaces for Canvas rendering. Fraunces (soft, optical-size aware) carries the big numerals;
 * Onest carries everything else and has full Cyrillic support.
 */
class WidgetFonts(val numerals: Typeface, val text: Typeface) {
    companion object {
        @Volatile private var cached: WidgetFonts? = null

        fun get(context: Context): WidgetFonts = cached ?: synchronized(this) {
            cached ?: WidgetFonts(
                numerals = runCatching { context.resources.getFont(R.font.fraunces) }.getOrDefault(Typeface.SERIF),
                text = runCatching { context.resources.getFont(R.font.onest) }.getOrDefault(Typeface.SANS_SERIF),
            ).also { cached = it }
        }
    }
}

/** Reusable text paints with variable-font settings and safe fitting helpers. */
class WidgetType(private val fonts: WidgetFonts) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    fun numerals(size: Float, color: Int, weight: Int = 420, soft: Int = 100): TextPaint = configure(
        typeface = fonts.numerals,
        size = size,
        color = color,
        variation = "'opsz' ${size.coerceIn(9f, 144f).toInt()}, 'wght' $weight, 'SOFT' $soft, 'WONK' 0",
    )

    fun text(size: Float, color: Int, weight: Int = 500): TextPaint =
        configure(fonts.text, size, color, "'wght' $weight")

    private fun configure(typeface: Typeface, size: Float, color: Int, variation: String): TextPaint {
        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        paint.typeface = typeface
        paint.textSize = size
        paint.color = color
        runCatching { paint.fontVariationSettings = variation }
        return paint
    }

    /** Largest size in [minSize]..[maxSize] at which [text] fits [maxWidth] (binary search). */
    fun fitSize(text: String, maxWidth: Float, minSize: Float, maxSize: Float, factory: (Float) -> TextPaint): Float {
        if (maxWidth <= 0f) return minSize
        var lo = minSize
        var hi = maxSize
        if (factory(hi).measureText(text) <= maxWidth) return hi
        repeat(10) {
            val mid = (lo + hi) / 2
            if (factory(mid).measureText(text) <= maxWidth) lo = mid else hi = mid
        }
        return lo
    }

    companion object {
        /** Draws single-line text, ellipsized to [maxWidth]; returns the drawn width. */
        fun draw(
            canvas: Canvas,
            text: String,
            x: Float,
            baseline: Float,
            paint: TextPaint,
            maxWidth: Float = Float.MAX_VALUE,
            align: Paint.Align = Paint.Align.LEFT,
            shadow: Boolean = false,
        ): Float {
            val shown = if (paint.measureText(text) > maxWidth) {
                TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
            } else {
                text
            }
            val width = paint.measureText(shown)
            val left = when (align) {
                Paint.Align.LEFT -> x
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT -> x - width
            }
            if (shadow) paint.setShadowLayer(min(paint.textSize * 0.18f, 3f), 0f, min(paint.textSize * 0.05f, 1f), 0x66000000)
            canvas.drawText(shown, left, baseline, paint)
            if (shadow) paint.clearShadowLayer()
            return width
        }

        /** Cap height ≈ 0.7 × size: used to vertically centre numerals optically. */
        fun capHeight(paint: TextPaint): Float = -paint.fontMetrics.ascent * 0.72f
    }
}
