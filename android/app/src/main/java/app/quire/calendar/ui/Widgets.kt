package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring

/**
 * A switch that is a line and a disc, not a pill of Material chrome. Off it is
 * an outline; on it fills with the accent. The knob travels on the same easing
 * as the month selection so the whole app moves with one hand.
 */
class ToggleView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val phaseSpring = Spring(0f)
    private var lastFrameNanos = 0L
    private val phase: Float get() = phaseSpring.value

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) { field = value; phaseSpring.profile(value); invalidate() }

    private var checkedState = false

    var checked: Boolean
        get() = checkedState
        set(value) {
            if (checkedState == value) return
            checkedState = value
            phaseSpring.target = if (value) 1f else 0f
            if (motion.instant) phaseSpring.snapTo(phaseSpring.target)
            lastFrameNanos = 0L
            invalidate()
        }

    init {
        isClickable = false
        isFocusable = false
    }

    /** Initial state, without the travel. */
    fun setCheckedImmediately(value: Boolean) {
        checkedState = value
        phaseSpring.snapTo(if (value) 1f else 0f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(38f).toInt(), dp(22f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        val moving = phaseSpring.advance(dt.coerceIn(0f, 0.064f))

        val inset = dp(0.75f)
        bounds.set(inset, inset, width - inset, height - inset)
        val radius = bounds.height() / 2f

        trackPaint.style = Paint.Style.FILL
        trackPaint.color = Tokens.withAlpha(palette.accent, phase)
        canvas.drawRoundRect(bounds, radius, radius, trackPaint)

        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = dp(1.2f)
        trackPaint.color = Tokens.withAlpha(palette.hairlineStrong, 1f - phase)
        canvas.drawRoundRect(bounds, radius, radius, trackPaint)

        val knobRadius = radius - dp(4f)
        val travel = bounds.width() - 2 * (knobRadius + dp(4f))
        val cx = bounds.left + knobRadius + dp(4f) + travel * phase
        knobPaint.color = if (phase > 0.5f) palette.onAccent else palette.inkFaint
        canvas.drawCircle(cx, bounds.centerY(), knobRadius, knobPaint)

        if (moving) postInvalidateOnAnimation() else lastFrameNanos = 0L
    }
}

/** One accent swatch. Selected, it gains a ring rather than a checkmark. */
class AccentDotView(context: Context, val accent: Accent) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var active: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(30f).toInt(), dp(30f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        fill.color = if (palette.dark) accent.dark else accent.light
        canvas.drawCircle(cx, cy, dp(8f), fill)
        if (active) {
            ring.color = palette.ink
            canvas.drawCircle(cx, cy, dp(12.5f), ring)
        }
    }
}
