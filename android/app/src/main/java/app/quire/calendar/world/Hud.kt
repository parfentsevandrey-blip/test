package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import app.quire.calendar.R
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.clamp
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * The part of the screen that does not move: the month title in the top corner and the five
 * targets along the bottom, drawn in screen space over whatever the world is doing behind them.
 *
 * Everything here is hand-drawn onto the host's canvas. There is no child view, no drawable and
 * no system widget, for one reason: the world underneath is a perspective scene that repaints
 * every frame, and a real view sitting on top of it would be composited on its own layer, lag
 * the scene by a frame under load, and carry a theme this app does not use. Drawing the chrome
 * in the same pass as the world keeps the two welded together and keeps every colour coming
 * from [Theme].
 *
 * The icons are [Path]s built once in [relayout] and re-used every frame; nothing in [draw]
 * allocates. The host owns the frame loop: call [advance] each frame while it returns true, and
 * call [press] from the touch handler before running the action so the target has something to
 * do while the world changes.
 *
 * Not thread safe — the paints and paths are shared fields. Call it from the thread that draws.
 */
class Hud(context: Context) {

    // Only read for the locale that uppercases the labels. Application resources follow a
    // locale change and holding them cannot leak an activity.
    private val resources = context.applicationContext.resources

    // ---- configuration -------------------------------------------------

    private var theme: Theme? = null
    private var metrics: Metrics = Metrics(1f)
    private var motion: MotionProfile = MotionProfile.STANDARD
    private var locale: Locale = currentLocale()

    private var viewWidth: Float = 0f
    private var viewHeight: Float = 0f
    private var topInset: Float = 0f
    private var bottomInset: Float = 0f

    // ---- per-frame state, never allocated in draw ----------------------

    private val icons: Array<Path> = Array(ACTIONS) { Path() }

    // One spring per target, resting at 1 and dipped by press(). Held apart from the icons so a
    // second tap can retarget a spring that is still returning, instead of restarting it.
    private val springs: Array<Spring> = Array(ACTIONS) { Spring(1f) }

    private val labels: Array<String> = Array(ACTIONS) { "" }

    private val washRect = RectF()
    private val scratch = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    // A real 500 weight only became addressable in API 28; before that the medium face has to be
    // asked for by family name, which is the same font on every device that ships it and falls
    // back to the regular face on any device that does not.
    private val medium: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, MEDIUM_WEIGHT, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = LABEL_TRACKING
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.LEFT
    }
    private val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        // Tabular figures: the year sits beside a name whose width changes every month, and
        // proportional digits would make it shuffle sideways as well.
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.LEFT
    }

    // ---- geometry, all in screen pixels --------------------------------

    private var ruleWidth: Float = 1f
    private var iconSize: Float = 0f
    private var iconGap: Float = 0f
    private var labelHeight: Float = 0f
    private var barContent: Float = 0f
    private var washInset: Float = 0f
    private var washRadius: Float = 0f
    private var titleGap: Float = 0f
    private var labelNudge: Float = 0f

    private var barTop: Float = 0f
    private var cellWidth: Float = 0f
    private var iconCentreY: Float = 0f
    private var labelBaseline: Float = 0f
    private var titleLeft: Float = 0f
    private var titleBaseline: Float = 0f
    private var washTop: Float = 0f
    private var washBottom: Float = 0f
    private var washHalf: Float = 0f

    // The month name is measured once per name rather than once per frame: it changes when the
    // world lands on a new month, which is far less often than sixty times a second.
    private var measuredTitle: String? = null
    private var measuredWidth: Float = 0f

    init {
        rebuildLabels()
        relayout()
    }

    /**
     * How tall the bar is, including the bottom safe inset it sits over, so the world above can
     * keep its content clear of it. Moves with the metrics and with [setSafeInsets].
     */
    val barHeight: Float
        get() = barContent + bottomInset

    /**
     * Every input that changes how the foreground looks or moves. Cheap to call with unchanged
     * values, so pass the current state on each configuration change rather than working out
     * whether it moved.
     *
     * @param theme the only source of colour here; nothing in this file names one.
     * @param metrics the only source of size, including the type sizes.
     * @param motion the app's liveliness setting, which every press spring adopts.
     */
    fun configure(theme: Theme, metrics: Metrics, motion: MotionProfile) {
        val nextLocale = currentLocale()
        val sizesMoved = !sameShape(metrics, this.metrics)
        this.theme = theme
        this.metrics = metrics
        this.motion = motion
        if (nextLocale != locale) {
            locale = nextLocale
            rebuildLabels()
        }
        var i = 0
        while (i < ACTIONS) {
            springs[i].profile(motion)
            i++
        }
        if (sizesMoved) {
            relayout()
            place()
        }
    }

    /**
     * The window's own insets, so the title clears the status bar and the bar clears the gesture
     * area. The strip is still painted through the bottom inset — a bar that stops short of the
     * edge leaves a stripe of world under the thumb.
     */
    fun setSafeInsets(top: Float, bottom: Float) {
        val safeTop = max(0f, top)
        val safeBottom = max(0f, bottom)
        if (safeTop == topInset && safeBottom == bottomInset) return
        topInset = safeTop
        bottomInset = safeBottom
        place()
    }

    /** The size of the surface being drawn into, which is what the targets are spread across. */
    fun layout(width: Float, height: Float) {
        if (width == viewWidth && height == viewHeight) return
        viewWidth = max(0f, width)
        viewHeight = max(0f, height)
        place()
    }

    /**
     * Paints the whole foreground: title, strip, five targets.
     *
     * [title] is the month name, [subtitle] the year; [level] 0 year 1 month 2 day. The level
     * only chooses which target is lit — the year wall lights `year`, and the month and day
     * views light `today`, since that is the entry that returns to them. `add`, `search` and
     * `settings` are actions rather than places and are never lit.
     *
     * @param alpha 0..1 fade for everything drawn here, for a world that wants the screen.
     */
    fun draw(canvas: Canvas, title: String, subtitle: String, level: Int, alpha: Float) {
        val theme = this.theme ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return
        // Below one step of an eight-bit alpha there is nothing to see, and the guard is written
        // positively so a NaN alpha draws nothing rather than something opaque.
        if (!(alpha > MIN_ALPHA)) return
        val fade = min(alpha, 1f)
        drawTitle(canvas, theme, title, subtitle, fade)
        drawBar(canvas, theme, activeAction(level), fade)
    }

    /**
     * Moves every press spring on by [dt] seconds; returns false on the frame nothing here needs
     * another one, which is the host's cue to stop asking the clock for frames.
     */
    fun advance(dt: Float): Boolean {
        var moving = false
        var i = 0
        while (i < ACTIONS) {
            if (springs[i].advance(dt)) moving = true
            i++
        }
        return moving
    }

    /**
     * The action id under a point, or 0 where there is nothing to hit.
     *
     * @return [ACTION_TODAY], [ACTION_YEAR], [ACTION_ADD], [ACTION_SEARCH], [ACTION_SETTINGS],
     *     or 0 for anything above the strip, including the title.
     */
    fun actionAt(x: Float, y: Float): Int {
        if (viewWidth <= 0f || viewHeight <= 0f || cellWidth <= 0f) return 0
        if (y < barTop || y > viewHeight) return 0
        if (x < 0f || x > viewWidth) return 0
        val index = (x / cellWidth).toInt()
        if (index < 0 || index >= ACTIONS) return 0
        return index + 1
    }

    /**
     * Dips the entry's icon so it can spring back while the action runs, which is what makes a
     * tap feel answered before the world has finished moving. Arming it is all this does: the
     * host still owes it [advance] calls, and the action itself is the caller's to run.
     *
     * @param action one of the ids [actionAt] returns; anything else is ignored.
     */
    fun press(action: Int) {
        val index = action - 1
        if (index < 0 || index >= ACTIONS) return
        // Reduced motion is a contract about arriving now. A dip that springs back is motion
        // with nothing to say, so it is skipped rather than hurried.
        if (motion.instant) return
        val spring = springs[index]
        spring.snapTo(PRESS_DIP)
        spring.target = 1f
    }

    // ---- drawing -------------------------------------------------------

    /** The month name in ink with its year beside it in inkGhost, hard into the top corner. */
    private fun drawTitle(
        canvas: Canvas,
        theme: Theme,
        title: String,
        subtitle: String,
        fade: Float,
    ) {
        var x = titleLeft
        if (title.isNotEmpty()) {
            titlePaint.color = withAlpha(theme.ink, fade)
            canvas.drawText(title, x, titleBaseline, titlePaint)
            x += titleWidth(title) + titleGap
        }
        if (subtitle.isEmpty()) return
        yearPaint.color = withAlpha(theme.inkGhost, fade)
        canvas.drawText(subtitle, x, titleBaseline, yearPaint)
    }

    /** The surface strip, its hairline, and the five icon-over-label targets on top of it. */
    private fun drawBar(canvas: Canvas, theme: Theme, active: Int, fade: Float) {
        fillPaint.color = withAlpha(theme.surface, fade)
        canvas.drawRect(0f, barTop, viewWidth, viewHeight, fillPaint)
        fillPaint.color = withAlpha(theme.hairline, fade)
        canvas.drawRect(0f, barTop, viewWidth, barTop + ruleWidth, fillPaint)

        var i = 0
        while (i < ACTIONS) {
            val centreX = (i + 0.5f) * cellWidth
            val scale = springs[i].value
            val lit = (i + 1) == active

            // The wash is read off the dip rather than kept as its own state, so it is at its
            // strongest exactly when the icon is at its smallest and gone once it is back.
            val dip = clamp((1f - scale) / (1f - PRESS_DIP), 0f, 1f)
            if (dip > 0f) {
                washRect.set(centreX - washHalf, washTop, centreX + washHalf, washBottom)
                fillPaint.color = withAlpha(theme.press, fade * dip)
                canvas.drawRoundRect(washRect, washRadius, washRadius, fillPaint)
            }

            iconPaint.color = withAlpha(if (lit) theme.accent else theme.inkMuted, fade)
            canvas.save()
            canvas.translate(centreX, iconCentreY)
            if (scale != 1f) canvas.scale(scale, scale)
            canvas.drawPath(icons[i], iconPaint)
            canvas.restore()

            labelPaint.typeface = if (lit) medium else regular
            labelPaint.color = withAlpha(if (lit) theme.accent else theme.inkFaint, fade)
            // Tracking is added after every glyph including the last, so a centred label sits
            // half a space right of true centre unless it is pulled back.
            canvas.drawText(labels[i], centreX - labelNudge, labelBaseline, labelPaint)
            i++
        }
    }

    private fun activeAction(level: Int): Int =
        if (level <= LEVEL_YEAR) ACTION_YEAR else ACTION_TODAY

    private fun titleWidth(title: String): Float {
        if (title != measuredTitle) {
            measuredTitle = title
            measuredWidth = titlePaint.measureText(title)
        }
        return measuredWidth
    }

    // ---- layout --------------------------------------------------------

    /** Everything that depends on the metrics alone, including the icon paths. */
    private fun relayout() {
        ruleWidth = metrics.hairline
        iconSize = metrics.dp(ICON_DP)
        iconGap = metrics.dp(ICON_GAP_DP)
        iconPaint.strokeWidth = metrics.dp(ICON_STROKE_DP)

        labelPaint.textSize = metrics.sp(LABEL_DP)
        labelHeight = labelPaint.descent() - labelPaint.ascent()
        labelNudge = labelPaint.textSize * LABEL_TRACKING * 0.5f

        titlePaint.textSize = metrics.sp(TITLE_DP)
        yearPaint.textSize = metrics.sp(YEAR_DP)
        titleGap = metrics.dp(TITLE_GAP_DP)
        // The cached width was measured at the old type size.
        measuredTitle = null

        // The bar is as tall as what it holds rather than a number of its own, so a larger type
        // preference grows the strip instead of crushing the label against the icon.
        barContent = iconSize + iconGap + labelHeight + 2f * metrics.dp(BAR_PAD_DP)

        washInset = metrics.dp(WASH_INSET_DP)
        washRadius = metrics.radiusSmall

        buildIcons()
    }

    /** Everything that depends on the surface size and the insets. */
    private fun place() {
        if (viewWidth <= 0f || viewHeight <= 0f) return
        cellWidth = viewWidth / ACTIONS
        barTop = viewHeight - barHeight

        val contentTop = barTop + ruleWidth
        val contentBottom = viewHeight - bottomInset
        val stack = iconSize + iconGap + labelHeight
        val stackTop = contentTop + max(0f, (contentBottom - contentTop - stack) * 0.5f)
        iconCentreY = stackTop + iconSize * 0.5f
        labelBaseline = stackTop + iconSize + iconGap - labelPaint.ascent()

        washTop = contentTop + washInset
        washBottom = max(washTop, contentBottom - washInset)
        washHalf = max(0f, cellWidth * 0.5f - washInset)

        titleLeft = metrics.gutter
        titleBaseline = topInset + metrics.gutter - titlePaint.ascent()
    }

    private fun rebuildLabels() {
        // Uppercased here rather than in the string table, because which letter is a capital is
        // the locale's business: a Turkish i does not become an I.
        var i = 0
        while (i < ACTIONS) {
            labels[i] = resources.getString(WORD_IDS[i]).uppercase(locale)
            i++
        }
    }

    // ---- icons ---------------------------------------------------------

    /**
     * The five icons, drawn about their own centre so a press only has to scale the canvas. All
     * five are strokes of one width, which is what makes them read as one set.
     */
    private fun buildIcons() {
        val half = iconSize * 0.5f
        buildRing(icons[ACTION_TODAY - 1], half)
        buildGrid(icons[ACTION_YEAR - 1], half)
        buildCross(icons[ACTION_ADD - 1], half)
        buildLens(icons[ACTION_SEARCH - 1], half)
        buildSliders(icons[ACTION_SETTINGS - 1], half)
    }

    /** Today: a ring, the same mark the world puts round the current day. */
    private fun buildRing(path: Path, half: Float) {
        path.rewind()
        path.addCircle(0f, 0f, half * RING_RADIUS, Path.Direction.CW)
    }

    /** Year: two by two, the wall of months seen from far enough away to be a grid. */
    private fun buildGrid(path: Path, half: Float) {
        path.rewind()
        val side = half * GRID_SIDE
        val step = (side + half * GRID_GAP) * 0.5f
        val radius = side * GRID_CORNER
        square(path, -step, -step, side, radius)
        square(path, step, -step, side, radius)
        square(path, -step, step, side, radius)
        square(path, step, step, side, radius)
    }

    /** Add: a cross, the one icon that may not be anything else. */
    private fun buildCross(path: Path, half: Float) {
        path.rewind()
        val reach = half * CROSS_REACH
        path.moveTo(-reach, 0f)
        path.lineTo(reach, 0f)
        path.moveTo(0f, -reach)
        path.lineTo(0f, reach)
    }

    /** Search: a magnifier, its handle leaving the lens along the diagonal it touches. */
    private fun buildLens(path: Path, half: Float) {
        path.rewind()
        val radius = half * LENS_RADIUS
        val centre = -half * LENS_OFFSET
        path.addCircle(centre, centre, radius, Path.Direction.CW)
        val touch = centre + radius * DIAGONAL
        path.moveTo(touch, touch)
        path.lineTo(half * LENS_HANDLE, half * LENS_HANDLE)
    }

    /** Settings: three sliders, each rule broken where its knob sits. */
    private fun buildSliders(path: Path, half: Float) {
        path.rewind()
        val left = -half * SLIDER_REACH
        val right = half * SLIDER_REACH
        val knob = half * SLIDER_KNOB
        var row = 0
        while (row < SLIDER_ROWS) {
            val y = (row - 1) * half * SLIDER_ROW_GAP
            val at = half * KNOB_AT[row]
            path.moveTo(left, y)
            path.lineTo(at - knob, y)
            path.moveTo(at + knob, y)
            path.lineTo(right, y)
            path.addCircle(at, y, knob, Path.Direction.CW)
            row++
        }
    }

    private fun square(path: Path, cx: Float, cy: Float, side: Float, radius: Float) {
        val half = side * 0.5f
        scratch.set(cx - half, cy - half, cx + half, cy + half)
        path.addRoundRect(scratch, radius, radius, Path.Direction.CW)
    }

    // ---- helpers -------------------------------------------------------

    private fun currentLocale(): Locale {
        val locales = resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    // Metrics has no equality of its own, and callers rebuild it freely, so two of them count as
    // the same when they would hand back the same numbers.
    private fun sameShape(a: Metrics, b: Metrics): Boolean =
        a === b || (a.scale == b.scale && a.hairline == b.hairline && a.dp(1f) == b.dp(1f))

    private fun withAlpha(colour: Int, alpha: Float): Int {
        // The colour's own alpha is scaled rather than replaced, so a theme colour that is
        // already a wash — the hairline, the press — fades from what it is instead of to full.
        val source = (colour ushr 24) and 0xFF
        return (toAlpha(source / 255f * alpha) shl 24) or (colour and RGB_MASK)
    }

    private fun toAlpha(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    /**
     * The action ids [actionAt] reports and [press] takes, so a host can name them rather than
     * carry its own copy of the numbering.
     */
    companion object {

        /** Jumps the world back to the current day; lit at the month and day levels. */
        const val ACTION_TODAY: Int = 1

        /** Pulls back to the wall of months; lit at the year level. */
        const val ACTION_YEAR: Int = 2

        /** Starts a new event. */
        const val ACTION_ADD: Int = 3

        /** Opens search. */
        const val ACTION_SEARCH: Int = 4

        /** Opens the settings layer. */
        const val ACTION_SETTINGS: Int = 5

        /** How many targets the bar holds, and the width it divides itself into. */
        const val ACTIONS: Int = 5

        // The zoomed-out level, the one that lights the year entry rather than today.
        private const val LEVEL_YEAR = 0

        private const val MEDIUM_WEIGHT = 500

        private const val ICON_DP = 22f
        private const val ICON_GAP_DP = 5f
        private const val ICON_STROKE_DP = 1.7f
        private const val BAR_PAD_DP = 9f
        private const val LABEL_DP = 9.5f
        private const val LABEL_TRACKING = 0.09f

        private const val TITLE_DP = 28f
        private const val YEAR_DP = 15f
        private const val TITLE_GAP_DP = 7f

        private const val WASH_INSET_DP = 4f

        // Far enough for the dip to be seen at 22dp, near enough that it never reads as a
        // separate thing shrinking on the bar.
        private const val PRESS_DIP = 0.78f

        // Icon geometry, every number a fraction of half the icon box, so the whole set scales
        // with one measurement and none of them can drift apart.
        private const val RING_RADIUS = 0.80f

        private const val GRID_SIDE = 0.70f
        private const val GRID_GAP = 0.22f
        private const val GRID_CORNER = 0.28f

        private const val CROSS_REACH = 0.76f

        private const val LENS_RADIUS = 0.52f
        private const val LENS_OFFSET = 0.16f
        private const val LENS_HANDLE = 0.82f

        private const val SLIDER_ROWS = 3
        private const val SLIDER_REACH = 0.82f
        private const val SLIDER_KNOB = 0.15f
        private const val SLIDER_ROW_GAP = 0.56f

        // Where each slider's knob sits along its rule. Deliberately uneven: three knobs in a
        // column would read as a list, and the point of the icon is that they have been set.
        private val KNOB_AT = floatArrayOf(0.30f, -0.26f, 0.10f)

        // Half the square root of two: where a 45-degree diagonal leaves a unit circle.
        private const val DIAGONAL = 0.70710678f

        private const val MIN_ALPHA = 1f / 255f
        private const val RGB_MASK = 0x00FFFFFF

        // The label under each icon, in target order, read from the string table so the bar is
        // translated with the rest of the app.
        private val WORD_IDS = intArrayOf(
            R.string.today,
            R.string.year,
            R.string.add,
            R.string.search,
            R.string.settings,
        )
    }
}
