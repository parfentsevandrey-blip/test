package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

/** What the grid shows, independent of how large it is drawn. */
class GridStyle(
    val showAdjacent: Boolean = true,
    val dimWeekends: Boolean = true,
    val colouredDots: Boolean = true,
    val weekNumbers: Boolean = false,
    val heat: Boolean = false,
)

/**
 * Draws one month into any rectangle at any level of detail.
 *
 * The stage needs the same month at a dozen sizes in a single frame — twelve
 * thumbnails in a year, one full grid, one mid-zoom — so the drawing is a
 * function of a rectangle rather than the state of a view. `detail` fades in
 * the parts that only make sense when there is room: the weekday strip, the
 * week rules, the event marks.
 */
class MonthPainter(context: Context) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.16f
    }
    private val weekNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint()

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)

    // ---- geometry ------------------------------------------------------

    /** Height of the weekday strip, which only exists at full detail. */
    fun headerHeight(rect: RectF, detail: Float): Float =
        min(rect.height() * 0.095f, dp(26f)) * detail

    fun gutterWidth(rect: RectF, detail: Float, style: GridStyle): Float =
        if (style.weekNumbers) min(rect.width() * 0.07f, dp(20f)) * detail else 0f

    fun cellBounds(rect: RectF, index: Int, detail: Float, style: GridStyle, out: RectF) {
        val gutter = gutterWidth(rect, detail, style)
        val top = rect.top + headerHeight(rect, detail)
        val cellW = (rect.width() - gutter) / MonthModel.COLUMNS
        val rowH = (rect.bottom - top) / MonthModel.ROWS
        val column = index % MonthModel.COLUMNS
        val row = index / MonthModel.COLUMNS
        out.set(
            rect.left + gutter + column * cellW,
            top + row * rowH,
            rect.left + gutter + (column + 1) * cellW,
            top + (row + 1) * rowH,
        )
    }

    fun indexAt(rect: RectF, detail: Float, style: GridStyle, x: Float, y: Float): Int? {
        val gutter = gutterWidth(rect, detail, style)
        val top = rect.top + headerHeight(rect, detail)
        if (x < rect.left + gutter || x > rect.right || y < top || y > rect.bottom) return null
        val cellW = (rect.width() - gutter) / MonthModel.COLUMNS
        val rowH = (rect.bottom - top) / MonthModel.ROWS
        val column = ((x - rect.left - gutter) / cellW).toInt().coerceIn(0, MonthModel.COLUMNS - 1)
        val row = ((y - top) / rowH).toInt().coerceIn(0, MonthModel.ROWS - 1)
        return row * MonthModel.COLUMNS + column
    }

    // ---- drawing -------------------------------------------------------

    fun draw(
        canvas: Canvas,
        rect: RectF,
        month: YearMonth,
        firstDay: DayOfWeek,
        today: LocalDate,
        selected: LocalDate?,
        loads: Map<LocalDate, DayLoad>,
        style: GridStyle,
        detail: Float,
        alpha: Float,
        locale: Locale = Locale.getDefault(),
        /** Lets the caller pop the chosen disc when it has just been chosen. */
        selectionScale: Float = 1f,
    ) {
        if (alpha <= 0.01f || rect.width() <= 1f || rect.height() <= 1f) return
        val cells = MonthModel.cells(month, firstDay)
        val gutter = gutterWidth(rect, detail, style)
        val headerH = headerHeight(rect, detail)
        val gridTop = rect.top + headerH
        val cellW = (rect.width() - gutter) / MonthModel.COLUMNS
        val rowH = (rect.bottom - gridTop) / MonthModel.ROWS

        val numberSize = min(rowH * 0.34f, cellW * 0.42f)
        dayPaint.textSize = numberSize
        val markerRadius = min(rowH * 0.35f, cellW * 0.40f)
        val dotDetail = smoothstep(0.35f, 0.8f, detail)
        val dotRadius = min(dp(2f), rowH * 0.06f) * dotDetail
        val dotGap = dotRadius * 1.8f
        val stackGap = if (dotDetail > 0f) rowH * 0.09f * dotDetail else 0f
        val stackHeight = 2 * markerRadius + stackGap + 2 * dotRadius

        if (headerH > 0.5f) {
            weekdayPaint.textSize = min(headerH * 0.46f, dp(10f))
            val labels = MonthModel.weekdayLabels(firstDay, locale)
            val order = MonthModel.weekdayOrder(firstDay)
            val baseline = rect.top + headerH * 0.68f
            val nudge = weekdayPaint.textSize * weekdayPaint.letterSpacing / 2f
            for (i in labels.indices) {
                weekdayPaint.color = Tokens.withAlpha(
                    if (style.dimWeekends && MonthModel.isWeekend(order[i])) {
                        palette.inkGhost
                    } else {
                        palette.inkFaint
                    },
                    alpha * detail,
                )
                canvas.drawText(
                    labels[i],
                    rect.left + gutter + (i + 0.5f) * cellW - nudge,
                    baseline,
                    weekdayPaint,
                )
            }
        }

        if (detail > 0.02f) {
            val hairline = maxOf(1f, Math.round(density * 0.5f).toFloat())
            rulePaint.color = Tokens.withAlpha(palette.hairline, alpha * detail)
            for (row in 0 until MonthModel.ROWS) {
                val y = gridTop + row * rowH
                canvas.drawRect(rect.left + gutter, y, rect.right, y + hairline, rulePaint)
            }
        }

        if (style.weekNumbers && gutter > 1f) {
            weekNumberPaint.textSize = min(rowH * 0.24f, dp(9f))
            weekNumberPaint.color = Tokens.withAlpha(palette.inkGhost, alpha * detail)
            for (row in 0 until MonthModel.ROWS) {
                val cy = gridTop + (row + 0.42f) * rowH
                canvas.drawText(
                    MonthModel.weekOfYear(cells[row * MonthModel.COLUMNS], locale).toString(),
                    rect.left + gutter / 2f,
                    cy - (weekNumberPaint.descent() + weekNumberPaint.ascent()) / 2f,
                    weekNumberPaint,
                )
            }
        }

        for (index in cells.indices) {
            val date = cells[index]
            val inMonth = date.month == month.month && date.year == month.year
            if (!inMonth && !style.showAdjacent) continue

            val column = index % MonthModel.COLUMNS
            val row = index / MonthModel.COLUMNS
            val cx = rect.left + gutter + (column + 0.5f) * cellW
            val rowTop = gridTop + row * rowH
            val stackTop = rowTop + (rowH - stackHeight) / 2f
            val cy = stackTop + markerRadius

            val load = loads[date]

            if (style.heat && inMonth && load != null && load.count > 0) {
                val strength = (load.count.coerceAtMost(4) / 4f) * 0.16f * alpha
                fillPaint.color = Tokens.withAlpha(palette.accent, strength)
                canvas.drawRect(
                    rect.left + gutter + column * cellW,
                    rowTop,
                    rect.left + gutter + (column + 1) * cellW,
                    rowTop + rowH,
                    fillPaint,
                )
            }

            val isToday = date == today
            val isSelected = date == selected
            var textColour = when {
                !inMonth -> palette.inkGhost
                isToday -> palette.accent
                style.dimWeekends && MonthModel.isWeekend(date.dayOfWeek) -> palette.inkMuted
                else -> palette.ink
            }
            dayPaint.typeface = if (isToday || isSelected) medium else regular

            // A trailing cell of the previous month also *is* today; marking it
            // would put two discs in the year view for one date.
            if (isSelected && (inMonth || detail > 0.5f)) {
                val disc = if (isToday) palette.accent else palette.ink
                if (detail > 0.5f) {
                    // Two flat rings stand in for a blur: a real one would cost
                    // a mask filter per cell, and this is a 20dp halo.
                    val halo = smoothstep(0.5f, 0.9f, detail) * alpha
                    fillPaint.color = Tokens.withAlpha(disc, halo * 0.05f)
                    canvas.drawCircle(cx, cy, markerRadius * 1.62f * selectionScale, fillPaint)
                    fillPaint.color = Tokens.withAlpha(disc, halo * 0.07f)
                    canvas.drawCircle(cx, cy, markerRadius * 1.28f * selectionScale, fillPaint)
                }
                fillPaint.color = Tokens.withAlpha(disc, alpha)
                canvas.drawCircle(cx, cy, markerRadius * selectionScale, fillPaint)
                textColour = if (isToday) palette.onAccent else palette.canvas
            } else if (isToday && inMonth && detail < 0.5f) {
                // Too small for coloured type to register: use the disc instead.
                fillPaint.color = Tokens.withAlpha(palette.accent, alpha * (1f - detail * 2f))
                canvas.drawCircle(cx, cy, markerRadius, fillPaint)
                textColour = palette.onAccent
            }

            dayPaint.color = Tokens.withAlpha(textColour, alpha)
            canvas.drawText(
                date.dayOfMonth.toString(),
                cx,
                cy - (dayPaint.descent() + dayPaint.ascent()) / 2f,
                dayPaint,
            )

            if (dotDetail <= 0.01f || load == null || load.count <= 0) continue
            val visible = min(
                if (style.colouredDots) maxOf(load.colours.size, 1) else 1,
                min(3, load.count),
            )
            val dotsWidth = visible * 2 * dotRadius + (visible - 1) * dotGap
            var dx = cx - dotsWidth / 2f + dotRadius
            val dy = stackTop + 2 * markerRadius + stackGap + dotRadius
            for (i in 0 until visible) {
                fillPaint.color = Tokens.withAlpha(
                    when {
                        !inMonth -> palette.inkGhost
                        style.colouredDots && i < load.colours.size -> load.colours[i]
                        else -> palette.inkFaint
                    },
                    alpha * dotDetail,
                )
                canvas.drawCircle(dx, dy, dotRadius, fillPaint)
                dx += 2 * dotRadius + dotGap
            }
        }
    }
}
