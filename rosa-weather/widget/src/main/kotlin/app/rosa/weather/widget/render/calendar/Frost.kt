package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frosted glass's view of what lies behind it: a painting made small and blurred. Launchers give
 * widgets no blur, so it is baked into the picture: [of] halves the painting down to [targetPxPerDp]
 * pixels per dp (averaging, never skipping pixels), three box-blur passes smooth it into a
 * near-Gaussian veil, and the caller draws it back scaled up (bilinear filtering finishes the job).
 */
internal object Frost {
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun of(source: Bitmap, sourcePxPerDp: Float, targetPxPerDp: Float, radius: Int): Bitmap {
        var current = source
        var scale = sourcePxPerDp
        while (scale / 2f >= targetPxPerDp && current.width >= 8 && current.height >= 8) {
            val half = createBitmap(max(1, current.width / 2), max(1, current.height / 2))
            Canvas(half).drawBitmap(current, null, RectF(0f, 0f, half.width.toFloat(), half.height.toFloat()), filter)
            if (current !== source) current.recycle()
            current = half
            scale /= 2f
        }
        val w = max(4, (source.width * targetPxPerDp / sourcePxPerDp).roundToInt())
        val h = max(4, (source.height * targetPxPerDp / sourcePxPerDp).roundToInt())
        val small = createBitmap(w, h)
        Canvas(small).drawBitmap(current, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), filter)
        if (current !== source) current.recycle()
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        Blur.box(pixels, w, h, radius)
        small.setPixels(pixels, 0, w, 0, 0, w, h)
        return small
    }
}
