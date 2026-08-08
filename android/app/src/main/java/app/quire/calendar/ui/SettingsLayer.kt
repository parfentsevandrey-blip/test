package app.quire.calendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import app.quire.calendar.core.Accent
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.calendar.core.Palette
import app.quire.calendar.core.Tokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Settings, drawn.
 *
 * The previous version was ordinary Views animated with ViewPropertyAnimator,
 * which the system scales to zero whenever animations are turned down — so on
 * a phone in that state none of it moved at all. Everything here is painted and
 * every moving part is a spring on this view's own frame loop, so the motion is
 * the same on every device.
 *
 * A live month sits at the top and answers each change as it is made: the point
 * of a setting called Accent is easier to see than to read.
 */
class SettingsLayer(context: Context) : View(context) {

    sealed class Row {
        class Section(val title: String) : Row()
        class Segmented(
            val title: String,
            val options: List<String>,
            var index: Int,
            val onSelect: (Int) -> Unit,
        ) : Row()

        class Toggle(
            val title: String,
            val hint: String?,
            var on: Boolean,
            val onChange: (Boolean) -> Unit,
        ) : Row()

        class Accents(var selected: Int, val onSelect: (Int) -> Unit) : Row()
        class Check(
            val title: String,
            val subtitle: String?,
            val colour: Int,
            var on: Boolean,
            val onChange: (Boolean) -> Unit,
        ) : Row()

        class Note(val text: String) : Row()
    }

    private class RowState {
        val enter = Spring(0f, 0f)
        val toggle = Spring(0f, 0f)
        val pillX = Spring(0f, 0f)
        val pillW = Spring(0f, 0f)
        val press = Spring(0f, 0f)
        var top = 0f
        var height = 0f
        var slabTop = false
        var slabBottom = false
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = android.graphics.BlurMaskFilter(
            dp(18f),
            android.graphics.BlurMaskFilter.Blur.NORMAL,
        )
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = -0.025f
    }
    private val capsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.13f
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val optionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val painter = MonthPainter(context)

    private val enter = Spring(0f, 0f)
    private val scroll = Spring(0f, 0f)
    private var lastFrameNanos = 0L

    private var rows: List<Row> = emptyList()
    private var states: List<RowState> = emptyList()
    private var contentHeight = 0f
    private var closing = false
    private var pressedRow = -1

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) { field = value; painter.palette = value; invalidate() }

    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) {
            field = value
            enter.profile(value)
            scroll.profile(MotionProfile.CALM)
            states.forEach {
                it.enter.profile(value)
                it.toggle.profile(value)
                it.pillX.profile(value)
                it.pillW.profile(value)
                it.press.profile(value)
            }
            invalidate()
        }

    var haptics = true
    var onClose: (() -> Unit)? = null

    // Live preview inputs.
    var previewStyle: GridStyle = GridStyle()
        set(value) { field = value; invalidate() }
    var previewFirstDay: DayOfWeek = DayOfWeek.MONDAY
        set(value) { field = value; invalidate() }
    var previewLoads: Map<LocalDate, DayLoad> = emptyMap()
        set(value) { field = value; invalidate() }

    var safeTop = 0f
    var safeBottom = 0f

    val isShowing: Boolean get() = visibility == VISIBLE && !closing

    init {
        visibility = INVISIBLE
        isClickable = true
    }

    fun present(rows: List<Row>) {
        this.rows = rows
        states = rows.map { row ->
            RowState().apply {
                enter.profile(motion)
                toggle.profile(motion)
                pillX.profile(motion)
                pillW.profile(motion)
                press.profile(motion)
                if (row is Row.Toggle) toggle.snapTo(if (row.on) 1f else 0f)
                if (row is Row.Check) toggle.snapTo(if (row.on) 1f else 0f)
            }
        }
        closing = false
        pressedRow = -1
        scroll.snapTo(0f)
        layoutRows()
        visibility = VISIBLE
        enter.snapTo(0f)
        enter.target = 1f
        states.forEach { it.enter.target = 1f }
        if (motion.instant) {
            enter.snapTo(1f)
            states.forEach { it.enter.snapTo(1f) }
        }
        lastFrameNanos = 0L
        invalidate()
    }

    fun dismiss() {
        if (!isShowing) return
        closing = true
        enter.target = 0f
        states.forEach { it.enter.target = 0f }
        if (motion.instant) {
            enter.snapTo(0f)
            states.forEach { it.enter.snapTo(0f) }
        }
        lastFrameNanos = 0L
        invalidate()
    }

    // ---- layout --------------------------------------------------------

    private val previewHeight get() = dp(196f)

    private fun rowHeight(row: Row): Float = when (row) {
        is Row.Section -> dp(46f)
        is Row.Segmented -> dp(64f)
        is Row.Toggle -> if (row.hint != null) dp(70f) else dp(58f)
        is Row.Accents -> dp(62f)
        is Row.Check -> if (row.subtitle != null) dp(66f) else dp(56f)
        is Row.Note -> dp(24f) + noteLines(row.text).size * dp(18f)
    }

    private fun noteLines(text: String): List<String> {
        labelPaint.textSize = dp(12.5f)
        val available = width - dp(76f)
        if (available <= 0f) return listOf(text)
        val out = ArrayList<String>()
        text.split("\n").forEach { paragraph ->
            var rest = paragraph
            while (rest.isNotEmpty()) {
                val fits = labelPaint.breakText(rest, true, available, null)
                var cut = fits
                if (cut < rest.length) {
                    val space = rest.lastIndexOf(' ', cut)
                    if (space > 0) cut = space
                }
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
        }
        return out
    }

    private fun layoutRows() {
        if (width <= 0) return
        var y = safeTop + dp(86f) + previewHeight + dp(20f)
        rows.forEachIndexed { index, row ->
            val state = states[index]
            state.top = y
            state.height = rowHeight(row)
            state.slabTop = row !is Row.Section &&
                (index == 0 || rows[index - 1] is Row.Section)
            state.slabBottom = row !is Row.Section &&
                (index == rows.lastIndex || rows[index + 1] is Row.Section)
            y += state.height
        }
        contentHeight = y - (safeTop + dp(86f)) + safeBottom + dp(32f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRows()
    }

    private val maxScroll: Float
        get() = (contentHeight - (height - safeTop - dp(86f))).coerceAtLeast(0f)

    // ---- frame loop ----------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        var moving = enter.advance(dt.coerceIn(0f, 0.064f))
        moving = scroll.advance(dt) || moving
        states.forEach { state ->
            moving = state.enter.advance(dt) || moving
            moving = state.toggle.advance(dt) || moving
            moving = state.pillX.advance(dt) || moving
            moving = state.pillW.advance(dt) || moving
            moving = state.press.advance(dt) || moving
        }

        val shown = enter.value.coerceIn(0f, 1.2f)
        if (closing && !moving && shown <= 0.01f) {
            visibility = INVISIBLE
            closing = false
            lastFrameNanos = 0L
            onClose?.invoke()
            return
        }
        if (width <= 0 || rows.isEmpty()) return

        scrimPaint.color = Tokens.withAlpha(
            if (palette.dark) 0xFF000000.toInt() else palette.ink,
            0.68f * shown.coerceAtMost(1f),
        )
        canvas.drawPaint(scrimPaint)

        titlePaint.textSize = dp(30f)
        titlePaint.color = Tokens.withAlpha(
            if (palette.dark) palette.ink else palette.canvas,
            shown.coerceAtMost(1f),
        )
        titlePaint.setShadowLayer(dp(10f), 0f, dp(2f), 0x99000000.toInt())
        canvas.drawText(
            context.getString(app.quire.calendar.R.string.settings),
            dp(24f),
            safeTop + dp(58f),
            titlePaint,
        )
        titlePaint.clearShadowLayer()

        val restore = canvas.save()
        canvas.clipRect(0f, safeTop + dp(70f), width.toFloat(), height.toFloat())
        canvas.translate(0f, -scroll.value)

        drawPreview(canvas, shown)
        rows.forEachIndexed { index, row -> drawRow(canvas, index, row, states[index]) }

        canvas.restoreToCount(restore)
        if (moving) postInvalidateOnAnimation() else lastFrameNanos = 0L
    }

    /** The month above the list, wearing whatever the settings currently say. */
    private fun drawPreview(canvas: Canvas, shown: Float) {
        val phase = smoothstep(0f, 0.7f, enter.value)
        if (phase <= 0.004f) return
        val rect = RectF(
            dp(18f),
            safeTop + dp(86f) + lerp(dp(24f), 0f, phase),
            width - dp(18f),
            safeTop + dp(86f) + previewHeight + lerp(dp(24f), 0f, phase),
        )
        drawSlab(canvas, rect, dp(24f), phase * shown)
        val inner = RectF(
            rect.left + dp(12f),
            rect.top + dp(10f),
            rect.right - dp(12f),
            rect.bottom - dp(10f),
        )
        val month = YearMonth.now()
        painter.draw(
            canvas = canvas,
            rect = inner,
            month = month,
            firstDay = previewFirstDay,
            today = LocalDate.now(),
            selected = LocalDate.now(),
            loads = previewLoads,
            style = previewStyle,
            detail = 1f,
            alpha = phase * shown,
        )
    }

    private fun drawRow(canvas: Canvas, index: Int, row: Row, state: RowState) {
        val phase = state.enter.value.coerceIn(0f, 1.2f)
        if (phase <= 0.004f) return
        val slide = lerp(dp(30f), 0f, smoothstep(0f, 1f, phase))
        val top = state.top + slide
        val bottom = top + state.height
        if (bottom < scroll.value - dp(40f) || top > scroll.value + height + dp(40f)) return
        val alpha = phase.coerceAtMost(1f)

        if (row !is Row.Section && row !is Row.Note) {
            val slab = RectF(dp(18f), top, width - dp(18f), bottom)
            val radius = dp(24f)
            slabPaint.color = Tokens.withAlpha(palette.surface, alpha)
            if (state.slabTop || state.slabBottom) {
                drawSlab(canvas, slab, radius, alpha, top = state.slabTop, bottom = state.slabBottom)
            } else {
                canvas.drawRect(slab, slabPaint)
            }
            if (state.press.value > 0.004f) {
                slabPaint.color = Tokens.withAlpha(palette.press, alpha * state.press.value)
                canvas.drawRect(slab, slabPaint)
            }
            if (!state.slabBottom) {
                strokePaint.strokeWidth = maxOf(1f, density * 0.5f)
                strokePaint.color = Tokens.withAlpha(palette.hairline, alpha)
                canvas.drawLine(dp(38f), bottom, width - dp(18f), bottom, strokePaint)
            }
        }

        when (row) {
            is Row.Section -> {
                capsPaint.textSize = dp(11f)
                // The world is still visible behind, so section labels carry
                // their own shadow rather than relying on the scrim.
                capsPaint.setShadowLayer(dp(8f), 0f, dp(1.5f), 0xA6000000.toInt())
                capsPaint.color = Tokens.withAlpha(
                    if (palette.dark) palette.inkMuted else palette.inkGhost,
                    alpha,
                )
                canvas.drawText(
                    row.title.uppercase(Locale.getDefault()),
                    dp(32f),
                    bottom - dp(14f),
                    capsPaint,
                )
                capsPaint.clearShadowLayer()
            }
            is Row.Note -> {
                labelPaint.textSize = dp(12.5f)
                labelPaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
                noteLines(row.text).forEachIndexed { line, text ->
                    canvas.drawText(text, dp(38f), top + dp(16f) + line * dp(18f), labelPaint)
                }
            }
            is Row.Segmented -> drawSegmented(canvas, row, state, top, alpha)
            is Row.Toggle -> drawToggle(canvas, row, state, top, alpha)
            is Row.Accents -> drawAccents(canvas, row, state, top, alpha)
            is Row.Check -> drawCheck(canvas, row, state, top, alpha)
        }
    }

    private fun title(canvas: Canvas, text: String, hint: String?, top: Float, alpha: Float, right: Float) {
        labelPaint.textSize = dp(15f)
        labelPaint.color = Tokens.withAlpha(palette.ink, alpha)
        val baseline = if (hint == null) top + dp(34f) else top + dp(28f)
        canvas.drawText(ellipsise(text, labelPaint, right - dp(38f)), dp(38f), baseline, labelPaint)
        if (hint != null) {
            labelPaint.textSize = dp(12f)
            labelPaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
            canvas.drawText(
                ellipsise(hint, labelPaint, right - dp(38f)),
                dp(38f),
                top + dp(48f),
                labelPaint,
            )
        }
    }

    /** The active option is a pill that travels, not a colour that swaps. */
    private fun drawSegmented(canvas: Canvas, row: Row.Segmented, state: RowState, top: Float, alpha: Float) {
        optionPaint.textSize = dp(13f)
        optionPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val padding = dp(13f)
        val widths = row.options.map { optionPaint.measureText(it) + padding * 2f }
        val total = widths.sum()
        val right = width - dp(30f)
        var x = right - total
        val centreY = top + state.height / 2f

        val targetX = x + widths.take(row.index).sum()
        if (state.pillW.target == 0f) {
            state.pillX.snapTo(targetX)
            state.pillW.snapTo(widths[row.index])
        }
        state.pillX.target = targetX
        state.pillW.target = widths[row.index]

        val pill = RectF(
            state.pillX.value,
            centreY - dp(15f),
            state.pillX.value + state.pillW.value,
            centreY + dp(15f),
        )
        slabPaint.color = Tokens.withAlpha(palette.accent, alpha * 0.16f)
        canvas.drawRoundRect(pill, dp(15f), dp(15f), slabPaint)

        row.options.forEachIndexed { i, option ->
            val cx = x + widths[i] / 2f
            val here = abs(cx - (state.pillX.value + state.pillW.value / 2f)) < widths[i] / 2f
            optionPaint.color = Tokens.withAlpha(
                if (here) palette.accent else palette.inkFaint,
                alpha,
            )
            canvas.drawText(
                option,
                cx,
                centreY - (optionPaint.descent() + optionPaint.ascent()) / 2f,
                optionPaint,
            )
            x += widths[i]
        }
        title(canvas, row.title, null, top, alpha, right - total)
    }

    private fun drawToggle(canvas: Canvas, row: Row.Toggle, state: RowState, top: Float, alpha: Float) {
        state.toggle.target = if (row.on) 1f else 0f
        val phase = state.toggle.value.coerceIn(0f, 1.2f)
        val right = width - dp(30f)
        val trackW = dp(46f)
        val trackH = dp(28f)
        val track = RectF(right - trackW, top + (state.height - trackH) / 2f, right, top + (state.height + trackH) / 2f)

        slabPaint.color = Tokens.withAlpha(palette.accent, alpha * phase.coerceIn(0f, 1f))
        canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, slabPaint)
        strokePaint.strokeWidth = dp(1.4f)
        strokePaint.color = Tokens.withAlpha(palette.hairlineStrong, alpha * (1f - phase).coerceIn(0f, 1f))
        canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, strokePaint)

        val knobR = trackH / 2f - dp(4.5f)
        val travel = track.width() - 2 * (knobR + dp(4.5f))
        slabPaint.color = Tokens.withAlpha(
            if (phase > 0.5f) palette.onAccent else palette.inkFaint,
            alpha,
        )
        canvas.drawCircle(
            track.left + knobR + dp(4.5f) + travel * phase.coerceIn(0f, 1f),
            track.centerY(),
            knobR,
            slabPaint,
        )
        title(canvas, row.title, row.hint, top, alpha, track.left - dp(12f))
    }

    private fun drawAccents(canvas: Canvas, row: Row.Accents, state: RowState, top: Float, alpha: Float) {
        val entries = Accent.entries
        val step = dp(30f)
        val right = width - dp(30f)
        val startX = right - step * (entries.size - 1) - dp(9f)
        val centreY = top + state.height / 2f

        val targetX = startX + step * row.selected
        if (state.pillW.target == 0f) {
            state.pillX.snapTo(targetX)
            state.pillW.snapTo(1f)
        }
        state.pillX.target = targetX
        state.pillW.target = 1f

        strokePaint.strokeWidth = dp(1.4f)
        strokePaint.color = Tokens.withAlpha(palette.ink, alpha)
        canvas.drawCircle(state.pillX.value, centreY, dp(13f), strokePaint)

        entries.forEachIndexed { i, accent ->
            slabPaint.color = Tokens.withAlpha(
                if (palette.dark) accent.dark else accent.light,
                alpha,
            )
            canvas.drawCircle(startX + step * i, centreY, dp(8.5f), slabPaint)
        }
        title(canvas, context.getString(app.quire.calendar.R.string.accent), null, top, alpha, startX - dp(16f))
    }

    private fun drawCheck(canvas: Canvas, row: Row.Check, state: RowState, top: Float, alpha: Float) {
        state.toggle.target = if (row.on) 1f else 0f
        val phase = state.toggle.value.coerceIn(0f, 1f)
        val right = width - dp(30f)
        val centreY = top + state.height / 2f

        slabPaint.color = Tokens.withAlpha(
            if (row.colour == 0) palette.inkFaint else row.colour,
            alpha,
        )
        canvas.drawCircle(dp(30f), centreY, dp(5f), slabPaint)

        strokePaint.strokeWidth = dp(1.8f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = Tokens.withAlpha(palette.ink, alpha * phase)
        val tick = dp(6f)
        canvas.drawLine(
            right - tick * 2f,
            centreY,
            right - tick * 0.8f,
            centreY + tick * 0.9f,
            strokePaint,
        )
        canvas.drawLine(
            right - tick * 0.8f,
            centreY + tick * 0.9f,
            right + tick * 0.4f,
            centreY - tick,
            strokePaint,
        )

        labelPaint.textSize = dp(15f)
        labelPaint.color = Tokens.withAlpha(palette.ink, alpha)
        val baseline = if (row.subtitle == null) centreY + dp(5f) else top + dp(26f)
        canvas.drawText(
            ellipsise(row.title, labelPaint, right - dp(60f)),
            dp(46f),
            baseline,
            labelPaint,
        )
        if (row.subtitle != null) {
            labelPaint.textSize = dp(12f)
            labelPaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
            canvas.drawText(
                ellipsise(row.subtitle, labelPaint, right - dp(60f)),
                dp(46f),
                top + dp(46f),
                labelPaint,
            )
        }
    }

    private fun drawSlab(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        alpha: Float,
        top: Boolean = true,
        bottom: Boolean = true,
    ) {
        shadowPaint.color = Tokens.withAlpha(
            if (palette.dark) 0xFF000000.toInt() else 0xFF14130F.toInt(),
            alpha * if (palette.dark) 0.5f else 0.16f,
        )
        canvas.drawRoundRect(
            rect.left,
            rect.top + dp(4f),
            rect.right,
            rect.bottom + dp(4f),
            radius,
            radius,
            shadowPaint,
        )
        slabPaint.color = Tokens.withAlpha(palette.surface, alpha)
        val r = RectF(rect)
        if (!top) r.top -= radius
        if (!bottom) r.bottom += radius
        val save = canvas.save()
        canvas.clipRect(rect)
        canvas.drawRoundRect(r, radius, radius, slabPaint)
        canvas.restoreToCount(save)
    }

    private fun ellipsise(text: String, paint: Paint, available: Float): String {
        if (available <= 0f) return ""
        if (paint.measureText(text) <= available) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > available) end--
        return text.substring(0, end) + "…"
    }

    // ---- interaction ---------------------------------------------------

    private fun rowAt(x: Float, y: Float): Int {
        val world = y + scroll.value
        states.forEachIndexed { index, state ->
            if (world >= state.top && world < state.top + state.height) {
                if (rows[index] is Row.Section || rows[index] is Row.Note) return -1
                return index
            }
        }
        return -1
    }

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scroll.velocity = 0f
                pressedRow = rowAt(e.x, e.y)
                if (pressedRow >= 0) {
                    states[pressedRow].press.snapTo(1f)
                    states[pressedRow].press.target = 0f
                }
                invalidate()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                pressedRow = -1
                scroll.snapTo((scroll.value + distanceY).coerceIn(0f, maxScroll))
                lastFrameNanos = 0L
                invalidate()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                scroll.velocity = -velocityY / 1000f
                scroll.target = scroll.value.coerceIn(0f, maxScroll)
                lastFrameNanos = 0L
                invalidate()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val index = rowAt(e.x, e.y)
                if (index < 0) {
                    if (e.y < safeTop + dp(70f)) dismiss()
                    return true
                }
                activate(index, e.x)
                return true
            }
        },
    )

    private fun activate(index: Int, x: Float) {
        val row = rows[index]
        val state = states[index]
        if (haptics) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        when (row) {
            is Row.Toggle -> {
                row.on = !row.on
                row.onChange(row.on)
            }
            is Row.Check -> {
                row.on = !row.on
                row.onChange(row.on)
            }
            is Row.Segmented -> {
                optionPaint.textSize = dp(13f)
                val padding = dp(13f)
                val widths = row.options.map { optionPaint.measureText(it) + padding * 2f }
                var left = width - dp(30f) - widths.sum()
                var picked = row.index
                for (i in widths.indices) {
                    if (x >= left && x < left + widths[i]) { picked = i; break }
                    left += widths[i]
                }
                row.index = picked
                row.onSelect(picked)
            }
            is Row.Accents -> {
                val entries = Accent.entries
                val step = dp(30f)
                val startX = width - dp(30f) - step * (entries.size - 1) - dp(9f)
                val picked = Math.round((x - startX) / step).coerceIn(0, entries.size - 1)
                row.selected = picked
                row.onSelect(picked)
            }
            else -> Unit
        }
        state.press.snapTo(1f)
        state.press.target = 0f
        lastFrameNanos = 0L
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isShowing) return false
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            pressedRow = -1
            scroll.target = scroll.value.coerceIn(0f, maxScroll)
            lastFrameNanos = 0L
            invalidate()
        }
        return handled || true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Rebuilds the visible state of a row after a setting changed elsewhere. */
    fun refresh() {
        layoutRows()
        invalidate()
    }
}
