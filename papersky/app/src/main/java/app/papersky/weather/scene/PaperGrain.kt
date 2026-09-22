package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * A tileable paper texture: fine speckle plus a few long cellulose fibres. Generated once per
 * process; shared by the app scene, UI cards and widget bitmaps.
 */
object PaperGrain {
    private const val SIZE = 160

    val tile: Bitmap by lazy { generate() }

    fun shader(): BitmapShader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    private fun generate(): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SIZE * SIZE)
        // Two octaves of value noise around mid-grey; alpha carries the strength.
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val fine = rand(x + y * SIZE, 11)
                val coarse = valueNoise(x / 10f, y / 10f)
                val v = (0.62f * fine + 0.38f * coarse)
                val grey = (v * 255).toInt().coerceIn(0, 255)
                val alpha = (40 + kotlin.math.abs(v - 0.5f) * 180).toInt().coerceIn(0, 255)
                pixels[y * SIZE + x] = Color.argb(alpha, grey, grey, grey)
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
        repeat(26) { i ->
            val x0 = rand(i, 3) * SIZE
            val y0 = rand(i, 5) * SIZE
            val angle = rand(i, 7) * Math.PI * 2
            val len = 12 + rand(i, 9) * 40
            val bend = (rand(i, 13) - 0.5f) * 14
            val light = rand(i, 17) > 0.5f
            paint.color = if (light) Color.argb(70, 255, 255, 255) else Color.argb(46, 60, 50, 40)
            paint.strokeWidth = 0.6f + rand(i, 19) * 0.9f
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

    private fun valueNoise(x: Float, y: Float): Float {
        val period = SIZE / 10
        val xi = kotlin.math.floor(x).toInt()
        val yi = kotlin.math.floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        fun h(ix: Int, iy: Int) = rand(((ix % period) + period) % period + (((iy % period) + period) % period) * 97, 23)
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * sx
        val b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * sx
        return a + (b - a) * sy
    }
}
