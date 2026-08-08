package app.quire.calendar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import app.quire.calendar.core.Accent
import app.quire.calendar.core.AgendaEntry
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
import kotlin.math.roundToInt

/**
 * The whole app is one surface with one continuous coordinate: `zoom`.
 *
 *   0 — the year, twelve months laid out three by four
 *   1 — one month, filling the screen
 *   2 — one day, its square grown into a card
 *
 * Nothing here switches between screens. Every level is drawn from the same
 * month rectangles, interpolated, so pulling back from a month to its year is
 * the same month moving and shrinking rather than one view replacing another.
 * Pinch, double-tap, drag and the back gesture all just move `zoom` along.
 */
class StageView(context: Context) : View(context) {

    interface Data {
        /** Marks already known for [month]; asking may kick off a fetch. */
        fun loads(month: YearMonth): Map<LocalDate, DayLoad>
        fun agenda(date: LocalDate): List<AgendaEntry>
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val painter = MonthPainter(context)
    private val ambient = Ambient(context)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = -0.025f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val capsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.13f
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        fontFeatureSettings = "tnum"
    }
    private val slabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ---- state ---------------------------------------------------------

    val zoom = Spring(1f, 1f)
    private val focus = Spring(0f, 0f)
    private val dayEntrance = Spring(0f, 0f)
    private val scroll = Spring(0f, 0f)
    /** How far the world is pushed back while something floats above it. */
    private val recede = Spring(0f, 0f)
    /** The chosen disc lands rather than appears. */
    private val selectionPop = Spring(1f, 1f)

    var palette: Palette = Tokens.palette(false, Accent.CINNABAR)
        set(value) {
            field = value
            painter.palette = value
            ambient.palette = value
            thumbs.clear()
            invalidate()
        }

    var motion: MotionProfile = MotionProfile.STANDARD
        set(value) {
            field = value
            listOf(zoom, focus, dayEntrance, scroll, recede, selectionPop)
                .forEach { it.profile(value) }
            invalidate()
        }

    var style: GridStyle = GridStyle()
        set(value) { field = value; thumbs.clear(); invalidate() }

    var firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
        set(value) { field = value; thumbs.clear(); invalidate() }

    var today: LocalDate = LocalDate.now()
        set(value) { field = value; thumbs.clear(); invalidate() }

    var haptics = true

    var data: Data? = null

    var selected: LocalDate = LocalDate.now()
        private set

    var onSelectionChanged: ((LocalDate) -> Unit)? = null
    var onEntryActivated: ((AgendaEntry) -> Unit)? = null
    var onComposeRequested: ((LocalDate) -> Unit)? = null
    var onLevelChanged: (() -> Unit)? = null

    private var safeTop = 0f
    private var safeBottom = 0f

    private val monthRect = RectF()
    private val yearRect = RectF()
    private val scratch = RectF()
    private val cellRect = RectF()
    private val cardRect = RectF()
    private val dockRect = RectF()

    private var lastFrameNanos = 0L
    private var dragging = false
    private var pinching = false
    private var cardDragging = false
    private var pressedEntry = -1
    private var dockGesture = false
    private var dockDragTotal = 0f

    /** Thumbnails for the year level, where a live redraw would cost the frame. */
    private val thumbs = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Int, Bitmap>): Boolean {
            if (size <= 30) return false
            eldest.value.recycle()
            return true
        }

        override fun clear() {
            values.forEach { it.recycle() }
            super.clear()
        }
    }
    private var thumbWidth = 0
    private var thumbHeight = 0

    init {
        isClickable = true
        isFocusable = true
        focus.snapTo(MonthModel.indexOf(YearMonth.from(selected)).toFloat())
    }

    fun setSafeInsets(top: Float, bottom: Float) {
        safeTop = top
        safeBottom = bottom
        requestLayout()
        invalidate()
    }

    /** Pushes the world back a little while a menu or panel floats over it. */
    fun setReceded(receded: Boolean) {
        recede.target = if (receded) 1f else 0f
        if (motion.instant) recede.snapTo(recede.target)
        step()
    }

    /** Marks or agenda arrived; drop what was baked from the old numbers. */
    fun dataChanged() {
        thumbs.clear()
        invalidate()
    }

    val level: Int get() = zoom.target.roundToInt()

    fun goTo(date: LocalDate, level: Int = 1, animate: Boolean = true) {
        select(date, notify = true, haptic = false)
        val index = MonthModel.indexOf(YearMonth.from(date)).toFloat()
        if (animate && !motion.instant) {
            focus.target = index
            zoom.target = level.toFloat()
        } else {
            focus.snapTo(index)
            zoom.snapTo(level.toFloat())
        }
        if (level >= 2) startDayEntrance()
        onLevelChanged?.invoke()
        step()
    }

    /** Move to a level without changing which day is chosen. */
    fun goToLevel(level: Int) = setLevel(level.coerceIn(0, 2))

    fun zoomOut(): Boolean {
        val next = (level - 1).coerceAtLeast(0)
        if (next == level) return false
        setLevel(next)
        return true
    }

    private fun setLevel(next: Int) {
        if (next == 1 && level == 0) {
            // Coming back from the year: settle on the month under the focus.
            focus.target = focus.value.roundToInt().toFloat()
        }
        if (next == 0) focus.target = Math.floorDiv(focus.value.roundToInt(), 12) * 12f
        zoom.target = next.toFloat()
        if (motion.instant) {
            zoom.snapTo(zoom.target)
            focus.snapTo(focus.target)
        }
        if (next >= 2) startDayEntrance() else dayEntrance.target = 0f
        tick(HapticFeedbackConstants.CLOCK_TICK)
        onLevelChanged?.invoke()
        step()
    }

    private fun startDayEntrance() {
        dayEntrance.snapTo(if (motion.instant) 1f else 0f)
        dayEntrance.target = 1f
        scroll.snapTo(0f)
    }

    private fun select(date: LocalDate, notify: Boolean, haptic: Boolean) {
        if (selected == date) return
        selected = date
        thumbs.clear()
        if (motion.instant) {
            selectionPop.snapTo(1f)
        } else {
            selectionPop.snapTo(0.68f)
            selectionPop.target = 1f
        }
        if (haptic) tick(HapticFeedbackConstants.CLOCK_TICK)
        if (notify) onSelectionChanged?.invoke(date)
        invalidate()
    }

    private fun tick(constant: Int) {
        if (haptics) performHapticFeedback(constant)
    }

    // ---- layout --------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRects()
        thumbs.clear()
    }

    private fun layoutRects() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val inset = dp(14f)
        val top = safeTop + dp(76f)
        monthRect.set(inset, top, w - inset, h - safeBottom - dp(108f))
        yearRect.set(inset, top, w - inset, h - safeBottom - dp(28f))
        cardRect.set(inset, safeTop + dp(72f), w - inset, h - safeBottom - dp(20f))
        dockRect.set(
            inset,
            h - safeBottom - dp(88f),
            w - inset,
            h - safeBottom - dp(24f),
        )
        thumbWidth = ((yearRect.width() / 3f) - dp(10f)).toInt().coerceAtLeast(1)
        thumbHeight = ((yearRect.height() / 4f) - dp(24f)).toInt().coerceAtLeast(1)
    }

    /** Where month [index] sits at year level, before the focus offset. */
    private fun yearCell(index: Int, out: RectF) {
        val m = Math.floorMod(index, 12)
        val cellW = yearRect.width() / 3f
        val cellH = yearRect.height() / 4f
        val left = yearRect.left + (m % 3) * cellW
        val top = yearRect.top + (m / 3) * cellH
        out.set(
            left + dp(5f),
            top + dp(19f),
            left + cellW - dp(5f),
            top + cellH - dp(5f),
        )
    }

    private fun rectFor(index: Int, t: Float, out: RectF) {
        val w = width.toFloat()
        yearCell(index, out)
        val yearOffset = (Math.floorDiv(index, 12) - focus.value / 12f) * w
        out.offset(yearOffset, 0f)
        if (t <= 0f) return
        scratch.set(monthRect)
        scratch.offset((index - focus.value) * w, 0f)
        out.set(
            lerp(out.left, scratch.left, t),
            lerp(out.top, scratch.top, t),
            lerp(out.right, scratch.right, t),
            lerp(out.bottom, scratch.bottom, t),
        )
    }

    // ---- frame loop ----------------------------------------------------

    private fun step() {
        if (lastFrameNanos == 0L) lastFrameNanos = System.nanoTime()
        invalidate()
    }

    private fun advanceSprings(): Boolean {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        var moving = false
        if (!dragging && !pinching) moving = focus.advance(dt) || moving
        if (!pinching) moving = zoom.advance(dt) || moving
        moving = dayEntrance.advance(dt) || moving
        if (!cardDragging) moving = scroll.advance(dt) || moving
        moving = recede.advance(dt) || moving
        moving = selectionPop.advance(dt) || moving
        return moving
    }

    override fun onDraw(canvas: Canvas) {
        val moving = advanceSprings()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val z = zoom.value.coerceIn(0f, 2f)
        val t = z.coerceIn(0f, 1f)
        val u = (z - 1f).coerceIn(0f, 1f)

        ambient.draw(canvas, w, h, zoom.value, focus.value)

        // The ground stays put; only what the user is holding moves back.
        val worldRestore = canvas.save()
        if (recede.value > 0.001f) {
            val scale = lerp(1f, 0.94f, recede.value)
            canvas.scale(scale, scale, w / 2f, h * 0.45f)
        }
        drawMonths(canvas, t, u)
        drawChrome(canvas, t, u)
        if (u > 0.001f) drawDayCard(canvas, u)
        canvas.restoreToCount(worldRestore)

        if (moving) postInvalidateOnAnimation() else lastFrameNanos = 0L
    }

    private fun drawMonths(canvas: Canvas, t: Float, u: Float) {
        val focusIndex = focus.value.roundToInt()
        val range = if (t < 0.6f) 26 else 3
        val gridAlpha = 1f - u
        if (gridAlpha <= 0.004f) return

        val zoomPivotIndex = MonthModel.indexOf(YearMonth.from(selected))
        var restore = -1
        if (u > 0.001f) {
            rectFor(zoomPivotIndex, 1f, scratch)
            val cells = MonthModel.cells(YearMonth.from(selected), firstDayOfWeek)
            val cellIndex = cells.indexOf(selected).coerceAtLeast(0)
            painter.cellBounds(scratch, cellIndex, 1f, style, cellRect)
            restore = canvas.save()
            val scale = lerp(1f, 2.4f, u)
            canvas.scale(scale, scale, cellRect.centerX(), cellRect.centerY())
        }

        for (index in (focusIndex - range)..(focusIndex + range)) {
            if (index < 0 || index > MonthModel.indexOf(YearMonth.of(2100, 12))) continue
            rectFor(index, t, scratch)
            if (scratch.right < -dp(24f) || scratch.left > width + dp(24f)) continue
            if (scratch.width() < 2f || scratch.height() < 2f) continue

            val month = MonthModel.monthAt(index)
            val distance = abs(index - focus.value)
            // At year level every month is equally present; at month level the
            // neighbours fade so the focused one is unambiguous.
            val alpha = gridAlpha * lerp(1f, (1f - smoothstep(0.15f, 1f, distance) * 0.55f), t)

            if (t < 0.08f && scratch.width() <= thumbWidth * 1.3f) {
                drawThumb(canvas, index, scratch, alpha)
            } else {
                painter.draw(
                    canvas = canvas,
                    rect = scratch,
                    month = month,
                    firstDay = firstDayOfWeek,
                    today = today,
                    selected = selected,
                    loads = data?.loads(month).orEmpty(),
                    style = style,
                    detail = t,
                    alpha = alpha,
                    selectionScale = selectionPop.value,
                )
            }

            val labelFade = 1f - smoothstep(0.15f, 0.6f, t)
            if (labelFade > 0.004f) {
                labelPaint.textSize = min(dp(12f), scratch.width() * 0.13f)
                val isFocusMonth = index == focusIndex
                labelPaint.color = Tokens.withAlpha(
                    if (month == YearMonth.from(today)) palette.accent else palette.inkMuted,
                    alpha * labelFade * if (isFocusMonth) 1f else 0.85f,
                )
                canvas.drawText(
                    MonthModel.monthName(month, Locale.getDefault()),
                    scratch.centerX(),
                    scratch.top - dp(6f),
                    labelPaint,
                )
            }
        }
        if (restore >= 0) canvas.restoreToCount(restore)
    }

    private fun drawThumb(canvas: Canvas, index: Int, rect: RectF, alpha: Float) {
        if (thumbWidth <= 1 || thumbHeight <= 1) return
        val bitmap = thumbs.getOrPut(index) {
            val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
            val month = MonthModel.monthAt(index)
            painter.draw(
                canvas = Canvas(bmp),
                rect = RectF(0f, 0f, thumbWidth.toFloat(), thumbHeight.toFloat()),
                month = month,
                firstDay = firstDayOfWeek,
                today = today,
                selected = selected,
                loads = data?.loads(month).orEmpty(),
                style = style,
                detail = 0f,
                alpha = 1f,
            )
            bmp
        }
        slabPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(bitmap, null, rect, slabPaint)
    }

    private fun drawChrome(canvas: Canvas, t: Float, u: Float) {
        val locale = Locale.getDefault()
        val focusMonth = MonthModel.monthAt(focus.value.roundToInt())
        val alpha = 1f - u
        if (alpha <= 0.004f) return
        val baseline = safeTop + dp(50f)

        // One title, not two crossfading on top of each other: the year slides
        // right to make room and the month name grows in ahead of it.
        titlePaint.textSize = lerp(dp(30f), dp(28f), t)
        val name = MonthModel.monthName(focusMonth, locale)
        val nameWidth = titlePaint.measureText(name)
        // The year clears the space first and the name only then fades into it;
        // running both on one ramp would print them over each other halfway.
        val slide = smoothstep(0f, 0.45f, t)
        val nameAlpha = smoothstep(0.45f, 0.85f, t)

        titlePaint.color = Tokens.withAlpha(
            androidx.core.graphics.ColorUtils.blendARGB(palette.ink, palette.inkGhost, t),
            alpha,
        )
        canvas.drawText(
            focusMonth.year.toString(),
            dp(20f) + (nameWidth + dp(8f)) * slide,
            baseline,
            titlePaint,
        )
        if (nameAlpha > 0.004f) {
            titlePaint.color = Tokens.withAlpha(palette.ink, alpha * nameAlpha)
            canvas.drawText(name, dp(20f), baseline, titlePaint)
        }

        if (t * alpha > 0.004f) drawDock(canvas, t * alpha)
    }

    /** The floating strip under the month: what the chosen day holds. */
    private fun drawDock(canvas: Canvas, alpha: Float) {
        val entries = data?.agenda(selected).orEmpty()
        val rect = RectF(dockRect)
        drawSlab(canvas, rect, dp(22f), alpha, lifted = true)

        capsPaint.textSize = dp(10.5f)
        capsPaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
        val locale = Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMM")
        canvas.drawText(
            java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
                .format(selected)
                .uppercase(locale),
            rect.left + dp(18f),
            rect.top + dp(24f),
            capsPaint,
        )

        bodyPaint.textSize = dp(14f)
        bodyPaint.color = Tokens.withAlpha(
            if (entries.isEmpty()) palette.inkGhost else palette.ink,
            alpha,
        )
        val summary = entries.firstOrNull()?.title?.takeIf { it.isNotBlank() }
            ?: context.getString(app.quire.calendar.R.string.nothing_scheduled)
        val available = rect.width() - dp(36f) - dp(34f)
        canvas.drawText(
            ellipsise(summary, bodyPaint, available),
            rect.left + dp(18f),
            rect.top + dp(46f),
            bodyPaint,
        )

        if (entries.size > 1) {
            val badge = entries.size.toString()
            timePaint.textSize = dp(12f)
            timePaint.color = Tokens.withAlpha(palette.onAccent, alpha)
            slabPaint.color = Tokens.withAlpha(palette.accent, alpha)
            val cx = rect.right - dp(28f)
            val cy = rect.centerY()
            canvas.drawCircle(cx, cy, dp(13f), slabPaint)
            timePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                badge,
                cx,
                cy - (timePaint.descent() + timePaint.ascent()) / 2f,
                timePaint,
            )
            timePaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawDayCard(canvas: Canvas, u: Float) {
        val entries = data?.agenda(selected).orEmpty()
        val cells = MonthModel.cells(YearMonth.from(selected), firstDayOfWeek)
        rectFor(MonthModel.indexOf(YearMonth.from(selected)), 1f, scratch)
        painter.cellBounds(scratch, cells.indexOf(selected).coerceAtLeast(0), 1f, style, cellRect)

        val morph = smoothstep(0f, 0.85f, u)
        val rect = RectF(
            lerp(cellRect.left, cardRect.left, morph),
            lerp(cellRect.top, cardRect.top, morph),
            lerp(cellRect.right, cardRect.right, morph),
            lerp(cellRect.bottom, cardRect.bottom, morph),
        )
        val radius = lerp(min(cellRect.width(), cellRect.height()) / 2f, dp(28f), morph)
        drawSlab(canvas, rect, radius, u, lifted = true)

        val contentAlpha = smoothstep(0.5f, 1f, u) * smoothstep(0f, 0.4f, dayEntrance.value)
        if (contentAlpha <= 0.004f) return

        val restore = canvas.save()
        canvas.clipRect(rect)
        val locale = Locale.getDefault()

        titlePaint.textSize = dp(34f)
        titlePaint.color = Tokens.withAlpha(palette.ink, contentAlpha)
        canvas.drawText(
            selected.dayOfMonth.toString(),
            rect.left + dp(22f),
            rect.top + dp(56f),
            titlePaint,
        )
        val dayWidth = titlePaint.measureText(selected.dayOfMonth.toString())
        capsPaint.textSize = dp(11f)
        capsPaint.color = Tokens.withAlpha(palette.inkFaint, contentAlpha)
        canvas.drawText(
            MonthModel.monthName(YearMonth.from(selected), locale).uppercase(locale),
            rect.left + dp(22f) + dayWidth + dp(10f),
            rect.top + dp(48f),
            capsPaint,
        )
        canvas.drawText(
            selected.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, locale)
                .uppercase(locale),
            rect.left + dp(22f) + dayWidth + dp(10f),
            rect.top + dp(34f),
            capsPaint,
        )

        strokePaint.strokeWidth = maxOf(1f, density * 0.5f)
        strokePaint.color = Tokens.withAlpha(palette.hairline, contentAlpha)
        canvas.drawLine(
            rect.left + dp(22f),
            rect.top + dp(74f),
            rect.right - dp(22f),
            rect.top + dp(74f),
            strokePaint,
        )

        entryTops.clear()
        var y = rect.top + dp(74f) + dp(14f) - scroll.value
        val rowHeight = dp(62f)
        if (entries.isEmpty()) {
            bodyPaint.textSize = dp(14f)
            bodyPaint.color = Tokens.withAlpha(palette.inkGhost, contentAlpha)
            canvas.drawText(
                context.getString(app.quire.calendar.R.string.nothing_scheduled),
                rect.left + dp(22f),
                y + dp(24f),
                bodyPaint,
            )
        }
        entries.forEachIndexed { index, entry ->
            val stagger = smoothstep(index * 0.09f, index * 0.09f + 0.55f, dayEntrance.value)
            val rowAlpha = contentAlpha * stagger
            val slide = lerp(dp(22f), 0f, stagger)
            entryTops += y
            if (y < rect.bottom && y + rowHeight > rect.top) {
                drawEntry(canvas, entry, rect, y + slide, rowHeight, rowAlpha, index == pressedEntry)
            }
            y += rowHeight
        }
        contentHeight = (y + scroll.value) - (rect.top + dp(88f))
        canvas.restoreToCount(restore)
    }

    private val entryTops = ArrayList<Float>()
    private var contentHeight = 0f

    private fun drawEntry(
        canvas: Canvas,
        entry: AgendaEntry,
        card: RectF,
        top: Float,
        height: Float,
        alpha: Float,
        pressed: Boolean,
    ) {
        if (alpha <= 0.004f) return
        val left = card.left + dp(14f)
        val right = card.right - dp(14f)
        if (pressed) {
            slabPaint.color = Tokens.withAlpha(palette.press, alpha)
            canvas.drawRoundRect(left, top, right, top + height - dp(6f), dp(14f), dp(14f), slabPaint)
        }

        timePaint.textSize = dp(13f)
        timePaint.color = Tokens.withAlpha(palette.ink, alpha)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        if (entry.allDay) {
            timePaint.color = Tokens.withAlpha(palette.inkMuted, alpha)
            canvas.drawText(
                context.getString(app.quire.calendar.R.string.all_day),
                left + dp(8f),
                top + dp(24f),
                timePaint,
            )
        } else {
            canvas.drawText(
                timeFormat.format(java.util.Date(entry.begin)),
                left + dp(8f),
                top + dp(22f),
                timePaint,
            )
            timePaint.textSize = dp(11f)
            timePaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
            canvas.drawText(
                timeFormat.format(java.util.Date(entry.end)),
                left + dp(8f),
                top + dp(38f),
                timePaint,
            )
        }

        val ruleX = left + dp(66f)
        slabPaint.color = Tokens.withAlpha(
            if (entry.colour == 0) palette.inkGhost else entry.colour,
            alpha,
        )
        canvas.drawRoundRect(
            ruleX,
            top + dp(6f),
            ruleX + dp(3f),
            top + height - dp(14f),
            dp(1.5f),
            dp(1.5f),
            slabPaint,
        )

        bodyPaint.textSize = dp(15f)
        bodyPaint.color = Tokens.withAlpha(palette.ink, alpha)
        val textLeft = ruleX + dp(14f)
        canvas.drawText(
            ellipsise(entry.title.ifBlank { "—" }, bodyPaint, right - textLeft - dp(8f)),
            textLeft,
            top + dp(22f),
            bodyPaint,
        )
        val subtitle = entry.location ?: entry.calendarName
        if (!subtitle.isNullOrBlank()) {
            bodyPaint.textSize = dp(12f)
            bodyPaint.color = Tokens.withAlpha(palette.inkFaint, alpha)
            canvas.drawText(
                ellipsise(subtitle, bodyPaint, right - textLeft - dp(8f)),
                textLeft,
                top + dp(40f),
                bodyPaint,
            )
        }
    }

    private fun drawSlab(canvas: Canvas, rect: RectF, radius: Float, alpha: Float, lifted: Boolean) {
        if (lifted) {
            shadowPaint.color = Tokens.withAlpha(
                if (palette.dark) 0xFF000000.toInt() else 0xFF14130F.toInt(),
                alpha * if (palette.dark) 0.55f else 0.16f,
            )
            shadowPaint.maskFilter = shadowFilter
            canvas.drawRoundRect(
                rect.left,
                rect.top + dp(3f),
                rect.right,
                rect.bottom + dp(3f),
                radius,
                radius,
                shadowPaint,
            )
            shadowPaint.maskFilter = null
        }
        slabPaint.color = Tokens.withAlpha(palette.surface, alpha)
        canvas.drawRoundRect(rect, radius, radius, slabPaint)
        strokePaint.strokeWidth = maxOf(1f, density * 0.5f)
        strokePaint.color = Tokens.withAlpha(palette.hairlineStrong, alpha)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private val shadowFilter = android.graphics.BlurMaskFilter(
        dp(18f),
        android.graphics.BlurMaskFilter.Blur.NORMAL,
    )

    private fun ellipsise(text: String, paint: Paint, available: Float): String {
        if (available <= 0f) return ""
        if (paint.measureText(text) <= available) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > available) end--
        return text.substring(0, end) + "…"
    }

    // ---- gestures ------------------------------------------------------

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                focus.velocity = 0f
                scroll.velocity = 0f
                dockGesture = level == 1 && dockRect.contains(e.x, e.y)
                dockDragTotal = 0f
                pressedEntry = entryAt(e.x, e.y)
                if (pressedEntry >= 0) invalidate()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (pinching) return false
                pressedEntry = -1
                if (dockGesture) {
                    // Pulling the dock upwards opens the day with the finger.
                    dockDragTotal += distanceY
                    val travel = (dockDragTotal / dp(150f)).coerceIn(0f, 1f)
                    if (travel > 0f && dayEntrance.value < 1f) dayEntrance.snapTo(1f)
                    zoom.snapTo(1f + travel)
                    step()
                    return true
                }
                if (level >= 2 && zoom.value > 1.6f) {
                    cardDragging = true
                    val next = (scroll.value + distanceY)
                        .coerceIn(0f, (contentHeight - cardRect.height() + dp(120f)).coerceAtLeast(0f))
                    // Pulling further than the top closes the card.
                    if (next <= 0f && distanceY < 0f && abs(distanceY) > dp(2f)) {
                        scroll.snapTo(0f)
                        cardDragging = false
                        setLevel(1)
                        return true
                    }
                    scroll.snapTo(next)
                    step()
                    return true
                }
                dragging = true
                val perScreen = lerp(12f, 1f, zoom.value.coerceIn(0f, 1f))
                focus.snapTo(focus.value + distanceX / width * perScreen)
                step()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (level >= 2) {
                    cardDragging = false
                    scroll.velocity = -velocityY / 900f
                    scroll.target = scroll.value
                    step()
                    return true
                }
                dragging = false
                val perScreen = lerp(12f, 1f, zoom.value.coerceIn(0f, 1f))
                val throw_ = (-velocityX / width * perScreen * 0.28f)
                    .coerceIn(-perScreen * 2f, perScreen * 2f)
                settleFocus(focus.value + throw_)
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                when (level) {
                    0 -> { focusMonthAt(e.x, e.y); setLevel(1) }
                    1 -> { selectDayAt(e.x, e.y); setLevel(2) }
                    else -> setLevel(1)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (level != 1) return
                pressedEntry = -1
                dockGesture = false
                selectDayAt(e.x, e.y)
                tick(HapticFeedbackConstants.LONG_PRESS)
                onComposeRequested?.invoke(selected)
                invalidate()
            }
        },
    ).apply { setIsLongpressEnabled(true) }

    private val pinch = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinching = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val delta = (detector.scaleFactor - 1f) * 2.4f
                zoom.snapTo((zoom.value + delta).coerceIn(0f, 2f))
                step()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinching = false
                setLevel(zoom.value.roundToInt())
            }
        },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (dockGesture) {
                dockGesture = false
                setLevel(if (zoom.value > 1.35f) 2 else 1)
            }
            if (dragging) {
                dragging = false
                settleFocus(focus.value)
            }
            cardDragging = false
            if (pressedEntry >= 0) { pressedEntry = -1; invalidate() }
            step()
        }
        return handled || super.onTouchEvent(event)
    }

    private fun settleFocus(raw: Float) {
        val snapped = if (zoom.value < 0.5f) {
            (raw / 12f).roundToInt() * 12f
        } else {
            raw.roundToInt().toFloat()
        }
        focus.target = snapped.coerceIn(0f, MonthModel.indexOf(YearMonth.of(2100, 12)).toFloat())
        if (motion.instant) focus.snapTo(focus.target)
        tick(HapticFeedbackConstants.CLOCK_TICK)
        step()
    }

    private fun handleTap(x: Float, y: Float) {
        when (level) {
            0 -> { focusMonthAt(x, y); setLevel(1) }
            1 -> {
                if (dockRect.contains(x, y)) {
                    setLevel(2)
                } else {
                    selectDayAt(x, y)
                }
            }
            else -> {
                val index = entryAt(x, y)
                val entries = data?.agenda(selected).orEmpty()
                if (index >= 0 && index < entries.size) {
                    onEntryActivated?.invoke(entries[index])
                } else if (!cardRect.contains(x, y)) {
                    setLevel(1)
                }
            }
        }
    }

    private fun focusMonthAt(x: Float, y: Float) {
        val focusIndex = focus.value.roundToInt()
        for (index in (focusIndex - 26)..(focusIndex + 26)) {
            rectFor(index, zoom.value.coerceIn(0f, 1f), scratch)
            if (scratch.contains(x, y)) {
                focus.target = index.toFloat()
                val month = MonthModel.monthAt(index)
                val landing = if (month == YearMonth.from(today)) today else month.atDay(1)
                select(landing, notify = true, haptic = false)
                return
            }
        }
    }

    private fun selectDayAt(x: Float, y: Float) {
        val index = focus.value.roundToInt()
        rectFor(index, 1f, scratch)
        val cell = painter.indexAt(scratch, 1f, style, x, y) ?: return
        val month = MonthModel.monthAt(index)
        val date = MonthModel.cells(month, firstDayOfWeek)[cell]
        select(date, notify = true, haptic = true)
    }

    private fun entryAt(x: Float, y: Float): Int {
        if (level < 2 || entryTops.isEmpty() || !cardRect.contains(x, y)) return -1
        val rowHeight = dp(62f)
        for (i in entryTops.indices) {
            if (y >= entryTops[i] && y < entryTops[i] + rowHeight) return i
        }
        return -1
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
