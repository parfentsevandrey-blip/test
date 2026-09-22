package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Paper textures, generated once per process and shared by the app scene, UI cards and widget
 * bitmaps.
 *
 * Everything here composites with plain source-over blending: advanced blend modes force the GPU
 * to read back the framebuffer, which is exactly what a 120 Hz scene can't afford.
 */
object PaperGrain {
    private const val SIZE = 160

    /**
     * Fine speckle plus a few long cellulose fibres as light and dark specks with alpha, so it can
     * be laid over any colour.
     */
    val tile: Bitmap by lazy { generateGrain() }

    /** Soft, low-frequency blooms for a watercolour wash; stretch it over the sky. */
    val wash: Bitmap by lazy { generateWash() }

    fun shader(): BitmapShader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    private fun generateGrain(): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val fine = rand(x + y * SIZE, 11)
                val coarse = valueNoise(x / 10f, y / 10f, SIZE / 10)
                val v = 0.62f * fine + 0.38f * coarse - 0.5f
                // Positive → a pale speck, negative → a darker one; strength in alpha.
                val alpha = (abs(v) * 120).toInt().coerceIn(0, 255)
                pixels[y * SIZE + x] = if (v > 0) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 40, 32, 24)
            }
        }
        bmp.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        // Fibres, drawn wrapped so the tile stays seamless.
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        repeat(24) { i ->
            val x0 = rand(i, 3) * SIZE
            val y0 = rand(i, 5) * SIZE
            val angle = rand(i, 7) * Math.PI * 2
            val len = 12 + rand(i, 9) * 40
            val bend = (rand(i, 13) - 0.5f) * 14
            val light = rand(i, 17) > 0.45f
            paint.color = if (light) Color.argb(34, 255, 255, 255) else Color.argb(20, 60, 50, 40)
            paint.strokeWidth = 0.6f + rand(i, 19) * 0.8f
            for (dx in intArrayOf(-SIZE, 0, SIZE)) for (dy in intArrayOf(-SIZE, 0, SIZE)) {
                path.reset()
                val sx = x0 + dx
                val sy = y0 + dy
                val ex = sx + (cos(angle) * len).toFloat()
                val ey = sy + (sin(angle) * len).toFloat()
                path.moveTo(sx, sy)
                path.quadTo((sx + ex) / 2 + bend, (sy + ey) / 2 - bend, ex, ey)
                canvas.drawPath(path, paint)
            }
        }
        return bmp
    }

    private fun generateWash(): Bitmap {
        val w = 72
        val h = 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var v = 0f
                var amp = 0.55f
                var f = 1f / 14f
                for (o in 0 until 4) {
                    v += amp * valueNoise(x * f + o * 7.1f, y * f + o * 3.3f, 1 shl 16)
                    amp *= 0.5f
                    f *= 2.1f
                }
                val d = v / 1.03f - 0.5f
                // Pigment pools darken a touch; dry patches go paler.
                val alpha = (smoothstep(0.06f, 0.34f, abs(d)) * 22).toInt()
                pixels[y * w + x] = if (d > 0) Color.argb(alpha, 20, 18, 30) else Color.argb(alpha, 255, 255, 250)
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun valueNoise(x: Float, y: Float, period: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        fun h(ix: Int, iy: Int) = rand(((ix % period) + period) % period + (((iy % period) + period) % period) * 9973, 23)
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * sx
        val b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * sx
        return a + (b - a) * sy
    }
}
