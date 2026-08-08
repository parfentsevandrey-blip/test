package app.quire.calendar.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import app.quire.calendar.core.Accent
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.min

/**
 * One month, drawn rather than assembled.
 *
 * Every number is placed by hand: tabular figures so columns of digits do not
 * shimmer between months, hairlines snapped to whole pixels, one filled disc for
 * today and one for the selection. There is no elevation, no ripple and no
 * rounded container — the horizontal rules carry the structure, the way a
 * printed calendar does.
 */
class MonthGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** Half of the user's font preference, clamped: the grid must still fit. */
    private val fontScale =
        (1f + (resources.configuration.fontScale - 1f) * 0.5f).coerceIn(0.9f, 1.3f)

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint()
    private val weekNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    private val mediumFace: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val regularFace: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var month: YearMonth = YearMonth.now()
        set(value) { field = value; rebuild() }

    var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
        set(value) { field = value; rebuild() }

    var loads: Map<LocalDate, DayLoad> = emptyMap()
        set(value) { field = value; invalidate() }

    var today: LocalDate = LocalDate.now()
        set(value) { field = value; invalidate() }

    var selected: LocalDate? = null
        set(value) {
            if (field == value) return
            field = value
            if (!compact) animateSelection()
            invalidate()
        }

    var showAdjacent = true
        set(value) { field = value; invalidate() }

    var dimWeekends = true
        set(value) { field = value; invalidate() }

    var weekNumbers = false
        set(value) { field = value; requestLayout(); invalidate() }

    var colouredDots = true
        set(value) { field = value; invalidate() }

    /** Year-view mode: numbers only, no rules, no dots, no interaction chrome. */
    var compact = false
        set(value) { field = value; invalidate() }

    var onDayClick: ((LocalDate) -> Unit)? = null

    private var cells: List<LocalDate> = MonthModel.cells(month, firstDayOfWeek)
    private var pressed: LocalDate? = null
    private var selectionPhase = 1f
    private var selectionAnimator: ValueAnimator? = null
    private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)

    private var gutter = 0f
    private var cellW = 0f
    private var rowH = 0f

    init {
        isClickable = true
        isFocusable = true
        rebuild()
    }

    private fun rebuild() {
        cells = MonthModel.cells(month, firstDayOfWeek)
        contentDescription = MonthModel.monthName(month, Locale.getDefault()) + " " + month.year
        invalidate()
    }

    private fun animateSelection() {
        selectionAnimator?.cancel()
        selectionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 170L
            interpolator = easing
            addUpdateListener { selectionPhase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gutter = if (weekNumbers && !compact) dp(20f) else 0f
        cellW = (w - gutter) / MonthModel.COLUMNS
        rowH = h.toFloat() / MonthModel.ROWS
    }

    override fun onDraw(canvas: Canvas) {
        if (cellW <= 0f || rowH <= 0f) return
        val hairline = maxOf(1f, Math.round(density * 0.5f).toFloat())

        if (!compact) {
            rulePaint.color = palette.hairline
            for (row in 1 until MonthModel.ROWS) {
                val y = Math.round(row * rowH).toFloat()
                canvas.drawRect(gutter, y, width.toFloat(), y + hairline, rulePaint)
            }
            if (weekNumbers) {
                canvas.drawRect(gutter - hairline, 0f, gutter, height.toFloat(), rulePaint)
            }
        }

        val numberSize = if (compact) {
            min(rowH * 0.44f, cellW * 0.52f).coerceIn(dp(7f), dp(11f))
        } else {
            min(rowH * 0.30f, cellW * 0.36f).coerceIn(dp(11f), dp(15.5f))
        }
        // Forty-two cells cannot follow the system font scale outright, so they
        // follow half of it and stop before the numbers touch the rules.
        dayPaint.textSize = (numberSize * fontScale)
            .coerceAtMost(min(rowH * 0.40f, cellW * 0.46f))

        // The disc, the gap and the dots have to clear the rule under the row;
        // 0.35 of the row height leaves the stack breathing at both ends.
        val markerRadius = if (compact) {
            min(rowH, cellW) * 0.38f
        } else {
            min(rowH * 0.35f, cellW * 0.40f).coerceAtMost(dp(17.5f))
        }
        val dotRadius = dp(1.9f)
        val dotGap = dp(3.4f)
        val stackGap = if (compact) 0f else dp(4.6f)
        val stackHeight = 2 * markerRadius + if (compact) 0f else stackGap + 2 * dotRadius

        if (weekNumbers && !compact) {
            weekNumberPaint.textSize = dp(9f)
            weekNumberPaint.color = palette.inkGhost
        }

        for (index in cells.indices) {
            val date = cells[index]
            val row = index / MonthModel.COLUMNS
            val column = index % MonthModel.COLUMNS
            val inMonth = date.month == month.month && date.year == month.year
            if (!inMonth && !showAdjacent) continue

            val cx = gutter + (column + 0.5f) * cellW
            val rowTop = row * rowH
            val stackTop = rowTop + (rowH - stackHeight) / 2f
            val cy = stackTop + markerRadius

            if (column == 0 && weekNumbers && !compact) {
                val week = MonthModel.weekOfYear(date, Locale.getDefault())
                canvas.drawText(
                    week.toString(),
                    gutter / 2f,
                    cy - (weekNumberPaint.descent() + weekNumberPaint.ascent()) / 2f,
                    weekNumberPaint,
                )
            }

            val isToday = date == today
            val isSelected = date == selected
            val isPressed = date == pressed

            if (isPressed && !isSelected) {
                markerPaint.color = palette.press
                canvas.drawCircle(cx, cy, markerRadius, markerPaint)
            }

            var textColour = when {
                !inMonth -> palette.inkGhost
                isToday -> palette.accent
                dimWeekends && MonthModel.isWeekend(date.dayOfWeek) -> palette.inkMuted
                else -> palette.ink
            }
            dayPaint.typeface = if (isToday || isSelected) mediumFace else regularFace

            if (isSelected) {
                val phase = easing.getInterpolation(selectionPhase)
                markerPaint.color = if (isToday) palette.accent else palette.ink
                canvas.drawCircle(cx, cy, markerRadius * (0.86f + 0.14f * phase), markerPaint)
                textColour = if (isToday) palette.onAccent else palette.canvas
            } else if (isToday && compact) {
                markerPaint.color = palette.accent
                canvas.drawCircle(cx, cy, markerRadius, markerPaint)
                textColour = palette.onAccent
            }

            dayPaint.color = if (!inMonth && isSelected) palette.canvas else textColour
            canvas.drawText(
                date.dayOfMonth.toString(),
                cx,
                cy - (dayPaint.descent() + dayPaint.ascent()) / 2f,
                dayPaint,
            )

            if (compact) continue

            val load = loads[date] ?: continue
            if (load.count <= 0) continue
            val visible = min(if (colouredDots) maxOf(load.colours.size, 1) else 1, 3)
                .coerceAtMost(load.count)
            val dotsWidth = visible * 2 * dotRadius + (visible - 1) * dotGap
            var dx = cx - dotsWidth / 2f + dotRadius
            val dy = stackTop + 2 * markerRadius + stackGap + dotRadius
            for (i in 0 until visible) {
                dotPaint.color = when {
                    !inMonth -> palette.inkGhost
                    colouredDots && i < load.colours.size -> load.colours[i]
                    else -> palette.inkFaint
                }
                canvas.drawCircle(dx, dy, dotRadius, dotPaint)
                dx += 2 * dotRadius + dotGap
            }
        }
    }

    private fun dateAt(x: Float, y: Float): LocalDate? {
        if (cellW <= 0f || rowH <= 0f) return null
        if (x < gutter) return null
        val column = ((x - gutter) / cellW).toInt().coerceIn(0, MonthModel.COLUMNS - 1)
        val row = (y / rowH).toInt().coerceIn(0, MonthModel.ROWS - 1)
        return cells.getOrNull(row * MonthModel.COLUMNS + column)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (compact) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = dateAt(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val next = dateAt(event.x, event.y)
                if (next != pressed) { pressed = next; invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val target = dateAt(event.x, event.y)
                pressed = null
                invalidate()
                if (target != null) {
                    performClick()
                    onDayClick?.invoke(target)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        selectionAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
