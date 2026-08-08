package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.quire.calendar.core.Accent
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens

/**
 * The ordinary Android bottom bar: five fixed targets, icon over label, always
 * in the same place. Everything above it is bespoke; this is not, on purpose —
 * it is the part of the screen a thumb reaches for without looking, and a
 * gesture that has to be learned belongs anywhere but here.
 *
 * The only liberty taken is the tap: the icon dips and springs back, which the
 * rest of the app does too.
 */
class BottomBar(context: Context) : LinearLayout(context) {

    class Item(val id: Int, val label: String, val iconRes: Int)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val rulePaint = Paint()
    private val cells = ArrayList<Cell>()

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) {
            field = value
            setBackgroundColor(value.surface)
            cells.forEach { it.paint(value, activeId) }
            invalidate()
        }

    var motion: MotionProfile = MotionProfile.STANDARD
    var haptics = true
    var onPick: ((Int) -> Unit)? = null

    private var activeId: Int? = null

    /** Height of the bar itself, before the gesture inset underneath it. */
    val contentHeight: Int = dp(58f)

    private var bottomInset = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        isClickable = true
        setBackgroundColor(palette.surface)
    }

    fun applyBottomInset(inset: Int) {
        if (bottomInset == inset) return
        bottomInset = inset
        setPadding(0, 0, 0, inset)
        requestLayout()
    }

    /** Total space the bar takes from the world above it. */
    fun occupiedHeight(): Int = contentHeight + bottomInset

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(occupiedHeight(), MeasureSpec.EXACTLY),
        )
    }

    fun setItems(items: List<Item>) {
        removeAllViews()
        cells.clear()
        items.forEach { item ->
            val cell = Cell(context, item)
            cells += cell
            addView(cell, LayoutParams(0, contentHeight, 1f))
        }
        cells.forEach { it.paint(palette, activeId) }
    }

    /** Marks the entry that describes where the app currently is, if any. */
    fun setActive(id: Int?) {
        if (activeId == id) return
        activeId = id
        cells.forEach { it.paint(palette, activeId) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rulePaint.color = palette.hairline
        val hairline = maxOf(1f, Math.round(density * 0.5f).toFloat())
        canvas.drawRect(0f, 0f, width.toFloat(), hairline, rulePaint)
    }

    private inner class Cell(context: Context, val item: Item) : LinearLayout(context) {

        private val icon = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, item.iconRes))
            layoutParams = LayoutParams(dp(22f), dp(22f))
        }
        private val label = TextView(context).apply {
            text = item.label.uppercase()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            letterSpacing = 0.09f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(5f) }
        }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(icon)
            addView(label)
            val out = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            contentDescription = item.label
            setOnClickListener {
                if (haptics) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                dip()
                onPick?.invoke(item.id)
            }
        }

        private val dipSpring = Spring(1f, 1f)
        private val ticker = Ticker(this) { dt ->
            val moving = dipSpring.advance(dt)
            icon.scaleX = dipSpring.value
            icon.scaleY = dipSpring.value
            moving
        }

        /**
         * Driven by our own ticker: ViewPropertyAnimator would be scaled to
         * nothing on a phone with system animations turned down.
         */
        private fun dip() {
            if (motion.instant) return
            dipSpring.profile(motion)
            dipSpring.snapTo(0.74f)
            dipSpring.target = 1f
            ticker.kick()
        }

        fun paint(palette: Palette, activeId: Int?) {
            val active = activeId == item.id
            val tint = if (active) palette.accent else palette.inkMuted
            icon.setColorFilter(tint)
            label.setTextColor(if (active) palette.accent else palette.inkFaint)
            label.typeface = Typeface.create(
                if (active) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL,
            )
        }
    }
}
