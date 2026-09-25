package app.rosa.weather.widget.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import app.rosa.weather.core.designsystem.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Typefaces for Canvas rendering, the same pair as the app: Cormorant Garamond (with lining
 * figures) carries the big numerals, Manrope everything else, with full Cyrillic support.
 */
class WidgetFonts(val numerals: Typeface, val text: Typeface) {
    private val variations = ConcurrentHashMap<Pair<Typeface, String>, Typeface>()

    /**
     * [base] at the axis values of [variation], made once. A paint's own variation settings build
     * a new typeface on every call — about a millisecond per label, dozens of labels per widget.
     */
    fun varied(base: Typeface, variation: String): Typeface = variations.getOrPut(base to variation) {
        val probe = TextPaint()
        probe.typeface = base
        runCatching { probe.fontVariationSettings = variation }
        probe.typeface ?: base
    }

    companion object {
        @Volatile private var cached: WidgetFonts? = null

        fun get(context: Context): WidgetFonts = cached ?: synchronized(this) {
            cached ?: WidgetFonts(
                numerals = runCatching { context.resources.getFont(R.font.cormorant) }.getOrDefault(Typeface.SERIF),
                text = runCatching { context.resources.getFont(R.font.manrope) }.getOrDefault(Typeface.SANS_SERIF),
            ).also { cached = it }
        }
    }
}

/** Reusable text paints with variable-font settings and safe fitting helpers. */
class WidgetType(private val fonts: WidgetFonts) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    /**
     * Numeral paint. A touch heavier than the app's hero (Light): widgets sit on any wallpaper and
     * are often small, where Garamond hairlines would thin out.
     */
    fun numerals(size: Float, color: Int, weight: Int = 450): TextPaint = configure(
        typeface = fonts.numerals,
        size = size,
        color = color,
        variation = "'wght' $weight",
        features = "'lnum'",
    )

    fun text(size: Float, color: Int, weight: Int = 500): TextPaint =
        configure(fonts.text, size, color, "'wght' $weight")

    private fun configure(typeface: Typeface, size: Float, color: Int, variation: String, features: String? = null): TextPaint {
        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
        paint.typeface = fonts.varied(typeface, variation)
        paint.textSize = size
        paint.color = color
        paint.fontFeatureSettings = features
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
