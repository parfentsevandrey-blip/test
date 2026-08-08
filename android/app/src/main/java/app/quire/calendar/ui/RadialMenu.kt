package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The app's only menu, and it has no fixed home: it blooms from wherever the
 * finger was, on an arc that always faces the middle of the screen so it never
 * opens off the edge. Hold and slide to the item and let go, or lift and tap —
 * both work, because a long-press that forces you to lift first is a menu
 * pretending to be a gesture.
 */
class RadialMenu(context: Context) : View(context) {

    class Item(val id: Int, val label: String, val iconRes: Int)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = android.graphics.BlurMaskFilter(
            dp(14f),
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.1f
    }

    private val bloom = Spring(0f, 0f)
    private var lastFrameNanos = 0L

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) { field = value; bloom.profile(value); invalidate() }

    var haptics = true
    var onPick: ((Int) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var items: List<Item> = emptyList()
    private var icons: List<Drawable?> = emptyList()
    private val positionsX = ArrayList<Float>()
    private val positionsY = ArrayList<Float>()
    private var originX = 0f
    private var originY = 0f
    private var highlighted = -1
    private var closing = false

    var safeTop = 0f
    var safeBottom = 0f

    val isOpen: Boolean get() = visibility == VISIBLE && !closing

    private val radius get() = dp(27f)

    init {
        // INVISIBLE, not GONE: a GONE view is never measured, so the first
        // open() would lay its items out against a zero-sized screen.
        visibility = INVISIBLE
    }

    fun open(x: Float, y: Float, items: List<Item>) {
        this.items = items
        icons = items.map { ContextCompat.getDrawable(context, it.iconRes) }
        originX = x
        originY = y
        closing = false
        highlighted = -1
        layoutItems()
        visibility = VISIBLE
        bloom.snapTo(if (motion.instant) 1f else 0f)
        bloom.target = 1f
        lastFrameNanos = 0L
        invalidate()
    }

    fun close() {
        if (!isOpen) return
        closing = true
        bloom.target = 0f
        if (motion.instant) bloom.snapTo(0f)
        lastFrameNanos = 0L
        invalidate()
    }

    /**
     * Clamping each item on its own would fold the fan into a heap whenever the
     * menu opens near a corner. Instead the arc is built at full size, then
     * shrunk until it can fit and slid as one piece until it does — the shape
     * survives, which is what makes the thing readable at a glance.
     */
    private fun layoutItems() {
        positionsX.clear()
        positionsY.clear()
        val n = items.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = radius + dp(10f)
        val labelRoom = dp(24f)
        // Before the first layout pass there is nothing to arrange against;
        // onSizeChanged runs this again once the real bounds arrive.
        if (w < margin * 2f || h < margin * 2f + labelRoom) return

        val toCentre = atan2(h / 2f - originY, w / 2f - originX)
        val step = Math.toRadians(40.0).toFloat()
        val sweep = step * (n - 1)
        val availableW = w - margin * 2f
        val availableH = h - safeTop - safeBottom - margin * 2f - labelRoom

        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var distance = dp(118f)
        var attempts = 0
        while (true) {
            for (i in 0 until n) {
                val angle = toCentre - sweep / 2f + step * i
                xs[i] = originX + cos(angle) * distance
                ys[i] = originY + sin(angle) * distance
            }
            val spanW = (xs.max() - xs.min())
            val spanH = (ys.max() - ys.min())
            if ((spanW <= availableW && spanH <= availableH) || attempts >= 6) break
            distance *= 0.86f
            attempts++
        }

        var shiftX = 0f
        val leftOver = margin - (xs.min() + shiftX)
        if (leftOver > 0f) shiftX += leftOver
        val rightOver = (xs.max() + shiftX) - (w - margin)
        if (rightOver > 0f) shiftX -= rightOver

        var shiftY = 0f
        val topOver = (safeTop + margin) - (ys.min() + shiftY)
        if (topOver > 0f) shiftY += topOver
        val bottomOver = (ys.max() + shiftY) - (h - safeBottom - margin - labelRoom)
        if (bottomOver > 0f) shiftY -= bottomOver

        for (i in 0 until n) {
            positionsX += xs[i] + shiftX
            positionsY += ys[i] + shiftY
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isOpen) layoutItems()
    }

    /** Called while the finger that opened the menu is still down. */
    fun trackDrag(x: Float, y: Float) {
        if (!isOpen) return
        val next = nearest(x, y)
        if (next != highlighted) {
            highlighted = next
            if (next >= 0 && haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            invalidate()
        }
    }

    fun trackRelease(x: Float, y: Float) {
        if (!isOpen) return
        val pick = nearest(x, y)
        if (pick >= 0) {
            if (haptics) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onPick?.invoke(items[pick].id)
        } else {
            onDismiss?.invoke()
        }
        close()
    }

    private fun nearest(x: Float, y: Float): Int {
        var best = -1
        var bestDistance = dp(56f)
        for (i in positionsX.indices) {
            val d = hypot(x - positionsX[i], y - positionsY[i])
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isOpen) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> trackDrag(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                performClick()
                trackRelease(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> {
                onDismiss?.invoke()
                close()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        val moving = bloom.advance(dt)

        val progress = bloom.value.coerceIn(0f, 1.4f)
        if (closing && !moving && progress <= 0.01f) {
            visibility = INVISIBLE
            closing = false
            lastFrameNanos = 0L
            return
        }

        scrimPaint.color = Tokens.withAlpha(
            if (palette.dark) 0xFF000000.toInt() else palette.ink,
            0.30f * progress.coerceAtMost(1f),
        )
        canvas.drawPaint(scrimPaint)

        labelPaint.textSize = dp(9.5f)
        val stagger = motion.staggerMillis / 260f

        for (i in items.indices) {
            val phase = smoothstep(i * stagger, i * stagger + 0.62f, progress)
            if (phase <= 0.004f) continue
            val cx = lerp(originX, positionsX[i], phase)
            val cy = lerp(originY, positionsY[i], phase)
            val active = i == highlighted
            val scale = lerp(0.35f, if (active) 1.14f else 1f, phase)
            val r = radius * scale

            shadowPaint.color = Tokens.withAlpha(
                if (palette.dark) 0xFF000000.toInt() else 0xFF14130F.toInt(),
                phase * if (palette.dark) 0.6f else 0.22f,
            )
            canvas.drawCircle(cx, cy + dp(3f), r, shadowPaint)

            discPaint.color = Tokens.withAlpha(
                if (active) palette.accent else palette.surface,
                phase,
            )
            canvas.drawCircle(cx, cy, r, discPaint)

            val icon = icons.getOrNull(i)
            if (icon != null) {
                val size = (r * 0.86f).toInt()
                icon.setBounds(
                    (cx - size / 2f).toInt(),
                    (cy - size / 2f).toInt(),
                    (cx + size / 2f).toInt(),
                    (cy + size / 2f).toInt(),
                )
                icon.setTint(
                    Tokens.withAlpha(if (active) palette.onAccent else palette.ink, phase),
                )
                icon.draw(canvas)
            }

            labelPaint.color = Tokens.withAlpha(
                if (active) palette.ink else palette.inkMuted,
                phase * if (active) 1f else 0.8f,
            )
            canvas.drawText(
                items[i].label.uppercase(),
                cx,
                cy + r + dp(14f),
                labelPaint,
            )
        }

        if (moving) postInvalidateOnAnimation() else lastFrameNanos = 0L
    }
}
