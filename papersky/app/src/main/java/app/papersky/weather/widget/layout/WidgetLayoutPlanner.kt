package app.papersky.weather.widget.layout

import app.papersky.weather.widget.WidgetBackground
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetDensity
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fonts available to widget text (each backed by a RemoteViews layout). */
enum class WFont { DisplayLight, Display, Body, BodyBold, Hand }

fun interface TextMeasure {
    /** Width in dp of [text] set in [font] at [sizeDp]. */
    fun width(text: String, font: WFont, sizeDp: Float): Float
}

/** Conservative average-advance estimate; used in tests and as a fallback. */
object EstimateMeasure : TextMeasure {
    override fun width(text: String, font: WFont, sizeDp: Float): Float {
        val k = when (font) {
            WFont.DisplayLight -> 0.72f
            WFont.Display -> 0.74f
            WFont.Body -> 0.56f
            WFont.BodyBold -> 0.6f
            WFont.Hand -> 0.46f
        }
        return text.length * sizeDp * k
    }
}

/** Line height factor (no font padding) for our fonts. */
const val LINE = 1.26f

/** Glance truncates Row/Column containers to this many children. */
const val MAX_CHILDREN = 10

data class PlanInput(
    val width: Float,
    val height: Float,
    val config: WidgetConfig,
    /** The hero temperature exactly as it will be shown, e.g. "−12°". */
    val heroText: String = "−12°",
    val clockSample: String = "22:48",
    val hourLabelSample: String = "22",
    /** First hourly label ("Now"), usually the widest. */
    val nowLabel: String = "Now",
    /** Widest day label ("Today", "Tomorrow", weekdays) as it will be shown. */
    val dayLabelSample: String = "Tmrw",
    val hourlyAvailable: Int = 24,
    val dailyAvailable: Int = 10,
    val fontScale: Float = 1f,
    val measure: TextMeasure = EstimateMeasure,
)

enum class Mode { Micro, Strip, Tower, Card, Panorama }

data class RectDp(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
}

enum class InfoLine { Location, Condition, HiLo, FeelsLike }
enum class HeroStyle { Row, Stacked, TempOnly, Clock, ClockStacked }

sealed interface Block { val height: Float }

data class GapBlock(override val height: Float) : Block

data class HeaderBlock(
    override val height: Float,
    val textSize: Float,
    val iconSize: Float,
    val showLocation: Boolean,
    val showTime: Boolean,
    val showRefresh: Boolean,
    val showClock: Boolean = false,
) : Block

data class HeroBlock(
    override val height: Float,
    val style: HeroStyle,
    val tempSize: Float,
    val glyphSize: Float,
    val infoLines: List<InfoLine>,
    val infoSize: Float,
    val clockSize: Float = 0f,
) : Block

data class WhisperBlock(override val height: Float, val textSize: Float) : Block

/** A single small line of text (used by tiny widgets). */
data class InfoBlock(override val height: Float, val line: InfoLine, val textSize: Float) : Block

/** Horizontal hourly strip; temperatures ride a curve inside [band]. */
data class HourlyBlock(
    override val height: Float,
    val count: Int,
    val innerPad: Float,
    val padTop: Float,
    val labelSize: Float,
    val labelLine: Float,
    val gap: Float,
    val glyphSize: Float,
    val tempSize: Float,
    val tempLine: Float,
    val band: Float,
    val barHeight: Float,
) : Block {
    val bandTop get() = padTop + labelLine + gap + glyphSize + gap
}

/** Vertical hourly list for narrow widgets. */
data class HourlyRowsBlock(
    override val height: Float,
    val rows: Int,
    val rowHeight: Float,
    val innerPad: Float,
    val textSize: Float,
    val glyphSize: Float,
    val showGlyph: Boolean,
) : Block

data class DetailsBlock(
    override val height: Float,
    val count: Int,
    val innerPad: Float,
    val textSize: Float,
    val glyphSize: Float,
) : Block

data class DailyBlock(
    override val height: Float,
    val rows: Int,
    val rowHeight: Float,
    val innerPad: Float,
    val textSize: Float,
    val glyphSize: Float,
    val dayWidth: Float,
    val tempWidth: Float,
    val precipWidth: Float,
    val barWidth: Float,
    val compact: Boolean,
    val showGlyph: Boolean = true,
    val showMin: Boolean = true,
    /** Day names don't fit: use two-letter weekdays instead of ellipsising "Tod…". */
    val shortDays: Boolean = false,
) : Block

data class ColumnPlan(val x: Float, val y: Float, val width: Float, val height: Float, val blocks: List<Block>)

enum class ChipKind { Hourly, Daily }
enum class ChipStyle { Full, TwoLine, TempOnly }

data class StripPlan(
    val clockWidth: Float,
    val clockSize: Float,
    val heroWidth: Float,
    val tempSize: Float,
    val glyphSize: Float,
    val infoWidth: Float,
    val infoLines: List<InfoLine>,
    val infoSize: Float,
    val chips: Int,
    val chipKind: ChipKind,
    val chipStyle: ChipStyle,
    val chipLabelSize: Float,
    val chipTempSize: Float,
    val chipGlyphSize: Float,
    val gap: Float,
    val chipsLeft: Float,
)

data class WidgetPlan(
    val mode: Mode,
    val width: Float,
    val height: Float,
    val pad: Float,
    val scale: Float,
    val columns: List<ColumnPlan>,
    val strip: StripPlan?,
    /** Paper sheets to print behind dense sections (Scene background only). */
    val panels: List<RectDp>,
) {
    /** Union of the blocks that put text straight onto the sky (header, hero, lines). */
    fun textRect(): RectDp? {
        if (strip != null) return RectDp(pad, pad, width * 0.62f, height - 2 * pad)
        return unionOf { it is HeaderBlock || it is HeroBlock || it is InfoBlock || it is WhisperBlock }
    }

    /** Where the big temperature sits. */
    fun heroRect(): RectDp? = if (strip != null) textRect() else unionOf { it is HeroBlock }

    private fun unionOf(pick: (Block) -> Boolean): RectDp? {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (c in columns) {
            var y = c.y
            for (block in c.blocks) {
                if (pick(block)) {
                    l = minOf(l, c.x); t = minOf(t, y); r = maxOf(r, c.x + c.width); b = maxOf(b, y + block.height)
                }
                y += block.height
            }
        }
        return if (r > l && b > t) RectDp(l, t, r - l, b - t) else null
    }

    /** Top of [block] inside its column, in widget coordinates. */
    fun topOf(block: Block): Float? {
        for (c in columns) {
            var y = c.y
            for (b in c.blocks) {
                if (b === block) return y
                y += b.height
            }
        }
        return null
    }

    fun columnOf(block: Block): ColumnPlan? = columns.firstOrNull { c -> c.blocks.any { it === block } }
}

/**
 * Decides, for an exact widget size in dp, which sections fit and how big everything is. Pure and
 * deterministic so the widget text (RemoteViews) and the painted background (bitmap) line up, and
 * so it can be unit-tested for every size from 1×1 to 6×6 and beyond.
 */
object WidgetLayoutPlanner {

    fun plan(input: PlanInput): WidgetPlan {
        val w = max(input.width, 24f)
        val h = max(input.height, 24f)
        val c = input.config
        val densityK = when (c.density) {
            WidgetDensity.Compact -> 0.9f
            WidgetDensity.Balanced -> 1f
            WidgetDensity.Airy -> 1.12f
        }
        val fontK = 1f + (input.fontScale.coerceIn(0.85f, 2f) - 1f) * 0.5f
        val s = ((sqrt(w * h) / 250f).coerceIn(0.78f, 1.22f) * c.textScale.coerceIn(0.7f, 1.5f) * fontK).coerceIn(0.62f, 1.8f)
        val pad = (min(w, h) * 0.075f).coerceIn(5f, 16f) * densityK

        // Thresholds grow with the text so big type never gets squeezed into a card.
        val k = s.coerceIn(1f, 1.4f)
        val mode = when {
            w < 116 * k && h < 116 * k -> Mode.Micro
            h < 118 * k -> Mode.Strip
            w < 128 * k -> Mode.Tower
            w >= 300 && h >= 150 && w / h >= 1.55f -> Mode.Panorama
            else -> Mode.Card
        }
        val gap = 5f * s * densityK
        val panelsWanted = c.background == WidgetBackground.Scene

        return when (mode) {
            Mode.Micro -> micro(w, h, pad, s, input)
            Mode.Strip -> strip(w, h, pad, s, gap, input, panelsWanted)
            Mode.Tower -> column(Mode.Tower, w, h, pad, s, gap, input, panelsWanted)
            Mode.Card -> column(Mode.Card, w, h, pad, s, gap, input, panelsWanted)
            Mode.Panorama -> panorama(w, h, pad, s, gap, input, panelsWanted)
        }
    }

    // --- Micro -----------------------------------------------------------------------------------

    private fun micro(w: Float, h: Float, pad: Float, s: Float, input: PlanInput): WidgetPlan {
        val cw = w - 2 * pad
        val ch = h - 2 * pad
        val c = input.config
        val lines = mutableListOf<InfoLine>()
        val small = (10.5f * s).coerceIn(8.5f, 12f)
        var used = 0f
        if (c.showLocation && ch >= 92) { lines += InfoLine.Location; used += small * LINE }
        if (c.showCondition && ch >= 76) { lines += InfoLine.Condition; used += small * LINE }
        val glyphInline = c.background != WidgetBackground.Scene && cw >= 76
        val glyph = if (glyphInline) min(ch * 0.34f, 30f) else 0f
        var temp = (ch - used) / LINE
        temp = fitWidth(input.heroText, WFont.DisplayLight, temp, cw - glyph - (if (glyph > 0) 4f else 0f), input.measure)
        temp = temp.coerceIn(12f, 72f)
        val heroH = temp * LINE
        val hero = HeroBlock(
            height = heroH,
            style = if (glyph > 0) HeroStyle.Row else HeroStyle.TempOnly,
            tempSize = temp,
            glyphSize = glyph,
            infoLines = emptyList(),
            infoSize = small,
        )
        val blocks = mutableListOf<Block>()
        val top = lines.filter { it == InfoLine.Location }
        val bottom = lines.filter { it != InfoLine.Location }
        val free = (ch - heroH - lines.size * small * LINE).coerceAtLeast(0f)
        blocks += GapBlock(free / 2)
        top.forEach { blocks += InfoBlock(small * LINE, it, small) }
        blocks += hero
        bottom.forEach { blocks += InfoBlock(small * LINE, it, small) }
        return WidgetPlan(Mode.Micro, w, h, pad, s, listOf(ColumnPlan(pad, pad, cw, ch, blocks)), null, emptyList())
    }

    // --- Strip (short and wide) ----------------------------------------------------------------

    private fun strip(w: Float, h: Float, pad: Float, s: Float, gap: Float, input: PlanInput, panels: Boolean): WidgetPlan {
        val c = input.config
        val ch = h - 2 * pad
        var x = pad
        val right = w - pad
        val m = input.measure

        var clockW = 0f
        var clockSize = 0f
        if (c.showClock && w >= 250) {
            clockSize = min(ch / LINE, 44f * s)
            clockW = m.width(input.clockSample, WFont.DisplayLight, clockSize) + 2
            x += clockW + gap * 2
        }

        val glyph = if (ch >= 34 && w >= 170) min(ch * 0.62f, 40f * s) else 0f
        var temp = min(ch * 0.78f / LINE * 1.08f, 58f * s)
        temp = fitWidth(input.heroText, WFont.DisplayLight, temp, (right - x) * 0.5f, m).coerceAtLeast(12f)
        val tempW = m.width(input.heroText, WFont.DisplayLight, temp) + 2
        val heroW = tempW + (if (glyph > 0) glyph + gap else 0f)
        x += heroW + gap

        val infoSize = (11f * s).coerceIn(9f, 13f)
        val maxLines = floor(ch / (infoSize * LINE)).toInt().coerceIn(0, 3)
        val wantLines = buildList {
            if (c.showLocation) add(InfoLine.Location)
            if (c.showCondition) add(InfoLine.Condition)
            if (c.showHiLo) add(InfoLine.HiLo)
            if (c.showFeelsLike) add(InfoLine.FeelsLike)
        }.take(maxLines)
        var infoW = 0f
        val remaining = right - x
        val chipKind = if (c.showHourly || !c.showDaily) ChipKind.Hourly else ChipKind.Daily
        val chipsAllowed = (c.showHourly || c.showDaily) && input.hourlyAvailable > 0
        if (wantLines.isNotEmpty() && remaining >= 78 * s) {
            infoW = if (chipsAllowed) min(max(84f * s, remaining * 0.3f), 132f * s).coerceAtMost(remaining) else remaining
            x += infoW + gap
        }

        val chipStyle = when {
            ch >= 58 * s -> ChipStyle.Full
            ch >= 32 -> ChipStyle.TwoLine
            else -> ChipStyle.TempOnly
        }
        val chipMinW = when (chipStyle) {
            ChipStyle.Full -> 38f * s
            ChipStyle.TwoLine -> 36f * s
            ChipStyle.TempOnly -> 30f * s
        }
        val chipArea = (right - x).coerceAtLeast(0f)
        val available = if (chipKind == ChipKind.Hourly) input.hourlyAvailable else input.dailyAvailable
        val chips = if (chipsAllowed) floor(chipArea / chipMinW).toInt().coerceIn(0, min(available, MAX_CHILDREN)) else 0
        if (chips < 2 && infoW > 0) {
            // Not enough room for a meaningful strip: let the info column breathe instead.
            infoW += chipArea
        }
        val realChips = if (chips >= 2) chips else 0
        val labelSize = (9.5f * s).coerceIn(8f, 12f)
        val chipTemp = (12f * s).coerceIn(9f, 16f)
        val chipGlyph = when (chipStyle) {
            ChipStyle.Full -> (ch - labelSize * LINE - chipTemp * LINE - 4).coerceIn(10f, 26f * s)
            else -> 0f
        }
        val chipsLeft = right - chipArea
        val panelList = if (panels && realChips > 0) listOf(RectDp(chipsLeft - 2, pad - 2, chipArea + 4, ch + 4)) else emptyList()
        val plan = StripPlan(
            clockWidth = clockW,
            clockSize = clockSize,
            heroWidth = heroW,
            tempSize = temp,
            glyphSize = glyph,
            infoWidth = infoW,
            infoLines = if (infoW > 0) wantLines else emptyList(),
            infoSize = infoSize,
            chips = realChips,
            chipKind = chipKind,
            chipStyle = chipStyle,
            chipLabelSize = labelSize,
            chipTempSize = chipTemp,
            chipGlyphSize = chipGlyph,
            gap = gap,
            chipsLeft = chipsLeft,
        )
        return WidgetPlan(Mode.Strip, w, h, pad, s, emptyList(), plan, panelList)
    }

    // --- Card & Tower (single column) -----------------------------------------------------------

    private fun column(mode: Mode, w: Float, h: Float, pad: Float, s: Float, gap: Float, input: PlanInput, panels: Boolean): WidgetPlan {
        val cw = w - 2 * pad
        val ch = h - 2 * pad
        val blocks = buildColumn(mode, cw, ch, s, gap, input, sections = Sections.All)
        val col = ColumnPlan(pad, pad, cw, ch, blocks)
        return WidgetPlan(mode, w, h, pad, s, listOf(col), null, if (panels) panelsFor(col, gap) else emptyList())
    }

    private fun panorama(w: Float, h: Float, pad: Float, s: Float, gap: Float, input: PlanInput, panels: Boolean): WidgetPlan {
        val ch = h - 2 * pad
        val leftW = (w * 0.42f).coerceIn(150f, 270f)
        val rightW = w - 2 * pad - leftW - gap * 2
        val left = ColumnPlan(pad, pad, leftW, ch, buildColumn(Mode.Card, leftW, ch, s, gap, input, Sections.Left))
        val right = ColumnPlan(pad + leftW + gap * 2, pad, rightW, ch, buildColumn(Mode.Card, rightW, ch, s, gap, input, Sections.Right))
        val panelList = if (panels) panelsFor(left, gap) + panelsFor(right, gap) else emptyList()
        return WidgetPlan(Mode.Panorama, w, h, pad, s, listOf(left, right), null, panelList)
    }

    private enum class Sections { All, Left, Right }

    private fun buildColumn(mode: Mode, cw: Float, ch: Float, s: Float, gap: Float, input: PlanInput, sections: Sections): List<Block> {
        val c = input.config
        val m = input.measure
        val tower = mode == Mode.Tower
        val wantHero = sections != Sections.Right
        val wantHeader = sections != Sections.Right && (c.showLocation || c.showRefresh || c.showUpdated || (c.showClock && tower))
        val wantHourly = sections != Sections.Left && c.showHourly && input.hourlyAvailable > 0
        val wantDaily = sections != Sections.Left && c.showDaily && input.dailyAvailable > 0
        val wantDetails = sections != Sections.Right && c.showDetails && !tower
        val wantWhisper = sections != Sections.Right && c.showWhisper && cw >= 120

        var remaining = ch
        fun take(hh: Float): Boolean {
            if (hh <= remaining + 0.01f) { remaining -= hh; return true }
            return false
        }

        // Header.
        val headerText = (11f * s).coerceIn(9f, 14f)
        val header = if (wantHeader && ch >= 96) {
            HeaderBlock(
                height = headerText * LINE + 2,
                textSize = headerText,
                iconSize = headerText * 1.15f,
                showLocation = c.showLocation,
                showTime = c.showUpdated,
                showRefresh = c.showRefresh && cw >= 110,
                showClock = c.showClock && tower,
            ).takeIf { take(it.height + gap) }
        } else null

        // Hero minimum (never more than what's actually there).
        val heroMin = if (!wantHero) 0f else min(if (tower) 50f * s else 46f * s, remaining)
        if (wantHero) take(heroMin)

        // Section minimums in priority order.
        val labelSize = (9.5f * s).coerceIn(8f, 12.5f)
        val tempSmall = (11.5f * s).coerceIn(9f, 15f)
        val innerPad = if (input.config.background == WidgetBackground.Scene) 8f * s else 2f

        var hourly: HourlyBlock? = null
        var hourlyRows: HourlyRowsBlock? = null
        if (wantHourly) {
            if (!tower) {
                val glyph = (20f * s).coerceIn(14f, 30f)
                val labelLine = labelSize * LINE
                val tempLine = tempSmall * LINE
                val band = 9f * s
                val bars = if (ch >= 190) 5f * s else 0f
                val padTop = 5f * s
                val hh = padTop + labelLine + 3 + glyph + 3 + tempLine + band + 4f * s + bars + 4f * s
                val itemMin = maxOf(34f * s, m.width(input.hourLabelSample, WFont.Body, labelSize) + 8, m.width(input.nowLabel, WFont.BodyBold, labelSize) + 6)
                val count = floor((cw - 2 * innerPad) / itemMin).toInt().coerceAtMost(min(input.hourlyAvailable, MAX_CHILDREN))
                if (count >= 3 && take(hh + gap)) {
                    hourly = HourlyBlock(hh, count, innerPad, padTop, labelSize, labelLine, 3f, glyph, tempSmall, tempLine, band, bars)
                }
            } else {
                val rowH = (21f * s).coerceIn(16f, 30f)
                val minRows = 2
                if (take(rowH * minRows + gap + 6f * s)) {
                    hourlyRows = HourlyRowsBlock(rowH * minRows + 6f * s, minRows, rowH, innerPad * 0.6f, tempSmall, (15f * s).coerceIn(11f, 20f), cw >= 84)
                }
            }
        }

        val rowH = (22f * s).coerceIn(17f, 32f)
        val dailyPad = 5f * s
        var dailyRows = 0
        if (wantDaily && take(rowH * 2 + dailyPad * 2 + gap)) dailyRows = 2

        val detailsH = (27f * s).coerceIn(20f, 38f)
        val details = if (wantDetails && cw >= 150 && take(detailsH + gap)) {
            val count = floor((cw - 2 * innerPad) / (62f * s)).toInt().coerceIn(1, 5)
            DetailsBlock(detailsH, count, (10.5f * s).coerceIn(8.5f, 13f), (14f * s).coerceIn(11f, 20f), innerPad)
        } else null

        val whisperSize = (14f * s).coerceIn(11f, 19f)
        val whisper = if (wantWhisper && take(whisperSize * LINE + gap)) WhisperBlock(whisperSize * LINE, whisperSize) else null

        // Growth: tower fills with more hourly rows first, cards with more days.
        if (hourlyRows != null) {
            val add = floor(remaining / hourlyRows.rowHeight).toInt()
                .coerceAtMost(input.hourlyAvailable - hourlyRows.rows)
                .coerceIn(0, MAX_CHILDREN - hourlyRows.rows)
            hourlyRows = hourlyRows.copy(rows = hourlyRows.rows + add, height = hourlyRows.height + add * hourlyRows.rowHeight)
            remaining -= add * hourlyRows.rowHeight
        }
        if (dailyRows > 0) {
            val maxRows = min(input.dailyAvailable, if (tower) 7 else MAX_CHILDREN)
            // Days are worth more than a giant number; keep only a little growth for the hero.
            val heroRoom = if (wantHero) min(remaining, 22f * s) else 0f
            val extra = floor(((remaining - heroRoom).coerceAtLeast(0f)) / rowH).toInt().coerceIn(0, maxRows - dailyRows)
            dailyRows += extra
            remaining -= extra * rowH
        }

        // Hero grows into what's left (capped), leftover becomes breathing room.
        var heroH = heroMin
        if (wantHero) {
            val cap = if (tower) ch * 0.5f else max(heroMin, min(ch * 0.4f, 112f * s))
            val grow = min(remaining, cap - heroMin).coerceAtLeast(0f)
            heroH += grow
            remaining -= grow
        }

        val hero = if (wantHero) hero(mode, cw, heroH, s, gap, input) else null

        val daily = if (dailyRows > 0) {
            val text = (11.5f * s).coerceIn(9f, 15f)
            val glyph = (rowH * 0.78f).coerceAtMost(24f)
            val dayNeed = m.width(input.dayLabelSample, WFont.BodyBold, text * 1.05f) + 6
            val dayW = max(dayNeed, 34f * s).coerceAtMost(cw * 0.42f)
            val tempW = m.width("−22°", WFont.Display, text) + 4
            val usable = cw - 2 * innerPad
            val compact = tower || usable < dayW + glyph + tempW * 2 + 20
            val precipW = if (!compact && usable >= 230) 32f * s else 0f
            val barW = if (!compact) (usable - dayW - glyph - tempW * 2 - precipW - 6 * 5).coerceAtLeast(0f).takeIf { it >= 28 } ?: 0f else 0f
            DailyBlock(
                height = dailyRows * rowH + dailyPad * 2,
                rows = dailyRows,
                rowHeight = rowH,
                innerPad = innerPad,
                textSize = text,
                glyphSize = glyph,
                dayWidth = dayW,
                tempWidth = tempW,
                precipWidth = precipW,
                barWidth = barW,
                compact = compact,
                showGlyph = !compact || cw >= 96,
                showMin = !compact || cw >= 112,
                shortDays = if (compact) {
                    val room = usable - (if (cw >= 96) glyph else 0f) - (if (cw >= 112) tempW * 2 else tempW) - 4
                    room < dayNeed
                } else dayW < dayNeed,
            )
        } else null

        // Assemble in visual order and spread the leftover evenly between sections.
        val ordered = listOfNotNull(header, hero, whisper, hourly, hourlyRows, details, daily)
        val spare = remaining.coerceAtLeast(0f)
        val gaps = (ordered.size - 1).coerceAtLeast(1)
        val extraGap = if (ordered.size > 1) min(spare / gaps, 14f * s) else 0f
        val out = mutableListOf<Block>()
        ordered.forEachIndexed { i, b ->
            if (i > 0) out += GapBlock(gap + extraGap)
            out += b
        }
        val used = out.sumOf { it.height.toDouble() }.toFloat()
        val tail = (ch - used).coerceAtLeast(0f)
        if (tail > 0.5f) {
            // Centre the stack vertically when there's air.
            out.add(0, GapBlock(tail / 2))
            out += GapBlock(tail / 2)
        }
        return out
    }

    private fun hero(mode: Mode, cw: Float, heroH: Float, s: Float, gap: Float, input: PlanInput): HeroBlock {
        val c = input.config
        val m = input.measure
        val infoSize = (11.5f * s).coerceIn(9f, 15f)
        val wantLines = buildList {
            if (c.showCondition) add(InfoLine.Condition)
            if (c.showHiLo) add(InfoLine.HiLo)
            if (c.showFeelsLike) add(InfoLine.FeelsLike)
        }

        if (mode == Mode.Tower) {
            val glyph = min(cw * 0.42f, heroH * 0.34f).coerceIn(0f, 46f * s)
            val linesRoom = heroH - glyph - 4
            val lineH = infoSize * LINE
            var temp = min((linesRoom - lineH) / LINE, 64f * s)
            temp = fitWidth(input.heroText, WFont.DisplayLight, temp, cw, m).coerceAtLeast(14f)
            val maxLines = floor((heroH - glyph - temp * LINE - 4) / lineH).toInt().coerceIn(0, 2)
            return HeroBlock(heroH, HeroStyle.Stacked, temp, glyph, wantLines.take(maxLines), infoSize)
        }

        if (c.showClock) {
            val temp = min(heroH * 0.34f, 34f * s)
            val glyph = min(temp * 1.1f, 34f * s)
            val tempW = m.width(input.heroText, WFont.Display, temp) + glyph + 6
            val infoW = max(tempW, 64f * s)
            val sideBySide = cw - infoW - gap * 2 >= m.width(input.clockSample, WFont.DisplayLight, heroH * 0.42f)
            if (sideBySide) {
                val clockSize = fitWidth(input.clockSample, WFont.DisplayLight, min(heroH * 0.7f / LINE * 1.15f, 84f * s), cw - infoW - gap * 2, m)
                val lines = floor((heroH - temp * LINE) / (infoSize * LINE)).toInt().coerceIn(0, 2)
                return HeroBlock(heroH, HeroStyle.Clock, temp, glyph, wantLines.take(lines), infoSize, clockSize)
            }
            // Narrow: clock on its own line, weather row beneath it.
            val clockSize = fitWidth(input.clockSample, WFont.DisplayLight, heroH * 0.52f / LINE, cw, m)
            val rowH = heroH - clockSize * LINE
            val t2 = min(rowH / LINE, 30f * s).coerceAtLeast(10f)
            val g2 = min(t2 * 1.1f, 30f * s)
            val lines = if (cw - m.width(input.heroText, WFont.Display, t2) - g2 - gap * 2 >= 56f * s) wantLines.take(1) else emptyList()
            return HeroBlock(heroH, HeroStyle.ClockStacked, t2, g2, lines, infoSize, clockSize)
        }

        var temp = min(heroH / LINE, 110f * s)
        var glyph = min(heroH * 0.7f, 64f * s)
        val infoMin = 70f * s
        var tempW = m.width(input.heroText, WFont.DisplayLight, temp)
        val lineCount = floor(heroH / (infoSize * LINE)).toInt().coerceIn(0, 3)
        var lines = wantLines.take(lineCount)
        if (tempW + glyph + gap * 2 + infoMin > cw) {
            // Shrink the number a little before dropping the info column.
            val target = cw - glyph - gap * 2 - infoMin
            if (target > tempW * 0.72f) {
                temp = fitWidth(input.heroText, WFont.DisplayLight, temp, target, m)
            } else {
                lines = emptyList()
                if (tempW + glyph + gap > cw) {
                    temp = fitWidth(input.heroText, WFont.DisplayLight, temp, cw - glyph - gap, m)
                    tempW = m.width(input.heroText, WFont.DisplayLight, temp)
                    if (temp < heroH * 0.45f) {
                        glyph = 0f
                        temp = fitWidth(input.heroText, WFont.DisplayLight, heroH / LINE, cw, m)
                    }
                }
            }
        }
        return HeroBlock(heroH, HeroStyle.Row, temp.coerceAtLeast(12f), glyph, lines, infoSize)
    }

    /** Merge consecutive dense blocks into paper sheets. */
    private fun panelsFor(col: ColumnPlan, gap: Float): List<RectDp> {
        val out = mutableListOf<RectDp>()
        var y = col.y
        var start: Float? = null
        var end = 0f
        for (b in col.blocks) {
            val dense = b is HourlyBlock || b is HourlyRowsBlock || b is DailyBlock || b is DetailsBlock
            if (dense) {
                if (start == null) start = y
                end = y + b.height
            } else if (b !is GapBlock && start != null) {
                out += RectDp(col.x, start, col.width, end - start)
                start = null
            }
            y += b.height
        }
        if (start != null) out += RectDp(col.x, start, col.width, end - start)
        return out
    }

    /** Largest size ≤ [size] at which [text] fits in [maxWidth]. */
    fun fitWidth(text: String, font: WFont, size: Float, maxWidth: Float, m: TextMeasure): Float {
        if (size <= 0f || maxWidth <= 0f) return 0f
        val wAt = m.width(text, font, size)
        return if (wAt <= maxWidth) size else size * (maxWidth / wAt) * 0.98f
    }
}
