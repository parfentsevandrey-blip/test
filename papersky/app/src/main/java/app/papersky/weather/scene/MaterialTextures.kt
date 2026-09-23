package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The paper's own texture (DESIGN_DOCTRINE §3), generated procedurally once per process.
 *
 * A seamless 256 px tile of *detail only* — a soft cloudiness, a fine tooth and long cotton
 * fibres — encoded in alpha, so it lays over the paper's colour with plain source-over blending
 * and reads on ivory paper by day and on charcoal at night alike. No specks: good paper is clean.
 */
object MaterialTextures {
    const val SIZE = 256

    /** Cotton paper: a soft cloudiness, a fine tooth, long fibres. */
    val paper: Bitmap by lazy { detail(seed = 11, mottle = 0.05f, grain = 0.04f, fibres = 60, fibreLight = 0.16f, fibreDark = 0.06f) }

    /** Ice crystals growing from the top-left corner; mirror it for the other corners. */
    val frost: Bitmap by lazy { frost() }

    fun shader(bitmap: Bitmap): BitmapShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    /** Touch every texture so the first frame that needs them doesn't pay for generation. */
    fun warmUp() {
        paper; frost
    }

    // ---- Generators ------------------------------------------------------------------------

    private fun detail(seed: Int, mottle: Float, grain: Float, fibres: Int, fibreLight: Float, fibreDark: Float): Bitmap {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val cloudy = fbm(x, y, 64, 64, 4, seed) - 0.5f
                val fine = rand(x + y * SIZE, seed) - 0.5f
                val v = cloudy * mottle * 2f + fine * grain * 2f
                px[y * SIZE + x] = shade(v)
            }
        }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        fibres(bmp, seed + 1, fibres, fibreLight, fibreDark)
        return bmp
    }

    /** Light (v > 0) or dark (v < 0) speck with strength in alpha. */
    private fun shade(v: Float): Int {
        val a = (abs(v) * 255).toInt().coerceIn(0, 255)
        return if (v > 0) Color.argb(a, 255, 255, 255) else Color.argb(a, 52, 38, 24)
    }

    private fun fibres(bmp: Bitmap, seed: Int, count: Int, light: Float, dark: Float) {
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val path = Path()
        repeat(count) { i ->
            val x0 = rand(i, seed + 3) * SIZE
            val y0 = rand(i, seed + 5) * SIZE
            val angle = rand(i, seed + 7) * Math.PI * 2
            val len = 6 + rand(i, seed + 9) * 44
            val bend = (rand(i, seed + 13) - 0.5f) * 12
            val isLight = rand(i, seed + 17) > 0.45f
            paint.color = if (isLight) Color.argb((light * 255).toInt(), 255, 255, 255) else Color.argb((dark * 255).toInt(), 60, 42, 26)
            paint.strokeWidth = 0.4f + rand(i, seed + 19) * 0.6f
            // Drawn at every wrap offset so the tile stays seamless.
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
    }

    private fun frost(): Bitmap {
        val size = 160
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val haze = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, size * 0.7f, intArrayOf(0x88FFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), haze)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(170, 255, 255, 255) }
        fun branch(x: Float, y: Float, angle: Float, len: Float, depth: Int, salt: Int) {
            if (depth == 0 || len < 3f) return
            val ex = x + cos(angle) * len
            val ey = y + sin(angle) * len
            line.strokeWidth = 0.4f + depth * 0.35f
            line.alpha = (80 + depth * 30).coerceAtMost(220)
            canvas.drawLine(x, y, ex, ey, line)
            val n = 2 + (rand(salt, 91) * 2).toInt()
            for (k in 0 until n) {
                val t = 0.3f + 0.6f * rand(salt + k, 93)
                val side = if (k % 2 == 0) 1f else -1f
                branch(x + (ex - x) * t, y + (ey - y) * t, angle + side * (0.6f + 0.3f * rand(salt + k, 95)), len * (0.35f + 0.2f * rand(salt + k, 97)), depth - 1, salt * 7 + k)
            }
        }
        repeat(7) { i ->
            val a = (0.05f + 0.35f * i / 6f) * Math.PI.toFloat() * 1.0f
            branch(0f, 0f, a, size * (0.45f + 0.35f * rand(i, 99)), 4, i + 1)
        }
        return bmp
    }

    // ---- Noise -----------------------------------------------------------------------------

    /** Seamless fractal noise in 0..1 over the tile; [sx], [sy] are feature sizes in px. */
    private fun fbm(px: Int, py: Int, sx: Int, sy: Int, octaves: Int, seed: Int): Float {
        var v = 0f
        var amp = 0.5f
        var total = 0f
        var f = 1
        for (o in 0 until octaves) {
            val cx = sx / f
            val cy = sy / f
            if (cx < 1 || cy < 1) break
            v += amp * valueNoise(px.toFloat() / cx, py.toFloat() / cy, SIZE / cx, SIZE / cy, seed + o * 31)
            total += amp
            amp *= 0.5f
            f *= 2
        }
        return if (total > 0f) v / total else 0.5f
    }

    private fun valueNoise(x: Float, y: Float, periodX: Int, periodY: Int, seed: Int): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        fun h(ix: Int, iy: Int) = rand(((ix % periodX) + periodX) % periodX + (((iy % periodY) + periodY) % periodY) * 7919, seed)
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = h(xi, yi) + (h(xi + 1, yi) - h(xi, yi)) * sx
        val b = h(xi, yi + 1) + (h(xi + 1, yi + 1) - h(xi, yi + 1)) * sx
        return a + (b - a) * sy
    }
}
