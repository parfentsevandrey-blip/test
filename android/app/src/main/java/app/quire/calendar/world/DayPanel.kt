package app.quire.calendar.world

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.format.DateFormat
import app.quire.calendar.R
import app.quire.calendar.core.AgendaEntry
import app.quire.calendar.core.MonthModel
import app.quire.engine.anim.Decay
import app.quire.engine.anim.MotionProfile
import app.quire.engine.anim.Spring
import app.quire.engine.anim.clamp
import app.quire.engine.anim.lerp
import app.quire.engine.anim.smoothstep
import app.quire.engine.design.Metrics
import app.quire.engine.design.Theme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

// Sizes are named in design units and pass through Metrics.dp / Metrics.sp, so the whole panel
// rescales with the user's size preference rather than with whatever the display happens to be.
private const val PAD_H = 22f
private const val HEADER_TOP = 22f
private const val HEADER_GAP = 15f
private const val LIST_TOP = 10f
private const val LIST_BOTTOM = 32f
private const val NUMBER_SP = 42f
private const val CAPS_SP = 11.5f
private const val TIME_SP = 12.5f
private const val TITLE_SP = 15.5f
private const val SUB_SP = 12.5f
private const val EMPTY_SP = 14f
private const val CAPS_GAP = 3f
private const val NUMBER_GAP = 14f
private const val ROW_PAD_V = 13f
private const val ROW_TEXT_GAP = 3f
private const val TIME_GAP = 12f
private const val RULE_W = 3f
private const val RULE_INSET = 3f
private const val RULE_GAP = 14f
private const val ROW_RISE = 16f

// Wide enough to read as small caps rather than as squeezed capitals, narrow enough that a long
// weekday name still sits beside the number.
private const val CAPS_TRACKING = 0.15f

// Digits have no descender and a flat top: their ink runs from roughly this fraction of the text
// size above the baseline, which is what the small caps beside them are centred against.
private const val DIGIT_CAP = 0.71f

// The container is already growing before its contents mean anything; text that starts fading at
// zero reads as a smear across the morph instead of as a panel filling up.
private const val CONTENT_FADE_START = 0.34f
private const val CONTENT_FADE_END = 0.96f

// A spring of stiffness k covers most of its travel in about this over the root of k. It turns
// the motion profile into the length of one entry's fade without inventing a second tempo.
private const val RISE_CONSTANT = 4f

// Extra wave beyond the last entry's window, so an under-damped profile swinging back past its
// target cannot dip the final entry below full strength.
private const val WAVE_TAIL = 1.25f

// How much of a drag step survives once the list is already past its end.
private const val RUBBER = 0.42f

// A drag that never reports its end — a cancelled stroke, a lifted-away finger — must not leave
// the list parked outside its bounds; this is how long the hold outlives the last step.
private const val HOLD_RELEASE_SECONDS = 0.4f

// Within a pixel of the top is at the top: the world closes on a downward drag from here.
private const val TOP_EPSILON = 1f

/**
 * The opened day. It grows out of the tile that was chosen, fills the screen with that day's
 * entries, and then scrolls under the finger with real inertia.
 *
 * The panel owns its scroll and the stagger its entries arrive on; it does not own the opening
 * itself. The world drives that from its own spring and hands the progress to [draw], so one
 * gesture can open the panel and drag it back closed without two springs disagreeing.
 *
 * @param context supplies the locale, the 12- or 24-hour preference and the two strings the
 *   panel can need. Nothing is read from it after construction.
 */
class DayPanel(context: Context) {

    private val locale: Locale = context.resources.configuration.locales.let {
        if (it.isEmpty) Locale.getDefault() else it.get(0)
    }

    // Built once: formatting inside a frame would allocate a string per entry per frame.
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
        locale,
    )

    private val emptyText: String = context.getString(R.string.nothing_scheduled)

    // "All day" split at its last space so it stacks into the two lines an entry's time column
    // already has. A single-word translation keeps one line and is centred between them.
    private val allDayTop: String
    private val allDayBottom: String?

    init {
        val label = context.getString(R.string.all_day).uppercase(locale)
        val cut = label.lastIndexOf(' ')
        allDayTop = if (cut > 0) label.substring(0, cut) else label
        allDayBottom = if (cut > 0) label.substring(cut + 1) else null
    }

    private val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        fontFeatureSettings = "tnum"
    }
    private val capsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        letterSpacing = CAPS_TRACKING
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        fontFeatureSettings = "tnum"
        textAlign = Paint.Align.RIGHT
    }
    private val timeCapsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = medium
        letterSpacing = CAPS_TRACKING
        textAlign = Paint.Align.RIGHT
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular }
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint()

    // Every rectangle, path and array a frame touches is a field: a panel that allocated per
    // frame would collect exactly while it was being flung.
    private val origin = RectF()
    private val full = RectF()
    private val morph = RectF()
    private val edge = RectF()
    private val ruleRect = RectF()
    private val clip = Path()

    private var theme: Theme? = null
    private var metrics: Metrics? = null

    private var motion: MotionProfile = MotionProfile.STANDARD
    private var zone: ZoneId = ZoneId.systemDefault()

    private val rows = ArrayList<Row>(16)

    private var dayText: String = ""
    private var rawWeekdayText: String = ""
    private var rawMonthText: String = ""
    private var weekdayText: String = ""
    private var monthText: String = ""

    private var originRadius = 0f
    private var contentLeft = 0f
    private var contentRight = 0f
    private var numberBaseline = 0f
    private var capsLeft = 0f
    private var weekdayBaseline = 0f
    private var monthBaseline = 0f
    private var ruleY = 0f
    private var listTop = 0f
    private var emptyBaseline = 0f
    private var timeRight = 0f
    private var ruleX = 0f
    private var textLeft = 0f

    // The inertial scroll. Its bounds are the list's, so an overrun is rubber-banded by the
    // engine rather than clamped flat at the end of the travel. They start closed rather than
    // at the engine's infinities, so a panel that is touched before it is laid out cannot run.
    private val scroll = Decay().apply {
        min = 0f
        max = 0f
    }

    // One spring is the whole stagger: it walks a wave front along the list's own timeline, and
    // each entry reads its fade off a smoothstep window at its own offset into that wave.
    private val wave = Spring()
    private var waveEnd = 0f
    private var riseSeconds = RISE_CONSTANT / sqrt(MotionProfile.STANDARD.stiffness)

    private var held = false
    private var heldSeconds = 0f

    /** One entry's laid-out line: measured text, its slot, and where it sits in the wave. */
    private class Row(val entry: AgendaEntry) {
        var startText: String = ""
        var endText: String = ""
        var rawTitle: String = ""
        var rawSubtitle: String? = null
        var title: String = ""
        var subtitle: String? = null
        var colour: Int = 0
        var allDay: Boolean = false
        var top: Float = 0f
        var height: Float = 0f
        var delay: Float = 0f
    }

    /**
     * Adopts the palette and the size system. Both are held, so a theme or scale change is one
     * call followed by the next frame rather than a rebuilt panel.
     */
    fun configure(theme: Theme, metrics: Metrics) {
        this.theme = theme
        this.metrics = metrics
        numberPaint.textSize = metrics.sp(NUMBER_SP)
        capsPaint.textSize = metrics.sp(CAPS_SP)
        timePaint.textSize = metrics.sp(TIME_SP)
        timeCapsPaint.textSize = metrics.sp(CAPS_SP - 1f)
        titlePaint.textSize = metrics.sp(TITLE_SP)
        subPaint.textSize = metrics.sp(SUB_SP)
        emptyPaint.textSize = metrics.sp(EMPTY_SP)
        edgePaint.strokeWidth = metrics.hairline
        measureOriginRadius()
        layout()
    }

    /** Where it grows from, in screen pixels; supplied by the world as the tile's bounds. */
    fun setOrigin(origin: RectF) {
        this.origin.set(origin)
        measureOriginRadius()
    }

    /** Where it ends up: the rectangle the panel occupies once it is fully open. */
    fun setBounds(full: RectF) {
        this.full.set(full)
        layout()
    }

    /**
     * Hands the panel a day and its entries and starts the stagger. The list is copied, so the
     * caller is free to reuse or mutate what it passed.
     */
    fun show(date: LocalDate, entries: List<AgendaEntry>, motion: MotionProfile) {
        this.motion = motion
        zone = ZoneId.systemDefault()
        dayText = date.dayOfMonth.toString()
        rawWeekdayText = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .uppercase(locale)
        rawMonthText = MonthModel.monthName(YearMonth.from(date), locale).uppercase(locale) +
            " " + date.year
        rows.clear()
        for (i in entries.indices) {
            rows.add(buildRow(entries[i], date))
        }
        scroll.snapTo(0f)
        held = false
        heldSeconds = 0f
        riseSeconds = RISE_CONSTANT / sqrt(max(motion.stiffness, 1f))
        layout()
        // Seeded before the profile is applied: OFF pins the value to the target on the spot,
        // which is what "arrive now" has to mean for a staggered list too.
        wave.snapTo(0f)
        wave.target = waveEnd
        wave.profile(motion)
    }

    /** Parks the panel's own motion. The world keeps drawing it while its openness runs out. */
    fun hide() {
        held = false
        heldSeconds = 0f
        scroll.velocity = 0f
    }

    /**
     * 0 = still a tile, 1 = fully open. The world drives this from its own spring.
     *
     * The rectangle and its corner morph with [openness]; the contents are laid out for the open
     * panel throughout and fade in once there is somewhere to put them, which is why they are
     * clipped rather than scaled — text that scales through a morph reads as stretched text.
     */
    fun draw(canvas: Canvas, openness: Float) {
        val theme = this.theme ?: return
        val metrics = this.metrics ?: return
        if (full.width() <= 0f || full.height() <= 0f) return
        val t = clamp(openness, 0f, 1f)
        if (t <= 0.002f) return

        morph.set(
            lerp(origin.left, full.left, t),
            lerp(origin.top, full.top, t),
            lerp(origin.right, full.right, t),
            lerp(origin.bottom, full.bottom, t),
        )
        val radius = lerp(originRadius, metrics.radiusLarge, t)
        platePaint.color = theme.surface
        canvas.drawRoundRect(morph, radius, radius, platePaint)
        edge.set(morph)
        edge.inset(metrics.hairline * 0.5f, metrics.hairline * 0.5f)
        edgePaint.color = theme.hairline
        canvas.drawRoundRect(edge, radius, radius, edgePaint)

        val alpha = smoothstep(CONTENT_FADE_START, CONTENT_FADE_END, t)
        if (alpha <= 0.004f) return
        val saved = canvas.save()
        clip.reset()
        clip.addRoundRect(morph, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        // The contents travel with the panel's corner, so they emerge from the tile rather than
        // sliding in from where they will eventually be.
        canvas.translate(morph.left - full.left, morph.top - full.top)
        drawHeader(canvas, theme, metrics, alpha)
        drawList(canvas, theme, metrics, alpha)
        canvas.restoreToCount(saved)
    }

    /** Moves the scroll and the entry stagger on; false once neither needs another frame. */
    fun advance(dt: Float): Boolean {
        var running = false
        if (held) {
            heldSeconds += dt
            if (heldSeconds > HOLD_RELEASE_SECONDS) held = false
            // Kept live while a finger owns the list, so the release above can actually arrive
            // on a stroke that ends without ever reporting itself.
            running = true
        } else if (scroll.advance(dt)) {
            running = true
        }
        if (wave.advance(dt)) running = true
        return running
    }

    /** Returns the entry under a point, or null. Coordinates are the screen's, as drawn open. */
    fun entryAt(x: Float, y: Float): AgendaEntry? {
        if (rows.isEmpty()) return null
        if (x < full.left || x > full.right) return null
        if (y < listTop || y > full.bottom) return null
        val local = y - listTop + scroll.value
        for (i in rows.indices) {
            val row = rows[i]
            if (local >= row.top && local < row.top + row.height) return row.entry
        }
        return null
    }

    /**
     * Scrolls by one step of a drag. [dy] is the finger's own step, as GestureEngine reports it,
     * so the list follows the finger; travel past either end is resisted rather than refused.
     */
    fun scrollBy(dy: Float) {
        val start = scroll.value
        val delta = -dy
        var end = start + delta
        if (end < scroll.min) {
            val inside = if (start > scroll.min) scroll.min - start else 0f
            end = start + inside + (delta - inside) * RUBBER
        } else if (end > scroll.max) {
            val inside = if (start < scroll.max) scroll.max - start else 0f
            end = start + inside + (delta - inside) * RUBBER
        }
        scroll.snapTo(end)
        // The finger owns the value until it lets go, so the rubber band does not fight a drag
        // that is deliberately holding the list past its end.
        held = true
        heldSeconds = 0f
    }

    /** Releases the list with the drag's exit velocity, in pixels per second, from onDragEnd. */
    fun fling(velocityY: Float) {
        held = false
        heldSeconds = 0f
        if (motion.instant) {
            scroll.snapTo(clamp(scroll.value, scroll.min, scroll.max))
            return
        }
        scroll.velocity = -velocityY
    }

    /** The world uses this to decide when a drag closes the panel instead of scrolling it. */
    val scrollAtTop: Boolean
        get() = scroll.value <= TOP_EPSILON

    // ---- layout --------------------------------------------------------

    private fun measureOriginRadius() {
        val metrics = this.metrics ?: return
        // A tile's own corner is not reported, so it is inferred: the panel's small radius,
        // never more than would round the tile into a circle.
        originRadius = minOf(
            metrics.radiusSmall * 2f,
            origin.width() * 0.5f,
            origin.height() * 0.5f,
        ).coerceAtLeast(0f)
    }

    private fun buildRow(entry: AgendaEntry, date: LocalDate): Row {
        val row = Row(entry)
        row.allDay = entry.allDay
        val calendar = entry.calendarName?.takeIf { it.isNotBlank() }
        // An untitled event borrows its calendar's name rather than being labelled with a phrase
        // it never carried; the line beneath then drops what has already been said.
        row.rawTitle = entry.title.ifBlank { calendar.orEmpty() }
        row.rawSubtitle = entry.location ?: calendar?.takeIf { it != row.rawTitle }
        if (!entry.allDay) {
            // A span that reaches this day from outside it has no start or end to show here, so
            // the dash says "already running" instead of quoting yesterday's clock.
            val startsEarlier = dateAt(entry.begin).isBefore(date)
            val endsLater = dateAt(max(entry.begin, entry.end - 1L)).isAfter(date)
            row.startText = if (startsEarlier) CONTINUES else clockAt(entry.begin)
            row.endText = if (endsLater) CONTINUES else clockAt(entry.end)
        }
        return row
    }

    private fun dateAt(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun clockAt(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(timeFormatter)

    private fun layout() {
        val theme = this.theme ?: return
        val metrics = this.metrics ?: return
        if (full.width() <= 0f || full.height() <= 0f) return

        contentLeft = full.left + metrics.dp(PAD_H)
        contentRight = full.right - metrics.dp(PAD_H)

        numberBaseline = full.top + metrics.dp(HEADER_TOP) + numberPaint.textSize * DIGIT_CAP
        val capsLine = capsPaint.descent() - capsPaint.ascent()
        val stack = capsLine * 2f + metrics.dp(CAPS_GAP)
        val digitMiddle = numberBaseline - numberPaint.textSize * DIGIT_CAP * 0.5f
        weekdayBaseline = digitMiddle - stack * 0.5f - capsPaint.ascent()
        monthBaseline = weekdayBaseline + capsLine + metrics.dp(CAPS_GAP)
        capsLeft = contentLeft + numberPaint.measureText(dayText) + metrics.dp(NUMBER_GAP)
        // A long weekday name in a wide locale on a narrow screen would otherwise run out past
        // the panel and be cut by the clip; better a named ellipsis than a severed word.
        val capsRoom = max(contentRight - capsLeft, 1f)
        weekdayText = fit(rawWeekdayText, capsPaint, capsRoom)
        monthText = fit(rawMonthText, capsPaint, capsRoom)
        ruleY = numberBaseline + numberPaint.descent() + metrics.dp(HEADER_GAP)
        listTop = ruleY + metrics.hairline + metrics.dp(LIST_TOP)
        emptyBaseline = listTop + metrics.dp(ROW_PAD_V) - emptyPaint.ascent()

        // The time column is measured from the strings it will actually hold, so a locale whose
        // meridiem is three letters long widens the column instead of colliding with the rule.
        var timeWidth = 0f
        var anyAllDay = false
        for (i in rows.indices) {
            val row = rows[i]
            if (row.allDay) {
                anyAllDay = true
            } else {
                timeWidth = max(timeWidth, timePaint.measureText(row.startText))
                timeWidth = max(timeWidth, timePaint.measureText(row.endText))
            }
        }
        if (anyAllDay) {
            timeWidth = max(timeWidth, timeCapsPaint.measureText(allDayTop))
            val bottom = allDayBottom
            if (bottom != null) timeWidth = max(timeWidth, timeCapsPaint.measureText(bottom))
        }
        timeRight = contentLeft + timeWidth
        ruleX = timeRight + metrics.dp(TIME_GAP)
        textLeft = ruleX + metrics.dp(RULE_W) + metrics.dp(RULE_GAP)

        val available = max(contentRight - textLeft, 1f)
        val padV = metrics.dp(ROW_PAD_V)
        val titleLine = titlePaint.descent() - titlePaint.ascent()
        val subLine = subPaint.descent() - subPaint.ascent()
        val timeLine = timePaint.descent() - timePaint.ascent()
        val capsSlot = timeCapsPaint.descent() - timeCapsPaint.ascent()
        val slot = max(timeLine, capsSlot) * 2f
        var y = 0f
        for (i in rows.indices) {
            val row = rows[i]
            row.colour = if ((row.entry.colour ushr 24) == 0) {
                // The provider had no colour of its own, so the palette lends one that is at
                // least guaranteed to be legible against this surface.
                theme.categorical(i)
            } else {
                row.entry.colour
            }
            row.title = fit(row.rawTitle, titlePaint, available)
            val raw = row.rawSubtitle
            row.subtitle = if (raw == null) null else fit(raw, subPaint, available)
            var height = padV * 2f + titleLine
            if (row.subtitle != null) height += metrics.dp(ROW_TEXT_GAP) + subLine
            row.height = max(height, padV * 2f + slot)
            row.top = y
            row.delay = i * motion.staggerSeconds
            y += row.height
        }

        val contentHeight = if (rows.isEmpty()) 0f else y + metrics.dp(LIST_BOTTOM)
        scroll.min = 0f
        scroll.max = max(0f, contentHeight - (full.bottom - listTop))
        scroll.value = clamp(scroll.value, scroll.min, scroll.max)

        val lastDelay = if (rows.isEmpty()) 0f else rows[rows.size - 1].delay
        waveEnd = lastDelay + riseSeconds * WAVE_TAIL
        if (wave.target != waveEnd) wave.target = waveEnd
    }

    /**
     * Cuts [text] down to [width], ending on an ellipsis. Written out rather than handed to
     * `TextUtils.ellipsize` for two reasons: that helper is a no-op under the test graphics mode,
     * so a title that overflowed would look fine in a render and be cut on a real phone; and a
     * word boundary within reach reads better than a severed syllable, which it will not do.
     *
     * Runs from [layout], never from a frame, so the substring it builds costs nothing per frame.
     */
    private fun fit(text: String, paint: TextPaint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        val room = width - paint.measureText(ELLIPSIS)
        if (room <= 0f) return ELLIPSIS
        val fits = paint.breakText(text, true, room, null)
        if (fits <= 0) return ELLIPSIS
        // Back up to the last space, but only if one is close enough that the line still fills
        // its width; dropping half a title to reach a boundary is worse than cutting a word.
        var cut = fits
        var back = fits
        while (back > 0 && fits - back <= WORD_REACH) {
            if (text[back - 1] == ' ') {
                cut = back - 1
                break
            }
            back--
        }
        return text.substring(0, cut).trimEnd() + ELLIPSIS
    }

    // ---- drawing -------------------------------------------------------

    private fun drawHeader(canvas: Canvas, theme: Theme, metrics: Metrics, alpha: Float) {
        numberPaint.color = withAlpha(theme.ink, alpha)
        canvas.drawText(dayText, contentLeft, numberBaseline, numberPaint)
        capsPaint.color = withAlpha(theme.ink, alpha)
        canvas.drawText(weekdayText, capsLeft, weekdayBaseline, capsPaint)
        capsPaint.color = withAlpha(theme.inkFaint, alpha)
        canvas.drawText(monthText, capsLeft, monthBaseline, capsPaint)
        rulePaint.color = withAlpha(theme.hairline, alpha)
        canvas.drawRect(contentLeft, ruleY, contentRight, ruleY + metrics.hairline, rulePaint)
    }

    private fun drawList(canvas: Canvas, theme: Theme, metrics: Metrics, alpha: Float) {
        val saved = canvas.save()
        canvas.clipRect(full.left, listTop, full.right, full.bottom)
        if (rows.isEmpty()) {
            emptyPaint.color = withAlpha(theme.inkFaint, alpha * revealAt(0f))
            canvas.drawText(emptyText, contentLeft, emptyBaseline, emptyPaint)
            canvas.restoreToCount(saved)
            return
        }
        val offset = scroll.value
        val viewport = full.bottom - listTop
        // An entry still arriving sits below its slot, so the cull is widened by that much or a
        // row would be skipped on the very frames it is rising into view.
        val slack = metrics.dp(ROW_RISE)
        canvas.translate(0f, -offset)
        for (i in rows.indices) {
            val row = rows[i]
            if (row.top + row.height + slack < offset) continue
            if (row.top > offset + viewport) break
            drawRow(canvas, theme, metrics, row, alpha)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawRow(
        canvas: Canvas,
        theme: Theme,
        metrics: Metrics,
        row: Row,
        alpha: Float,
    ) {
        val reveal = revealAt(row.delay)
        if (reveal <= 0.004f) return
        val a = alpha * reveal
        val top = listTop + row.top + (1f - reveal) * metrics.dp(ROW_RISE)
        val bottom = top + row.height

        val ruleWidth = metrics.dp(RULE_W)
        ruleRect.set(
            ruleX,
            top + metrics.dp(RULE_INSET),
            ruleX + ruleWidth,
            bottom - metrics.dp(RULE_INSET),
        )
        fillPaint.color = withAlpha(row.colour, a)
        canvas.drawRoundRect(ruleRect, ruleWidth * 0.5f, ruleWidth * 0.5f, fillPaint)

        val titleBaseline = top + metrics.dp(ROW_PAD_V) - titlePaint.ascent()
        if (row.allDay) {
            // Letter spacing is carried on the last glyph too, so right-aligned caps would sit a
            // space short of the column edge without this nudge.
            val x = timeRight + timeCapsPaint.textSize * CAPS_TRACKING
            val line = timeCapsPaint.descent() - timeCapsPaint.ascent()
            val second = allDayBottom
            timeCapsPaint.color = withAlpha(theme.inkMuted, a)
            if (second == null) {
                canvas.drawText(allDayTop, x, titleBaseline + line * 0.5f, timeCapsPaint)
            } else {
                canvas.drawText(allDayTop, x, titleBaseline, timeCapsPaint)
                canvas.drawText(second, x, titleBaseline + line, timeCapsPaint)
            }
        } else {
            val line = timePaint.descent() - timePaint.ascent()
            timePaint.color = withAlpha(theme.ink, a)
            canvas.drawText(row.startText, timeRight, titleBaseline, timePaint)
            timePaint.color = withAlpha(theme.inkFaint, a)
            canvas.drawText(row.endText, timeRight, titleBaseline + line, timePaint)
        }

        titlePaint.color = withAlpha(theme.ink, a)
        canvas.drawText(row.title, textLeft, titleBaseline, titlePaint)
        val subtitle = row.subtitle
        if (subtitle != null) {
            val baseline = titleBaseline + titlePaint.descent() +
                metrics.dp(ROW_TEXT_GAP) - subPaint.ascent()
            subPaint.color = withAlpha(theme.inkMuted, a)
            canvas.drawText(subtitle, textLeft, baseline, subPaint)
        }
    }

    /** How far into its own window the wave front has carried an entry held back by [delay]. */
    private fun revealAt(delay: Float): Float =
        smoothstep(delay, delay + riseSeconds, wave.value)

    private fun withAlpha(colour: Int, alpha: Float): Int {
        val scaled = ((colour ushr 24) and 0xFF) * clamp(alpha, 0f, 1f)
        return ((scaled + 0.5f).toInt() shl 24) or (colour and 0x00FFFFFF)
    }

    private companion object {

        /** Stands in for a clock time that belongs to a neighbouring day, not to this one. */
        const val CONTINUES = "–"

        /** What a cut title ends on: one glyph, so it costs almost none of the width it saves. */
        const val ELLIPSIS = "…"

        /** How many characters back a cut will search for a space before giving up on one. */
        const val WORD_REACH = 12
    }
}
