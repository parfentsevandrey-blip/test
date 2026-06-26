package com.monthcalendar.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.glance.ImageProvider

/**
 * Builds gradient bitmaps used as premium widget backgrounds. Glance can't draw
 * Compose gradients, so we render a tall, thin bitmap and let it stretch under
 * a rounded-corner clip.
 */
object WidgetGradient {

    private const val W = 12
    private const val H = 800

    fun vertical(top: Int, bottom: Int): ImageProvider =
        provider(intArrayOf(top, bottom), null)

    fun vertical(top: Int, middle: Int, bottom: Int): ImageProvider =
        provider(intArrayOf(top, middle, bottom), floatArrayOf(0f, 0.55f, 1f))

    /** Per-channel blend of two ARGB colours (t = weight of [b]). */
    fun blend(a: Int, b: Int, t: Float): Int {
        val ia = a; val ib = b
        fun ch(shift: Int): Int {
            val ca = (ia shr shift) and 0xFF
            val cb = (ib shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun provider(colors: IntArray, positions: FloatArray?): ImageProvider {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                colors, positions, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        return ImageProvider(bmp)
    }
}
