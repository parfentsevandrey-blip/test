package app.rosa.weather.widget.render.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frosted glass's view of what lies behind it: the scene painted small and blurred. Launchers
 * give widgets no blur, so it is baked into the picture: [draw] paints into a bitmap of [pxPerDp]
 * pixels per dp, three box-blur passes smooth it into a near-Gaussian veil, and the caller draws it
 * back scaled up (bilinear filtering finishes the job).
 */
internal object Frost {
    fun render(width: Float, height: Float, pxPerDp: Float, radius: Int, draw: (Canvas) -> Unit): Bitmap {
        val w = max(4, (width * pxPerDp).roundToInt())
        val h = max(4, (height * pxPerDp).roundToInt())
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.scale(w / width, h / height)
        draw(canvas)
        val pixels = IntArray(w * h)
        val scratch = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            pass(pixels, scratch, w, h, radius, horizontal = true)
            pass(scratch, pixels, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /** One box-blur pass of [radius] along rows or columns, from [src] into [dst]; edges clamp. */
    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val k = i.coerceIn(0, length - 1)
                return if (horizontal) src[line * w + k] else src[k * w + line]
            }
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val p = at(i)
                a += p ushr 24
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                val out = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                val gone = at(i - radius)
                val come = at(i + radius + 1)
                a += (come ushr 24) - (gone ushr 24)
                r += (come shr 16 and 0xFF) - (gone shr 16 and 0xFF)
                g += (come shr 8 and 0xFF) - (gone shr 8 and 0xFF)
                b += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }
}
