package app.quire.calendar.world

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import app.quire.engine.anim.smoothstep
import app.quire.engine.design.Metrics
import app.quire.engine.design.Oklch
import app.quire.engine.design.Theme
import app.quire.engine.fx.Glow
import app.quire.engine.scene.Quad3D
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
 * Light is the other thing that cannot be baked, because it answers where the plate is standing
 * rather than what is written on it: a contact shadow under the projected quad, a sheen that
 * slides as the plate turns, and a bloom on today. All three are pure functions of the quad and
 * the fade the caller passes, so a world that has stopped moving holds a still picture and needs
 * no frame of its own. Every one of them is bought with alpha over the whole card, which is
 * contrast taken off the numbers, so each is culled once the plate is too faint to read and none
 * of them is allowed to reach the ink: see the peaks in the companion for where the line is.
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
    private val washRect = RectF()
    private val shadowRect = RectF()
    private val rasterCanvas = Canvas()
    private val glow = Glow()

    // The plate's own edge, and the same shape as a path so anything laid over the card can be
    // clipped to it: a wash that reached past a rounded corner would draw a square card.
    private val plateEdge = RectF()
    private val platePath = Path()

    // Shader placement, never rebuilt in a frame — the gradients are made once per palette at
    // unit size and only moved, which is the one way a gradient stays out of the frame path.
    private val washMatrix = Matrix()
    private val sheenMatrix = Matrix()
    private val cellPoint = FloatArray(2)

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rulePaint = Paint().apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ---- light, all derived once per palette ---------------------------

    private val sheenColours = IntArray(SHEEN_STOPS.size)
    private val densityColours = IntArray(DENSITY_STOPS.size)
    private val warmthColours = IntArray(WARMTH_STOPS.size)
    private val rimColours = IntArray(RIM_STOPS.size)
    private val shadowAlpha = FloatArray(SHADOW_RINGS)

    private var sheenShader: LinearGradient? = null
    private var densityShader: RadialGradient? = null
    private var warmthShader: RadialGradient? = null
    private var rimShader: LinearGradient? = null
    private var shadowColour = 0
    private var shadowPeak = 0f
    private var shadowBuiltFor = 0

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
    private var shadowSpread = 0f
    private var shadowDrop = 0f
    private var warmthRadius = 0f

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
        val paletteMoved = theme != this.theme

        this.theme = theme
        this.metrics = metrics
        this.firstDay = firstDay
        this.today = today
        this.colouredMarks = colouredMarks
        this.densityTint = density
        this.locale = nextLocale

        if (sizesMoved) relayout()
        if (weekdaysMoved) rebuildWeekdays()
        if (paletteMoved) rebuildLight(theme)
        invalidate(null)
    }

    /**
     * Paints [month] onto [quad], which the caller has already projected this frame.
     *
     * One `drawBitmap` for the picture, a shadow under it, a sheen and a bloom over it, and one
     * ring when [selected] falls inside this month's grid. A month the cache has not seen is
     * rasterised here, which is the one call that allocates; every later frame is those draws
     * and nothing else. The light is read off [quad] rather than off a clock, so a plate that
     * has stopped moving paints the same picture for ever and asks for no frames.
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
        val fade = min(alpha, 1f)

        // Every light in here is a full-plate blended fill, and a plate this faint is a grey
        // shape rather than a month: it gets the picture and none of the overdraw. Ramped
        // rather than switched, so a plate leaving the world does not shed its shadow in one
        // frame. In the corridor this holds the whole package to the three or four plates near
        // the focus, whatever the caller has decided to keep on screen.
        val lit = smoothstep(LIT_FADE_START, LIT_FADE_END, fade)
        // How square the card is standing to the eye. Two of the three axes are usually zero,
        // and the third is what the caller spent its layout on, so this is free depth.
        val turn = cos(quad.yaw) * cos(quad.pitch)
        val facing = if (turn > 0f) turn else 0f

        if (lit > 0f) drawShadow(canvas, fade * lit)

        platePaint.alpha = toAlpha(fade)
        canvas.drawBitmap(plate.bitmap, matrix, platePaint)

        if (lit > 0f) {
            canvas.save()
            canvas.concat(matrix)
            // The card has round corners and today can sit in the first column, so everything
            // laid over the picture is held inside the card's own outline.
            canvas.clipPath(platePath)
            drawBloom(canvas, theme, plate, month, fade * lit, facing)
            drawSheen(canvas, quad, fade * lit, facing)
            canvas.restore()
        }

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

    // ---- light over and under the picture ------------------------------

    /**
     * The card's contact shadow: a stack of rounded rectangles laid down outside in, each one
     * grown a little further than the last and all of them dropped together.
     *
     * It is drawn through the plate's own matrix rather than as a screen rectangle, so it takes
     * the projected quad's shape — sheared, foreshortened, keystoned — offset in the card's own
     * down direction. That is what makes a plate read as a slab standing in the world rather
     * than as a picture stuck onto the background; a screen-aligned rectangle behind a turned
     * plate would read as a second, flatter card lying behind the first.
     */
    private fun drawShadow(canvas: Canvas, strength: Float) {
        canvas.save()
        canvas.concat(matrix)
        // The card is opaque and about to be drawn over all of this, so the bands are cut back
        // to the fringe they are actually seen in. Without it, thirteen full-card fills per
        // plate would be most of the world's blended fill, spent under something that hides it.
        // clipOutPath is API 26, which is this app's floor, so there is no older path to keep
        // working and no version check to forget.
        canvas.clipOutPath(platePath)

        // How many bands the penumbra needs is a question about pixels, not about taste: at the
        // month level it is seventy pixels wide and wants every one of them, and at the year
        // level twelve plates are on screen at once and each penumbra is a fifth of that, where
        // thirteen bands would land on top of one another and cost thirteen times as much to
        // look identical. Under a perspective matrix mapRadius is an average rather than an
        // answer, which is all this needs.
        val bands = (matrix.mapRadius(shadowSpread) / SHADOW_PIXELS_PER_BAND)
            .toInt()
            .coerceIn(SHADOW_RINGS_MIN, SHADOW_RINGS)
        if (bands != shadowBuiltFor) buildShadowProfile(shadowPeak, bands)

        var i = 0
        while (i < bands) {
            val a = shadowAlpha[i] * strength
            if (a > MIN_ALPHA) {
                // Ring 0 is the widest and faintest; the innermost is the card's own shape,
                // which is where a contact shadow is darkest. The drop is the same for all of
                // them, because a light has one direction: only the spread opens out.
                val reach = 1f - i / bands.toFloat()
                val spread = shadowSpread * reach
                shadowRect.set(
                    plateEdge.left - spread,
                    plateEdge.top - spread + shadowDrop,
                    plateEdge.right + spread,
                    plateEdge.bottom + spread + shadowDrop,
                )
                shadowPaint.color = withAlpha(shadowColour, a)
                val radius = cornerRadius + spread
                canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
            }
            i++
        }
        canvas.restore()
    }

    /**
     * A specular band across the card, strongest square on to the eye and sliding as the card
     * turns away. One gradient, built once per palette and only re-placed here, so a turning
     * plate costs a matrix and one blended rectangle.
     *
     * The band is deliberately weak. Every alpha laid over the picture is contrast taken off
     * [Theme.ink] against [Theme.surface], and the palette walked those two to a measured target;
     * the peaks in the companion are the most that can be spent without the numbers under the
     * band reading fainter than the numbers beside it.
     */
    private fun drawSheen(canvas: Canvas, quad: Quad3D, strength: Float, facing: Float) {
        val shader = sheenShader ?: return
        // Strongest square on, but it does not fade all the way out with the facing: measured on
        // the render, a squared falloff turned the whole thing into a fade rather than a slide,
        // because the band was leaving the card at the same time as it was going out.
        val level = strength * (SHEEN_FLOOR + (1f - SHEEN_FLOOR) * facing)
        if (!(level > MIN_ALPHA)) return

        sheenMatrix.setTranslate(-SHEEN_CENTRE, 0f)
        sheenMatrix.postScale(SHEEN_SPAN, SHEEN_SPAN)
        // Applied before the rotation, so the slide runs along the band's own axis rather than
        // across the screen, which is what keeps the highlight on the card as it turns.
        sheenMatrix.postTranslate(-sin(quad.yaw) * SHEEN_SLIDE, 0f)
        // Roll is taken back out, so the light stays where it is in the world while the drawing
        // spins on the card, which is the whole difference between a light and a decal.
        sheenMatrix.postRotate(SHEEN_ANGLE - quad.roll * DEGREES)
        sheenMatrix.postTranslate(CACHE_WIDTH * 0.5f, CACHE_HEIGHT * 0.5f)
        shader.setLocalMatrix(sheenMatrix)

        sheenPaint.shader = shader
        sheenPaint.alpha = toAlpha(level)
        canvas.drawRect(0f, 0f, CACHE_WIDTH, CACHE_HEIGHT, sheenPaint)
        // Cleared, because this paint is a field and a stale shader is the kind of bug that
        // only shows up on the next thing drawn with it.
        sheenPaint.shader = null
    }

    /**
     * Today's disc, given a live bloom on top of the one baked into the picture, and then
     * reprinted over it.
     *
     * The reprint is the point. A halo drawn over the disc puts the accent back across the
     * number written on it, and a date you cannot read is not a marked date; drawing the disc and
     * its numeral again over the bloom means the light grades right up to the disc's edge and
     * stops there, at full [Theme.onAccent] contrast. It costs one circle and one glyph.
     *
     * Strength is a pure function of the fade and the facing, and quantised on the way in so a
     * settled world hands [Glow] the same target frame after frame and its ring table is not
     * rebuilt behind a plate that has not moved.
     */
    private fun drawBloom(
        canvas: Canvas,
        theme: Theme,
        plate: Plate,
        month: YearMonth,
        strength: Float,
        facing: Float,
    ) {
        // Only the month that owns the date wears the disc, exactly as the rasteriser decides
        // it, or a wall of plates would bloom the same day three times over.
        if (today.monthValue != month.monthValue || today.year != month.year) return
        val index = (today.toEpochDay() - plate.startEpochDay).toInt()
        if (index < 0 || index >= MonthModel.CELLS) return

        // A card turned away keeps most of its bloom: today has to stay findable across a whole
        // year of plates, which is the one thing the baked glow cannot do once a plate is small.
        val level = BLOOM_PEAK * strength * (BLOOM_FLOOR + (1f - BLOOM_FLOOR) * facing)
        discCentre(index, cellPoint)
        val cx = cellPoint[0]
        val cy = cellPoint[1]
        glow.draw(canvas, cx, cy, discRadius * BLOOM_REACH, theme.accent, quantise(level))

        fillPaint.color = theme.accent
        canvas.drawCircle(cx, cy, discRadius, fillPaint)
        dayPaint.typeface = medium
        dayPaint.color = theme.onAccent
        canvas.drawText(
            DAY_LABELS[today.dayOfMonth],
            cx,
            cy - (dayPaint.descent() + dayPaint.ascent()) * 0.5f,
            dayPaint,
        )
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
        // Both washes go under the rules and the numbers, so they read as the paper the month is
        // printed on rather than as a highlight laid over it.
        if (densityTint) paintDensity(canvas, month, cells, loads)
        paintWarmth(canvas, month, cells)
        paintRules(canvas, theme)
        paintHead(canvas, theme, month)
        paintCells(canvas, theme, month, cells, loads)

        // Released straight away: a Canvas holding a bitmap keeps it alive, and this one is a
        // field that outlives every plate it draws.
        canvas.setBitmap(null)
        return Plate(bitmap, cells[0].toEpochDay())
    }

    /**
     * The card the month sits on: its plane, the light along its top edge and the shade along
     * its bottom one, and the hairline that separates it from the world behind.
     *
     * The rim is a single stroke carrying a vertical gradient rather than two strokes, and it is
     * a hairline wide, so the whole of this costs no contrast anywhere a number is written. It is
     * what makes the card read as having a thickness once the shadow has put it above the ground.
     */
    private fun paintGround(canvas: Canvas, theme: Theme) {
        fillPaint.color = theme.surface
        canvas.drawRoundRect(plateEdge, cornerRadius, cornerRadius, fillPaint)

        val rim = rimShader
        if (rim != null) {
            val inset = ruleWidth
            cellRect.set(plateEdge)
            cellRect.inset(inset, inset)
            rimPaint.shader = rim
            rimPaint.strokeWidth = ruleWidth
            val radius = max(cornerRadius - inset, 0f)
            canvas.drawRoundRect(cellRect, radius, radius, rimPaint)
            rimPaint.shader = null
        }

        strokePaint.color = theme.hairline
        strokePaint.strokeWidth = ruleWidth
        canvas.drawRoundRect(plateEdge, cornerRadius, cornerRadius, strokePaint)
    }

    /**
     * The busy-ness wash, as a field rather than as a table of blocks.
     *
     * A flat rectangle per cell drew the month as a bar chart with hard edges on days that have
     * nothing to do with each other; one soft blob per cell, wider than its cell, lets a run of
     * busy days pool into a single shape and a lone one stay a lone one. The blob is elliptical
     * so it fills a cell that is taller than it is wide, and the ramp holds near full over the
     * middle of the cell so the day still reads as tinted rather than as blurred.
     */
    private fun paintDensity(
        canvas: Canvas,
        month: YearMonth,
        cells: List<LocalDate>,
        loads: Map<LocalDate, DayLoad>,
    ) {
        val shader = densityShader ?: return
        val rx = cellWidth * DENSITY_REACH
        val ry = rowHeight * DENSITY_REACH
        washPaint.shader = shader
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
            washMatrix.setScale(rx, ry)
            washMatrix.postTranslate(cellRect.centerX(), cellRect.centerY())
            shader.setLocalMatrix(washMatrix)
            washPaint.alpha = toAlpha(strength)
            washRect.set(
                cellRect.centerX() - rx,
                cellRect.centerY() - ry,
                cellRect.centerX() + rx,
                cellRect.centerY() + ry,
            )
            canvas.drawRect(washRect, washPaint)
        }
        washPaint.shader = null
        washPaint.alpha = OPAQUE
    }

    /**
     * A breath of the accent over the days around today, so the week today sits in is warmer
     * than the weeks either side of it before anything is read.
     *
     * Far wider and far fainter than the bloom: the bloom says which day, this says roughly
     * where on the card to look. At [WARMTH_PEAK] it is weaker than a single event's density
     * tint, which is the test — a wash that could hide a date has stopped being atmosphere.
     */
    private fun paintWarmth(canvas: Canvas, month: YearMonth, cells: List<LocalDate>) {
        val shader = warmthShader ?: return
        // Only the owning month, exactly as the disc is decided, so a trailing cell holding
        // today in a neighbouring month does not light that month up too.
        if (!inMonth(today, month)) return
        val index = (today.toEpochDay() - cells[0].toEpochDay()).toInt()
        if (index < 0 || index >= MonthModel.CELLS) return

        discCentre(index, cellPoint)
        washMatrix.setScale(warmthRadius, warmthRadius)
        washMatrix.postTranslate(cellPoint[0], cellPoint[1])
        shader.setLocalMatrix(washMatrix)
        washPaint.shader = shader
        canvas.save()
        canvas.clipPath(platePath)
        canvas.drawRect(
            cellPoint[0] - warmthRadius,
            cellPoint[1] - warmthRadius,
            cellPoint[0] + warmthRadius,
            cellPoint[1] + warmthRadius,
            washPaint,
        )
        canvas.restore()
        washPaint.shader = null
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
        // The month today falls in is named in the accent. On a wall of twelve that is what the
        // eye lands on first — today's disc is one cell in five hundred and cannot carry the
        // page by itself — and it stays true wherever the plate is seen, so a month does not
        // change colour on its way from the grid to the front of the corridor.
        titlePaint.color = if (month == YearMonth.from(today)) theme.accent else theme.ink
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
        for (index in 0 until MonthModel.CELLS) {
            val date = cells[index]
            val column = index % MonthModel.COLUMNS
            val here = inMonth(date, month)
            // The same centre the live bloom reaches for, from the same function, so the halo
            // drawn every frame cannot drift off the disc baked once.
            discCentre(index, cellPoint)
            val cx = cellPoint[0]
            val cy = cellPoint[1]
            val stackTop = cy - discRadius

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

        shadowSpread = px(SHADOW_SPREAD_DP)
        shadowDrop = px(SHADOW_DROP_DP)
        warmthRadius = cellWidth * WARMTH_REACH

        // The card's own outline, held as both a rectangle and a path: the rectangle is what
        // the ground and the shadow are drawn from, the path is what everything laid over the
        // picture is clipped to.
        val half = ruleWidth * 0.5f
        plateEdge.set(half, half, CACHE_WIDTH - half, CACHE_HEIGHT - half)
        platePath.rewind()
        platePath.addRoundRect(plateEdge, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    /**
     * Every gradient, every derived colour and the shadow's ring table, built once per palette.
     *
     * Nothing here may run in a frame: a `LinearGradient` is an allocation and [Oklch] is a few
     * hundred floating-point operations, so both live on this side of the line and the draw path
     * only ever moves what this made.
     */
    private fun rebuildLight(theme: Theme) {
        // A shadow is the absence of the page it falls on, so it is the canvas taken most of the
        // way to black rather than a black of its own: on a warm palette it stays warm.
        shadowColour = Oklch.lighten(theme.canvas, -SHADOW_SINK)
        shadowPeak = if (theme.dark) SHADOW_PEAK_DARK else SHADOW_PEAK_LIGHT
        // Zeroed rather than rebuilt: the first plate drawn is what says how many bands its
        // penumbra is worth, and it will build the table then.
        shadowBuiltFor = 0

        // The two lobes are the plane's own colour walked towards light and towards dark, so the
        // sheen carries the palette's temperature instead of laying grey over it. Which lobe
        // does the work depends on the skin: near-white paper cannot be lit much further, and a
        // dark card has almost no room to be shaded.
        val highTint = Oklch.lighten(theme.surface, SHEEN_LIFT)
        val shadeTint = Oklch.lighten(theme.surface, -SHEEN_SINK)
        val high = if (theme.dark) SHEEN_HIGH_DARK else SHEEN_HIGH_LIGHT
        val shade = if (theme.dark) SHEEN_SHADE_DARK else SHEEN_SHADE_LIGHT
        sheenColours[0] = withAlpha(shadeTint, 0f)
        sheenColours[1] = withAlpha(shadeTint, shade)
        sheenColours[2] = withAlpha(shadeTint, 0f)
        sheenColours[3] = withAlpha(highTint, 0f)
        sheenColours[4] = withAlpha(highTint, high)
        sheenColours[5] = withAlpha(highTint, 0f)
        sheenShader = LinearGradient(
            0f,
            0f,
            1f,
            0f,
            sheenColours,
            SHEEN_STOPS,
            // Both ends of the ramp are transparent, so clamping past them adds nothing: the
            // band can slide off the card without flooding what it leaves behind.
            Shader.TileMode.CLAMP,
        )

        // Built at full alpha and dimmed by the paint, so one gradient serves every strength the
        // density ramp asks for instead of one gradient per level of busy-ness.
        densityColours[0] = withAlpha(theme.accent, 1f)
        densityColours[1] = withAlpha(theme.accent, DENSITY_PLATEAU)
        densityColours[2] = withAlpha(theme.accent, DENSITY_SHOULDER)
        densityColours[3] = withAlpha(theme.accent, 0f)
        densityShader =
            RadialGradient(0f, 0f, 1f, densityColours, DENSITY_STOPS, Shader.TileMode.CLAMP)

        warmthColours[0] = withAlpha(theme.accent, WARMTH_PEAK)
        warmthColours[1] = withAlpha(theme.accent, WARMTH_PEAK * WARMTH_KNEE)
        warmthColours[2] = withAlpha(theme.accent, WARMTH_PEAK * WARMTH_SHOULDER)
        warmthColours[3] = withAlpha(theme.accent, 0f)
        warmthShader =
            RadialGradient(0f, 0f, 1f, warmthColours, WARMTH_STOPS, Shader.TileMode.CLAMP)

        val rimTop = Oklch.lighten(theme.surface, RIM_LIFT)
        val rimBottom = Oklch.lighten(theme.surface, -RIM_SINK)
        rimColours[0] = withAlpha(rimTop, RIM_TOP_ALPHA)
        rimColours[1] = withAlpha(rimTop, 0f)
        rimColours[2] = withAlpha(rimBottom, 0f)
        rimColours[3] = withAlpha(rimBottom, RIM_BOTTOM_ALPHA)
        rimShader = LinearGradient(
            0f,
            0f,
            0f,
            CACHE_HEIGHT,
            rimColours,
            RIM_STOPS,
            Shader.TileMode.CLAMP,
        )
    }

    /**
     * Turns the shadow's falloff into per-band alphas for a given band count. Bands are laid down
     * over one another, so each only has to add the difference between the cover already there
     * and the cover its own band is meant to reach — the same accounting [Glow] does for a
     * circle, which is what stops thirteen stacked rectangles adding up to a black slab.
     *
     * It is worth recomputing rather than storing one table per count: it is thirteen lines of
     * arithmetic, it only runs when a plate has changed size enough to want a different count,
     * and the alternative is a table that silently no longer sums to [peak].
     */
    private fun buildShadowProfile(peak: Float, bands: Int) {
        var covered = 0f
        var i = 0
        while (i < bands) {
            // Sampled at the middle of the band rather than its edge, so the stack straddles the
            // ideal falloff instead of sitting consistently inside or outside it.
            val mid = 1f - (i + 0.5f) / bands
            val k = 1f - mid * mid
            val target = k * k * peak
            val add = if (covered >= 1f) 0f else ((target - covered) / (1f - covered))
            val clamped = add.coerceIn(0f, 1f)
            shadowAlpha[i] = clamped
            covered += clamped * (1f - covered)
            i++
        }
        shadowBuiltFor = bands
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

    /**
     * The centre of one cell's day disc, in cache pixels, written into [out] as x,y.
     *
     * The disc is not centred in its cell: the number and its event marks are stacked, and the
     * stack is centred instead. Both the baked picture and the live bloom come here for the
     * answer, because a halo half a millimetre off its disc is worse than no halo.
     */
    private fun discCentre(index: Int, out: FloatArray) {
        val clamped = index.coerceIn(0, MonthModel.CELLS - 1)
        val column = clamped % MonthModel.COLUMNS
        val row = clamped / MonthModel.COLUMNS
        val stackHeight = 2f * discRadius + stackGap + 2f * markRadius
        out[0] = gridLeft + (column + 0.5f) * cellWidth
        out[1] = gridTop + row * rowHeight + (rowHeight - stackHeight) * 0.5f + discRadius
    }

    // Snapped to a coarse ladder before it reaches Glow, whose ring table is rebuilt whenever
    // the strength it is handed differs from the last one. Twelve plates each passing a slightly
    // different float would rebuild it twelve times a frame for a difference no eye can see.
    private fun quantise(value: Float): Float =
        (value * BLOOM_STEPS + 0.5f).toInt().coerceIn(0, BLOOM_STEPS.toInt()) / BLOOM_STEPS

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

        // The baked half of today's light. It is drawn under the disc at a fixed strength and
        // pulled back from what it used to be, because the live bloom now adds to it: the two
        // together sit where the baked one alone used to, and the pair only reaches further
        // when the plate is square on and worth looking at.
        const val GLOW_STRENGTH = 0.32f

        // The live half. Kept under the baked strength on purpose: this is laid over a picture
        // that already has the numbers 7, 9 and 15 inside the halo's reach, and a bloom that
        // makes a neighbouring date harder to read has stopped marking today and started
        // hiding a day. Its reach is a little wider than the baked one so the two stacks of
        // rings fall between one another: at equal radii they land on the same eight steps and
        // the halo shows its rings, which was visible on the first render of this.
        const val BLOOM_PEAK = 0.26f
        const val BLOOM_REACH = 2.85f

        // What survives when the card is edge on. Today has to stay findable across a whole
        // year of plates, which is the one thing the baked glow cannot do once a plate is a
        // finger wide, so the bloom does not fade all the way out with the facing.
        const val BLOOM_FLOOR = 0.45f
        const val BLOOM_STEPS = 24f

        // Five events is a full day: past that the wash would only lose its own gradations.
        const val DENSITY_FULL = 5

        // The blob is soft, so its average over a cell is well under its peak; these are higher
        // than the flat fills they replaced and land at about the same weight on the page.
        // Deliberately the same peak the flat blocks used, so the shape changed here and the
        // weight did not: the tint under a full day's number is what costs that number contrast,
        // and the plateau is at full strength exactly where the number is. Measured on the
        // render, a five-event day reads 13.5:1 against its own paper against 17.8:1 for an
        // empty one, which is where it stood before.
        const val DENSITY_MIN = 0.06f
        const val DENSITY_MAX = 0.20f

        // The reach is picked so that the ramp is at exactly half strength on the cell's own
        // boundary, in both axes at once — hence one reach scaled by the cell's width and by its
        // row height rather than a circle. Two busy neighbours then hand over across the line
        // and sum back to the tint each of them has in the middle, which is what makes a run of
        // busy days one shape instead of a row of pills with dark seams between them. A lone
        // busy day still reaches zero before its neighbour's number, so nothing is tinted that
        // has no events. DENSITY_SEAM must stay equal to 0.5 / DENSITY_REACH for that to hold.
        const val DENSITY_REACH = 0.85f
        const val DENSITY_SEAM = 0.59f
        const val DENSITY_PLATEAU = 0.92f
        const val DENSITY_SHOULDER = 0.50f
        val DENSITY_STOPS = floatArrayOf(0f, 0.42f, DENSITY_SEAM, 1f)

        // The warmth around today, in cell widths. Weaker than one event's density tint, which
        // is the whole test for it: this may say where to look and may not hide a date.
        const val WARMTH_REACH = 1.85f
        const val WARMTH_PEAK = 0.05f
        const val WARMTH_KNEE = 0.60f
        const val WARMTH_SHOULDER = 0.20f
        val WARMTH_STOPS = floatArrayOf(0f, 0.32f, 0.68f, 1f)

        // The contact shadow. The band count is not a taste: at eight, sampling the render found
        // steps of seven eight-bit levels between neighbouring bands, which contours visibly on
        // a flat page. Thirteen brings the worst step under four levels, which the background's
        // own grain finishes off. Each band is clipped to the fringe it is seen in, so the count
        // buys smoothness rather than overdraw.
        const val SHADOW_RINGS = 13
        const val SHADOW_RINGS_MIN = 3
        const val SHADOW_PIXELS_PER_BAND = 5f
        const val SHADOW_SPREAD_DP = 26f
        const val SHADOW_DROP_DP = 13f

        // A dark plane has less room under it than a light one, so the shadow has to be laid on
        // harder there to read at all — and even then it can only ever be a hint, which is why
        // the card's edge light is what does the work of standing it up in the dark skin.
        const val SHADOW_PEAK_LIGHT = 0.15f
        const val SHADOW_PEAK_DARK = 0.34f
        const val SHADOW_SINK = 0.9f

        // Where a plate stops being read and starts being scenery. Below this it keeps its
        // picture and loses its shadow, sheen and bloom, which is most of the blended fill in
        // the world and none of the information.
        const val LIT_FADE_START = 0.30f
        const val LIT_FADE_END = 0.66f

        // The sheen. Its span is a little over the card's diagonal, so one lobe can sit off
        // each side and the band can slide without either end clamping onto the card.
        const val PLATE_DIAGONAL = 819.61f
        const val SHEEN_SPAN = PLATE_DIAGONAL * 0.95f
        const val SHEEN_CENTRE = 0.5f
        const val SHEEN_SLIDE = SHEEN_SPAN * 0.26f
        const val SHEEN_ANGLE = 230f
        const val SHEEN_LIFT = 0.5f
        const val SHEEN_SINK = 0.5f
        const val SHEEN_FLOOR = 0.45f

        // The peaks, and the line this whole file is drawn against. Theme walks ink and surface
        // to a measured contrast; a wash over the card spends that budget and nothing gives it
        // back. Measured on the render, the worst cell under the band loses about a tenth of
        // its contrast ratio and stays far above the readable floor. Raising these is how the
        // plate stops being a calendar.
        // Which lobe carries the effect is not symmetric. Near-white paper cannot be lit any
        // further, so on the light skin the shading is the whole of it; a dark card has almost
        // no room to be shaded, so there the highlight does the work.
        const val SHEEN_HIGH_LIGHT = 0.030f
        const val SHEEN_SHADE_LIGHT = 0.055f
        const val SHEEN_HIGH_DARK = 0.042f
        const val SHEEN_SHADE_DARK = 0.085f
        val SHEEN_STOPS = floatArrayOf(0f, 0.22f, 0.44f, 0.56f, 0.74f, 1f)

        // The card's edge light and edge shade. A hairline wide and at the very rim, so it buys
        // the card a thickness without laying a single unit of alpha over a number.
        const val RIM_LIFT = 0.5f
        const val RIM_SINK = 0.5f
        const val RIM_TOP_ALPHA = 0.55f
        const val RIM_BOTTOM_ALPHA = 0.40f
        val RIM_STOPS = floatArrayOf(0f, 0.42f, 0.60f, 1f)

        const val DEGREES = 57.29578f

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
