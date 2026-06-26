package com.monthcalendar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.glance.ImageProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds gradient bitmaps used as premium widget backgrounds. Glance can't draw
 * Compose gradients, so we render a bitmap and let it stretch under the widget.
 *
 * The rounded corners are baked into the bitmap (a `drawRoundRect`) rather than
 * relying on `GlanceModifier.cornerRadius`, which is a no-op below API 31 — so
 * the surface reads as a rounded card on every supported device. Results are
 * cached per colour-set (there are only a handful of weather moods), avoiding a
 * fresh allocation + RemoteViews serialization on every render.
 */
object WidgetGradient {

    // Portrait-ish canvas keeps the corner curvature reasonable once stretched.
    private const val W = 300
    private const val H = 480
    private const val CORNER = 60f

    private val cache = ConcurrentHashMap<List<Int>, ImageProvider>()

    fun vertical(top: Int, bottom: Int): ImageProvider =
        cache.getOrPut(listOf(top, bottom)) { provider(intArrayOf(top, bottom), null) }

    fun vertical(top: Int, middle: Int, bottom: Int): ImageProvider =
        cache.getOrPut(listOf(top, middle, bottom)) { provider(intArrayOf(top, middle, bottom), floatArrayOf(0f, 0.55f, 1f)) }

    /** Per-channel blend of two ARGB colours (t = weight of [b]). */
    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun provider(colors: IntArray, positions: FloatArray?): ImageProvider {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), colors, positions, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), CORNER, CORNER, paint)
        return ImageProvider(bmp)
    }
}
