package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.ColorInt
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.EventRepository
import app.quire.engine.anim.Decay
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.Timeline
import app.quire.engine.anim.Track
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.anim.smoothstep
import app.quire.engine.design.Metrics
import app.quire.engine.design.Oklch
import app.quire.engine.design.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// The provider hands back at most sixty instances, so one fixed bank of rows holds a whole result
// set with room to spare and nothing has to be allocated once a query lands.
private const val ROW_CAPACITY: Int = 64

// Past the first handful of cards the stagger is only felt below the fold, so the delay stops
// growing: without a cap the fortieth card would still be waiting two seconds later.
private const val STAGGER_CAP: Int = 10

// How much of the world the sheet covers. The strip left below it is where the dim is seen, and
// what a dismissing tap lands on.
private const val SHEET_FRACTION: Float = 0.84f

private const val DIM_ALPHA: Float = 0.80f
private const val EDGE_SHADE_ALPHA: Float = 0.24f

private const val ROW_RISE_DP: Float = 24f
private const val ROW_ENTER_SCALE: Float = 0.965f

// Below this the provider refuses to search at all, so the panel explains the range instead.
private const val MIN_QUERY: Int = 2

private const val TYPING_HOLD_SECONDS: Float = 0.85f

private const val MEDIUM_WEIGHT: Int = 500

private const val ELLIPSIS: String = "…"
private const val UNTITLED: String = "—"
private const val OPEN_QUOTE: Char = '“'
private const val CLOSE_QUOTE: Char = '”'
private const val SEPARATOR: String = " · "
private const val TIME_RANGE: String = " – "

// A title only ever loses characters from its tail, so a pass or two settles the fit; the cap is
// only there so a pathological font cannot turn a truncation into a spin.
private const val FIT_PASSES: Int = 8

// Anything fainter than this is not on screen, and stepping over it saves a row of text drawing.
private const val VISIBLE_ALPHA: Float = 0.004f

/**
 * Free-text search over the calendar, drawn as a sheet that comes down from the top of the world.
 *
 * The panel owns no window, no widget and no clock: a host view drives it through [configure],
 * [setBounds], [show], [draw] and [advance] — the same contract every other panel in the world is
 * driven by — and hands results back through [setResults] once it has asked the repository off
 * the main thread. Everything visible here, field and caret included, is painted by hand.
 *
 * Text entry is the one place a platform service is unavoidable, and it stops there: the host
 * serves an InputConnection and mirrors it into [insert], [backspace], [clear] and [moveCaret].
 * The field, its caret and its blink are this class's own.
 *
 * @param context read once, for string resources and the user's twelve or twenty-four hour clock.
 */
class SearchPanel(context: Context) {

    // Held for string resources and the user's clock preference only. The application context
    // outlives every panel, so nothing here can pin an activity.
    private val appContext: Context = context.applicationContext

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    private val medium: Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, MEDIUM_WEIGHT, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // ---- state -----------------------------------------------------------------------------

    private var theme: Theme? = null
    private var metrics: Metrics? = null
    private var motion: MotionProfile = MotionProfile.STANDARD
    private var ready: Boolean = false

    private var locale: Locale = Locale.getDefault()
    private var zone: ZoneId = ZoneId.systemDefault()
    private var timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    // Resolved once per configure: Resources.getString builds a new String on every call, which
    // is not something a draw is allowed to do.
    private var placeholderText: String = ""
    private var rangeText: String = ""
    private var noMatchText: String = ""
    private var allDayText: String = ""

    private var hint: String = ""
    private var resultsQuery: String? = null
    private var caretIndex: Int = 0

    /** Whether the host has asked for the panel; see [show] for what it does and does not gate. */
    var visible: Boolean = false
        private set

    /**
     * The text being searched for. The host mirrors the IME into this; assigning it clamps the
     * caret into the new text and reports the change through [onQueryChanged].
     */
    var query: String = ""
        set(value) {
            if (value == field) return
            field = value
            afterTextChange()
        }

    /** Reports every edit, so the host can run the query away from the main thread. */
    var onQueryChanged: ((String) -> Unit)? = null

    /** Reports the card a finger landed on, so the host can travel to that day. */
    var onResultChosen: ((AgendaEntry) -> Unit)? = null

    /** Reports a tap on the dimmed world below the sheet: closing belongs to the host. */
    var onDismiss: (() -> Unit)? = null

    // ---- geometry --------------------------------------------------------------------------

    private val bounds = RectF()
    private val sheet = RectF()
    private val fieldRect = RectF()
    private val clearRect = RectF()
    private val listRect = RectF()
    private val scratch = RectF()

    private val sheetPath = Path()
    private val magnifierPath = Path()
    private val crossPath = Path()
    private val sheetRadii = FloatArray(8)

    private var sheetHeight = 0f
    private var fieldRadius = 0f
    private var rowRadius = 0f
    private var rowStride = 0f
    private var rowRise = 0f
    private var rowInset = 0f
    private var edgeShade = 0f

    private var dateCentreX = 0f
    private var ruleX = 0f
    private var ruleWidth = 0f
    private var ruleHeight = 0f
    private var titleLeft = 0f
    private var titleMaxWidth = 0f
    private var titleBaseline = 0f
    private var metaBaseline = 0f
    private var dayBaseline = 0f
    private var monthBaseline = 0f
    private var hintBaseline = 0f

    private var textLeft = 0f
    private var textWidthAvailable = 0f
    private var textBaseline = 0f
    private var textScroll = 0f
    private var caretTop = 0f
    private var caretBottom = 0f
    private var caretWidth = 0f
    private var caretPad = 0f

    private var maxScroll = 0f
    private var rubberLimit = 1f

    private var lastOpenness = 0f

    // ---- paint -----------------------------------------------------------------------------

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val queryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val matchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium }

    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        fontFeatureSettings = "tnum"
    }

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.CENTER
    }

    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        textAlign = Paint.Align.CENTER
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        textAlign = Paint.Align.CENTER
    }

    private val fontMetrics = Paint.FontMetrics()

    // Colours resolved once per configure, so no frame ever asks the theme for anything.
    @ColorInt private var inkColour = 0
    @ColorInt private var mutedColour = 0
    @ColorInt private var faintColour = 0
    @ColorInt private var accentColour = 0
    @ColorInt private var pressColour = 0
    @ColorInt private var dimColour = 0
    @ColorInt private var shadeColour = 0

    // ---- motion ----------------------------------------------------------------------------

    private val scroll = Decay(0f)
    private val clearFade = Spring(0f)
    private val hintFade = Spring(0f)

    // The caret loops rather than plays once: [advance] restarts it, so it keeps its rhythm for
    // as long as the panel is up and stops dead the moment it is put away.
    private val caretBlink = Track(
        0f to 1f,
        0.52f to 1f,
        0.60f to 0f,
        1.04f to 0f,
        1.12f to 1f,
    )

    // Parked at its end so the first frame of the app is not a flash on a row nobody touched.
    private val flash = Track(
        0f to 0f,
        0.03f to 1f,
        0.30f to 0f,
    ).apply { seek(duration) }

    private var flashRow = -1
    private var typingHold = 0f
    private var staggerBuiltFor = -1f

    // ---- rows ------------------------------------------------------------------------------

    /** One drawn card: an entry, its own spring, and every string and width it needs measured. */
    private class Row {
        var entry: AgendaEntry? = null
        val enter = Spring(0f)
        var leaving = false
        var day = ""
        var month = ""
        var title = ""
        var meta = ""
        var matchStart = 0
        var matchEnd = 0
        var beforeWidth = 0f
        var matchWidth = 0f

        @ColorInt
        var colour = 0
    }

    /**
     * A whole hand of cards and the timeline that deals it. Two banks exist so a leaving set can
     * sink out while an arriving set is still coming down, and neither ever waits for the other.
     */
    private class Bank {
        val rows: Array<Row> = Array(ROW_CAPACITY) { Row() }
        var count = 0
        var parkedScroll = 0f
        var timeline = Timeline()

        /** Rebinds the deal to a new stagger; delays are fixed once a member is added. */
        fun rebuild(stagger: Float) {
            val fresh = Timeline()
            var i = 0
            while (i < rows.size) {
                fresh.add(rows[i].enter, min(i, STAGGER_CAP) * stagger)
                i++
            }
            timeline = fresh
        }
    }

    private val bankA = Bank()
    private val bankB = Bank()
    private var frontIsA = true

    private val front: Bank
        get() = if (frontIsA) bankA else bankB

    private val back: Bank
        get() = if (frontIsA) bankB else bankA

    // ---- text measurement ------------------------------------------------------------------

    // Per-character advances and their running sum, so putting the caret where a finger landed is
    // a scan of an array rather than one measurement per candidate index.
    private var advances = FloatArray(64)
    private var prefixes = FloatArray(65)
    private var prefixCount = 1

    private val builder = StringBuilder(96)

    // ---- lifecycle -------------------------------------------------------------------------

    /**
     * Adopts a palette, a scale and a liveliness. Safe to call whenever any of the three change:
     * measured text and laid-out geometry are rebuilt, and motion in flight keeps its velocity.
     */
    fun configure(theme: Theme, metrics: Metrics, motion: MotionProfile) {
        this.theme = theme
        this.metrics = metrics
        this.motion = motion

        locale = currentLocale()
        zone = ZoneId.systemDefault()
        timeFormat = DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(appContext)) "HH:mm" else "h:mm a",
            locale,
        )
        placeholderText = appContext.getString(R.string.search_hint)
        rangeText = appContext.getString(R.string.search_empty)
        noMatchText = appContext.getString(R.string.search_none)
        allDayText = appContext.getString(R.string.all_day)

        inkColour = theme.ink
        mutedColour = theme.inkMuted
        faintColour = theme.inkFaint
        accentColour = theme.accent
        pressColour = theme.press
        dimColour = theme.canvas
        // A contact shadow is ink laid on a light world, and an absence of light on a dark one.
        shadeColour = if (theme.dark) theme.canvas else theme.ink

        platePaint.color = theme.surface
        fieldPaint.color = theme.surfaceLifted
        ringPaint.color = theme.hairline
        ringPaint.strokeWidth = metrics.hairline
        caretPaint.color = theme.accent
        glyphPaint.color = theme.inkMuted
        glyphPaint.strokeWidth = max(metrics.hairline * 2f, metrics.dp(1.6f))

        queryPaint.color = theme.ink
        queryPaint.textSize = metrics.sp(16.5f)
        placeholderPaint.color = theme.inkGhost
        placeholderPaint.textSize = queryPaint.textSize
        titlePaint.color = theme.ink
        titlePaint.textSize = metrics.sp(15.5f)
        matchPaint.color = theme.accent
        matchPaint.textSize = titlePaint.textSize
        metaPaint.color = theme.inkFaint
        metaPaint.textSize = metrics.sp(12.5f)
        dayPaint.color = theme.ink
        dayPaint.textSize = metrics.sp(19f)
        monthPaint.color = theme.inkMuted
        monthPaint.textSize = metrics.sp(10.5f)
        hintPaint.color = theme.inkFaint
        hintPaint.textSize = metrics.sp(13.5f)

        applyProfile()
        measureQuery()
        layout()
        updateHint()
    }

    /**
     * The slab of world the sheet may use. The sheet hangs from [full]'s top edge, and the strip
     * left below it is the dim a dismissing tap lands on.
     */
    fun setBounds(full: RectF) {
        if (bounds == full) return
        bounds.set(full)
        layout()
    }

    /**
     * Brings the panel up: the cards are re-armed so they deal again, the list returns to the top
     * and the caret starts blinking. The sheet's own travel belongs to the host, which drives it
     * through the openness handed to [draw].
     */
    fun show() {
        if (visible) return
        visible = true
        flashRow = -1
        scroll.snapTo(0f)
        textScroll = 0f
        ensureCaretVisible()
        rearmFront()
        holdCaret()
    }

    /**
     * Puts the panel away. [draw] still honours whatever openness it is given afterwards, so a
     * host animating the sheet back up keeps a whole picture all the way off the top of the
     * world; only the caret and the pressed row are dropped here.
     */
    fun hide() {
        if (!visible) return
        visible = false
        typingHold = 0f
        flashRow = -1
    }

    // ---- text entry ------------------------------------------------------------------------

    /** Where the caret sits, as an index into [query]; the host needs it to serve the IME. */
    val caret: Int
        get() = caretIndex

    /**
     * The caret's x in the same space as the bounds, so the host can keep it in view. Nothing
     * horizontal depends on how far the sheet has come down, so this is honest at any openness.
     */
    val caretX: Float
        get() = textLeft - textScroll + prefixWidth(caretIndex)

    /** Types [text] in at the caret, which is how the IME reaches the drawn field. */
    fun insert(text: CharSequence) {
        if (text.isEmpty()) return
        val at = caretIndex.coerceIn(0, query.length)
        builder.setLength(0)
        builder.append(query, 0, at).append(text).append(query, at, query.length)
        setText(builder.toString(), at + text.length)
    }

    /** Deletes the character before the caret, keeping a surrogate pair whole. */
    fun backspace() {
        val at = caretIndex.coerceIn(0, query.length)
        if (at == 0) return
        val pair = at >= 2 && query[at - 1].isLowSurrogate() && query[at - 2].isHighSurrogate()
        val start = if (pair) at - 2 else at - 1
        builder.setLength(0)
        builder.append(query, 0, start).append(query, at, query.length)
        setText(builder.toString(), start)
    }

    /** Empties the field: what the clear cross does, and what a host can do outright. */
    fun clear() {
        setText("", 0)
    }

    /** Puts the caret at [to], clamped into the text; the caret goes solid while it moves. */
    fun moveCaret(to: Int) {
        val at = to.coerceIn(0, query.length)
        caretIndex = at
        holdCaret()
        ensureCaretVisible()
    }

    // ---- results ---------------------------------------------------------------------------

    /**
     * Takes a finished search. [forQuery] is the text the host was asked about, so a result for
     * anything but the current [query] is stale and dropped — which is what stops a fast typist
     * seeing an older answer land on a newer question.
     *
     * The cards already up leave downward while these arrive, one [MotionProfile.staggerSeconds]
     * after another.
     */
    fun setResults(forQuery: String, results: List<AgendaEntry>) {
        if (forQuery != query) return
        resultsQuery = forQuery
        park()
        val target = front
        val take = min(results.size, ROW_CAPACITY)
        var i = 0
        while (i < ROW_CAPACITY) {
            val row = target.rows[i]
            if (i < take) {
                prepareRow(row, results[i])
                row.leaving = false
                row.enter.snapTo(0f)
                row.enter.target = 1f
                row.enter.profile(motion)
            } else {
                row.entry = null
                row.leaving = false
                row.enter.snapTo(0f)
            }
            i++
        }
        target.count = take
        target.timeline.restart()
        scroll.snapTo(0f)
        flashRow = -1
        updateScrollBounds()
        updateHint()
    }

    // ---- frame -----------------------------------------------------------------------------

    /**
     * Paints the sheet at [openness], where 0 is off the top of the world and 1 is fully down.
     * The world behind is dimmed by the same number, so a half-open sheet is half a dim.
     */
    fun draw(canvas: Canvas, openness: Float) {
        if (!ready) return
        val open = clamp(openness, 0f, 1f)
        lastOpenness = open
        if (open <= 0f) return

        val veil = smoothstep(0f, 1f, open)
        fade(dimPaint, dimColour, DIM_ALPHA * veil)
        canvas.drawRect(bounds, dimPaint)

        val save = canvas.save()
        canvas.translate(0f, slideFor(open))

        canvas.drawPath(sheetPath, platePaint)
        if (edgeShade > 0f) {
            scratch.set(sheet.left, sheet.bottom, sheet.right, sheet.bottom + edgeShade)
            shadePaint.alpha = (EDGE_SHADE_ALPHA * veil * 255f + 0.5f).toInt().coerceIn(0, 255)
            canvas.drawRect(scratch, shadePaint)
        }

        drawField(canvas)

        val clip = canvas.save()
        // Both clips: the rectangle is the list's own window, the plate keeps the bottom row out
        // of the sheet's rounded corners.
        canvas.clipPath(sheetPath)
        canvas.clipRect(listRect)
        val leaving = back
        drawBank(canvas, leaving, leaving.parkedScroll)
        drawBank(canvas, front, scroll.value)
        canvas.restoreToCount(clip)

        drawHint(canvas)
        canvas.restoreToCount(save)
    }

    /**
     * Moves every spring, decay and track on by [dt] seconds. Returns true while anything still
     * has somewhere to be — which includes the caret, so a shown panel always asks for frames.
     */
    fun advance(dt: Float): Boolean {
        var running = false
        if (bankA.timeline.advance(dt)) running = true
        if (bankB.timeline.advance(dt)) running = true
        if (scroll.advance(dt)) running = true
        if (clearFade.advance(dt)) running = true
        if (hintFade.advance(dt)) running = true
        if (flash.advance(dt)) {
            running = true
        } else if (flashRow >= 0) {
            flashRow = -1
        }
        if (visible) {
            if (typingHold > 0f) {
                typingHold -= dt
                caretBlink.seek(0f)
            } else if (!motion.instant && !caretBlink.advance(dt)) {
                caretBlink.restart()
            }
            running = true
        }
        return running
    }

    // ---- input -----------------------------------------------------------------------------

    /**
     * Takes a tap in world space. Returns true for any tap at all while the panel is up: the
     * sheet and its dim cover the world, and a tap on the dim is a dismissal rather than
     * something for the world underneath to answer.
     */
    fun onTap(x: Float, y: Float): Boolean {
        if (!ready || !visible) return false
        val local = y - slideFor(lastOpenness)
        if (!sheet.contains(x, local)) {
            onDismiss?.invoke()
            return true
        }
        if (query.isNotEmpty() && clearRect.contains(x, local)) {
            clear()
            return true
        }
        if (fieldRect.contains(x, local)) {
            moveCaret(indexAt(x))
            return true
        }
        if (listRect.contains(x, local)) {
            val index = rowAt(local)
            val entry = if (index >= 0) front.rows[index].entry else null
            if (entry != null) {
                flashRow = index
                flash.restart()
                onResultChosen?.invoke(entry)
            }
        }
        return true
    }

    /**
     * Drags the list by [dy] pixels of finger travel, positive downwards. Past either end the
     * travel is rubber-banded, so the list resists rather than stops.
     */
    fun scrollBy(dy: Float) {
        if (!ready) return
        // A finger always wins: whatever fling was running is over the moment one lands.
        scroll.velocity = 0f
        val settled = clamp(scroll.value, scroll.min, scroll.max)
        val raw = settled + undamp(scroll.value - settled) + dy
        val next = clamp(raw, scroll.min, scroll.max)
        scroll.value = next + damp(raw - next)
    }

    /** Releases the list at [velocityY] pixels a second, positive downwards, into a decay. */
    fun fling(velocityY: Float) {
        if (!ready) return
        scroll.velocity = velocityY
    }

    // ---- drawing ---------------------------------------------------------------------------

    private fun drawField(canvas: Canvas) {
        canvas.drawRoundRect(fieldRect, fieldRadius, fieldRadius, fieldPaint)
        canvas.drawRoundRect(fieldRect, fieldRadius, fieldRadius, ringPaint)
        glyphPaint.color = mutedColour
        canvas.drawPath(magnifierPath, glyphPaint)

        val clip = canvas.save()
        canvas.clipRect(textLeft, fieldRect.top, textLeft + textWidthAvailable, fieldRect.bottom)
        if (query.isEmpty()) {
            // Nudged clear of the caret, which sits at the head of an empty field.
            canvas.drawText(
                placeholderText,
                0,
                placeholderText.length,
                textLeft + caretWidth * 3f,
                textBaseline,
                placeholderPaint,
            )
        } else {
            canvas.drawText(query, 0, query.length, textLeft - textScroll, textBaseline, queryPaint)
        }
        if (visible) {
            val alpha = if (typingHold > 0f || motion.instant) 1f else caretBlink.value
            if (alpha > VISIBLE_ALPHA) {
                fade(caretPaint, accentColour, alpha)
                val x = textLeft - textScroll + prefixWidth(caretIndex)
                scratch.set(x, caretTop, x + caretWidth, caretBottom)
                canvas.drawRoundRect(scratch, caretWidth * 0.5f, caretWidth * 0.5f, caretPaint)
            }
        }
        canvas.restoreToCount(clip)

        val cross = clearFade.value
        if (cross > VISIBLE_ALPHA) {
            val save = canvas.save()
            val scale = lerp(0.7f, 1f, cross)
            canvas.scale(scale, scale, clearRect.centerX(), clearRect.centerY())
            fade(pressPaint, pressColour, cross)
            val radius = clearRect.width() * 0.30f
            canvas.drawCircle(clearRect.centerX(), clearRect.centerY(), radius, pressPaint)
            fade(glyphPaint, mutedColour, cross)
            canvas.drawPath(crossPath, glyphPaint)
            canvas.restoreToCount(save)
        }
    }

    private fun drawBank(canvas: Canvas, bank: Bank, offset: Float) {
        val first = listRect.top + offset
        var i = 0
        while (i < bank.count) {
            val top = first + i * rowStride
            // Rows are in order, so the first one past the bottom ends the bank.
            if (top > listRect.bottom + rowRise) return
            val row = bank.rows[i]
            val progress = clamp(row.enter.value, 0f, 1f)
            val onScreen = top + rowStride > listRect.top - rowRise
            if (row.entry != null && progress > VISIBLE_ALPHA && onScreen) {
                drawRow(canvas, row, top, progress, bank === front && i == flashRow)
            }
            i++
        }
    }

    private fun drawRow(canvas: Canvas, row: Row, top: Float, progress: Float, pressed: Boolean) {
        // Arriving cards come down into place with the sheet and leaving ones keep going the same
        // way, so the whole list only ever flows in one direction.
        val travel = (1f - progress) * rowRise * (if (row.leaving) 1f else -1f)
        val scale = lerp(ROW_ENTER_SCALE, 1f, progress)
        val save = canvas.save()
        canvas.translate(0f, top + travel)
        canvas.scale(scale, scale, listRect.left, rowStride * 0.5f)

        if (pressed && flash.value > VISIBLE_ALPHA) {
            scratch.set(
                listRect.left + rowInset * 0.5f,
                rowInset * 0.25f,
                listRect.right - rowInset * 0.5f,
                rowStride - rowInset * 0.25f,
            )
            fade(pressPaint, pressColour, flash.value)
            canvas.drawRoundRect(scratch, rowRadius, rowRadius, pressPaint)
        }

        fade(dayPaint, inkColour, progress)
        canvas.drawText(row.day, dateCentreX, dayBaseline, dayPaint)
        fade(monthPaint, faintColour, progress)
        canvas.drawText(row.month, dateCentreX, monthBaseline, monthPaint)

        fade(rulePaint, row.colour, progress)
        scratch.set(
            ruleX,
            (rowStride - ruleHeight) * 0.5f,
            ruleX + ruleWidth,
            (rowStride + ruleHeight) * 0.5f,
        )
        canvas.drawRoundRect(scratch, ruleWidth * 0.5f, ruleWidth * 0.5f, rulePaint)

        val title = row.title
        fade(titlePaint, inkColour, progress)
        if (row.matchEnd > row.matchStart) {
            canvas.drawText(title, 0, row.matchStart, titleLeft, titleBaseline, titlePaint)
            fade(matchPaint, accentColour, progress)
            canvas.drawText(
                title,
                row.matchStart,
                row.matchEnd,
                titleLeft + row.beforeWidth,
                titleBaseline,
                matchPaint,
            )
            canvas.drawText(
                title,
                row.matchEnd,
                title.length,
                titleLeft + row.beforeWidth + row.matchWidth,
                titleBaseline,
                titlePaint,
            )
        } else {
            canvas.drawText(title, 0, title.length, titleLeft, titleBaseline, titlePaint)
        }

        fade(metaPaint, faintColour, progress)
        canvas.drawText(row.meta, 0, row.meta.length, titleLeft, metaBaseline, metaPaint)

        canvas.restoreToCount(save)
    }

    private fun drawHint(canvas: Canvas) {
        val alpha = hintFade.value
        if (alpha <= VISIBLE_ALPHA || hint.isEmpty()) return
        fade(hintPaint, faintColour, alpha)
        canvas.drawText(hint, 0, hint.length, listRect.centerX(), hintBaseline, hintPaint)
    }

    // ---- layout ----------------------------------------------------------------------------

    private fun layout() {
        val metrics = this.metrics
        if (metrics == null || bounds.isEmpty) {
            ready = false
            return
        }

        val gutter = metrics.gutter
        sheetHeight = max(bounds.height() * SHEET_FRACTION, metrics.rowHeight * 4f)
        sheet.set(bounds.left, bounds.top, bounds.right, bounds.top + sheetHeight)

        val corner = metrics.radiusLarge
        sheetRadii[0] = 0f
        sheetRadii[1] = 0f
        sheetRadii[2] = 0f
        sheetRadii[3] = 0f
        sheetRadii[4] = corner
        sheetRadii[5] = corner
        sheetRadii[6] = corner
        sheetRadii[7] = corner
        sheetPath.reset()
        sheetPath.addRoundRect(sheet, sheetRadii, Path.Direction.CW)

        edgeShade = metrics.dp(18f)
        shadePaint.shader = LinearGradient(
            0f,
            sheet.bottom,
            0f,
            sheet.bottom + edgeShade,
            (shadeColour and 0x00FFFFFF) or (0xFF shl 24),
            shadeColour and 0x00FFFFFF,
            Shader.TileMode.CLAMP,
        )

        val fieldHeight = metrics.rowHeight * 0.88f
        val fieldTop = sheet.top + gutter * 1.25f
        fieldRect.set(sheet.left + gutter, fieldTop, sheet.right - gutter, fieldTop + fieldHeight)
        fieldRadius = min(metrics.radiusLarge, fieldHeight * 0.5f)
        clearRect.set(fieldRect.right - fieldHeight, fieldTop, fieldRect.right, fieldRect.bottom)

        buildMagnifier(fieldHeight)
        buildCross()

        textLeft = fieldRect.left + fieldHeight * 0.92f
        textWidthAvailable = max(clearRect.left - metrics.dp(4f) - textLeft, metrics.dp(24f))
        caretPad = metrics.dp(2f)
        caretWidth = max(metrics.hairline * 2f, metrics.dp(1.6f))
        queryPaint.getFontMetrics(fontMetrics)
        textBaseline = fieldRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) * 0.5f
        caretTop = textBaseline + fontMetrics.ascent * 0.92f
        caretBottom = textBaseline + fontMetrics.descent * 0.7f

        listRect.set(
            sheet.left,
            fieldRect.bottom + gutter * 0.75f,
            sheet.right,
            sheet.bottom - gutter * 0.75f,
        )

        rowStride = metrics.rowHeight * 1.26f
        rowRise = metrics.dp(ROW_RISE_DP)
        rowInset = gutter
        rowRadius = metrics.radiusSmall

        val dateWidth = metrics.dp(44f)
        dateCentreX = listRect.left + gutter + dateWidth * 0.5f
        ruleX = listRect.left + gutter + dateWidth + gutter * 0.55f
        ruleWidth = metrics.dp(3f)
        ruleHeight = rowStride * 0.44f
        titleLeft = ruleX + ruleWidth + gutter * 0.75f
        titleMaxWidth = max(listRect.right - gutter - titleLeft, metrics.dp(48f))

        layoutRowText(metrics)
        ready = true
        reflowRows()
        updateScrollBounds()
        ensureCaretVisible()
    }

    private fun layoutRowText(metrics: Metrics) {
        val gap = metrics.dp(3f)

        titlePaint.getFontMetrics(fontMetrics)
        val titleAscent = fontMetrics.ascent
        val titleDescent = fontMetrics.descent
        val titleLine = titleDescent - titleAscent
        metaPaint.getFontMetrics(fontMetrics)
        val metaAscent = fontMetrics.ascent
        val metaLine = fontMetrics.descent - fontMetrics.ascent
        val block = titleLine + gap + metaLine
        titleBaseline = (rowStride - block) * 0.5f - titleAscent
        metaBaseline = titleBaseline + titleDescent + gap - metaAscent

        dayPaint.getFontMetrics(fontMetrics)
        val dayAscent = fontMetrics.ascent
        val dayDescent = fontMetrics.descent
        val dayLine = dayDescent - dayAscent
        monthPaint.getFontMetrics(fontMetrics)
        val monthAscent = fontMetrics.ascent
        val monthLine = fontMetrics.descent - fontMetrics.ascent
        val dateBlock = dayLine + monthLine
        dayBaseline = (rowStride - dateBlock) * 0.5f - dayAscent
        monthBaseline = dayBaseline + dayDescent - monthAscent

        hintPaint.getFontMetrics(fontMetrics)
        hintBaseline = listRect.top + rowStride * 0.5f -
            (fontMetrics.ascent + fontMetrics.descent) * 0.5f
    }

    private fun buildMagnifier(fieldHeight: Float) {
        val cx = fieldRect.left + fieldHeight * 0.46f
        val cy = fieldRect.centerY()
        val r = fieldHeight * 0.17f
        val reach = r * 0.72f
        magnifierPath.reset()
        magnifierPath.addCircle(cx - r * 0.16f, cy - r * 0.16f, r, Path.Direction.CW)
        magnifierPath.moveTo(cx + r * 0.52f, cy + r * 0.52f)
        magnifierPath.lineTo(cx + r * 0.52f + reach, cy + r * 0.52f + reach)
    }

    private fun buildCross() {
        val cx = clearRect.centerX()
        val cy = clearRect.centerY()
        val arm = clearRect.width() * 0.14f
        crossPath.reset()
        crossPath.moveTo(cx - arm, cy - arm)
        crossPath.lineTo(cx + arm, cy + arm)
        crossPath.moveTo(cx + arm, cy - arm)
        crossPath.lineTo(cx - arm, cy + arm)
    }

    private fun updateScrollBounds() {
        val content = front.count * rowStride + (metrics?.gutter ?: 0f)
        maxScroll = max(0f, content - listRect.height())
        scroll.min = -maxScroll
        scroll.max = 0f
        rubberLimit = max(listRect.height() * 0.22f, metrics?.dp(64f) ?: 1f)
    }

    // ---- rows ------------------------------------------------------------------------------

    /** Sends the hand that is up downward and hands the other bank over to be dealt into. */
    private fun park() {
        val leaving = front
        leaving.parkedScroll = scroll.value
        var i = 0
        while (i < leaving.count) {
            val row = leaving.rows[i]
            row.leaving = true
            row.enter.target = 0f
            i++
        }
        frontIsA = !frontIsA
    }

    private fun rearmFront() {
        val bank = front
        var i = 0
        while (i < bank.count) {
            val row = bank.rows[i]
            row.leaving = false
            row.enter.snapTo(0f)
            row.enter.target = 1f
            row.enter.profile(motion)
            i++
        }
        bank.timeline.restart()
    }

    private fun clearResults() {
        if (front.count == 0) {
            resultsQuery = null
            return
        }
        park()
        front.count = 0
        resultsQuery = null
        updateScrollBounds()
    }

    private fun reflowRows() {
        reflowBank(bankA)
        reflowBank(bankB)
    }

    private fun reflowBank(bank: Bank) {
        var i = 0
        while (i < bank.count) {
            val row = bank.rows[i]
            val entry = row.entry
            if (entry != null) prepareRow(row, entry)
            i++
        }
    }

    /** Everything a card needs, measured once here so no frame ever measures or allocates. */
    private fun prepareRow(row: Row, entry: AgendaEntry) {
        row.entry = entry
        // An all-day instance is stored at UTC midnight, so reading it in the device's own zone
        // would move a morning entry onto the day before.
        val date: LocalDate = if (entry.allDay) {
            Instant.ofEpochMilli(entry.begin).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            EventRepository.dateOf(entry)
        }
        row.day = date.dayOfMonth.toString()
        row.month = date.month
            .getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            .replace(".", "")
            .uppercase(locale)
        row.colour = resolveColour(entry.colour)
        row.meta = buildMeta(entry)
        fitTitle(row, entry.title)
    }

    private fun buildMeta(entry: AgendaEntry): String {
        builder.setLength(0)
        if (entry.allDay) {
            builder.append(allDayText)
        } else {
            val start = Instant.ofEpochMilli(entry.begin).atZone(zone).toLocalTime()
            builder.append(timeFormat.format(start))
            if (entry.end > entry.begin) {
                val end = Instant.ofEpochMilli(entry.end).atZone(zone).toLocalTime()
                builder.append(TIME_RANGE).append(timeFormat.format(end))
            }
        }
        val calendar = entry.calendarName
        if (!calendar.isNullOrBlank()) builder.append(SEPARATOR).append(calendar)
        return builder.toString()
    }

    /**
     * Truncates a title to the room a card has, then locates the searched-for run inside what is
     * left. The run is measured with its own paint: it is set a weight heavier, and would
     * otherwise overrun the width the rest of the title was fitted to.
     */
    private fun fitTitle(row: Row, rawTitle: String) {
        val source = if (rawTitle.isBlank()) UNTITLED else rawTitle
        val needle = (resultsQuery ?: query).trim()
        var text = source
        var start = if (needle.length >= MIN_QUERY) {
            source.indexOf(needle, ignoreCase = true)
        } else {
            -1
        }
        var end = if (start >= 0) start + needle.length else -1

        if (titleMaxWidth > 0f && composedWidth(text, start, end) > titleMaxWidth) {
            val ellipsisWidth = titlePaint.measureText(ELLIPSIS)
            val room = max(titleMaxWidth - ellipsisWidth, 0f)
            var keep = titlePaint.breakText(source, true, room, null)
            var pass = 0
            while (true) {
                if (keep <= 0) {
                    text = ELLIPSIS
                    start = -1
                    end = -1
                    break
                }
                builder.setLength(0)
                builder.append(source, 0, keep).append(ELLIPSIS)
                text = builder.toString()
                // The ellipsis is never part of a match, so the run stops where the kept text does.
                val s = if (start in 0 until keep) start else -1
                val e = if (s >= 0) min(end, keep) else -1
                if (composedWidth(text, s, e) <= titleMaxWidth || pass >= FIT_PASSES) {
                    start = s
                    end = e
                    break
                }
                keep--
                pass++
            }
        }

        row.title = text
        if (start >= 0 && end > start) {
            row.matchStart = start
            row.matchEnd = end
            row.beforeWidth = titlePaint.measureText(text, 0, start)
            row.matchWidth = matchPaint.measureText(text, start, end)
        } else {
            row.matchStart = 0
            row.matchEnd = 0
            row.beforeWidth = 0f
            row.matchWidth = 0f
        }
    }

    private fun composedWidth(text: String, start: Int, end: Int): Float {
        if (start < 0 || end <= start) return titlePaint.measureText(text, 0, text.length)
        return titlePaint.measureText(text, 0, start) +
            matchPaint.measureText(text, start, end) +
            titlePaint.measureText(text, end, text.length)
    }

    @ColorInt
    private fun resolveColour(@ColorInt raw: Int): Int {
        val theme = this.theme ?: return raw
        if (raw == 0) return theme.accent
        val opaque = raw or (0xFF shl 24)
        // A calendar's colour was chosen against whatever its own app draws on, and can land
        // near-invisible here; it is walked far enough away from the plane to stay a mark.
        if (Oklch.contrast(opaque, theme.surface) >= 1.4f) return opaque
        return Oklch.lighten(opaque, if (theme.dark) 0.18f else -0.18f)
    }

    private fun rowAt(localY: Float): Int {
        if (rowStride <= 0f) return -1
        val y = localY - listRect.top - scroll.value
        if (y < 0f) return -1
        val index = floor(y / rowStride).toInt()
        return if (index in 0 until front.count) index else -1
    }

    // ---- text ------------------------------------------------------------------------------

    private fun setText(text: String, at: Int) {
        if (text == query) {
            moveCaret(at)
            return
        }
        caretIndex = at.coerceIn(0, text.length)
        query = text
    }

    private fun afterTextChange() {
        if (caretIndex > query.length) caretIndex = query.length
        measureQuery()
        clearFade.target = if (query.isEmpty()) 0f else 1f
        holdCaret()
        ensureCaretVisible()
        // Under the provider's floor nothing will ever come back, so the cards leave now rather
        // than sitting there answering a question that is no longer being asked.
        if (query.trim().length < MIN_QUERY) clearResults() else resultsQuery = null
        updateHint()
        onQueryChanged?.invoke(query)
    }

    private fun measureQuery() {
        val n = query.length
        if (advances.size < n) advances = FloatArray(max(n, advances.size * 2))
        if (prefixes.size < n + 1) prefixes = FloatArray(max(n + 1, prefixes.size * 2))
        prefixes[0] = 0f
        if (n > 0) {
            queryPaint.getTextWidths(query, 0, n, advances)
            var i = 0
            var sum = 0f
            while (i < n) {
                sum += advances[i]
                prefixes[i + 1] = sum
                i++
            }
        }
        prefixCount = n + 1
    }

    private fun prefixWidth(index: Int): Float {
        if (prefixCount <= 0) return 0f
        return prefixes[index.coerceIn(0, prefixCount - 1)]
    }

    private fun ensureCaretVisible() {
        if (!ready) return
        val total = prefixWidth(prefixCount - 1)
        val at = prefixWidth(caretIndex)
        if (at - textScroll > textWidthAvailable - caretPad) {
            textScroll = at - textWidthAvailable + caretPad
        }
        if (at - textScroll < caretPad) textScroll = at - caretPad
        textScroll = clamp(textScroll, 0f, max(0f, total - textWidthAvailable + caretPad))
    }

    private fun indexAt(x: Float): Int {
        val wanted = x - textLeft + textScroll
        if (wanted <= 0f) return 0
        var i = 1
        while (i < prefixCount) {
            // Half way across a glyph is where the caret changes sides, which is what makes
            // tapping into the middle of a word land where the eye expects.
            if (wanted < (prefixes[i - 1] + prefixes[i]) * 0.5f) return i - 1
            i++
        }
        return prefixCount - 1
    }

    private fun holdCaret() {
        typingHold = TYPING_HOLD_SECONDS
        caretBlink.seek(0f)
    }

    private fun updateHint() {
        val trimmed = query.trim()
        hint = when {
            trimmed.length < MIN_QUERY -> rangeText
            resultsQuery == query && front.count == 0 -> {
                builder.setLength(0)
                builder.append(noMatchText)
                    .append(' ')
                    .append(OPEN_QUOTE)
                    .append(trimmed)
                    .append(CLOSE_QUOTE)
                builder.toString()
            }
            else -> ""
        }
        hintFade.target = if (hint.isEmpty()) 0f else 1f
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun applyProfile() {
        clearFade.profile(motion)
        hintFade.profile(motion)
        applyProfileTo(bankA)
        applyProfileTo(bankB)
        val stagger = motion.staggerSeconds
        if (stagger != staggerBuiltFor) {
            bankA.rebuild(stagger)
            bankB.rebuild(stagger)
            staggerBuiltFor = stagger
        }
    }

    private fun applyProfileTo(bank: Bank) {
        var i = 0
        while (i < bank.rows.size) {
            bank.rows[i].enter.profile(motion)
            i++
        }
    }

    private fun slideFor(open: Float): Float = -(1f - open) * sheetHeight

    // Overscroll is shown compressed towards a limit it never reaches, so the further the list is
    // pulled the harder it pulls back — and [undamp] recovers the raw travel a finger has put in,
    // so a second drag continues the first instead of jumping.
    private fun damp(over: Float): Float {
        if (over == 0f) return 0f
        return rubberLimit * over / (abs(over) + rubberLimit)
    }

    private fun undamp(shown: Float): Float {
        if (shown == 0f) return 0f
        val room = max(rubberLimit - abs(shown), rubberLimit * 0.02f)
        return rubberLimit * shown / room
    }

    private fun fade(paint: Paint, @ColorInt colour: Int, alpha: Float) {
        val base = (colour ushr 24) and 0xFF
        val a = (base * clamp(alpha, 0f, 1f) + 0.5f).toInt().coerceIn(0, 255)
        paint.color = (colour and 0x00FFFFFF) or (a shl 24)
    }

    private fun currentLocale(): Locale {
        val locales = appContext.resources.configuration.locales
        return if (locales.size() > 0) locales.get(0) else Locale.getDefault()
    }
}
