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
 * Real-world material textures (DESIGN_DOCTRINE §3), generated procedurally once per process.
 *
 * Each texture is a seamless 256 px tile of *detail only* — pale and dark specks, fibres,
 * weave, granules — encoded in alpha, so it can be laid over a material's base colour (which the
 * light model tints) with plain source-over blending.
 */
object MaterialTextures {
    const val SIZE = 256

    /** Cotton rag paper: long fibres, fine speckle, cloudy density. */
    val cotton: Bitmap by lazy { detail(seed = 11, mottle = 0.05f, grain = 0.05f, fibres = 70, fibreLight = 0.2f, fibreDark = 0.07f) }

    /** Kraft paper: warm mottling, short dark fibres, specks. */
    val kraft: Bitmap by lazy {
        detail(seed = 23, mottle = 0.1f, grain = 0.07f, fibres = 160, fibreLight = 0.12f, fibreDark = 0.18f).also { specks(it, 29, 700, 0.35f, 0.18f) }
    }

    /** Chipboard: pressed pulp with coloured flecks. */
    val chipboard: Bitmap by lazy {
        detail(seed = 37, mottle = 0.08f, grain = 0.09f, fibres = 90, fibreLight = 0.1f, fibreDark = 0.12f).also { flecks(it, 41, 1400) }
    }

    /** Cork: granules and pores. */
    val cork: Bitmap by lazy { cork() }

    /** Linen: a loose plain weave with uneven threads. */
    val linen: Bitmap by lazy { linen() }

    /** Ice crystals growing from the top-left corner; mirror it for the other corners. */
    val frost: Bitmap by lazy { frost() }

    fun shader(bitmap: Bitmap): BitmapShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    /** Touch every texture so the first frame that needs them doesn't pay for generation. */
    fun warmUp() {
        cotton; kraft; chipboard; cork; linen; frost
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
            paint.strokeWidth = 0.5f + rand(i, seed + 19) * 0.9f
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

    private fun specks(bmp: Bitmap, seed: Int, count: Int, darkAlpha: Float, lightAlpha: Float) {
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(count) { i ->
            val dark = rand(i, seed) > 0.3f
            paint.color = if (dark) Color.argb((darkAlpha * 255 * (0.4f + 0.6f * rand(i, seed + 1))).toInt(), 70, 44, 22)
            else Color.argb((lightAlpha * 255).toInt(), 255, 248, 230)
            val r = 0.4f + rand(i, seed + 2) * 1.1f
            wrapped(rand(i, seed + 3) * SIZE, rand(i, seed + 4) * SIZE, r) { x, y -> canvas.drawCircle(x, y, r, paint) }
        }
    }

    private fun flecks(bmp: Bitmap, seed: Int, count: Int) {
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colors = intArrayOf(
            Color.argb(150, 64, 48, 36), Color.argb(120, 110, 92, 70), Color.argb(130, 238, 230, 214),
            Color.argb(110, 90, 96, 104), Color.argb(90, 150, 80, 60), Color.argb(80, 80, 100, 140),
        )
        repeat(count) { i ->
            paint.color = colors[(rand(i, seed) * colors.size).toInt().coerceAtMost(colors.size - 1)]
            val w = 0.6f + rand(i, seed + 1) * 2.4f
            val h = 0.5f + rand(i, seed + 2) * 1.2f
            val a = rand(i, seed + 3) * 180f
            wrapped(rand(i, seed + 4) * SIZE, rand(i, seed + 5) * SIZE, w) { x, y ->
                canvas.save()
                canvas.rotate(a, x, y)
                canvas.drawOval(x - w, y - h, x + w, y + h, paint)
                canvas.restore()
            }
        }
    }

    private fun cork(): Bitmap {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            px[y * SIZE + x] = shade((fbm(x, y, 32, 32, 3, 51) - 0.5f) * 0.16f + (rand(x + y * SIZE, 53) - 0.5f) * 0.08f)
        }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(1100) { i ->
            val r = 1.2f + rand(i, 57) * 3.8f
            val light = rand(i, 59) > 0.55f
            paint.color = if (light) Color.argb((40 + rand(i, 61) * 70).toInt(), 255, 236, 205) else Color.argb((40 + rand(i, 61) * 90).toInt(), 70, 40, 18)
            wrapped(rand(i, 63) * SIZE, rand(i, 65) * SIZE, r) { x, y ->
                canvas.drawOval(x - r, y - r * (0.6f + 0.4f * rand(i, 67)), x + r, y + r * (0.6f + 0.4f * rand(i, 67)), paint)
            }
        }
        // Pores.
        paint.color = Color.argb(120, 40, 22, 10)
        repeat(260) { i ->
            val r = 0.5f + rand(i, 71) * 1f
            wrapped(rand(i, 73) * SIZE, rand(i, 75) * SIZE, r) { x, y -> canvas.drawCircle(x, y, r, paint) }
        }
        return bmp
    }

    private fun linen(): Bitmap {
        val px = IntArray(SIZE * SIZE)
        val cols = FloatArray(SIZE) { rand(it, 81) - 0.5f }
        val rows = FloatArray(SIZE) { rand(it, 83) - 0.5f }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            // Threads 2 px wide; over/under alternates in a plain weave.
            val cx = x / 2
            val cy = y / 2
            val warpOnTop = (cx + cy) % 2 == 0
            val thread = if (warpOnTop) cols[cx * 2 % SIZE] * 0.9f + 0.12f else rows[cy * 2 % SIZE] * 0.9f - 0.06f
            val slub = (fbm(x, y, 16, 128, 2, 85) - 0.5f) * 0.25f
            val fine = (rand(x + y * SIZE, 87) - 0.5f) * 0.1f
            px[y * SIZE + x] = shade((thread + slub + fine) * 0.22f)
        }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return bmp
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

    private inline fun wrapped(x: Float, y: Float, r: Float, draw: (Float, Float) -> Unit) {
        draw(x, y)
        val nearX = x < r || x > SIZE - r
        val nearY = y < r || y > SIZE - r
        if (nearX) draw(if (x < r) x + SIZE else x - SIZE, y)
        if (nearY) draw(x, if (y < r) y + SIZE else y - SIZE)
        if (nearX && nearY) draw(if (x < r) x + SIZE else x - SIZE, if (y < r) y + SIZE else y - SIZE)
    }

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
