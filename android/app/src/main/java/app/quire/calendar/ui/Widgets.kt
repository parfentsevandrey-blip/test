package app.quire.calendar.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens

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
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)

    private var phase = 0f
    private var animator: ValueAnimator? = null

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    private var checkedState = false

    var checked: Boolean
        get() = checkedState
        set(value) {
            if (checkedState == value) return
            checkedState = value
            animateTo(if (value) 1f else 0f)
        }

    init {
        isClickable = false
        isFocusable = false
    }

    /** Initial state, without the travel animation. */
    fun setCheckedImmediately(value: Boolean) {
        animator?.cancel()
        checkedState = value
        phase = if (value) 1f else 0f
        invalidate()
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(phase, target).apply {
            duration = 170L
            interpolator = easing
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(38f).toInt(), dp(22f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
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
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
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
