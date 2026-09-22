package app.papersky.weather.scene

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Tiny pre-rendered sprites for soft particles. Drawing a cached bitmap is the cheapest thing a
 * GPU can do, so snowflakes, motes, fireflies and drops on the glass cost almost nothing per
 * frame. All are white and tinted at draw time.
 */
object SceneSprites {
    private const val SIZE = 64

    /** Gaussian-ish dot: soft snowflakes and dust motes. */
    val soft: Bitmap by lazy {
        radial(floatArrayOf(0f, 0.35f, 0.7f, 1f), intArrayOf(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF))
    }

    /** Bright core with a wide halo: fireflies and bright stars. */
    val glow: Bitmap by lazy {
        radial(floatArrayOf(0f, 0.12f, 0.3f, 1f), intArrayOf(0xFFFFFFFF.toInt(), 0xEEFFFFFF.toInt(), 0x44FFFFFF, 0x00FFFFFF))
    }

    /** A four-point glint for the brightest stars. */
    val glint: Bitmap by lazy {
        val bmp = radial(floatArrayOf(0f, 0.1f, 0.25f, 0.6f), intArrayOf(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF))
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = SIZE / 2f
        for (vertical in listOf(false, true)) {
            p.shader = if (vertical) {
                android.graphics.LinearGradient(0f, 0f, 0f, SIZE.toFloat(), intArrayOf(0, 0xFFFFFFFF.toInt(), 0), null, Shader.TileMode.CLAMP)
            } else {
                android.graphics.LinearGradient(0f, 0f, SIZE.toFloat(), 0f, intArrayOf(0, 0xFFFFFFFF.toInt(), 0), null, Shader.TileMode.CLAMP)
            }
            if (vertical) c.drawRect(half - 0.9f, 0f, half + 0.9f, SIZE.toFloat(), p) else c.drawRect(0f, half - 0.9f, SIZE.toFloat(), half + 0.9f, p)
        }
        bmp
    }

    /** A raindrop resting on the glass in front of the scene: dark rim below, a bright glint above. */
    val drop: Bitmap by lazy {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size * 0.42f
        val cx = size / 2f
        val cy = size / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Body: faint lens that lightens what's behind it.
        p.shader = RadialGradient(cx, cy - r * 0.25f, r * 1.05f, intArrayOf(0x30FFFFFF, 0x14FFFFFF, 0x38000000), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        // Refracted light pooled at the bottom edge.
        p.shader = RadialGradient(cx, cy + r * 0.55f, r * 0.6f, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - r * 0.7f, cy + r * 0.2f, cx + r * 0.7f, cy + r * 0.9f), p)
        // Specular glint.
        p.shader = null
        p.color = 0xE6FFFFFF.toInt()
        c.drawOval(RectF(cx - r * 0.42f, cy - r * 0.62f, cx - r * 0.08f, cy - r * 0.36f), p)
        // Rim.
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.025f
        p.color = 0x40000000
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 20f, 140f, false, p)
        bmp
    }

    private fun radial(stops: FloatArray, colors: IntArray): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(SIZE / 2f, SIZE / 2f, SIZE / 2f, colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
        return bmp
    }
}

/** A paint that tints white sprites, re-creating its colour filter only when the colour changes. */
internal class SpritePaint {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var tint = 0
    private val src = android.graphics.Rect()
    private val dst = RectF()

    fun tint(color: Int, alpha: Float) {
        val opaque = color or 0xFF000000.toInt()
        if (opaque != tint || paint.colorFilter == null) {
            tint = opaque
            paint.colorFilter = BlendModeColorFilter(opaque, BlendMode.SRC_IN)
        }
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }

    fun draw(canvas: Canvas, sprite: Bitmap, cx: Float, cy: Float, radius: Float) {
        src.set(0, 0, sprite.width, sprite.height)
        dst.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawBitmap(sprite, src, dst, paint)
    }
}
