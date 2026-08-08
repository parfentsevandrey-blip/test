package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A wheel, not a fan.
 *
 * The first version scattered discs along an arc, which meant every item had to
 * be hit precisely and the whole thing had to be folded to fit near an edge —
 * it read as spilled, not designed. A closed ring fixes both: it is one rigid
 * object that only ever has to be moved to fit, and a sector is chosen by the
 * *direction* of the finger rather than its position, so a short flick in any
 * direction picks reliably. The hole in the middle is cancel, and it is where
 * the finger already is.
 */
class WheelMenu(context: Context) : View(context) {

    class Item(val id: Int, val label: String, val iconRes: Int)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wedgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = android.graphics.BlurMaskFilter(
            dp(20f),
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.1f
    }
    private val centrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
    }

    private val ringPath = Path()
    private val wedgePath = Path()
    private val outerRect = RectF()
    private val innerRect = RectF()

    private val bloom = Spring(0f, 0f)
    private val bulge = Spring(0f, 0f)
    private var lastFrameNanos = 0L

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) {
            field = value
            bloom.profile(value)
            bulge.profile(value)
            invalidate()
        }

    var haptics = true
    var onPick: ((Int) -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    var safeTop = 0f
    var safeBottom = 0f

    private var items: List<Item> = emptyList()
    private var icons: List<Drawable?> = emptyList()
    private var centreLabel: String = ""
    private var centreX = 0f
    private var centreY = 0f
    private var outer = 0f
    private var inner = 0f
    private var highlighted = -1
    private var cancelling = false
    private var closing = false

    val isOpen: Boolean get() = visibility == VISIBLE && !closing

    init {
        // INVISIBLE, not GONE: a GONE view is never measured, and the wheel is
        // placed against its own bounds the instant it opens.
        visibility = INVISIBLE
    }

    fun open(x: Float, y: Float, items: List<Item>, centreLabel: String) {
        this.items = items
        this.centreLabel = centreLabel
        icons = items.map { ContextCompat.getDrawable(context, it.iconRes) }
        highlighted = -1
        cancelling = false
        closing = false
        place(x, y)
        visibility = VISIBLE
        bloom.snapTo(if (motion.instant) 1f else 0f)
        bloom.target = 1f
        bulge.snapTo(1f)
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
     * The ring keeps its shape and is moved as a whole; only when the screen
     * genuinely cannot hold it does it shrink.
     */
    private fun place(x: Float, y: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return
        val margin = dp(10f)
        val room = minOf(
            (w - margin * 2f) / 2f,
            (h - safeTop - safeBottom - margin * 2f) / 2f,
        )
        outer = minOf(dp(128f), room).coerceAtLeast(dp(60f))
        inner = outer * 0.40f
        centreX = x.coerceIn(margin + outer, (w - margin - outer).coerceAtLeast(margin + outer))
        centreY = y.coerceIn(
            safeTop + margin + outer,
            (h - safeBottom - margin - outer).coerceAtLeast(safeTop + margin + outer),
        )
        outerRect.set(centreX - outer, centreY - outer, centreX + outer, centreY + outer)
        innerRect.set(centreX - inner, centreY - inner, centreX + inner, centreY + inner)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isOpen) place(centreX, centreY)
    }

    // ---- selection -----------------------------------------------------

    private val sweep: Float get() = 360f / items.size.coerceAtLeast(1)

    /** Angle of the middle of sector [index], degrees, zero pointing right. */
    private fun sectorCentreDegrees(index: Int): Float = -90f + sweep * index

    private fun sectorAt(x: Float, y: Float): Int {
        if (items.isEmpty()) return -1
        val distance = hypot(x - centreX, y - centreY)
        if (distance < inner) return -1
        var degrees = Math.toDegrees(
            atan2((y - centreY).toDouble(), (x - centreX).toDouble()),
        ).toFloat()
        degrees = (degrees + 90f + sweep / 2f + 720f) % 360f
        return (degrees / sweep).toInt().coerceIn(0, items.size - 1)
    }

    fun trackDrag(x: Float, y: Float) {
        if (!isOpen) return
        val next = sectorAt(x, y)
        val nowCancelling = next < 0
        if (next != highlighted || nowCancelling != cancelling) {
            highlighted = next
            cancelling = nowCancelling
            if (next >= 0) {
                bulge.snapTo(0f)
                bulge.target = 1f
                if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            lastFrameNanos = 0L
            invalidate()
        }
    }

    fun trackRelease(x: Float, y: Float) {
        if (!isOpen) return
        val pick = sectorAt(x, y)
        if (pick >= 0) {
            if (haptics) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onPick?.invoke(items[pick].id)
        }
        close()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isOpen) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> trackDrag(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                performClick()
                trackRelease(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> close()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // ---- drawing -------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        var moving = bloom.advance(dt)
        moving = bulge.advance(dt) || moving

        val progress = bloom.value.coerceIn(0f, 1.3f)
        if (closing && !moving && progress <= 0.01f) {
            visibility = INVISIBLE
            closing = false
            highlighted = -1
            lastFrameNanos = 0L
            onClosed?.invoke()
            return
        }
        if (items.isEmpty() || outer <= 0f) return

        val shown = progress.coerceAtMost(1f)
        scrimPaint.color = Tokens.withAlpha(
            if (palette.dark) 0xFF000000.toInt() else palette.ink,
            0.44f * shown,
        )
        canvas.drawPaint(scrimPaint)

        val restore = canvas.save()
        // Arriving: the wheel scales up and unwinds the last few degrees.
        canvas.scale(lerp(0.62f, 1f, progress), lerp(0.62f, 1f, progress), centreX, centreY)
        canvas.rotate(lerp(-12f, 0f, progress), centreX, centreY)

        shadowPaint.color = Tokens.withAlpha(
            if (palette.dark) 0xFF000000.toInt() else 0xFF14130F.toInt(),
            shown * if (palette.dark) 0.6f else 0.26f,
        )
        canvas.drawCircle(centreX, centreY + dp(6f), outer, shadowPaint)

        ringPath.reset()
        ringPath.addCircle(centreX, centreY, outer, Path.Direction.CW)
        ringPath.addCircle(centreX, centreY, inner, Path.Direction.CCW)
        ringPaint.color = Tokens.withAlpha(palette.surface, shown)
        canvas.drawPath(ringPath, ringPaint)
        // The hub is filled, not a hole: the calendar showing through the middle
        // of a solid object reads as a rendering mistake.
        ringPaint.color = Tokens.withAlpha(palette.canvas, shown)
        canvas.drawCircle(centreX, centreY, inner, ringPaint)

        if (highlighted >= 0) {
            val grow = outer + dp(7f) * bulge.value
            val start = sectorCentreDegrees(highlighted) - sweep / 2f
            wedgePath.reset()
            outerRect.set(centreX - grow, centreY - grow, centreX + grow, centreY + grow)
            wedgePath.arcTo(outerRect, start, sweep, false)
            wedgePath.arcTo(innerRect, start + sweep, -sweep, false)
            wedgePath.close()
            wedgePaint.color = Tokens.withAlpha(palette.accent, shown)
            canvas.drawPath(wedgePath, wedgePaint)
            outerRect.set(centreX - outer, centreY - outer, centreX + outer, centreY + outer)
        }

        strokePaint.strokeWidth = maxOf(1f, density * 0.5f)
        strokePaint.color = Tokens.withAlpha(palette.hairlineStrong, shown)
        canvas.drawCircle(centreX, centreY, outer, strokePaint)
        canvas.drawCircle(centreX, centreY, inner, strokePaint)
        for (i in items.indices) {
            val radians = Math.toRadians((sectorCentreDegrees(i) - sweep / 2f).toDouble())
            val dx = cos(radians).toFloat()
            val dy = sin(radians).toFloat()
            canvas.drawLine(
                centreX + dx * inner,
                centreY + dy * inner,
                centreX + dx * outer,
                centreY + dy * outer,
                strokePaint,
            )
        }

        // Icon over label, stacked in screen space at the middle of each
        // sector. Laying them out along the radius instead puts the two on the
        // same line for the sectors on the flanks, where they collide.
        val bandRadius = (inner + outer) / 2f
        val labelRoom = dp(52f)
        labelPaint.textSize = dp(8.5f)
        val stagger = motion.staggerMillis / 240f

        for (i in items.indices) {
            val phase = smoothstep(i * stagger, i * stagger + 0.6f, progress)
            if (phase <= 0.004f) continue
            val active = i == highlighted
            val radians = Math.toRadians(sectorCentreDegrees(i).toDouble())
            val cx = centreX + cos(radians).toFloat() * bandRadius
            val cy = centreY + sin(radians).toFloat() * bandRadius
            val ink = if (active) palette.onAccent else palette.ink

            icons.getOrNull(i)?.let { icon ->
                val size = dp(21f) * lerp(0.8f, if (active) 1.1f else 1f, phase)
                val iconY = cy - dp(9f)
                icon.setBounds(
                    (cx - size / 2f).toInt(),
                    (iconY - size / 2f).toInt(),
                    (cx + size / 2f).toInt(),
                    (iconY + size / 2f).toInt(),
                )
                icon.setTint(Tokens.withAlpha(ink, phase * shown))
                icon.draw(canvas)
            }

            labelPaint.color = Tokens.withAlpha(
                if (active) palette.onAccent else palette.inkMuted,
                phase * shown,
            )
            canvas.drawText(
                fit(items[i].label.uppercase(), labelRoom),
                cx,
                cy + dp(17f),
                labelPaint,
            )
        }

        if (cancelling) {
            wedgePaint.color = Tokens.withAlpha(palette.press, shown)
            canvas.drawCircle(centreX, centreY, inner, wedgePaint)
        }
        centrePaint.textSize = if (cancelling) dp(11f) else dp(19f)
        centrePaint.color = Tokens.withAlpha(
            if (cancelling) palette.inkMuted else palette.ink,
            shown,
        )
        centrePaint.letterSpacing = if (cancelling) 0.12f else 0f
        canvas.drawText(
            centreLabel,
            centreX,
            centreY - (centrePaint.descent() + centrePaint.ascent()) / 2f,
            centrePaint,
        )

        canvas.restoreToCount(restore)
        if (moving) postInvalidateOnAnimation() else lastFrameNanos = 0L
    }

    private fun fit(text: String, available: Float): String {
        if (available <= 0f || labelPaint.measureText(text) <= available) return text
        var end = text.length
        while (end > 1 && labelPaint.measureText(text.substring(0, end) + "…") > available) end--
        return text.substring(0, end) + "…"
    }

    /** Exposed so the host can label the hole with whatever cancelling means. */
    fun setCentreLabel(text: String) {
        if (centreLabel == text) return
        centreLabel = text
        invalidate()
    }

    fun highlightedIndex(): Int = highlighted

    fun distanceFromCentre(x: Float, y: Float): Float = abs(hypot(x - centreX, y - centreY))
}
