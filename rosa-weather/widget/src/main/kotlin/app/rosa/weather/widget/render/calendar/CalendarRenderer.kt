package app.rosa.weather.widget.render.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.util.SizeF
import androidx.core.graphics.withClip
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.model.Argb
import app.rosa.weather.core.model.CalendarDay
import app.rosa.weather.core.model.CalendarMonth
import app.rosa.weather.core.model.DailyPoint
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WeatherCondition
import app.rosa.weather.core.model.WeatherVisual
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.render.GlassBevel
import app.rosa.weather.widget.render.GlassNumerals
import app.rosa.weather.widget.render.SkyAnchor
import app.rosa.weather.widget.render.WidgetBackground
import app.rosa.weather.widget.render.WidgetContent
import app.rosa.weather.widget.render.WidgetFonts
import app.rosa.weather.widget.render.WidgetLight
import app.rosa.weather.widget.render.WidgetPalette
import app.rosa.weather.widget.render.WidgetRenderRequest
import app.rosa.weather.widget.render.WidgetType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a calendar widget shows at this moment: the month on display, today, the events. */
data class CalendarView(
    val month: YearMonth,
    val today: LocalDate,
    /** The colours of each day's events, in order; a few are shown as dots. */
    val events: Map<LocalDate, List<Int>> = emptyMap(),
    val locale: Locale = Locale.getDefault(),
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)

    /** What the widget says to accessibility services, to which it is one picture: "Октябрь 2026. Пятница, 25". */
    val spoken: String
        get() {
            val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
            val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
            return "$name ${month.year}. $weekday, ${today.dayOfMonth}"
        }

    companion object {
        /** Today where the phone is, and the month [offset] months from this one. */
        fun at(
            epochSeconds: Long,
            offset: Int = 0,
            events: Map<LocalDate, List<Int>> = emptyMap(),
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): CalendarView {
            val today = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()
            return CalendarView(YearMonth.from(today).plusMonths(offset.toLong()), today, events, locale)
        }
    }
}

/** Where a calendar widget answers taps, in dp from its top-left corner. */
data class CalendarTargets(
    val previous: RectF? = null,
    val next: RectF? = null,
    /** The month's name: back to today's month. */
    val title: RectF? = null,
    val add: RectF? = null,
    val days: List<Pair<LocalDate, RectF>> = emptyList(),
) {
    companion object {
        val None = CalendarTargets()
    }
}

/**
 * Draws the calendar widget: a month as a grid, over a painting of its season ([SeasonScene]),
 * in any of the widget styles, at any size from a date tile to a wall calendar. All geometry is in
 * dp; the canvas is pre-scaled. Returns where the widget answers taps.
 */
internal class CalendarRenderer(private val context: Context, fonts: WidgetFonts) {
    private val type = WidgetType(fonts)
    private val background = WidgetBackground()
    private val glyphs = WeatherGlyphPainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The shared paint, reset: a gradient drawn with a paint that still holds the alpha of the
     * last line drawn with it comes out that much fainter — glass that measured thick enough for
     * its type would lie thin.
     */
    private fun clean(): Paint = paint.apply {
        reset()
        flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG
    }
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)
    private val exact = Paint()
    private val path = Path()

    fun draw(canvas: Canvas, request: WidgetRenderRequest): CalendarTargets {
        val view = request.calendar ?: CalendarView.at(request.content.nowEpochSeconds)
        val w = request.widthDp
        val h = request.heightDp
        // The month's painting as it looks in the week shown: today's, or the middle of another month.
        val art = WeekArt.of(SeasonClock.weekFor(view.month, view.today))
        val radius = request.cornerRadiusDp.coerceIn(0f, min(w, h) / 2f)
        // The dark theme: chosen, or the phone's in Auto — then the painting is by moonlight.
        val night = when (request.config.theme) {
            WidgetTheme.Dark -> true
            WidgetTheme.Light -> false
            WidgetTheme.Auto -> request.systemNight
        }
        val look = Look.of(request, art, night)
        @Suppress("DEPRECATION")
        val px = canvas.matrix.mapRadius(1f).coerceIn(0.25f, 6f)
        val s = Sheet(px, request, view, art, look, w, h, radius, night)
        return when (Layout.of(w, h)) {
            Layout.TallTile -> tile(canvas, s, vertical = true)
            Layout.FlatTile -> tile(canvas, s, vertical = false)
            Layout.Week -> week(canvas, s)
            Layout.Wide -> wide(canvas, s)
            Layout.Full -> full(canvas, s)
        }
    }

    /** How a calendar of a size is laid out: a date tile, today and its week, or the month. */
    internal enum class Layout {
        TallTile, FlatTile, Week, Wide, Full;

        companion object {
            fun of(w: Float, h: Float): Layout = when {
                w < 130f && h >= 100f || w < 100f -> TallTile
                h < 100f && w < 210f -> FlatTile
                h < 100f -> Week
                w >= h * 1.7f -> Wide
                else -> Full
            }
        }
    }

    companion object {
        /** How many pixels per dp the frost behind a sheet, and behind a whole pane of glass, is made at. */
        private const val SHEET_FROST = 0.34f
        private const val GLASS_FROST = 0.32f

        /**
         * Everything of the weather a calendar page of [view] shows at [sizes], as text: the
         * light of the sky (the palette follows day and night), today's weather where today is drawn
         * large or under this month's name, the forecast on the days still to come. Pages whose
         * text is the same show the same weather — a page drawn ahead is still good.
         */
        fun weatherShown(view: CalendarView, content: WidgetContent, config: WidgetConfig, sizes: List<SizeF>): String {
            val forecast = content.forecast ?: return "-"
            val units = content.units
            val moment = forecast.momentAt(content.nowEpochSeconds)
            val out = StringBuilder().append(units).append('|').append(moment.isDay)
            val layouts = sizes.mapTo(HashSet()) { Layout.of(it.width, it.height) }
            val now = Layout.TallTile in layouts || Layout.Wide in layouts || (Layout.Full in layouts && view.isCurrentMonth)
            if (now) {
                out.append('|').append(moment.condition).append(' ').append(units.roundedTemperature(moment.temperature))
                if (!moment.isDay) out.append(' ').append((moment.moonPhase.phase * 40).roundToInt())
            }
            if (config.calendar.forecastInDays) {
                val zone = WeatherFormat.zoneOf(forecast.timezone, forecast.utcOffsetSeconds)
                val days = forecast.daily.associateBy { Instant.ofEpochSecond(it.time + 3600).atZone(zone).toLocalDate() }
                val firstDay = CalendarMonth.firstDayFor(config.calendar.weekStart, view.locale)
                val dates = HashSet<LocalDate>()
                if (Layout.Full in layouts || Layout.Wide in layouts) CalendarMonth.of(view.month, view.today, firstDay).days.mapTo(dates) { it.date }
                if (Layout.Week in layouts) view.today.with(TemporalAdjusters.previousOrSame(firstDay)).let { start -> (0L until 7L).mapTo(dates) { start.plusDays(it) } }
                dates.filter { !it.isBefore(view.today) }.sorted().forEach { date ->
                    val day = days[date] ?: return@forEach
                    out.append('|').append(date.dayOfMonth).append(':').append(day.weatherCode).append(':').append(units.roundedTemperature(day.temperatureMax))
                }
            }
            return out.toString()
        }
    }

    // region Layouts

    /** A wall calendar: the month's name over its season, the grid on glass below. */
    private fun full(c: Canvas, s: Sheet): CalendarTargets {
        val k = s.k
        val paperPicture = s.style == WidgetStyle.Paper && s.h >= 230f
        val pictureH = if (paperPicture) s.h * 0.24f else 0f
        val headerH = when (s.style) {
            WidgetStyle.Sky -> (s.h * 0.24f).coerceIn(34f, 80f)
            else -> (s.h * 0.19f).coerceIn(30f, 58f)
        }
        val inset = if (s.style == WidgetStyle.Sky) (min(s.w, s.h) * 0.035f).coerceIn(5f, 9f) else 0f
        val grid = RectF(inset, pictureH + headerH, s.w - inset, s.h - inset)
        val header = RectF(0f, pictureH, s.w, pictureH + headerH)
        drawBackground(c, s, sheet = if (s.style == WidgetStyle.Sky) grid else null, picture = if (paperPicture) RectF(0f, 0f, s.w, pictureH) else null, header = header)
        val targets = header(c, s, header, compact = false)
        val pad = if (inset > 0f) (grid.width() * 0.025f).coerceIn(4f, 9f) else (s.w * 0.035f).coerceIn(6f, 12f)
        val days = grid(c, s, RectF(grid.left + pad, grid.top + pad * 0.6f, grid.right - pad, grid.bottom - pad * 0.8f), k)
        return targets.copy(days = days)
    }

    /** Today large on the left, over the scene; the month on glass to its right. */
    private fun wide(c: Canvas, s: Sheet): CalendarTargets {
        val panelW = s.w * 0.36f
        val inset = if (s.style == WidgetStyle.Sky) (s.h * 0.045f).coerceIn(5f, 9f) else 0f
        val sheet = RectF(panelW, inset, s.w - inset, s.h - inset)
        s.scrim = false
        drawBackground(c, s, sheet = if (s.style == WidgetStyle.Sky) sheet else null, picture = null)
        if (s.style == WidgetStyle.Sky) dateScrim(c, s, RectF(0f, 0f, panelW, s.h))
        today(c, s, RectF(0f, 0f, panelW, s.h))
        if (s.style != WidgetStyle.Sky && s.style != WidgetStyle.Glass) {
            // A hairline between the day and the month where no glass separates them.
            paint.shader = null
            paint.color = WidgetPalette.withAlpha(s.look.ink, 0.12f)
            c.drawRect(panelW - 0.4f, s.h * 0.14f, panelW + 0.4f, s.h * 0.86f, paint)
        }
        val headerH = (sheet.height() * 0.2f).coerceIn(24f, 40f)
        val targets = header(c, s, RectF(sheet.left, sheet.top, sheet.right, sheet.top + headerH), compact = true)
        val pad = (sheet.width() * 0.03f).coerceIn(4f, 9f)
        val days = grid(c, s, RectF(sheet.left + pad, sheet.top + headerH, sheet.right - pad, sheet.bottom - pad * 0.8f), s.k)
        return targets.copy(days = days)
    }

    /** A short, wide widget: today, then this week in a row. */
    private fun week(c: Canvas, s: Sheet): CalendarTargets {
        val left = (s.w * 0.24f).coerceIn(70f, 120f)
        val inset = (s.h * 0.07f).coerceIn(4f, 7f)
        // Today over the painting on the left; the week on a frosted sheet beside it.
        s.scrim = false
        drawBackground(c, s, sheet = if (s.style == WidgetStyle.Sky) RectF(left - 2f, inset, s.w - inset, s.h - inset) else null, picture = null)
        if (s.style == WidgetStyle.Sky) dateScrim(c, s, RectF(0f, 0f, left, s.h))
        today(c, s, RectF(0f, 0f, left, s.h), compact = true)
        val area = RectF(left + 4f, s.h * 0.12f, s.w - (s.w * 0.03f).coerceIn(6f, 12f), s.h * 0.9f)
        val start = s.view.today.with(TemporalAdjusters.previousOrSame(s.firstDay))
        val days = List(7) { start.plusDays(it.toLong()) }
        val cellW = area.width() / 7f
        val labelSize = (s.h * 0.15f).coerceIn(8f, 11f) * s.k
        val numberSize = (s.h * 0.24f).coerceIn(11f, 18f) * s.k
        val targets = mutableListOf<Pair<LocalDate, RectF>>()
        days.forEachIndexed { i, date ->
            val cx = area.left + cellW * (i + 0.5f)
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val label = type.text(labelSize, if (weekend) s.inks.labelWeekend else s.inks.label, 600)
            WidgetType.draw(c, weekdayName(date.dayOfWeek, s.view.locale, narrow = cellW < 30f), cx, area.top + labelSize, label, align = Paint.Align.CENTER, shadow = s.look.shadow)
            val cy = area.top + labelSize + (area.height() - labelSize) * 0.38f
            val day = CalendarDay(date, inMonth = true, isToday = date == s.view.today, isWeekend = weekend)
            number(c, s, day, cx, cy, numberSize, min(cellW, area.height() * 0.5f))
            forecastGlyph(c, s, date, cx, cy + numberSize * 0.95f, min(cellW * 0.5f, area.height() * 0.24f))
            targets += date to RectF(cx - cellW / 2, area.top, cx + cellW / 2, area.bottom)
        }
        return CalendarTargets(days = targets)
    }

    /** The smallest widgets: today's date as a tile. */
    private fun tile(c: Canvas, s: Sheet, vertical: Boolean): CalendarTargets {
        // The painting itself, as a postcard: the date over it, a shade under the date.
        s.scrim = false
        drawBackground(c, s, sheet = null, picture = null)
        if (s.style == WidgetStyle.Sky) dateScrim(c, s, RectF(0f, 0f, s.w, s.h))
        today(c, s, RectF(0f, 0f, s.w, s.h), compact = !vertical)
        return CalendarTargets(days = listOf(s.view.today to RectF(0f, 0f, s.w, s.h)))
    }

    // endregion

    // region Parts

    /** Month name, the way back to today, and the buttons: previous, next, a new event. */
    private fun header(c: Canvas, s: Sheet, box: RectF, compact: Boolean): CalendarTargets {
        val pad = if (compact) 8f else (s.w * 0.045f).coerceIn(9f, 16f)
        val button = if (compact) (box.height() * 0.62f).coerceIn(20f, 26f) else (box.height() * 0.5f).coerceIn(22f, 30f)
        val gap = button * 0.22f
        val withAdd = box.width() >= (if (compact) 170f else 210f)
        val buttonsW = button * (if (withAdd) 3 else 2) + gap * (if (withAdd) 2.6f else 1f)
        val withSubtitle = !compact && s.subtitle && box.height() >= 50f
        val subtitleSize = 11.5f * s.k

        // The month: Garamond, and the year when it isn't this one.
        val title = monthName(s.view.month, s.view.locale)
        val year = if (s.view.month.year != s.view.today.year) " ${s.view.month.year}" else ""
        val maxTitle = box.width() - pad * 2 - buttonsW - 8f
        // With a subtitle, title and subtitle share the header as one block, centred.
        val maxSize = if (withSubtitle) (box.height() - subtitleSize * 1.7f) * 0.9f else box.height() * (if (compact) 0.62f else 0.6f)
        val size = type.fitSize(title + year, maxTitle, 11f, maxSize * s.k) { type.numerals(it, s.header.ink, 600) }
        val titlePaint = type.numerals(size, s.header.ink, 600)
        val cap = WidgetType.capHeight(titlePaint)
        val block = if (withSubtitle) cap + subtitleSize * 1.55f else cap
        val baseline = box.top + (box.height() - block) / 2f + cap
        val cy = baseline - cap / 2f
        val titleW = WidgetType.draw(c, title, box.left + pad, baseline, titlePaint, maxTitle, shadow = s.header.shadow)
        if (year.isNotEmpty()) {
            val yp = type.text(size * 0.48f, s.header.soft, 500)
            WidgetType.draw(c, year, box.left + pad + titleW, baseline, yp, shadow = s.header.shadow)
        }
        if (withSubtitle) subtitle(c, s, box.left + pad, baseline + subtitleSize * 1.55f, subtitleSize, maxTitle + buttonsW)

        // Buttons, right to left: a new event, next, previous.
        var right = box.right - pad + button * 0.12f
        var add: RectF? = null
        if (withAdd) {
            add = RectF(right - button, cy - button / 2, right, cy + button / 2)
            glassButton(c, s, add, Icon.Add)
            right = add.left - gap * 1.6f
        }
        val next = RectF(right - button, cy - button / 2, right, cy + button / 2)
        val previous = RectF(next.left - gap - button, next.top, next.left - gap, next.bottom)
        glassButton(c, s, previous, Icon.Previous)
        glassButton(c, s, next, Icon.Next)
        val titleBox = RectF(box.left, box.top, previous.left - gap, box.bottom)
        // Taps land on a finger-sized area around each button.
        fun grow(r: RectF) = RectF(r.left - gap / 2, box.top, r.right + gap / 2, box.bottom)
        return CalendarTargets(previous = grow(previous), next = grow(next), title = titleBox, add = add?.let(::grow))
    }

    /** Under the month: today's weekday and date, and its weather. */
    private fun subtitle(c: Canvas, s: Sheet, x: Float, baseline: Float, size: Float, maxWidth: Float) {
        if (!s.view.isCurrentMonth) return
        val p = type.text(size, s.header.soft, 500)
        val date = s.view.today
        val text = weekdayFull(date.dayOfWeek, s.view.locale) + ", " + date.dayOfMonth + " " + monthGenitive(date, s.view.locale)
        val drawn = WidgetType.draw(c, text, x, baseline, p, maxWidth * 0.62f, shadow = s.header.shadow)
        val moment = s.request.content.forecast?.momentAt(s.request.content.nowEpochSeconds) ?: return
        val g = size * 1.35f
        val gx = x + drawn + size * 0.8f
        glyphs.draw(c, moment.condition, moment.isDay, RectF(gx, baseline - g * 0.8f, gx + g, baseline + g * 0.2f), WeatherGlyphPainter.Tone.Color, s.header.ink, moment.moonPhase.phase, onLightBackground = !s.header.dark)
        val temp = s.format.temperature(moment.temperature)
        WidgetType.draw(c, temp, gx + g + size * 0.25f, baseline, type.text(size, s.header.ink, 600), shadow = s.header.shadow)
    }

    /** Weekday names, then six weeks of days; returns where each day is. */
    private fun grid(c: Canvas, s: Sheet, area: RectF, k: Float): List<Pair<LocalDate, RectF>> {
        val month = CalendarMonth.of(s.view.month, s.view.today, s.firstDay)
        val weekCol = if (s.request.config.calendar.weekNumbers) area.width() * 0.075f else 0f
        val labelH = (area.height() * 0.1f).coerceIn(10f, 18f)
        val cellW = (area.width() - weekCol) / 7f
        val cellH = (area.height() - labelH) / CalendarMonth.WEEKS
        val labelSize = min(labelH * 0.72f, cellW * 0.34f).coerceIn(7.5f, 12f) * k
        month.columns.forEachIndexed { i, day ->
            val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            val p = type.text(labelSize, if (weekend) s.inks.labelWeekend else s.inks.label, 600)
            val cx = area.left + weekCol + cellW * (i + 0.5f)
            WidgetType.draw(c, weekdayName(day, s.view.locale, narrow = cellW < 26f), cx, area.top + labelH * 0.72f, p, align = Paint.Align.CENTER, shadow = s.look.shadow)
        }
        val showGlyphs = s.request.config.calendar.forecastInDays && cellH >= 30f
        val numberSize = min(cellH * (if (showGlyphs) 0.4f else 0.5f), cellW * 0.42f).coerceIn(8.5f, 22f) * k
        val targets = ArrayList<Pair<LocalDate, RectF>>(42)
        month.weeks.forEachIndexed { row, week ->
            val top = area.top + labelH + row * cellH
            if (weekCol > 0f) {
                val wp = type.text(min(labelSize, numberSize * 0.62f), s.inks.faint, 500)
                WidgetType.draw(c, month.weekNumbers[row].toString(), area.left + weekCol * 0.45f, top + cellH * (if (showGlyphs) 0.4f else 0.5f) + wp.textSize * 0.36f, wp, align = Paint.Align.CENTER, shadow = s.look.shadow)
            }
            week.forEachIndexed { col, day ->
                val cx = area.left + weekCol + cellW * (col + 0.5f)
                val cy = top + cellH * (if (showGlyphs) 0.36f else 0.5f)
                number(c, s, day, cx, cy, numberSize, min(cellW, if (showGlyphs) cellH * 0.72f else cellH))
                dots(c, s, day.date, cx, cy + numberSize * (if (showGlyphs) 0.62f else 0.66f), numberSize)
                if (showGlyphs && day.inMonth) {
                    forecastGlyph(c, s, day.date, cx, top + cellH * 0.8f, min(cellH * 0.32f, cellW * 0.42f), withTemperature = cellW >= 40f)
                }
                targets += day.date to RectF(cx - cellW / 2, top, cx + cellW / 2, top + cellH)
            }
        }
        return targets
    }

    /** A day's number: today in a glass bead of the season's colour, the weekend in it too. */
    private fun number(c: Canvas, s: Sheet, day: CalendarDay, cx: Float, cy: Float, size: Float, room: Float) {
        val text = day.date.dayOfMonth.toString()
        if (day.isToday) {
            val r = min(room * 0.46f, size * 1.12f)
            bead(c, cx, cy, r, s.look.accent)
            val p = type.text(size, 0xFFFFFFFF.toInt(), 700)
            WidgetType.draw(c, text, cx, cy + WidgetType.capHeight(p) / 2f + size * 0.02f, p, align = Paint.Align.CENTER)
            return
        }
        val past = day.inMonth && day.date.isBefore(s.view.today)
        val color = when {
            !day.inMonth -> s.inks.faint
            day.isWeekend -> if (past) s.inks.pastWeekend else s.inks.weekend
            past -> s.inks.past
            else -> s.inks.day
        }
        val p = type.text(size, color, if (day.inMonth) 600 else 500)
        WidgetType.draw(c, text, cx, cy + WidgetType.capHeight(p) / 2f + size * 0.02f, p, align = Paint.Align.CENTER, shadow = s.look.shadow && day.inMonth)
    }

    /** Up to three dots for the day's events, in their calendars' colours. */
    private fun dots(c: Canvas, s: Sheet, date: LocalDate, cx: Float, y: Float, size: Float) {
        val colors = s.view.events[date] ?: return
        if (colors.isEmpty()) return
        val shown = colors.take(3)
        val r = (size * 0.09f).coerceIn(1f, 2f)
        val step = r * 3f
        var x = cx - step * (shown.size - 1) / 2f
        paint.shader = null
        for (color in shown) {
            paint.color = color or 0xFF000000.toInt()
            c.drawCircle(x, y + r, r, paint)
            x += step
        }
    }

    /** The forecast's weather on a coming day: its glyph, and the day's high where there is room. */
    private fun forecastGlyph(c: Canvas, s: Sheet, date: LocalDate, cx: Float, cy: Float, size: Float, withTemperature: Boolean = false) {
        if (!s.request.config.calendar.forecastInDays || size < 8f) return
        val day = s.forecastDays[date] ?: return
        if (date.isBefore(s.view.today)) return
        val condition = WeatherCondition.fromWmo(day.weatherCode)
        if (withTemperature) {
            val tp = type.text((size * 0.62f).coerceIn(7f, 11f), s.inks.label, 600)
            val temp = s.format.temperatureNumber(day.temperatureMax) + "°"
            val tw = tp.measureText(temp)
            val total = size + size * 0.12f + tw
            val left = cx - total / 2f
            glyphs.draw(c, condition, true, RectF(left, cy - size / 2, left + size, cy + size / 2), s.look.glyphTone, s.inks.day, onLightBackground = !s.ground.dark)
            WidgetType.draw(c, temp, left + size * 1.12f, cy + WidgetType.capHeight(tp) / 2f, tp, shadow = s.look.shadow)
        } else {
            glyphs.draw(c, condition, true, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), s.look.glyphTone, s.inks.day, onLightBackground = !s.ground.dark)
        }
    }

    /**
     * Today, large: the weekday, the date in glass numerals, the month, and the weather. (Each
     * paint is made right before its text: [WidgetType] hands out one shared paint.)
     */
    private fun today(c: Canvas, s: Sheet, box: RectF, compact: Boolean = false) {
        val date = s.view.today
        val k = s.k
        val pad = (min(box.width(), box.height()) * 0.1f).coerceIn(6f, 14f)
        val moment = s.request.content.forecast?.momentAt(s.request.content.nowEpochSeconds)
        // Straight on the painting the type is white over a shade, like a cover's; elsewhere the style's.
        val painted = s.style == WidgetStyle.Sky
        val ink = if (painted) 0xFFFFFFFF.toInt() else s.header.ink
        val soft = if (painted) 0xEEFFFFFF.toInt() else s.header.soft
        val dayInk = if (painted) Argb.White.lerp(Argb(s.look.accent), 0.2f).value else s.inks.weekend
        val shadow = painted || s.header.shadow
        if (compact && box.width() > box.height() * 1.4f) {
            // Side by side: the big date, then the weekday over the month.
            val size = (box.height() * 0.62f).coerceAtMost(box.width() * 0.34f)
            val baseline = box.centerY() + WidgetType.capHeight(type.numerals(size, ink)) / 2f
            val numberW = bigNumber(c, s, date.dayOfMonth.toString(), box.left + pad, baseline, type.numerals(size, ink))
            val tx = box.left + pad + numberW + pad * 0.6f
            val lineSize = (box.height() * 0.17f).coerceIn(9f, 13f) * k
            WidgetType.draw(c, weekdayFull(date.dayOfWeek, s.view.locale), tx, box.centerY() - lineSize * 0.2f, type.text(lineSize, dayInk, 700), box.right - tx - 4f, shadow = shadow)
            WidgetType.draw(c, monthGenitive(date, s.view.locale), tx, box.centerY() + lineSize * 0.97f, type.text(lineSize * 0.92f, soft, 500), box.right - tx - 4f, shadow = shadow)
            return
        }
        val weekday = weekdayFull(date.dayOfWeek, s.view.locale)
        val room = box.width() - pad * 2
        // Narrow tiles shrink the words a little before they'd have to cut them.
        val weekdaySize = type.fitSize(weekday, room, (box.height() * 0.085f).coerceIn(9f, 14f) * k * 0.72f, (box.height() * 0.085f).coerceIn(9f, 14f) * k) { type.text(it, dayInk, 700) }
        val monthSize = (box.height() * 0.075f).coerceIn(9f, 13f) * k
        val top = box.top + pad + weekdaySize
        WidgetType.draw(c, weekday, box.left + pad, top, type.text(weekdaySize, dayInk, 700), room, shadow = shadow)
        val weatherRoom = if (moment != null && box.height() >= 110f) box.height() * 0.2f else 0f
        val numberRoom = box.bottom - pad - weatherRoom - monthSize * 1.6f - top
        val size = min(numberRoom * 1.18f, (box.width() - pad * 2) * 0.62f).coerceAtLeast(14f)
        val baseline = top + (numberRoom + WidgetType.capHeight(type.numerals(size, ink))) / 2f + size * 0.04f
        bigNumber(c, s, date.dayOfMonth.toString(), box.left + pad - size * 0.03f, baseline, type.numerals(size, ink))
        val monthLine = monthGenitive(date, s.view.locale) + if (box.width() >= 110f) " ${date.year}" else ""
        val monthFit = type.fitSize(monthLine, room, monthSize * 0.72f, monthSize) { type.text(it, soft, 500) }
        WidgetType.draw(c, monthLine, box.left + pad, baseline + monthSize * 1.55f, type.text(monthFit, soft, 500), room, shadow = shadow)
        if (weatherRoom > 0f && moment != null) {
            val g = (weatherRoom * 0.62f).coerceAtMost(26f)
            val y = box.bottom - pad - g / 2f
            glyphs.draw(c, moment.condition, moment.isDay, RectF(box.left + pad, y - g / 2, box.left + pad + g, y + g / 2), WeatherGlyphPainter.Tone.Color, ink, moment.moonPhase.phase, onLightBackground = !painted && !s.header.dark)
            val tp = type.text(g * 0.62f, ink, 600)
            WidgetType.draw(c, s.format.temperature(moment.temperature), box.left + pad + g * 1.15f, y + WidgetType.capHeight(tp) / 2f, tp, shadow = shadow)
        }
    }

    private fun bigNumber(c: Canvas, s: Sheet, text: String, left: Float, baseline: Float, paint: TextPaint): Float {
        // Glass numerals need calm glass behind them; on the painting they'd vanish into it.
        val ink = if (s.style == WidgetStyle.Sky) null else s.look.glassInk
        return if (ink != null) {
            GlassNumerals.draw(c, text, left, baseline, paint, ink, strongShadow = s.header.shadow, light = s.light)
            paint.measureText(text)
        } else {
            WidgetType.draw(c, text, left, baseline, paint, shadow = s.header.shadow)
        }
    }

    // endregion

    // region Surfaces

    /**
     * The style's pane: the season's painting with a frosted sheet for the grid ([WidgetStyle.Sky]),
     * frosted glass over the painting ([WidgetStyle.Glass]), or the weather widgets' own clear,
     * tonal and paper panes — paper with the painting as the page's picture ([picture]).
     */
    private fun drawBackground(c: Canvas, s: Sheet, sheet: RectF?, picture: RectF?, header: RectF? = null) {
        val rect = RectF(0f, 0f, s.w, s.h)
        when (s.style) {
            WidgetStyle.Sky, WidgetStyle.Glass -> {
                val key = paneKey(s, sheet)
                // How thick the glass must be for the type, and how the header must stand on the
                // painting: measured from the painting once, kept with the pane.
                val tone = CalendarArt.tone(key) { measure(s, sheet, header) }
                s.ground = tone.ground
                s.inks = Inks.of(s.look.ink, s.look.accent, tone.ground)
                s.header = tone.header ?: HeaderTone.on(s.inks, tone.ground, s.look.shadow)
                // Built once per week, size and look, then only drawn: the numbers are all that change.
                val pane = CalendarArt.pane(key, s.widthPx, s.heightPx, s.px) { pane(it, s, sheet, tone) }
                c.drawBitmap(pane, null, rect, exact)
            }
            else -> {
                background.draw(
                    c, s.w, s.h, s.radius, s.request.config.copy(showWeatherArt = false), s.look.palette,
                    WeatherVisual.ClearDay, SkyAnchor(s.art.bodyX, s.art.bodyY, !s.art.isMoon, 30.0, 0.5), s.request.dynamic, s.request.seed,
                    light = s.light,
                )
                if (picture != null) paperPicture(c, s, picture)
            }
        }
    }

    /** Everything a [pane] depends on, so a kept one is only reused where it would look the same. */
    private fun paneKey(s: Sheet, sheet: RectF?): String = buildString {
        fun f(v: Float) = (v * 10f).roundToInt()
        append(s.style.name).append("|w").append(s.art.week).append("|n").append(s.night).append("|r").append(f(s.radius))
        // The painting under it is drawn and measured at this size and density.
        append("|z").append(f(s.w)).append('x').append(f(s.h)).append('@').append(f(s.px))
        if (sheet != null) append("|s").append(f(sheet.left)).append(',').append(f(sheet.top)).append(',').append(f(sheet.right)).append(',').append(f(sheet.bottom))
        append("|o").append(f(s.request.config.opacity)).append("|g").append(s.request.config.glassRim)
        append("|d").append(s.look.dark).append("|l").append(s.request.live).append("|h").append(s.scrim)
    }

    /**
     * A pane: the month's painting under a frosted sheet for the grid ([WidgetStyle.Sky]) or frosted
     * all over ([WidgetStyle.Glass]), with its glass edge.
     */
    private fun pane(c: Canvas, s: Sheet, sheet: RectF?, tone: PaneTone) {
        val rect = RectF(0f, 0f, s.w, s.h)
        val clip = Path().apply { addRoundRect(rect, s.radius, s.radius, Path.Direction.CW) }
        val painting = CalendarArt.painting(context, s.art, s.widthPx, s.heightPx, s.px, s.request.live, s.night)
        if (s.style == WidgetStyle.Sky) {
            c.withClip(clip) {
                drawBitmap(painting, null, rect, filter)
                tone.header?.let { headerScrim(this, s, it) }
            }
            if (sheet != null) frostedSheet(c, s, sheet, painting, tone.alpha)
            if (s.request.config.glassRim) GlassBevel.draw(c, rect, s.radius, s.light, s.look.dark, strength = 0.8f)
        } else {
            val frost = Frost.of(painting, s.px, GLASS_FROST, 2)
            c.withClip(clip) {
                drawBitmap(frost, null, rect, filter)
                val tint = Argb(glassTint(s))
                clean().shader = LinearGradient(0f, 0f, 0f, s.h, tint.withAlpha(tone.alpha).value, tint.withAlpha((tone.alpha + 0.08f).coerceAtMost(0.95f)).value, Shader.TileMode.CLAMP)
                drawRect(rect, paint)
                paint.shader = null
            }
            frost.recycle()
            if (s.request.config.glassRim) GlassBevel.draw(c, rect, s.radius, s.light, s.look.dark, strength = 1f)
        }
    }

    /** The glass's tint: smoky, of the sky's own blue, under light type; milky under dark. */
    private fun glassTint(s: Sheet): Int =
        if (s.look.dark) s.art.zenith.lerp(Argb.hex(0x0A0E1A), 0.55f).value else Argb.hex(0xF4F6FB).value

    /**
     * Measures [s]'s painting for its type: how thick the glass over the grid ([sheet], or the
     * whole pane of frosted glass) must lie for the days to keep their contrast however light or
     * busy the picture behind is — never thinner than the opacity chosen — and, where the header
     * stands on the painting, which type and how much shade under it.
     */
    private fun measure(s: Sheet, sheet: RectF?, header: RectF?): PaneTone {
        val painting = CalendarArt.painting(context, s.art, s.widthPx, s.heightPx, s.px, s.request.live, s.night)
        val sky = s.style == WidgetStyle.Sky
        val scale = if (sky) SHEET_FROST else GLASS_FROST
        val frost = Frost.of(painting, s.px, scale, 2)
        val opacity = s.request.config.opacity.coerceIn(if (sky) 0.3f else 0.15f, 1f)
        val least = opacity * (if (sky) (if (s.look.dark) 0.34f else 0.3f) else 0.5f)
        val (alpha, ground) = Backing.under(frost, scale, sheet ?: RectF(0f, 0f, s.w, s.h), glassTint(s), s.look.ink, least)
        frost.recycle()
        val tone = if (sky && s.scrim && header != null) HeaderTone.over(painting, s.px, header, s.night) else null
        return PaneTone(alpha, ground, tone)
    }

    /** The grid's sheet: the painting behind it frosted and tinted, a glass edge round it. */
    private fun frostedSheet(c: Canvas, s: Sheet, sheet: RectF, painting: Bitmap, alpha: Float) {
        val radius = if (sheet.left <= 0f && sheet.top <= 0f) s.radius else (s.radius - (s.w - sheet.right)).coerceIn(8f, sheet.height() / 2f)
        val frost = Frost.of(painting, s.px, SHEET_FROST, 2)
        path.reset()
        path.addRoundRect(sheet, radius, radius, Path.Direction.CW)
        c.withClip(path) {
            drawBitmap(frost, null, RectF(0f, 0f, s.w, s.h), filter)
            // As thick as the type needs over this picture: a touch more towards the foot for depth.
            val tint = Argb(glassTint(s))
            clean().shader = LinearGradient(0f, sheet.top, 0f, sheet.bottom, tint.withAlpha(alpha).value, tint.withAlpha((alpha + 0.08f).coerceAtMost(0.95f)).value, Shader.TileMode.CLAMP)
            drawRect(sheet, paint)
            paint.shader = null
        }
        frost.recycle()
        GlassBevel.draw(c, sheet, radius, s.light, s.look.dark, strength = 0.9f)
    }

    /**
     * Under today's date over the painting: a shade rising from the foot, dense enough — measured
     * over the picture there — for the white type in each part of the panel to read.
     */
    private fun dateScrim(c: Canvas, s: Sheet, box: RectF) {
        val painting = CalendarArt.painting(context, s.art, s.widthPx, s.heightPx, s.px, s.request.live, s.night)
        val night = 0xFF0A0E1C.toInt()
        val soft = 0xEEFFFFFF.toInt() or 0xFF000000.toInt()
        val split = box.top + box.height() * 0.45f
        val top = Backing.shade(painting, s.px, RectF(box.left, box.top, box.right, split), night, soft, least = 0.2f, most = 0.72f)
        val foot = Backing.shade(painting, s.px, RectF(box.left, split, box.right, box.bottom), night, soft, least = 0.45f, most = 0.8f)
        val color = Argb(night)
        path.reset()
        path.addRoundRect(RectF(0f, 0f, s.w, s.h), s.radius, s.radius, Path.Direction.CW)
        c.withClip(path) {
            clean().shader = LinearGradient(0f, box.bottom, 0f, box.top, intArrayOf(color.withAlpha(foot).value, color.withAlpha(max(foot * 0.7f, top)).value, color.withAlpha(top).value), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            drawRect(box, paint)
            paint.shader = null
        }
    }

    /**
     * The header's type gets what it needs to stand on the painting: a shade under light type, a
     * veil under dark — only as dense as this sky needs — over the header, fading out below it.
     */
    private fun headerScrim(c: Canvas, s: Sheet, tone: HeaderTone) {
        val bottom = (s.h * 0.24f).coerceIn(34f, 80f)
        val end = bottom + 18f
        val color = Argb(tone.shade)
        clean().shader = LinearGradient(
            0f, 0f, 0f, end,
            intArrayOf(color.withAlpha(tone.scrim).value, color.withAlpha(tone.scrim * 0.9f).value, color.withAlpha(0f).value),
            floatArrayOf(0f, bottom / end, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, s.w, end, paint)
        paint.shader = null
    }

    /** Paper: the month's painting as the page's picture, set in a thin frame. */
    private fun paperPicture(c: Canvas, s: Sheet, box: RectF) {
        val inset = (s.w * 0.045f).coerceIn(8f, 14f)
        val frame = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset * 0.3f)
        val r = (s.radius - inset).coerceIn(4f, 16f)
        path.reset()
        path.addRoundRect(frame, r, r, Path.Direction.CW)
        val picture = CalendarArt.painting(context, s.art, max(1, (frame.width() * s.px).roundToInt()), max(1, (frame.height() * s.px).roundToInt()), s.px, live = false, night = s.night)
        c.withClip(path) { drawBitmap(picture, null, frame, filter) }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.7f
        paint.color = WidgetPalette.withAlpha(s.look.ink, 0.25f)
        c.drawRoundRect(frame, r, r, paint)
        paint.style = Paint.Style.FILL
    }

    /** Today's bead: a drop of the season's colour, lit from above, with a crisp rim. */
    private fun bead(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val base = Argb(color or 0xFF000000.toInt())
        clean().shader = RadialGradient(cx, cy + r * 0.25f, r * 1.6f, intArrayOf(0x33000000, 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + r * 0.2f, r * 1.25f, paint)
        paint.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.45f, r * 1.5f, intArrayOf(base.lerp(Argb.White, 0.35f).value, base.value, base.lerp(Argb.Black, 0.18f).value), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
        // The rim and a catch-light along the top.
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        paint.color = Argb.White.withAlpha(0.55f).value
        c.drawCircle(cx, cy, r - 0.4f, paint)
        paint.strokeWidth = 1.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Argb.White.withAlpha(0.75f).value
        c.drawArc(RectF(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f), 200f, 60f, false, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private enum class Icon { Previous, Next, Add }

    /** A small glass button: a translucent disc with a lit rim, and its icon drawn in ink. */
    private fun glassButton(c: Canvas, s: Sheet, box: RectF, icon: Icon) {
        val r = box.width() / 2f
        val cx = box.centerX()
        val cy = box.centerY()
        val fill = if (s.header.dark) Argb.White.withAlpha(0.18f) else Argb.White.withAlpha(0.55f)
        paint.shader = null
        paint.color = fill.value
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        // Opaque, so the gradient's own alphas hold rather than the fill's.
        paint.alpha = 255
        paint.shader = LinearGradient(0f, cy - r, 0f, cy + r, Argb.White.withAlpha(if (s.header.dark) 0.55f else 0.9f).value, Argb.White.withAlpha(0.08f).value, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - 0.4f, paint)
        paint.shader = null
        paint.color = s.header.ink
        paint.strokeWidth = (r * 0.13f).coerceIn(1.3f, 2f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val a = r * 0.28f
        path.reset()
        when (icon) {
            Icon.Previous -> {
                path.moveTo(cx + a * 0.45f, cy - a)
                path.lineTo(cx - a * 0.55f, cy)
                path.lineTo(cx + a * 0.45f, cy + a)
            }
            Icon.Next -> {
                path.moveTo(cx - a * 0.45f, cy - a)
                path.lineTo(cx + a * 0.55f, cy)
                path.lineTo(cx - a * 0.45f, cy + a)
            }
            Icon.Add -> {
                path.moveTo(cx - a * 1.1f, cy)
                path.lineTo(cx + a * 1.1f, cy)
                path.moveTo(cx, cy - a * 1.1f)
                path.lineTo(cx, cy + a * 1.1f)
            }
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }

    // endregion

    // region Words

    private fun monthName(month: YearMonth, locale: Locale): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    /** "25 сентября": the month as a date takes it (genitive in Russian). */
    private fun monthGenitive(date: LocalDate, locale: Locale): String =
        date.month.getDisplayName(TextStyle.FULL, locale)

    private fun weekdayFull(day: DayOfWeek, locale: Locale): String =
        day.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    private fun weekdayName(day: DayOfWeek, locale: Locale, narrow: Boolean): String =
        day.getDisplayName(if (narrow) TextStyle.NARROW_STANDALONE else TextStyle.SHORT_STANDALONE, locale)
            .trimEnd('.')
            .replaceFirstChar { it.titlecase(locale) }

    // endregion

    /** Everything one render needs, worked out once. */
    private inner class Sheet(
        val pxPerDp: Float,
        val request: WidgetRenderRequest,
        val view: CalendarView,
        val art: WeekArt,
        val look: Look,
        val w: Float,
        val h: Float,
        val radius: Float,
        /** The dark theme: the painting by moonlight under smoky glass. */
        val night: Boolean,
    ) {
        val style: WidgetStyle get() = request.config.style

        /** Pixels per dp of the canvas being drawn on, and the picture's size in pixels. */
        val px: Float = pxPerDp
        val widthPx: Int = max(1, (w * px).roundToInt())
        val heightPx: Int = max(1, (h * px).roundToInt())

        /** Whether the painting under the header gets a shade for the type (a pane with a header). */
        var scrim: Boolean = true

        /** What the grid's type stands on — measured once the painting is at hand — and its inks. */
        var ground: Backing = look.ground
        var inks: Inks = Inks.of(look.ink, look.accent, look.ground)

        /** The header's type: on the ground like the grid, or measured over the painting. */
        var header: HeaderTone = HeaderTone.on(inks, ground, look.shadow)
        val k: Float get() = request.config.textScale
        val firstDay: DayOfWeek = CalendarMonth.firstDayFor(request.config.calendar.weekStart, view.locale)
        val light: WidgetLight = art.light(w, h)
        val subtitle: Boolean get() = view.isCurrentMonth
        val format: WeatherFormat = WeatherFormat(
            context, request.content.units,
            request.content.forecast?.let { WeatherFormat.zoneOf(it.timezone, it.utcOffsetSeconds) } ?: ZoneId.systemDefault(),
        )

        /** The forecast's days by their date where the forecast is. */
        val forecastDays: Map<LocalDate, DailyPoint> by lazy {
            val forecast = request.content.forecast ?: return@lazy emptyMap()
            val zone = WeatherFormat.zoneOf(forecast.timezone, forecast.utcOffsetSeconds)
            forecast.daily.associateBy { Instant.ofEpochSecond(it.time + 3600).atZone(zone).toLocalDate() }
        }
    }

    /**
     * The colours of one render: the weather widgets' palette for this style and theme, built on
     * the month's painting instead of the sky, plus what the header over the painting needs.
     */
    internal class Look(
        val palette: WidgetPalette,
        /** Light type on dark: smoky glass, dark paper, a dark tonal pane, or the wallpaper. */
        val dark: Boolean,
        val ink: Int,
        val accent: Int,
        val shadow: Boolean,
        val glyphTone: WeatherGlyphPainter.Tone,
        val glassInk: app.rosa.weather.widget.render.GlassInk?,
        /** What the type stands on where nothing needs measuring: paper, a tonal pane, the wallpaper. */
        val ground: Backing,
    ) {
        companion object {
            /**
             * The theme: dark when chosen, or in Auto when the phone is in its dark theme — then the
             * painting is by moonlight too ([night]). In Auto by day the glass follows the painting:
             * smoky over a dark one, milky over a light one. Paper and tonal panes follow the theme;
             * over the wallpaper the type is always light, with a shadow.
             */
            fun of(request: WidgetRenderRequest, art: WeekArt, night: Boolean): Look {
                val light = Argb.hex(0xFFFBF5)
                val darkInk = Argb.hex(0x1B2030)
                val style = request.config.style
                val dark = when (style) {
                    WidgetStyle.Clear -> true
                    WidgetStyle.Paper, WidgetStyle.Tonal -> night
                    WidgetStyle.Sky, WidgetStyle.Glass -> night || (request.config.theme == WidgetTheme.Auto && art.dark)
                }
                val sky = SkyPalette(
                    zenith = art.zenith, horizon = art.horizon, glow = art.bodyColor, sun = art.bodyColor,
                    cloudLight = Argb.White, cloudShade = Argb.hex(0x8A93A6),
                    ink = if (dark) light else darkInk,
                    inkSoft = (if (dark) light else darkInk).withAlpha(0.72f),
                    accent = art.accent, warm = art.accent, cool = art.zenith,
                    brightness = if (dark) 0.15 else 0.6,
                )
                val daylight = request.content.forecast?.momentAt(request.content.nowEpochSeconds)?.isDay ?: !request.systemNight
                val themed = request.config.copy(theme = if (dark) WidgetTheme.Dark else WidgetTheme.Light)
                val palette = WidgetPalette.resolve(themed, sky, request.systemNight, isDaylight = daylight, dynamic = request.dynamic)
                val accent = when (request.config.accent) {
                    app.rosa.weather.core.model.WidgetAccent.Sky, app.rosa.weather.core.model.WidgetAccent.Temperature -> art.accent.value
                    else -> palette.accent
                }
                val dynamic = request.dynamic
                val ground = when (style) {
                    // Paper's gradient and the shade its vignette puts in the corners.
                    WidgetStyle.Paper -> if (dark) Backing.between(0xFF26231F.toInt(), 0xFF14120F.toInt()) else Backing.between(0xFFF8F1E4.toInt(), 0xFFE0D2BA.toInt())
                    WidgetStyle.Tonal -> if (dark) {
                        Backing.between(Argb(dynamic.neutralDark).lerp(Argb(dynamic.accentDark), 0.35f).value, Argb(dynamic.accentDark).lerp(Argb(dynamic.neutralDark), 0.4f).value)
                    } else {
                        Backing.between(dynamic.accentLight, Argb(dynamic.neutralLight).lerp(Argb(dynamic.accentLight), 0.45f).value)
                    }
                    // Over the wallpaper nothing is known: a darkish one is assumed, the shadow does the rest.
                    WidgetStyle.Clear -> Backing(0f, 0.12f, 0xFF26262C.toInt())
                    // Measured once the painting is at hand; until then, the glass's own tone.
                    WidgetStyle.Sky, WidgetStyle.Glass -> Backing.flat(if (dark) 0xFF1A2030.toInt() else 0xFFF0F2F6.toInt())
                }
                return Look(
                    palette = palette,
                    dark = dark,
                    ink = palette.ink,
                    accent = accent,
                    shadow = palette.textShadow,
                    glyphTone = palette.glyphTone,
                    glassInk = palette.glass,
                    ground = ground,
                )
            }
        }
    }
}
