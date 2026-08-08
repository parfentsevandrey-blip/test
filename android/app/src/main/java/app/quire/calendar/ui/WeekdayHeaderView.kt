package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import app.quire.calendar.core.Accent
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import java.time.DayOfWeek
import java.util.Locale

/**
 * The column headers sit outside the pager so they never slide with the month.
 * Small caps, widely tracked, low contrast — a label, not a row of the table.
 */
class WeekdayHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        textSize = dp(10f)
        letterSpacing = 0.16f
    }
    private val rulePaint = Paint()

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; invalidate() }

    var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
        set(value) { field = value; rebuild() }

    var dimWeekends = true
        set(value) { field = value; invalidate() }

    var weekNumbers = false
        set(value) { field = value; invalidate() }

    private var labels: List<String> = emptyList()
    private var weekend: List<Boolean> = emptyList()

    init { rebuild() }

    private fun rebuild() {
        val locale = Locale.getDefault()
        labels = MonthModel.weekdayLabels(firstDayOfWeek, locale)
        weekend = MonthModel.weekdayOrder(firstDayOfWeek).map { MonthModel.isWeekend(it) }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            dp(26f).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (labels.isEmpty()) return
        val gutter = if (weekNumbers) dp(20f) else 0f
        val cellW = (width - gutter) / MonthModel.COLUMNS
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        for (i in labels.indices) {
            textPaint.color = if (dimWeekends && weekend[i]) palette.inkGhost else palette.inkFaint
            // letterSpacing shifts the centre by half a tracking unit; pull it back.
            val nudge = textPaint.textSize * textPaint.letterSpacing / 2f
            canvas.drawText(labels[i], gutter + (i + 0.5f) * cellW - nudge, baseline, textPaint)
        }
        val hairline = maxOf(1f, Math.round(density * 0.5f).toFloat())
        rulePaint.color = palette.hairline
        canvas.drawRect(0f, height - hairline, width.toFloat(), height.toFloat(), rulePaint)
    }
}
