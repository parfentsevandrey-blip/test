package app.quire.engine.fx

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt

/** Draws soft light without a mask filter per call: concentric low-alpha rings, cached radii. */
class Glow {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Radii as a fraction of the call's radius, and the cover each ring's band should reach
    // once everything outside it has been laid down. Neither depends on the call, so both are
    // built once here and only scaled at draw time.
    private val radii = FloatArray(RINGS)
    private val profile = FloatArray(RINGS)

    private val ringAlpha = FloatArray(RINGS)
    private var builtFor = -1f

    init {
        var i = 0
        while (i < RINGS) {
            val outer = 1f - i / RINGS.toFloat()
            val inner = 1f - (i + 1) / RINGS.toFloat()
            radii[i] = outer
            // The band is sampled at its middle rather than its edge, so the stack of rings
            // straddles the ideal falloff instead of sitting consistently inside or outside it.
            val mid = (outer + inner) * 0.5f
            val k = 1f - mid * mid
            profile[i] = k * k
            i++
        }
    }

    /**
     * Lays [colour] down as light centred on [cx], [cy], reaching [radius], at 0..1 [strength].
     * The alpha of [colour] scales the result, so a translucent colour gives a fainter light.
     */
    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        @ColorInt colour: Int,
        strength: Float,
    ) {
        // Positive tests, so a NaN radius or strength draws nothing instead of a black disc.
        if (!(radius > 0f)) return
        val weight = strength.coerceIn(0f, 1f)
        val source = (colour ushr 24) and 0xFF
        if (!(weight > 0f) || source == 0) return
        // The colour's alpha joins the target the rings build up to, rather than scaling each
        // ring: eight half-alpha rings stacked still reach four fifths, so scaling them one by
        // one would make a half-transparent colour four fifths as bright instead of half.
        val target = weight * (source / 255f)
        if (target != builtFor) {
            rebuild(target)
            builtFor = target
        }
        val rgb = colour and 0x00FFFFFF
        var i = 0
        while (i < RINGS) {
            val a = (ringAlpha[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
            if (a > 0) {
                paint.color = rgb or (a shl 24)
                canvas.drawCircle(cx, cy, radii[i] * radius, paint)
            }
            i++
        }
    }

    private fun rebuild(strength: Float) {
        var covered = 0f
        var i = 0
        while (i < RINGS) {
            // Rings paint outside in, over one another, so each only has to add the difference
            // between the cover already there and the cover its band is supposed to reach.
            val target = profile[i] * strength
            val add = if (covered >= 1f) 0f else ((target - covered) / (1f - covered))
            val clamped = add.coerceIn(0f, 1f)
            ringAlpha[i] = clamped
            covered += clamped * (1f - covered)
            i++
        }
    }

    private companion object {
        // Eight is where the banding between rings drops below one step of an 8-bit channel
        // for the strengths this is used at.
        const val RINGS = 8
    }
}
