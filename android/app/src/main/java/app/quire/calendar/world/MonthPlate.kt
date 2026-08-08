package app.quire.calendar.world

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import app.quire.engine.fx.Glow
import app.quire.engine.scene.Quad3D
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * One month painted once into a bitmap and thereafter pasted onto a plane in 3D.
 *
 * A wall of months is the whole point of the world view, and a month is roughly two hundred
 * draw calls — six rules, seven labels, forty-two numbers, a hundred marks. Doing that for
 * twenty visible months every frame is not affordable, and doing it under a perspective matrix
 * is worse: text and circles would be re-tessellated at a new scale on every frame. So the
 * month is rasterised once at a fixed size and the quad's matrix does all the moving after
 * that. A plate costs exactly one `drawBitmap` per frame no matter how far away it is.
 *
 * The bitmap is [CACHE_WIDTH] x [CACHE_HEIGHT] whatever the screen is. That is deliberate: the
 * picture is the same on every device, the quad matrix scales it up or down, and the cache
 * stays a predictable twelve times 1.3 MB rather than growing with the display.
 *
 * Everything in the picture except the selection is baked in, so the caller owes this class an
 * [invalidate] whenever the event loads for a month change. The selection is the one thing that
 * moves on a tap, so it is drawn over the plate through the same matrix instead — otherwise
 * every tap would re-rasterise a month, and the cache would key on 42 selections per month.
 *
 * Not thread safe, and the paints are shared fields: call it from the thread that draws, and
 * call [invalidate] and [release] outside a draw pass, since they recycle bitmaps a display
 * list may still be holding.
 */
class MonthPlate(context: Context) {

    // Only used to read the locale for month and weekday names. Application resources follow
    // a locale change, and holding them cannot leak an activity.
    private val resources = context.applicationContext.resources

    // ---- configuration -------------------------------------------------

    private var theme: Theme? = null
    private var metrics: Metrics = Metrics(1f)
    private var firstDay: DayOfWeek = DayOfWeek.MONDAY
    private var today: LocalDate = LocalDate.MIN
    private var colouredMarks: Boolean = true
    private var densityTint: Boolean = false
    private var locale: Locale = currentLocale()

    // Column headers and the weekend flags behind them depend only on the configuration, so
    // they are worked out there rather than once per rasterised month.
    private var weekdayLabels: List<String> = emptyList()
    private val weekendColumn = BooleanArray(MonthModel.COLUMNS)

    // ---- cache ---------------------------------------------------------

    /** One rasterised month, plus the date its first cell holds, which dates the whole grid. */
    private class Plate(val bitmap: Bitmap, val startEpochDay: Long) {

        fun recycle() {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    // Access-ordered, so the eldest entry is the month least recently drawn. That is what makes
    // recycling at the moment of eviction safe: every plate drawn in the current frame has just
    // been touched, so the one thrown out cannot be sitting in this frame's display list unless
    // more than MAX_PLATES months were drawn in a single frame, which the world never does.
    private val cache =
        object : LinkedHashMap<YearMonth, Plate>(CACHE_BUCKETS, LOAD_FACTOR, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<YearMonth, Plate>,
            ): Boolean {
                if (size <= MAX_PLATES) return false
                eldest.value.recycle()
                return true
            }
        }

    // ---- per-frame scratch, never allocated in draw ---------------------

    private val matrix = Matrix()
    private val cellRect = RectF()
    private val plateRect = RectF()
    private val rasterCanvas = Canvas()
    private val glow = Glow()

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rulePaint = Paint().apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    // A real 500 weight only became addressable in API 28; before that the medium face has to
    // be asked for by family name, which is the same font on every device that ships it and
    // falls back to the regular face on any device that does not.
    private val medium: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, MEDIUM_WEIGHT, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Tabular figures: without them a grid of numbers wobbles, because a 1 is narrower than
        // a 0 in the proportional face and every column would be centred on a different width.
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.LEFT
    }
    private val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.LEFT
    }
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.CENTER
        letterSpacing = WEEKDAY_TRACKING
    }

    // ---- geometry, all in cache pixels ---------------------------------

    // Metrics speaks in screen pixels; the plate is a fixed picture. This ratio carries a size
    // across. Both the display density and the user's size preference cancel inside it, which
    // is the intent: the bitmap is identical everywhere, and the quad matrix — driven by the
    // same Metrics on screen — is what finally makes the plate large or small.
    private var toCache: Float = CACHE_WIDTH / metrics.dp(PLATE_WIDTH_DP)

    private var gridLeft = 0f
    private var gridTop = 0f
    private var cellWidth = 0f
    private var rowHeight = 0f
    private var ruleWidth = 1f
    private var titleBaseline = 0f
    private var weekdayBaseline = 0f
    private var discRadius = 0f
    private var markRadius = 0f
    private var markGap = 0f
    private var stackGap = 0f
    private var cornerRadius = 0f
    private var selectRadius = 0f
    private var selectInset = 0f
    private var selectStroke = 0f

    init {
        relayout()
        rebuildWeekdays()
    }

    /**
     * Every input that would change the picture. Changing any of them drops the cache, so this
     * is cheap to call with unchanged values and correct to call with changed ones — pass the
     * current state each time rather than trying to work out whether it moved.
     *
     * @param theme the only source of colour in the plate.
     * @param metrics the only source of size; see [toCache] for how it reaches a fixed bitmap.
     * @param firstDay which weekday the grid starts on, which sets the column headers.
     * @param today the date that gets the accent disc; re-pass it at midnight.
     * @param colouredMarks true to draw event marks in their calendar colours, false to draw
     *     them all in one faint ink, which is the quieter reading of a busy month.
     * @param density true to wash each cell with the accent in proportion to its event count.
     */
    fun configure(
        theme: Theme,
        metrics: Metrics,
        firstDay: DayOfWeek,
        today: LocalDate,
        colouredMarks: Boolean,
        density: Boolean,
    ) {
        val nextLocale = currentLocale()
        val changed = theme != this.theme ||
            !sameShape(metrics, this.metrics) ||
            firstDay != this.firstDay ||
            today != this.today ||
            colouredMarks != this.colouredMarks ||
            density != this.densityTint ||
            nextLocale != this.locale
        if (!changed) return

        val weekdaysMoved = firstDay != this.firstDay || nextLocale != this.locale
        val sizesMoved = !sameShape(metrics, this.metrics)

        this.theme = theme
        this.metrics = metrics
        this.firstDay = firstDay
        this.today = today
        this.colouredMarks = colouredMarks
        this.densityTint = density
        this.locale = nextLocale

        if (sizesMoved) relayout()
        if (weekdaysMoved) rebuildWeekdays()
        invalidate(null)
    }

    /**
     * Paints [month] onto [quad], which the caller has already projected this frame.
     *
     * One `drawBitmap` for the plate itself, plus one ring when [selected] falls inside this
     * month's grid. A month the cache has not seen is rasterised here, which is the one call
     * that allocates; every later frame is the pair of draws and nothing else.
     *
     * @param loads event counts by date, as baked into the bitmap. When these change for a
     *     month already drawn, call [invalidate] for it or the plate will keep the old picture.
     * @param alpha 0..1 fade for the whole plate, for a month arriving or leaving the world.
     */
    fun draw(
        canvas: Canvas,
        quad: Quad3D,
        month: YearMonth,
        selected: LocalDate?,
        loads: Map<LocalDate, DayLoad>,
        alpha: Float,
    ) {
        val theme = this.theme ?: return
        if (!quad.visible) return
        // Below one step of an eight-bit alpha there is nothing to see, and the guard is
        // written positively so a NaN alpha draws nothing rather than something opaque.
        if (!(alpha > MIN_ALPHA)) return

        val plate = cache[month] ?: rasterise(theme, month, loads).also { cache[month] = it }
        if (plate.bitmap.isRecycled) return

        quad.matrixFor(CACHE_WIDTH, CACHE_HEIGHT, matrix)
        platePaint.alpha = toAlpha(min(alpha, 1f))
        canvas.drawBitmap(plate.bitmap, matrix, platePaint)

        if (selected == null) return
        // Today already wears a filled disc in the accent, baked into the picture. Ringing it in
        // the same colour marks one day twice and reads as a mistake rather than as a selection.
        if (selected == today) return
        // The grid is 42 consecutive days from the plate's first cell, so the selected cell is
        // a subtraction — no date arithmetic and no list of dates in the per-frame path.
        val index = (selected.toEpochDay() - plate.startEpochDay).toInt()
        if (index < 0 || index >= MonthModel.CELLS) return

        cellRectIn(index, cellRect)
        cellRect.inset(selectInset, selectInset)
        canvas.save()
        // Drawing through the plate's own matrix puts the ring in the same perspective as the
        // picture it marks, so it stays welded to its cell as the plate turns.
        canvas.concat(matrix)
        fillPaint.color = withAlpha(theme.accent, alpha * SELECT_WASH)
        canvas.drawRoundRect(cellRect, selectRadius, selectRadius, fillPaint)
        strokePaint.color = withAlpha(theme.accent, alpha)
        strokePaint.strokeWidth = selectStroke
        canvas.drawRoundRect(cellRect, selectRadius, selectRadius, strokePaint)
        canvas.restore()
    }

    /**
     * The cell index under a point in quad space, taking the normalised u,v that `Quad3D.hit`
     * reports, so a touch can go straight from the screen to a date.
     *
     * @return 0 until [MonthModel.CELLS], or -1 for the title band, the weekday strip and the
     *     margins, where there is no day to hit.
     */
    fun cellAt(u: Float, v: Float): Int {
        val x = u * CACHE_WIDTH
        val y = v * CACHE_HEIGHT
        if (x < gridLeft || y < gridTop) return -1
        val column = ((x - gridLeft) / cellWidth).toInt()
        val row = ((y - gridTop) / rowHeight).toInt()
        if (column < 0 || column >= MonthModel.COLUMNS) return -1
        if (row < 0 || row >= MonthModel.ROWS) return -1
        return row * MonthModel.COLUMNS + column
    }

    /**
     * The quad-space rectangle of one cell, in the same normalised 0..1 u,v as [cellAt], so a
     * day can be grown out of the plate into its own quad and land exactly where it was drawn.
     */
    fun cellBounds(index: Int, out: RectF) {
        cellRectIn(index, out)
        out.set(
            out.left / CACHE_WIDTH,
            out.top / CACHE_HEIGHT,
            out.right / CACHE_WIDTH,
            out.bottom / CACHE_HEIGHT,
        )
    }

    /**
     * Throws away a baked picture so the next [draw] paints it again. Call it when the events
     * for a month change, or with null for everything after a change that touches all of them.
     */
    fun invalidate(month: YearMonth?) {
        if (month == null) {
            for (plate in cache.values) plate.recycle()
            cache.clear()
        } else {
            cache.remove(month)?.recycle()
        }
    }

    /**
     * Recycles every cached bitmap, for when the world goes away. The plate stays usable — the
     * next [draw] simply rasterises again — so this is a memory release, not a teardown.
     */
    fun release() {
        rasterCanvas.setBitmap(null)
        invalidate(null)
    }

    // ---- rasterising ---------------------------------------------------

    private fun rasterise(
        theme: Theme,
        month: YearMonth,
        loads: Map<LocalDate, DayLoad>,
    ): Plate {
        val bitmap = Bitmap.createBitmap(CACHE_PIXELS_W, CACHE_PIXELS_H, Bitmap.Config.ARGB_8888)
        // The bitmap is a texture, not an image at some screen density; saying so keeps any
        // canvas from helpfully rescaling it on the way out.
        bitmap.density = Bitmap.DENSITY_NONE

        val cells = MonthModel.cells(month, firstDay)
        val canvas = rasterCanvas
        canvas.setBitmap(bitmap)

        paintGround(canvas, theme)
        if (densityTint) paintDensity(canvas, theme, month, cells, loads)
        paintRules(canvas, theme)
        paintHead(canvas, theme, month)
        paintCells(canvas, theme, month, cells, loads)

        // Released straight away: a Canvas holding a bitmap keeps it alive, and this one is a
        // field that outlives every plate it draws.
        canvas.setBitmap(null)
        return Plate(bitmap, cells[0].toEpochDay())
    }

    /** The card the month sits on, with the hairline that separates it from the world behind. */
    private fun paintGround(canvas: Canvas, theme: Theme) {
        val half = ruleWidth * 0.5f
        plateRect.set(half, half, CACHE_WIDTH - half, CACHE_HEIGHT - half)
        fillPaint.color = theme.surface
        canvas.drawRoundRect(plateRect, cornerRadius, cornerRadius, fillPaint)
        strokePaint.color = theme.hairline
        strokePaint.strokeWidth = ruleWidth
        canvas.drawRoundRect(plateRect, cornerRadius, cornerRadius, strokePaint)
    }

    /**
     * The busy-ness wash. It is laid down before the rules and the numbers so that it reads as
     * the paper the month is printed on rather than as a highlight over it.
     */
    private fun paintDensity(
        canvas: Canvas,
        theme: Theme,
        month: YearMonth,
        cells: List<LocalDate>,
        loads: Map<LocalDate, DayLoad>,
    ) {
        for (index in 0 until MonthModel.CELLS) {
            val date = cells[index]
            val count = loads[date]?.count ?: 0
            if (count <= 0) continue
            // One event already has to be visible, so the ramp starts at a floor and climbs
            // from there; past DENSITY_FULL a day is simply full and the wash stops darkening.
            val ramp = ((count - 1).toFloat() / (DENSITY_FULL - 1f)).coerceIn(0f, 1f)
            var strength = DENSITY_MIN + (DENSITY_MAX - DENSITY_MIN) * ramp
            if (!inMonth(date, month)) strength *= ADJACENT_FADE
            cellRectIn(index, cellRect)
            fillPaint.color = withAlpha(theme.accent, strength)
            canvas.drawRect(cellRect, fillPaint)
        }
    }

    /** Six week rules, one above each row, which is what turns 42 numbers into a calendar. */
    private fun paintRules(canvas: Canvas, theme: Theme) {
        rulePaint.color = theme.hairline
        val right = gridLeft + cellWidth * MonthModel.COLUMNS
        for (row in 0 until MonthModel.ROWS) {
            val y = gridTop + row * rowHeight
            canvas.drawRect(gridLeft, y, right, y + ruleWidth, rulePaint)
        }
    }

    /** The month name, its year, and the weekday initials over the columns. */
    private fun paintHead(canvas: Canvas, theme: Theme, month: YearMonth) {
        val name = MonthModel.monthName(month, locale)
        titlePaint.color = theme.ink
        canvas.drawText(name, gridLeft, titleBaseline, titlePaint)
        yearPaint.color = theme.inkFaint
        canvas.drawText(
            month.year.toString(),
            gridLeft + titlePaint.measureText(name) + px(TITLE_GAP_DP),
            titleBaseline,
            yearPaint,
        )

        // Tracking is added after every glyph including the last, so a centred label sits half
        // a space to the right of true centre unless it is pulled back.
        val nudge = weekdayPaint.textSize * weekdayPaint.letterSpacing * 0.5f
        for (column in weekdayLabels.indices) {
            weekdayPaint.color =
                if (weekendColumn[column]) theme.inkGhost else theme.inkFaint
            canvas.drawText(
                weekdayLabels[column],
                gridLeft + (column + 0.5f) * cellWidth - nudge,
                weekdayBaseline,
                weekdayPaint,
            )
        }
    }

    /** The day numbers, the today disc and its glow, and up to three event marks per day. */
    private fun paintCells(
        canvas: Canvas,
        theme: Theme,
        month: YearMonth,
        cells: List<LocalDate>,
        loads: Map<LocalDate, DayLoad>,
    ) {
        val stackHeight = 2f * discRadius + stackGap + 2f * markRadius
        for (index in 0 until MonthModel.CELLS) {
            val date = cells[index]
            val column = index % MonthModel.COLUMNS
            val row = index / MonthModel.COLUMNS
            val here = inMonth(date, month)
            val cx = gridLeft + (column + 0.5f) * cellWidth
            val stackTop = gridTop + row * rowHeight + (rowHeight - stackHeight) * 0.5f
            val cy = stackTop + discRadius

            // A trailing cell can also be today; only the month that owns the date gets the
            // disc, so a wall of plates never shows the same day marked twice.
            val isToday = here && date == today
            if (isToday) {
                glow.draw(canvas, cx, cy, discRadius * GLOW_REACH, theme.accent, GLOW_STRENGTH)
                fillPaint.color = theme.accent
                canvas.drawCircle(cx, cy, discRadius, fillPaint)
            }

            dayPaint.typeface = if (isToday) medium else regular
            dayPaint.color = when {
                isToday -> theme.onAccent
                !here -> theme.inkGhost
                // A weekend is still a day of this month, so it steps down one rung of the ink
                // ladder rather than off it.
                weekendColumn[column] -> theme.inkMuted
                else -> theme.ink
            }
            canvas.drawText(
                DAY_LABELS[date.dayOfMonth],
                cx,
                cy - (dayPaint.descent() + dayPaint.ascent()) * 0.5f,
                dayPaint,
            )

            val load = loads[date] ?: continue
            val marks = min(MAX_MARKS, load.count)
            if (marks <= 0) continue
            val colours = load.colours
            val span = marks * 2f * markRadius + (marks - 1) * markGap
            var x = cx - span * 0.5f + markRadius
            val y = stackTop + 2f * discRadius + stackGap + markRadius
            for (i in 0 until marks) {
                val raw = if (colouredMarks && i < colours.size) colours[i] else 0
                fillPaint.color = when {
                    !here -> theme.inkGhost
                    !colouredMarks -> theme.inkFaint
                    raw != 0 -> opaque(raw)
                    // No colour of its own: the mark's slot picks one, so three unattributed
                    // events still read as three things rather than one smudge.
                    else -> theme.categorical(i)
                }
                canvas.drawCircle(x, y, markRadius, fillPaint)
                x += 2f * markRadius + markGap
            }
        }
    }

    // ---- layout --------------------------------------------------------

    private fun relayout() {
        toCache = CACHE_WIDTH / metrics.dp(PLATE_WIDTH_DP)

        val pad = px(PAD_DP)
        val titleSize = px(TITLE_DP)
        val titleBand = titleSize * TITLE_BAND
        val weekdayBand = px(WEEKDAY_BAND_DP)

        titlePaint.textSize = titleSize
        yearPaint.textSize = px(YEAR_DP)
        weekdayPaint.textSize = px(WEEKDAY_DP)
        titleBaseline = pad + titleSize * TITLE_BASELINE
        weekdayBaseline = pad + titleBand + weekdayBand * WEEKDAY_BASELINE

        gridLeft = pad
        gridTop = pad + titleBand + weekdayBand
        cellWidth = (CACHE_WIDTH - 2f * pad) / MonthModel.COLUMNS
        rowHeight = (CACHE_HEIGHT - pad - gridTop) / MonthModel.ROWS

        // A hairline carried into cache space can land below a pixel, and a half-pixel line
        // rasterised here and then magnified by the quad is a grey smear rather than a rule.
        ruleWidth = max(1f, metrics.hairline * toCache)

        dayPaint.textSize = min(rowHeight * DAY_OF_ROW, cellWidth * DAY_OF_COLUMN)
        discRadius = min(rowHeight * DISC_OF_ROW, cellWidth * DISC_OF_COLUMN)
        markRadius = px(MARK_DP)
        markGap = px(MARK_GAP_DP)
        stackGap = px(STACK_GAP_DP)

        cornerRadius = metrics.radiusLarge * toCache
        selectRadius = metrics.radiusSmall * toCache
        selectInset = px(SELECT_INSET_DP)
        selectStroke = px(SELECT_STROKE_DP)
    }

    private fun rebuildWeekdays() {
        weekdayLabels = MonthModel.weekdayLabels(firstDay, locale)
        val order = MonthModel.weekdayOrder(firstDay)
        for (column in 0 until MonthModel.COLUMNS) {
            weekendColumn[column] = MonthModel.isWeekend(order[column])
        }
    }

    /** The cell rectangle in cache pixels, which is the space everything is baked in. */
    private fun cellRectIn(index: Int, out: RectF) {
        val clamped = index.coerceIn(0, MonthModel.CELLS - 1)
        val column = clamped % MonthModel.COLUMNS
        val row = clamped / MonthModel.COLUMNS
        val left = gridLeft + column * cellWidth
        val top = gridTop + row * rowHeight
        out.set(left, top, left + cellWidth, top + rowHeight)
    }

    private fun px(dp: Float): Float = metrics.dp(dp) * toCache

    private fun inMonth(date: LocalDate, month: YearMonth): Boolean =
        date.monthValue == month.monthValue && date.year == month.year

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    // Metrics has no equality of its own, and callers rebuild it freely, so two of them count
    // as the same when they would hand back the same numbers.
    private fun sameShape(a: Metrics, b: Metrics): Boolean =
        a === b || (a.scale == b.scale && a.hairline == b.hairline && a.dp(1f) == b.dp(1f))

    private fun withAlpha(colour: Int, alpha: Float): Int {
        // The colour's own alpha is scaled rather than replaced, so a theme colour that is
        // already a wash — a hairline, a press — fades from what it is instead of to full.
        val source = (colour ushr 24) and 0xFF
        return (toAlpha(source / 255f * alpha) shl 24) or (colour and RGB_MASK)
    }

    private fun toAlpha(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    // Provider colours arrive as packed ints and some calendars leave the alpha byte at zero,
    // which would make a perfectly good mark invisible.
    private fun opaque(colour: Int): Int = (OPAQUE shl 24) or (colour and RGB_MASK)

    private companion object {

        // Roughly a phone screen's worth of month at 1x, and a 4:5 plate. Large enough that the
        // numbers stay crisp when a plate fills the screen, small enough that twelve of them
        // are about 15 MB.
        const val CACHE_PIXELS_W = 512
        const val CACHE_PIXELS_H = 640

        // The same two numbers as floats. Written out rather than converted so they stay
        // compile-time constants, and kept next to their integers so they cannot drift.
        const val CACHE_WIDTH = 512f
        const val CACHE_HEIGHT = 640f

        // The design width of a plate. Every size from Metrics is a fraction of this, which is
        // what lets a fixed bitmap carry proportions that were designed in dp.
        const val PLATE_WIDTH_DP = 320f

        // A year either side of the month being read, which is as far as a flick reaches
        // before the cache would be refilled anyway.
        const val MAX_PLATES = 12
        const val CACHE_BUCKETS = 16
        const val LOAD_FACTOR = 0.75f

        const val MEDIUM_WEIGHT = 500

        const val PAD_DP = 13f
        const val TITLE_DP = 20f
        const val TITLE_GAP_DP = 6f
        const val YEAR_DP = 13f
        const val TITLE_BAND = 1.5f
        const val TITLE_BASELINE = 0.8f
        const val WEEKDAY_DP = 9.5f
        const val WEEKDAY_BAND_DP = 17f
        const val WEEKDAY_BASELINE = 0.72f
        const val WEEKDAY_TRACKING = 0.16f

        // The number is sized against both the row and the column, so it never outgrows the
        // narrower of the two whatever aspect the grid ends up with.
        const val DAY_OF_ROW = 0.30f
        const val DAY_OF_COLUMN = 0.40f
        const val DISC_OF_ROW = 0.33f
        const val DISC_OF_COLUMN = 0.36f

        const val MARK_DP = 2.3f
        const val MARK_GAP_DP = 2.6f
        const val STACK_GAP_DP = 4f
        const val MAX_MARKS = 3

        const val GLOW_REACH = 2.4f
        const val GLOW_STRENGTH = 0.55f

        // Five events is a full day: past that the wash would only lose its own gradations.
        const val DENSITY_FULL = 5
        const val DENSITY_MIN = 0.05f
        const val DENSITY_MAX = 0.20f

        // Days from the months either side are context, not content.
        const val ADJACENT_FADE = 0.45f

        const val SELECT_INSET_DP = 2f
        const val SELECT_STROKE_DP = 1.6f
        const val SELECT_WASH = 0.12f

        const val MIN_ALPHA = 1f / 255f

        const val RGB_MASK = 0x00FFFFFF
        const val OPAQUE = 255

        // Day numbers are drawn 42 times per rasterisation and there are only ever 31 of them,
        // so the strings are made once instead of on every plate.
        val DAY_LABELS: Array<String> = Array(32) { it.toString() }
    }
}
