package app.quire.calendar.world

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.quire.calendar.R
import app.quire.calendar.core.CalendarSource
import app.quire.calendar.core.MonthModel
import app.quire.engine.anim.Decay
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.Timeline
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.anim.smoothstep
import app.quire.engine.design.Metrics
import app.quire.engine.design.Oklch
import app.quire.engine.design.SystemScheme
import app.quire.engine.design.Theme
import app.quire.engine.fx.Noise
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Row kinds. A small integer beats a sealed hierarchy here: every row is drawn and hit-tested by
// the same two switches, and nothing outside this file ever sees one.
private const val KIND_SECTION: Int = 0
private const val KIND_SEEDS: Int = 1
private const val KIND_SEGMENTED: Int = 2
private const val KIND_SLIDER: Int = 3
private const val KIND_TOGGLE: Int = 4
private const val KIND_CHECK: Int = 5
private const val KIND_NOTE: Int = 6

// Which setting a row edits. Rows that edit nothing carry ID_NONE.
private const val ID_NONE: Int = 0
private const val ID_SEED: Int = 1
private const val ID_MODE: Int = 2
private const val ID_CONTRAST: Int = 3
private const val ID_SCALE: Int = 4
private const val ID_FIRST_DAY: Int = 5
private const val ID_MOTION: Int = 6
private const val ID_HAPTICS: Int = 7
private const val ID_DEPTH: Int = 8
private const val ID_DENSITY: Int = 9
private const val ID_MARKS: Int = 10
private const val ID_ADJACENT: Int = 11
private const val ID_CALENDAR: Int = 12
private const val ID_DYNAMIC: Int = 13

// The Size setting's range, and the step both sliders quantise to. Detents make the value text
// hold still and give the thumb somewhere to spring to when the finger lets go between them.
private const val SCALE_MIN: Float = 0.85f
private const val SCALE_MAX: Float = 1.25f
private const val SLIDER_STEPS: Int = 20

// The preview's marks are pseudo-random but must not reshuffle when the seed changes: the point
// of the preview is that only the colour moves when the colour setting moves.
private const val MARK_SEED: Int = 20250808

// How far past either end the list may be dragged, as a fraction of the viewport, and the least
// the rubber band ever gives back so a hard pull never feels like a dead stop.
private const val OVERSCROLL_FRACTION: Float = 0.22f
private const val OVERSCROLL_FLOOR: Float = 0.12f

private const val FLING_FRICTION: Float = 4.2f

// Reduced motion has no flings to speak of: the list stops where the finger left it.
private const val FLING_FRICTION_OFF: Float = 60f

private const val SCRIM_ALPHA: Float = 0.52f
private const val SHADOW_ALPHA: Float = 0.24f

private const val ELLIPSIS: String = "…"

/**
 * Everything the user can change, as one sheet drawn over the world.
 *
 * There is no platform widget anywhere in here: a switch is a track and a knob on a spring, a
 * segmented control is a pill that travels, a slider is a fill and a thumb. That is not a stunt.
 * The system animation setting scales the platform animators to zero, and this app's motion is
 * its interface — so every moving part is one of the engine's springs, advanced by the host's
 * frame time, and behaves the same on every device.
 *
 * The host owns the sheet's own travel: it drives [draw] with an openness of its own, so the
 * panel never has to know how it was summoned.
 */
class SettingsPanel(private val context: Context) {

    /**
     * Everything the panel shows and edits, emitted whole on every change.
     *
     * @property dynamic whether the palette comes from the device's own Material colour scheme
     *   rather than from [seed]; where it does, the seed row is still shown but does nothing
     *   until it is turned off.
     * @property seed the colour the whole palette is derived from, one of Theme.seeds.
     * @property dark light or dark, or null to follow whatever the system is wearing.
     * @property contrast 0..1, handed to Theme.contrastBoost.
     * @property scale 0.85..1.25, handed to Metrics.scale; every size in the app rides it.
     * @property firstDay which column the week starts in, or null to read it off the locale.
     * @property motion how lively everything that moves is allowed to be.
     * @property haptics whether a change ticks under the finger.
     * @property depth whether the world answers the tilt of the phone.
     * @property density whether a square is tinted by how full its day is.
     * @property colouredMarks whether a day's marks take their colour from their calendar.
     * @property adjacent whether the days either side of the month fill the empty squares.
     * @property hidden the calendars kept out of the grid, by provider id.
     */
    class State(
        val dynamic: Boolean,
        val seed: Int,
        val dark: Boolean?,
        val contrast: Float,
        val scale: Float,
        val firstDay: DayOfWeek?,
        val motion: MotionProfile,
        val haptics: Boolean,
        val depth: Boolean,
        val density: Boolean,
        val colouredMarks: Boolean,
        val adjacent: Boolean,
        val hidden: Set<Long>,
    )

    /**
     * One line of the sheet. Every moving part a row can own is a spring it keeps for itself, so
     * a row that is scrolled away and back resumes rather than restarts.
     */
    private class Row(
        val kind: Int,
        val id: Int,
        val title: String,
        val hint: String? = null,
        val options: Array<String>? = null,
        val source: CalendarSource? = null,
    ) {
        /** Arrival: one spring driving this row's offset and its alpha together. */
        val enter = Spring(0f)

        /** The dip under a finger, snapped to one on the tap and released back to zero. */
        val press = Spring(0f)

        /** Whatever slides: a segmented pill's left edge, a seed ring's centre, a slider's 0..1. */
        val travel = Spring(0f)

        /** Nought to one for anything that switches: a toggle's knob, a calendar's tick. */
        val knob = Spring(0f)

        /** How grown the slider's value bubble is; only a held slider has one. */
        val bubble = Spring(0f)

        var top: Float = 0f
        var height: Float = 0f
        var shownTitle: String = title
        var shownHint: String? = hint
        var lines: Array<String>? = null

        /** True while the entrance timeline owns this row's [enter], so nothing advances twice. */
        var staged: Boolean = false

        /** True once the row has been told to arrive, which for a row past the fold is late. */
        var armed: Boolean = false

        /** True for the last row before a section break, which wears no divider. */
        var lastInSection: Boolean = false

        val interactive: Boolean
            get() = kind != KIND_SECTION && kind != KIND_NOTE
    }

    // ---- what the panel is told -----------------------------------------------------------

    private var theme: Theme = Theme(Theme.seeds[0].second, systemDark(context))
    private var metrics: Metrics = Metrics(context.resources.displayMetrics.density)
    private var motionProfile: MotionProfile = MotionProfile.STANDARD

    private val bounds = RectF()
    private val sources = ArrayList<CalendarSource>(8)
    private var versionName: String = ""

    // ---- the settings themselves ----------------------------------------------------------
    //
    // Held as loose fields rather than as the State that arrived, so that emitting a change is
    // building a new State out of them: the one the host handed over is never touched.

    private var seed: Int = Theme.seeds[0].second
    private var darkMode: Boolean? = null
    private var contrast: Float = 0f
    private var scale: Float = 1f
    private var firstDay: DayOfWeek? = null
    private var dynamicColour: Boolean = true
    private var haptics: Boolean = true
    private var depth: Boolean = true
    private var density: Boolean = false
    private var colouredMarks: Boolean = true
    private var adjacent: Boolean = true
    private var hidden: Set<Long> = emptySet()

    // ---- motion ---------------------------------------------------------------------------

    private val rows = ArrayList<Row>(24)
    private var entrance = Timeline()
    private val scroll = Decay(0f)

    private var shown: Boolean = false
    private var shownOpenness: Float = 0f
    private var dragged: Row? = null

    // ---- layout ---------------------------------------------------------------------------

    private var structureDirty: Boolean = true
    private var layoutDirty: Boolean = true

    private var contentLeft: Float = 0f
    private var contentRight: Float = 0f
    private var contentHeight: Float = 0f
    private var headerHeight: Float = 0f
    private var previewTop: Float = 0f
    private var previewHeight: Float = 0f
    private var handleTop: Float = 0f
    private var titleBaseline: Float = 0f

    // ---- paint, all of it built once ------------------------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sheetTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = -0.02f
    }
    private val capsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val optionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val numeralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var shadowShader: Shader? = null
    private var scrimInk: Int = 0
    private var shadowHeight: Float = 0f

    // ---- reused scratch, so no frame allocates ---------------------------------------------

    private val text = StringBuilder(8)
    private val numerals = Array(32) { it.toString() }

    // ---- the live preview ------------------------------------------------------------------

    private val previewDays = IntArray(MonthModel.CELLS)
    private val previewInMonth = BooleanArray(MonthModel.CELLS)
    private val previewMarks = IntArray(MonthModel.CELLS)
    private val weekdayText = Array(MonthModel.COLUMNS) { "" }
    private var previewToday: Int = -1
    private var cellsFirstDay: DayOfWeek? = null
    private var cellsDirty: Boolean = true
    private var today: LocalDate = LocalDate.now()

    private var cachedTheme: Theme? = null
    private var cachedSeed: Int = 0
    private var cachedDark: Boolean = false
    private var cachedContrast: Float = -1f

    // ---- strings, resolved once ------------------------------------------------------------
    //
    // Every label is a resource, resolved here rather than inside a frame: a sheet that called
    // getString while drawing would allocate once per row per frame.

    private val locale: Locale = context.resources.configuration.locales[0]

    private val textSettings = context.getString(R.string.settings)
    private val textLook = context.getString(R.string.section_look)
    private val textSeed = context.getString(R.string.seed)
    private val textDynamic = context.getString(R.string.dynamic_colour)
    private val textDynamicHint = context.getString(R.string.dynamic_colour_hint)
    private val textMode = context.getString(R.string.mode)
    private val textContrast = context.getString(R.string.contrast)
    private val textSize = context.getString(R.string.size)
    private val modeOptions = arrayOf(
        context.getString(R.string.mode_auto),
        context.getString(R.string.mode_light),
        context.getString(R.string.mode_dark),
    )
    private val firstDayOptions = arrayOf(
        context.getString(R.string.first_day_auto),
        context.getString(R.string.first_day_mon),
        context.getString(R.string.first_day_sat),
        context.getString(R.string.first_day_sun),
    )
    private val motionOptions = arrayOf(
        context.getString(R.string.motion_off),
        context.getString(R.string.motion_calm),
        context.getString(R.string.motion_standard),
        context.getString(R.string.motion_playful),
    )

    /** Emitted whole whenever anything at all is changed; never the instance that arrived. */
    var onChange: ((State) -> Unit)? = null

    /** Called when the sheet itself asks to go away, so the host can run its own close. */
    var onDismiss: (() -> Unit)? = null

    init {
        applyProfiles()
        scroll.min = 0f
    }

    /** Whether the panel considers itself shown, which is what a host routes taps by. */
    val visible: Boolean
        get() = shown

    /** True when the list is at its top, so a host can turn a downward drag into a dismissal. */
    val scrollAtTop: Boolean
        get() = scroll.value <= metrics.hairline

    // ---- being driven -----------------------------------------------------------------------

    /** Adopts the palette, the sizes and the liveliness the rest of the world is wearing. */
    fun configure(theme: Theme, metrics: Metrics, motion: MotionProfile) {
        this.theme = theme
        this.metrics = metrics
        this.motionProfile = motion
        // A scrim is the one thing that cannot be a tier of the ink: in dark mode the ink is
        // nearly white, and a wash of it would light the world up rather than put it behind.
        scrimInk = if (theme.dark) Oklch.toSrgb(0.03f, 0f, 0f) else theme.ink
        shadowHeight = metrics.dp(18f)
        shadowShader = LinearGradient(
            0f,
            0f,
            0f,
            shadowHeight,
            fade(scrimInk, 0f),
            fade(scrimInk, SHADOW_ALPHA),
            Shader.TileMode.CLAMP,
        )
        applyProfiles()
        layoutDirty = true
    }

    /** Takes the whole area the world occupies; the sheet slides within it and covers it at 1. */
    fun setBounds(full: RectF) {
        bounds.set(full)
        layoutDirty = true
    }

    /** The calendars offered as check rows; an empty list draws the "none found" note instead. */
    fun setCalendars(sources: List<CalendarSource>) {
        this.sources.clear()
        this.sources.addAll(sources)
        structureDirty = true
        layoutDirty = true
    }

    /** The build name for the About line, which is the only thing that line is for. */
    fun setVersion(name: String) {
        versionName = name
        structureDirty = true
        layoutDirty = true
    }

    /** Opens on [state]: the list is put back to the top and the rows arrive on a stagger. */
    fun show(state: State) {
        adopt(state)
        shown = true
        today = LocalDate.now()
        cellsDirty = true
        dragged = null
        scroll.snapTo(0f)
        // Where the rows are is worked out from the openness the last frame drew at, and no frame
        // has been drawn yet. Left at zero, the sheet would read as still off the bottom of the
        // screen and turn the first tap after opening into a dismissal; assuming it is open puts
        // that tap on the row it was aimed at, which is where the sheet is heading anyway.
        shownOpenness = 1f
        ensureLayout()
        stage()
    }

    /** Closes: the host keeps drawing at a falling openness for as long as its own spring runs. */
    fun hide() {
        if (!shown) return
        shown = false
        dragged = null
        scroll.velocity = 0f
        var i = 0
        while (i < rows.size) {
            rows[i].press.snapTo(0f)
            rows[i].bubble.target = 0f
            i++
        }
    }

    /** Moves every spring on by the host's frame time; false once nothing is left to draw. */
    fun advance(dt: Float): Boolean {
        var moving = entrance.advance(dt)
        scroll.min = 0f
        scroll.max = maxScroll()
        if (scroll.advance(dt)) moving = true
        if (armRowsInView()) moving = true
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            // A staged row's arrival belongs to the timeline, which holds its delay; advancing it
            // here as well would run it at twice the speed of the rows that are not yet due.
            if (!row.staged && row.enter.advance(dt)) moving = true
            if (row.press.advance(dt)) moving = true
            if (row.travel.advance(dt)) moving = true
            if (row.knob.advance(dt)) moving = true
            if (row.bubble.advance(dt)) moving = true
            i++
        }
        return moving
    }

    // ---- input --------------------------------------------------------------------------------

    /** True when the point was consumed, so the host stops looking for something else to hit. */
    fun onTap(x: Float, y: Float): Boolean {
        if (!shown) return false
        ensureLayout()
        val sheetTop = sheetTop(shownOpenness)
        if (y < sheetTop) {
            onDismiss?.invoke()
            return true
        }
        // The grab handle is the sheet's own dismiss: a drawn sheet has no system affordance
        // above it to fall back on, and the host's drag-down is only offered at the top of a list.
        if (y < sheetTop + handleTop + metrics.dp(26f)) {
            onDismiss?.invoke()
            return true
        }
        // The title band and the preview are the sheet, not the world: they swallow the tap.
        if (y < sheetTop + headerHeight) return true
        val row = rowAt(y)
        if (row != null && row.interactive) activate(row, x)
        return true
    }

    /** Moves the list by a finger's vertical travel, rubber-banding past either end. */
    fun scrollBy(dy: Float) {
        ensureLayout()
        val limit = maxScroll()
        val slack = if (motionProfile.instant) 0f else viewportHeight() * OVERSCROLL_FRACTION
        scroll.min = 0f
        scroll.max = limit
        scroll.velocity = 0f
        var delta = -dy
        val over = when {
            scroll.value < 0f -> scroll.value
            scroll.value > limit -> scroll.value - limit
            else -> 0f
        }
        // Resistance only on the way out. Coming back the finger gets its travel one for one, or
        // an overscrolled list would feel stuck to the edge it just left.
        if (over != 0f && slack > 0f && delta * over > 0f) {
            delta *= max(1f - abs(over) / slack, OVERSCROLL_FLOOR)
        }
        scroll.value = clamp(scroll.value + delta, -slack, limit + slack)
    }

    /** Hands a release velocity to the list's own decay, bounded to the content. */
    fun fling(velocityY: Float) {
        ensureLayout()
        scroll.min = 0f
        scroll.max = maxScroll()
        scroll.friction = if (motionProfile.instant) FLING_FRICTION_OFF else FLING_FRICTION
        scroll.velocity = -velocityY
    }

    /**
     * Optional: offers a horizontal drag to whatever slider is under [x], [y]. Returns true when
     * one took it, so a host that separates horizontal drags from scrolling can tell which it is.
     */
    fun onDragStart(x: Float, y: Float): Boolean {
        if (!shown) return false
        ensureLayout()
        val row = rowAt(y) ?: return false
        if (row.kind != KIND_SLIDER) return false
        dragged = row
        row.bubble.target = 1f
        setSliderFrom(row, x)
        return true
    }

    /** Continues a slider drag; the thumb springs to the finger rather than sticking to it. */
    fun onDrag(x: Float, y: Float) {
        val row = dragged ?: return
        setSliderFrom(row, x)
    }

    /** Ends a slider drag, leaving the thumb the speed the finger let go at. */
    fun onDragEnd(velocityX: Float) {
        val row = dragged ?: return
        dragged = null
        row.bubble.target = 0f
        val span = sliderSpan()
        if (span > 0f) row.travel.velocity += velocityX / span
    }

    // ---- drawing -------------------------------------------------------------------------------

    /** 0 = off the bottom of the screen, 1 = fully up. The host drives this from its spring. */
    fun draw(canvas: Canvas, openness: Float) {
        val open = clamp(openness, 0f, 1f)
        shownOpenness = open
        if (open <= 0.002f) return
        ensureLayout()
        if (bounds.width() <= 0f || rows.isEmpty()) return

        val sheetTop = sheetTop(open)

        fill.shader = null
        fill.color = fade(scrimInk, SCRIM_ALPHA * open)
        canvas.drawRect(bounds, fill)

        val shadow = shadowShader
        if (shadow != null) {
            val save = canvas.save()
            canvas.translate(bounds.left, sheetTop - shadowHeight)
            fill.shader = shadow
            fill.color = fade(scrimInk, open)
            canvas.drawRect(0f, 0f, bounds.width(), shadowHeight, fill)
            fill.shader = null
            canvas.restoreToCount(save)
        }

        // The corner lets go of the sheet only at the very end of the travel, so the shape reads
        // as a sheet for the whole of the journey and as the screen once it has arrived.
        val radius = metrics.radiusLarge * (1f - smoothstep(0.86f, 1f, open))
        fill.color = theme.surface
        canvas.drawRoundRect(
            bounds.left,
            sheetTop,
            bounds.right,
            bounds.bottom + radius,
            radius,
            radius,
            fill,
        )

        drawHandle(canvas, sheetTop)

        sheetTitlePaint.color = theme.ink
        canvas.drawText(textSettings, contentLeft, sheetTop + titleBaseline, sheetTitlePaint)

        drawPreview(
            canvas,
            contentLeft,
            sheetTop + previewTop,
            contentRight,
            sheetTop + previewTop + previewHeight,
        )

        val listTop = sheetTop + headerHeight
        val save = canvas.save()
        canvas.clipRect(bounds.left, listTop, bounds.right, bounds.bottom)
        val origin = listTop - scroll.value
        val margin = metrics.rowHeight
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            val top = origin + row.top
            if (top + row.height >= listTop - margin && top <= bounds.bottom + margin) {
                drawRow(canvas, row, top)
            }
            i++
        }
        canvas.restoreToCount(save)
    }

    private fun drawHandle(canvas: Canvas, sheetTop: Float) {
        val width = metrics.dp(38f)
        val height = metrics.dp(4.5f)
        val cx = bounds.centerX()
        fill.shader = null
        fill.color = theme.hairlineStrong
        canvas.drawRoundRect(
            cx - width * 0.5f,
            sheetTop + handleTop,
            cx + width * 0.5f,
            sheetTop + handleTop + height,
            height * 0.5f,
            height * 0.5f,
            fill,
        )
    }

    private fun drawRow(canvas: Canvas, row: Row, rowTop: Float) {
        val phase = clamp(row.enter.value, 0f, 1.2f)
        if (phase <= 0.004f) return
        val alpha = min(phase, 1f)
        val slide = lerp(metrics.dp(26f), 0f, smoothstep(0f, 1f, phase))
        val top = rowTop + slide
        val dip = clamp(row.press.value, 0f, 1f)

        val save = canvas.save()
        if (dip > 0.002f) {
            val k = 1f - 0.018f * dip
            canvas.scale(k, k, bounds.centerX(), top + row.height * 0.5f)
            fill.shader = null
            fill.color = fade(theme.press, alpha * dip)
            canvas.drawRect(bounds.left, top, bounds.right, top + row.height, fill)
        }

        when (row.kind) {
            KIND_SECTION -> drawSection(canvas, row, top, alpha)
            KIND_SEEDS -> drawSeeds(canvas, row, top, alpha)
            KIND_SEGMENTED -> drawSegmented(canvas, row, top, alpha)
            KIND_SLIDER -> drawSlider(canvas, row, top, alpha)
            KIND_TOGGLE -> drawToggle(canvas, row, top, alpha)
            KIND_CHECK -> drawCheck(canvas, row, top, alpha)
            KIND_NOTE -> drawNote(canvas, row, top, alpha)
        }

        if (row.interactive && !row.lastInSection) {
            fill.shader = null
            fill.color = fade(theme.hairline, alpha)
            canvas.drawRect(
                contentLeft,
                top + row.height - metrics.hairline,
                contentRight,
                top + row.height,
                fill,
            )
        }
        canvas.restoreToCount(save)
    }

    private fun drawSection(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        capsPaint.color = fade(theme.inkFaint, alpha)
        canvas.drawText(
            row.shownTitle,
            contentLeft,
            top + row.height - metrics.dp(12f),
            capsPaint,
        )
    }

    private fun drawNote(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        val lines = row.lines ?: return
        hintPaint.color = fade(theme.inkFaint, alpha)
        val step = hintPaint.fontSpacing
        var y = top + metrics.dp(6f) - hintPaint.ascent()
        var i = 0
        while (i < lines.size) {
            canvas.drawText(lines[i], contentLeft, y, hintPaint)
            y += step
            i++
        }
    }

    private fun drawSeeds(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        labelPaint.color = fade(theme.ink, alpha)
        canvas.drawText(row.shownTitle, contentLeft, top + metrics.dp(24f), labelPaint)

        val controlTop = top + metrics.dp(34f)
        val controlHeight = row.height - metrics.dp(48f)
        val cy = controlTop + controlHeight * 0.5f
        val step = (contentRight - contentLeft) / Theme.seeds.size
        val r = min(step * 0.30f, controlHeight * 0.42f)

        fill.shader = null
        stroke.strokeWidth = metrics.hairline
        stroke.color = fade(theme.hairline, alpha)
        var i = 0
        while (i < Theme.seeds.size) {
            val cx = contentLeft + step * (i + 0.5f)
            fill.color = fade(Theme.seeds[i].second, alpha)
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, stroke)
            i++
        }

        // The ring is one ring that moves. Redrawing it under whichever swatch is current would
        // say the same thing without ever showing the choice being made.
        stroke.strokeWidth = metrics.dp(1.6f)
        stroke.color = fade(theme.ink, alpha)
        canvas.drawCircle(row.travel.value, cy, r + metrics.dp(4.5f), stroke)
    }

    private fun drawSegmented(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        val options = row.options ?: return
        labelPaint.color = fade(theme.ink, alpha)
        canvas.drawText(row.shownTitle, contentLeft, top + metrics.dp(24f), labelPaint)

        val trackTop = top + metrics.dp(34f)
        val trackHeight = row.height - metrics.dp(48f)
        val trackBottom = trackTop + trackHeight
        val radius = trackHeight * 0.5f
        val trackL = contentLeft
        val trackR = contentRight
        fill.shader = null
        fill.color = fade(theme.canvas, alpha)
        canvas.drawRoundRect(trackL, trackTop, trackR, trackBottom, radius, radius, fill)
        stroke.strokeWidth = metrics.hairline
        stroke.color = fade(theme.hairline, alpha)
        canvas.drawRoundRect(trackL, trackTop, trackR, trackBottom, radius, radius, stroke)

        val slot = (contentRight - contentLeft) / options.size
        val inset = metrics.dp(3f)
        val pillLeft = row.travel.value
        val pillRight = pillLeft + slot
        fill.color = fade(theme.accent, alpha)
        canvas.drawRoundRect(
            pillLeft + inset,
            trackTop + inset,
            pillRight - inset,
            trackBottom - inset,
            radius - inset,
            radius - inset,
            fill,
        )

        val baseline = trackTop + trackHeight * 0.5f -
            (optionPaint.descent() + optionPaint.ascent()) * 0.5f
        var i = 0
        while (i < options.size) {
            val slotLeft = contentLeft + slot * i
            val cx = slotLeft + slot * 0.5f
            // How much of this option the pill has already reached, which is what the two inks
            // cross over: the label lights up as the fill arrives under it, not when it lands.
            val covered = min(pillRight, slotLeft + slot) - max(pillLeft, slotLeft)
            val overlap = max(0f, covered) / slot
            if (overlap < 1f) {
                optionPaint.color = fade(theme.inkMuted, alpha * (1f - overlap))
                canvas.drawText(options[i], cx, baseline, optionPaint)
            }
            if (overlap > 0f) {
                optionPaint.color = fade(theme.onAccent, alpha * overlap)
                canvas.drawText(options[i], cx, baseline, optionPaint)
            }
            i++
        }
    }

    private fun drawSlider(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        labelPaint.color = fade(theme.ink, alpha)
        canvas.drawText(row.shownTitle, contentLeft, top + metrics.dp(24f), labelPaint)

        writeValue(row.id)
        valuePaint.color = fade(theme.inkFaint, alpha)
        canvas.drawText(text, 0, text.length, contentRight, top + metrics.dp(24f), valuePaint)

        val thumbR = metrics.dp(10f)
        val trackY = top + metrics.dp(34f) + (row.height - metrics.dp(48f)) * 0.62f
        val trackH = metrics.dp(5f)
        val left = contentLeft + thumbR
        val span = sliderSpan()
        val at = left + clamp(row.travel.value, 0f, 1f) * span

        fill.shader = null
        fill.color = fade(theme.hairlineStrong, alpha)
        canvas.drawRoundRect(
            contentLeft,
            trackY - trackH * 0.5f,
            contentRight,
            trackY + trackH * 0.5f,
            trackH * 0.5f,
            trackH * 0.5f,
            fill,
        )
        fill.color = fade(theme.accent, alpha)
        canvas.drawRoundRect(
            contentLeft,
            trackY - trackH * 0.5f,
            max(at, contentLeft + trackH),
            trackY + trackH * 0.5f,
            trackH * 0.5f,
            trackH * 0.5f,
            fill,
        )

        fill.color = fade(theme.surface, alpha)
        canvas.drawCircle(at, trackY, thumbR, fill)
        fill.color = fade(theme.accent, alpha)
        canvas.drawCircle(at, trackY, thumbR - metrics.dp(2.5f), fill)

        val grown = clamp(row.bubble.value, 0f, 1f)
        if (grown > 0.004f) drawBubble(canvas, at, trackY - thumbR - metrics.dp(6f), grown, alpha)
    }

    private fun drawBubble(canvas: Canvas, cx: Float, bottom: Float, grown: Float, alpha: Float) {
        val save = canvas.save()
        canvas.scale(grown, grown, cx, bottom)
        val width = optionPaint.measureText(text, 0, text.length) + metrics.dp(18f)
        val height = metrics.dp(26f)
        fill.shader = null
        fill.color = fade(theme.accent, alpha * grown)
        canvas.drawRoundRect(
            cx - width * 0.5f,
            bottom - height,
            cx + width * 0.5f,
            bottom,
            height * 0.5f,
            height * 0.5f,
            fill,
        )
        optionPaint.color = fade(theme.onAccent, alpha * grown)
        canvas.drawText(
            text,
            0,
            text.length,
            cx,
            bottom - height * 0.5f - (optionPaint.descent() + optionPaint.ascent()) * 0.5f,
            optionPaint,
        )
        canvas.restoreToCount(save)
    }

    private fun drawToggle(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        val phase = clamp(row.knob.value, 0f, 1f)
        val trackW = metrics.dp(50f)
        val trackH = metrics.dp(30f)
        val cy = top + row.height * 0.5f
        val trackLeft = contentRight - trackW
        val trackTop = cy - trackH * 0.5f
        val radius = trackH * 0.5f

        val trackBottom = trackTop + trackH
        fill.shader = null
        fill.color = fade(theme.canvas, alpha)
        canvas.drawRoundRect(trackLeft, trackTop, contentRight, trackBottom, radius, radius, fill)
        // The track takes the accent on the way across rather than at the end of it, so the
        // colour is part of the movement instead of a result reported after it.
        fill.color = fade(theme.accentSoft, alpha * phase)
        canvas.drawRoundRect(trackLeft, trackTop, contentRight, trackBottom, radius, radius, fill)
        stroke.strokeWidth = metrics.hairline
        stroke.color = fade(theme.hairlineStrong, alpha * (1f - phase))
        canvas.drawRoundRect(trackLeft, trackTop, contentRight, trackBottom, radius, radius, stroke)

        val inset = metrics.dp(4f)
        val knobR = radius - inset
        val travel = trackW - 2f * (knobR + inset)
        val cx = trackLeft + knobR + inset + travel * phase
        fill.color = fade(theme.inkFaint, alpha)
        canvas.drawCircle(cx, cy, knobR, fill)
        fill.color = fade(theme.accent, alpha * phase)
        canvas.drawCircle(cx, cy, knobR, fill)

        drawLabelAndHint(canvas, row, top, alpha)
    }

    private fun drawCheck(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        val phase = clamp(row.knob.value, 0f, 1f)
        val cy = top + row.height * 0.5f
        val discR = metrics.dp(5f)
        val source = row.source
        fill.shader = null
        fill.color = fade(
            if (source == null || source.colour == 0) theme.inkFaint else source.colour,
            alpha,
        )
        canvas.drawCircle(contentLeft + discR, cy, discR, fill)

        val boxR = metrics.dp(11f)
        val boxX = contentRight - boxR
        stroke.strokeWidth = metrics.dp(1.4f)
        stroke.color = fade(theme.hairlineStrong, alpha * (1f - phase))
        canvas.drawCircle(boxX, cy, boxR, stroke)
        fill.color = fade(theme.accent, alpha * phase)
        canvas.drawCircle(boxX, cy, boxR * (0.72f + 0.28f * phase), fill)

        val tick = boxR * (0.62f + 0.38f * phase)
        stroke.strokeWidth = metrics.dp(2f)
        stroke.color = fade(theme.onAccent, alpha * phase)
        canvas.drawLine(
            boxX - tick * 0.46f,
            cy + tick * 0.02f,
            boxX - tick * 0.14f,
            cy + tick * 0.36f,
            stroke,
        )
        canvas.drawLine(
            boxX - tick * 0.14f,
            cy + tick * 0.36f,
            boxX + tick * 0.46f,
            cy - tick * 0.34f,
            stroke,
        )

        val left = contentLeft + discR * 2f + metrics.dp(12f)
        val hint = row.shownHint
        if (hint == null) {
            labelPaint.color = fade(theme.ink, alpha)
            canvas.drawText(
                row.shownTitle,
                left,
                cy - (labelPaint.descent() + labelPaint.ascent()) * 0.5f,
                labelPaint,
            )
        } else {
            labelPaint.color = fade(theme.ink, alpha)
            canvas.drawText(row.shownTitle, left, top + metrics.dp(26f), labelPaint)
            hintPaint.color = fade(theme.inkFaint, alpha)
            canvas.drawText(hint, left, top + metrics.dp(44f), hintPaint)
        }
    }

    private fun drawLabelAndHint(canvas: Canvas, row: Row, top: Float, alpha: Float) {
        val hint = row.shownHint
        labelPaint.color = fade(theme.ink, alpha)
        if (hint == null) {
            canvas.drawText(
                row.shownTitle,
                contentLeft,
                top + row.height * 0.5f - (labelPaint.descent() + labelPaint.ascent()) * 0.5f,
                labelPaint,
            )
            return
        }
        canvas.drawText(row.shownTitle, contentLeft, top + metrics.dp(26f), labelPaint)
        hintPaint.color = fade(theme.inkFaint, alpha)
        canvas.drawText(hint, contentLeft, top + metrics.dp(46f), hintPaint)
    }

    // ---- the live month ------------------------------------------------------------------------

    /**
     * The month above the list, wearing whatever the settings currently say. A seed is a word
     * until it is a colour on a grid, which is the only reason this is here rather than a swatch.
     */
    private fun drawPreview(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val shownTheme = previewTheme()
        ensurePreviewCells()
        val radius = metrics.radiusSmall
        fill.shader = null
        fill.color = shownTheme.canvas
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)
        stroke.strokeWidth = metrics.hairline
        stroke.color = shownTheme.hairline
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, stroke)

        val padX = metrics.dp(10f)
        val innerTop = top + metrics.dp(6f)
        val innerBottom = bottom - metrics.dp(6f)
        val columnW = (right - left - padX * 2f) / MonthModel.COLUMNS
        val headH = (innerBottom - innerTop) * 0.15f
        val cellH = (innerBottom - innerTop - headH) / MonthModel.ROWS
        val gridTop = innerTop + headH

        numeralPaint.textSize = metrics.sp(8f)
        numeralPaint.color = shownTheme.inkFaint
        val headBaseline = innerTop + headH * 0.5f -
            (numeralPaint.descent() + numeralPaint.ascent()) * 0.5f
        var c = 0
        while (c < MonthModel.COLUMNS) {
            canvas.drawText(
                weekdayText[c],
                left + padX + columnW * (c + 0.5f),
                headBaseline,
                numeralPaint,
            )
            c++
        }

        fill.color = shownTheme.hairline
        var r = 1
        while (r < MonthModel.ROWS) {
            val y = gridTop + cellH * r
            canvas.drawRect(left + padX, y - metrics.hairline, right - padX, y, fill)
            r++
        }

        numeralPaint.textSize = metrics.sp(10f)
        val discR = min(columnW, cellH) * 0.36f
        var i = 0
        while (i < MonthModel.CELLS) {
            val column = i % MonthModel.COLUMNS
            val rowIndex = i / MonthModel.COLUMNS
            val cx = left + padX + columnW * (column + 0.5f)
            val cy = gridTop + cellH * (rowIndex + 0.5f)
            val inMonth = previewInMonth[i]
            if (inMonth || adjacent) {
                val load = previewMarks[i]
                if (density && load > 0 && inMonth) {
                    fill.color = fade(shownTheme.accentSoft, load / 3f)
                    canvas.drawRoundRect(
                        cx - columnW * 0.44f,
                        cy - cellH * 0.42f,
                        cx + columnW * 0.44f,
                        cy + cellH * 0.42f,
                        metrics.dp(3f),
                        metrics.dp(3f),
                        fill,
                    )
                }
                val isToday = i == previewToday
                if (isToday) {
                    fill.color = shownTheme.accent
                    canvas.drawCircle(cx, cy - cellH * 0.06f, discR, fill)
                }
                numeralPaint.color = when {
                    isToday -> shownTheme.onAccent
                    inMonth -> shownTheme.ink
                    else -> shownTheme.inkGhost
                }
                canvas.drawText(
                    numerals[previewDays[i]],
                    cx,
                    cy - cellH * 0.06f - (numeralPaint.descent() + numeralPaint.ascent()) * 0.5f,
                    numeralPaint,
                )
                if (load > 0 && inMonth && !isToday) {
                    val dotR = metrics.dp(1.5f)
                    val gap = dotR * 2.6f
                    val startX = cx - gap * (load - 1) * 0.5f
                    var d = 0
                    while (d < load) {
                        fill.color = if (colouredMarks) {
                            shownTheme.categorical(i + d)
                        } else {
                            shownTheme.inkFaint
                        }
                        canvas.drawCircle(startX + gap * d, cy + cellH * 0.30f, dotR, fill)
                        d++
                    }
                }
            }
            i++
        }
    }

    /**
     * The theme the preview wears, rebuilt only when one of its three inputs actually moves —
     * deriving a palette costs a few hundred colour conversions, which is not a per-frame price.
     */
    private fun previewTheme(): Theme {
        val dark = darkMode ?: systemDark(context)
        val held = cachedTheme
        val same = cachedSeed == seed && cachedDark == dark && cachedContrast == contrast
        if (held != null && same) return held
        val built = Theme(seed, dark, contrast)
        cachedTheme = built
        cachedSeed = seed
        cachedDark = dark
        cachedContrast = contrast
        return built
    }

    private fun ensurePreviewCells() {
        val first = firstDay ?: MonthModel.firstDayOfWeek(null, locale)
        if (!cellsDirty && first == cellsFirstDay) return
        cellsDirty = false
        cellsFirstDay = first
        val month = YearMonth.from(today)
        val cells = MonthModel.cells(month, first)
        val labels = MonthModel.weekdayLabels(first, locale)
        previewToday = -1
        var i = 0
        while (i < MonthModel.CELLS) {
            val date = cells[i]
            previewDays[i] = date.dayOfMonth
            previewInMonth[i] = date.monthValue == month.monthValue && date.year == month.year
            if (date == today) previewToday = i
            // A fixed seed, not the palette's: the marks are here to be something for the colour
            // to happen to, and they must not reshuffle every time the colour setting moves.
            previewMarks[i] = (((Noise.value1(i * 0.83f, MARK_SEED) + 1f) * 1.9f).toInt())
                .coerceIn(0, 3)
            i++
        }
        var c = 0
        while (c < MonthModel.COLUMNS) {
            weekdayText[c] = labels[c]
            c++
        }
    }

    // ---- changing something ----------------------------------------------------------------

    private fun activate(row: Row, x: Float) {
        row.press.snapTo(1f)
        row.press.target = 0f
        when (row.kind) {
            KIND_SEEDS -> {
                val step = (contentRight - contentLeft) / Theme.seeds.size
                val picked = (((x - contentLeft) / step).toInt()).coerceIn(0, Theme.seeds.size - 1)
                seed = Theme.seeds[picked].second
                syncRow(row, snap = false)
                emit()
            }
            KIND_SEGMENTED -> {
                val options = row.options ?: return
                val slot = (contentRight - contentLeft) / options.size
                val picked = (((x - contentLeft) / slot).toInt()).coerceIn(0, options.size - 1)
                when (row.id) {
                    ID_MODE -> darkMode = when (picked) {
                        0 -> null
                        1 -> false
                        else -> true
                    }
                    ID_FIRST_DAY -> {
                        firstDay = when (picked) {
                            0 -> null
                            1 -> DayOfWeek.MONDAY
                            2 -> DayOfWeek.SATURDAY
                            else -> DayOfWeek.SUNDAY
                        }
                        cellsDirty = true
                    }
                    ID_MOTION -> {
                        motionProfile = MotionProfile.entries[picked]
                        applyProfiles()
                    }
                }
                syncRow(row, snap = false)
                emit()
            }
            KIND_SLIDER -> {
                row.bubble.snapTo(1f)
                row.bubble.target = 0f
                setSliderFrom(row, x)
            }
            KIND_TOGGLE -> {
                when (row.id) {
                    ID_DYNAMIC -> dynamicColour = !dynamicColour
                    ID_HAPTICS -> haptics = !haptics
                    ID_DEPTH -> depth = !depth
                    ID_DENSITY -> density = !density
                    ID_MARKS -> colouredMarks = !colouredMarks
                    ID_ADJACENT -> adjacent = !adjacent
                }
                syncRow(row, snap = false)
                emit()
            }
            KIND_CHECK -> {
                val source = row.source ?: return
                // A new set every time: the one that arrived in the State belongs to the host.
                val id = source.id
                hidden = if (hidden.contains(id)) hidden - id else hidden + id
                syncRow(row, snap = false)
                emit()
            }
        }
    }

    private fun setSliderFrom(row: Row, x: Float) {
        val span = sliderSpan()
        if (span <= 0f) return
        val raw = clamp((x - contentLeft - metrics.dp(10f)) / span, 0f, 1f)
        // Detents: the value text holds still under a moving finger, and a thumb let go between
        // two of them has somewhere to spring to rather than stopping wherever it happened to be.
        val stepped = (raw * SLIDER_STEPS).roundToInt() / SLIDER_STEPS.toFloat()
        row.travel.target = stepped
        val changed = when (row.id) {
            ID_CONTRAST -> {
                val was = contrast
                contrast = stepped
                was != contrast
            }
            else -> {
                val was = scale
                scale = SCALE_MIN + stepped * (SCALE_MAX - SCALE_MIN)
                was != scale
            }
        }
        if (changed) emit()
    }

    private fun emit() {
        onChange?.invoke(
            State(
                dynamic = dynamicColour,
                seed = seed,
                dark = darkMode,
                contrast = contrast,
                scale = scale,
                firstDay = firstDay,
                motion = motionProfile,
                haptics = haptics,
                depth = depth,
                density = density,
                colouredMarks = colouredMarks,
                adjacent = adjacent,
                hidden = hidden,
            ),
        )
    }

    private fun adopt(state: State) {
        dynamicColour = state.dynamic
        seed = state.seed
        darkMode = state.dark
        contrast = clamp(state.contrast, 0f, 1f)
        scale = clamp(state.scale, SCALE_MIN, SCALE_MAX)
        firstDay = state.firstDay
        motionProfile = state.motion
        haptics = state.haptics
        depth = state.depth
        density = state.density
        colouredMarks = state.colouredMarks
        adjacent = state.adjacent
        hidden = state.hidden
        cellsDirty = true
        applyProfiles()
    }

    // ---- arrival -------------------------------------------------------------------------------

    /**
     * Hands the rows above the fold to one timeline, a stagger apart. The rest are left where
     * they are: a row that has never been seen has not arrived yet, and gets its own entrance
     * on the frame it is scrolled into view.
     */
    private fun stage() {
        entrance = Timeline()
        val fold = viewportHeight()
        var staggered = 0
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            row.enter.snapTo(0f)
            row.press.snapTo(0f)
            row.bubble.snapTo(0f)
            row.staged = false
            row.armed = false
            syncRow(row, snap = true)
            if (row.top < fold) {
                row.staged = true
                row.armed = true
                row.enter.target = 1f
                entrance.add(row.enter, staggered * motionProfile.staggerSeconds)
                staggered++
            }
            i++
        }
        entrance.restart()
        if (motionProfile.instant) {
            var j = 0
            while (j < rows.size) {
                rows[j].enter.snapTo(1f)
                rows[j].armed = true
                j++
            }
        }
    }

    private fun armRowsInView(): Boolean {
        if (!shown) return false
        val fold = scroll.value + viewportHeight() + metrics.gutter
        var started = false
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            if (!row.armed && row.top < fold) {
                row.armed = true
                row.enter.target = 1f
                started = true
            }
            i++
        }
        return started
    }

    private fun applyProfiles() {
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            row.enter.profile(motionProfile)
            row.press.profile(motionProfile)
            row.travel.profile(motionProfile)
            row.knob.profile(motionProfile)
            row.bubble.profile(motionProfile)
            i++
        }
        scroll.friction = if (motionProfile.instant) FLING_FRICTION_OFF else FLING_FRICTION
    }

    /** Points every one of a row's springs at what the settings now say. */
    private fun syncRow(row: Row, snap: Boolean) {
        when (row.kind) {
            KIND_SEEDS -> {
                val step = (contentRight - contentLeft) / Theme.seeds.size
                val at = contentLeft + step * (seedIndex() + 0.5f)
                if (snap) row.travel.snapTo(at) else row.travel.target = at
            }
            KIND_SEGMENTED -> {
                val options = row.options ?: return
                val slot = (contentRight - contentLeft) / options.size
                val at = contentLeft + slot * selectedIndex(row.id)
                if (snap) row.travel.snapTo(at) else row.travel.target = at
            }
            KIND_SLIDER -> {
                val at = when (row.id) {
                    ID_CONTRAST -> contrast
                    else -> (scale - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)
                }
                if (snap) row.travel.snapTo(at) else row.travel.target = at
            }
            KIND_TOGGLE -> {
                val on = when (row.id) {
                    ID_DYNAMIC -> dynamicColour
                    ID_HAPTICS -> haptics
                    ID_DEPTH -> depth
                    ID_DENSITY -> density
                    ID_MARKS -> colouredMarks
                    else -> adjacent
                }
                val at = if (on) 1f else 0f
                if (snap) row.knob.snapTo(at) else row.knob.target = at
            }
            KIND_CHECK -> {
                val source = row.source ?: return
                val at = if (hidden.contains(source.id)) 0f else 1f
                if (snap) row.knob.snapTo(at) else row.knob.target = at
            }
        }
    }

    private fun seedIndex(): Int {
        var i = 0
        while (i < Theme.seeds.size) {
            if (Theme.seeds[i].second == seed) return i
            i++
        }
        return 0
    }

    private fun selectedIndex(id: Int): Int = when (id) {
        ID_MODE -> when (darkMode) {
            null -> 0
            false -> 1
            else -> 2
        }
        ID_FIRST_DAY -> when (firstDay) {
            null -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.SATURDAY -> 2
            DayOfWeek.SUNDAY -> 3
            else -> 0
        }
        ID_MOTION -> motionProfile.ordinal
        else -> 0
    }

    // ---- layout ---------------------------------------------------------------------------------

    private fun ensureLayout() {
        var rebuilt = false
        if (structureDirty) {
            structureDirty = false
            buildRows()
            layoutDirty = true
            rebuilt = true
        }
        if (layoutDirty) {
            layoutDirty = false
            measure()
        }
        // Calendars usually land after the sheet is already up. New rows have never arrived, so
        // the entrance is run again rather than having them appear fully formed mid-scroll.
        if (rebuilt && shown) stage()
    }

    private fun buildRows() {
        rows.clear()
        rows.add(Row(KIND_SECTION, ID_NONE, textLook.uppercase(locale)))
        if (SystemScheme.supported) {
            rows.add(Row(KIND_TOGGLE, ID_DYNAMIC, textDynamic, hint = textDynamicHint))
        }
        rows.add(Row(KIND_SEEDS, ID_SEED, textSeed))
        rows.add(Row(KIND_SEGMENTED, ID_MODE, textMode, options = modeOptions))
        rows.add(Row(KIND_SLIDER, ID_CONTRAST, textContrast))
        rows.add(Row(KIND_SLIDER, ID_SCALE, textSize))

        rows.add(section(R.string.section_week))
        rows.add(segmented(ID_FIRST_DAY, R.string.first_day, firstDayOptions))

        rows.add(section(R.string.section_motion))
        rows.add(segmented(ID_MOTION, R.string.motion, motionOptions))
        rows.add(toggle(ID_HAPTICS, R.string.haptics, R.string.haptics_hint))
        rows.add(toggle(ID_DEPTH, R.string.depth, R.string.depth_hint))

        rows.add(section(R.string.section_grid))
        rows.add(toggle(ID_DENSITY, R.string.heat, R.string.heat_hint))
        rows.add(toggle(ID_MARKS, R.string.coloured_dots, R.string.coloured_dots_hint))
        rows.add(toggle(ID_ADJACENT, R.string.show_adjacent, R.string.show_adjacent_hint))

        rows.add(section(R.string.section_calendars))
        if (sources.isEmpty()) {
            rows.add(Row(KIND_NOTE, ID_NONE, context.getString(R.string.no_calendars)))
        } else {
            var i = 0
            while (i < sources.size) {
                rows.add(check(sources[i]))
                i++
            }
        }

        rows.add(section(R.string.section_about))
        rows.add(Row(KIND_NOTE, ID_NONE, aboutText()))

        var i = 0
        while (i < rows.size) {
            val next = if (i + 1 < rows.size) rows[i + 1] else null
            rows[i].lastInSection = next == null || !next.interactive
            i++
        }
        applyProfiles()
    }

    private fun section(titleRes: Int): Row =
        Row(KIND_SECTION, ID_NONE, context.getString(titleRes).uppercase(locale))

    private fun segmented(id: Int, titleRes: Int, options: Array<String>): Row =
        Row(KIND_SEGMENTED, id, context.getString(titleRes), options = options)

    private fun toggle(id: Int, titleRes: Int, hintRes: Int): Row =
        Row(KIND_TOGGLE, id, context.getString(titleRes), context.getString(hintRes))

    private fun check(source: CalendarSource): Row {
        val account = source.accountName
        val subtitle = if (account.isEmpty() || account == source.displayName) null else account
        return Row(KIND_CHECK, ID_CALENDAR, source.displayName, subtitle, source = source)
    }

    private fun aboutText(): String {
        val line = context.getString(R.string.about_line, versionName)
        return line + "\n" + context.getString(R.string.about_body)
    }

    private fun measure() {
        applyTextSizes()
        val padX = metrics.gutter * 1.25f
        contentLeft = bounds.left + padX
        contentRight = bounds.right - padX
        handleTop = metrics.dp(9f)
        titleBaseline = metrics.dp(50f)
        previewTop = metrics.dp(62f)
        previewHeight = metrics.dp(150f)
        headerHeight = previewTop + previewHeight + metrics.gutter

        var y = 0f
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            row.top = y
            row.height = heightOf(row)
            y += row.height
            i++
        }
        contentHeight = y + metrics.gutter * 2f

        scroll.min = 0f
        scroll.max = maxScroll()
        if (scroll.value > scroll.max) scroll.snapTo(scroll.max)

        var j = 0
        while (j < rows.size) {
            syncRow(rows[j], snap = !shown)
            j++
        }
    }

    private fun applyTextSizes() {
        sheetTitlePaint.textSize = metrics.sp(26f)
        capsPaint.textSize = metrics.sp(11f)
        labelPaint.textSize = metrics.sp(15f)
        hintPaint.textSize = metrics.sp(12.5f)
        valuePaint.textSize = metrics.sp(13f)
        optionPaint.textSize = metrics.sp(13f)
        numeralPaint.textSize = metrics.sp(10f)
    }

    private fun heightOf(row: Row): Float {
        val width = contentRight - contentLeft
        return when (row.kind) {
            KIND_SECTION -> metrics.rowHeight * 0.80f
            KIND_SEEDS -> metrics.rowHeight * 1.35f
            KIND_SEGMENTED -> metrics.rowHeight * 1.55f
            KIND_SLIDER -> metrics.rowHeight * 1.40f
            KIND_TOGGLE -> {
                row.shownTitle = ellipsise(row.title, labelPaint, width - metrics.dp(62f))
                row.shownHint = row.hint?.let { ellipsise(it, hintPaint, width - metrics.dp(62f)) }
                if (row.hint == null) metrics.rowHeight else metrics.rowHeight * 1.25f
            }
            KIND_CHECK -> {
                val available = width - metrics.dp(56f)
                row.shownTitle = ellipsise(row.title, labelPaint, available)
                row.shownHint = row.hint?.let { ellipsise(it, hintPaint, available) }
                if (row.hint == null) metrics.rowHeight else metrics.rowHeight * 1.20f
            }
            KIND_NOTE -> {
                val lines = wrap(row.title, width)
                row.lines = lines
                metrics.dp(10f) + lines.size * hintPaint.fontSpacing
            }
            else -> metrics.rowHeight
        }
    }

    /** Breaks a paragraph to the sheet's width once, at layout, so no frame ever measures it. */
    private fun wrap(source: String, available: Float): Array<String> {
        if (available <= 0f) return arrayOf(source)
        val out = ArrayList<String>(4)
        for (paragraph in source.split("\n")) {
            var rest = paragraph
            while (rest.isNotEmpty()) {
                val fits = hintPaint.breakText(rest, true, available, null)
                if (fits <= 0) {
                    out.add(rest)
                    break
                }
                var cut = fits
                if (cut < rest.length) {
                    val space = rest.lastIndexOf(' ', cut)
                    if (space > 0) cut = space
                }
                out.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trim()
            }
            if (paragraph.isEmpty()) out.add("")
        }
        return out.toTypedArray()
    }

    private fun ellipsise(source: String, paint: Paint, available: Float): String {
        if (available <= 0f) return ""
        if (paint.measureText(source) <= available) return source
        val room = available - paint.measureText(ELLIPSIS)
        var end = source.length
        while (end > 1 && paint.measureText(source, 0, end) > room) end--
        return source.substring(0, end) + ELLIPSIS
    }

    // ---- small answers ----------------------------------------------------------------------

    private fun sheetTop(open: Float): Float = bounds.top + (1f - open) * bounds.height()

    private fun viewportHeight(): Float = max(bounds.height() - headerHeight, 0f)

    private fun maxScroll(): Float = max(contentHeight - viewportHeight(), 0f)

    private fun sliderSpan(): Float = max(contentRight - contentLeft - metrics.dp(20f), 0f)

    private fun rowAt(y: Float): Row? {
        val at = y - (sheetTop(shownOpenness) + headerHeight) + scroll.value
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            if (at >= row.top && at < row.top + row.height) return row
            i++
        }
        return null
    }

    /** Writes a slider's value into the shared builder, so no frame builds a string. */
    private fun writeValue(id: Int) {
        text.setLength(0)
        if (id == ID_CONTRAST) {
            text.append((contrast * 100f).roundToInt())
            text.append('%')
            return
        }
        val hundredths = (scale * 100f).roundToInt()
        text.append(hundredths / 100)
        text.append('.')
        val remainder = hundredths % 100
        if (remainder < 10) text.append('0')
        text.append(remainder)
        text.append('×')
    }

    private fun fade(colour: Int, amount: Float): Int {
        val a = (((colour ushr 24) and 0xFF) * clamp(amount, 0f, 1f) + 0.5f).toInt()
        return (a shl 24) or (colour and 0x00FFFFFF)
    }

    private companion object {

        /** What the system is wearing, which is what "Auto" means to the preview. */
        fun systemDark(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
