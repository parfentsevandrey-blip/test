package app.rosa.weather.widget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glyph.WeatherGlyphPainter
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.Headline
import app.rosa.weather.core.model.Headlines
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.TemperatureScale
import app.rosa.weather.core.model.Units
import app.rosa.weather.core.model.WeatherCondition
import app.rosa.weather.core.model.WeatherVisual
import app.rosa.weather.core.model.WidgetAccent
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.R
import app.rosa.weather.core.designsystem.R as DsR
import app.rosa.weather.widget.layout.Block
import app.rosa.weather.widget.layout.Box
import app.rosa.weather.widget.layout.DetailKind
import app.rosa.weather.widget.layout.HeroVariant
import app.rosa.weather.widget.layout.LayoutContent
import app.rosa.weather.widget.layout.WidgetLayout
import app.rosa.weather.widget.layout.WidgetLayoutEngine
import app.rosa.weather.widget.motion.LiveWeather
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything a widget shows, independent of its size. */
data class WidgetContent(
    val placeName: String,
    val isCurrentLocation: Boolean,
    val forecast: Forecast?,
    val nowEpochSeconds: Long,
    val units: Units,
    val refreshing: Boolean = false,
    val status: Status = Status.Ready,
    /** The place actually shown (a tap opens the app on it); null when there is none yet. */
    val placeId: String? = null,
) {
    enum class Status { Ready, Loading, NeedsLocation, NoData }

    val isStale: Boolean
        get() = forecast != null && nowEpochSeconds - forecast.fetchedAt > STALE_AFTER_SECONDS

    companion object {
        const val STALE_AFTER_SECONDS = 3 * 3600L
    }
}

data class WidgetRenderRequest(
    val widthDp: Float,
    val heightDp: Float,
    val config: WidgetConfig,
    val content: WidgetContent,
    val cornerRadiusDp: Float,
    val systemNight: Boolean,
    val dynamic: DynamicTones = DynamicTones.Fallback,
    val seed: Int = 0,
    /**
     * Falling rain or snow and lightning are animated over the picture ([LiveWeather]), so the
     * picture leaves them out and keeps only what stays put: drops resting on the glass, frost, mist.
     */
    val live: Boolean = false,
)

/**
 * Renders a widget of any size into a bitmap (or straight onto a canvas, which is how the widget
 * studio shows a live, pixel-identical preview). All geometry is in dp; the canvas is pre-scaled.
 */
class WidgetRenderer(private val context: Context) {
    private val fonts = WidgetFonts.get(context)
    private val type = WidgetType(fonts)
    private val glyphs = WeatherGlyphPainter()
    private val background = WidgetBackground()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun render(request: WidgetRenderRequest, pxPerDp: Float): Bitmap {
        val w = (request.widthDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val h = (request.heightDp * pxPerDp).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        canvas.scale(pxPerDp, pxPerDp)
        draw(canvas, request)
        return bitmap
    }

    /** Draws in dp units; callers scale the canvas. Returns the layout used (for hit regions). */
    fun draw(canvas: Canvas, request: WidgetRenderRequest): WidgetLayout {
        val content = request.content
        val forecast = content.forecast
        val now = content.nowEpochSeconds
        val moment = forecast?.momentAt(now)
        val visual = moment?.visual ?: WeatherVisual.ClearDay
        val sky = SkyPalette.of(moment?.sun?.elevation ?: 35.0, visual, moment?.moonPhase?.illumination ?: 0.5)
        val palette = WidgetPalette.resolve(
            request.config, sky, request.systemNight,
            isDaylight = moment?.isDay ?: true, dynamic = request.dynamic,
        )
        val zone = WeatherFormat.zoneOf(forecast?.timezone, forecast?.utcOffsetSeconds ?: 0)
        val format = WeatherFormat(context, content.units, zone)
        val anchor = if (moment == null) {
            SkyAnchor(0.8f, 0.2f, true, 40.0, 0.5)
        } else if (moment.sun.elevation > -4) {
            WidgetBackground.anchorFor(moment.sun.elevation, moment.sun.azimuth, true, moment.moonPhase.phase)
        } else {
            WidgetBackground.anchorFor(moment.moon.elevation, moment.moon.azimuth, false, moment.moonPhase.phase)
        }

        background.draw(
            canvas, request.widthDp, request.heightDp, request.cornerRadiusDp, request.config, palette,
            visual, anchor, request.dynamic, request.seed,
            pane = moment?.let { WidgetBackground.Pane(it.paneFrost, it.paneMist) } ?: WidgetBackground.Pane.Dry,
            live = request.live,
        )

        val headline = if (forecast != null && moment != null) Headlines.pick(forecast, moment) else null
        val layoutContent = layoutContentFor(forecast, moment, now)
        val layout = WidgetLayoutEngine.layout(request.widthDp, request.heightDp, request.config, layoutContent)

        if (forecast == null || moment == null || content.status != WidgetContent.Status.Ready) {
            drawEmptyState(canvas, request, palette, layout)
            return layout
        }
        val scene = Scene(request, palette, format, forecast, moment, headline, layout.scale)
        layout.blocks.forEach { block ->
            when (block) {
                is Block.Hero -> hero(canvas, block, scene)
                is Block.HourlyStrip -> hourlyStrip(canvas, block, scene)
                is Block.HourlyList -> hourlyList(canvas, block, scene)
                is Block.DailyList -> dailyList(canvas, block, scene)
                is Block.DailyColumns -> dailyColumns(canvas, block, scene)
                is Block.Details -> details(canvas, block, scene)
                is Block.Nowcast -> nowcast(canvas, block, scene)
                is Block.SunPath -> sunPath(canvas, block, scene)
            }
        }
        return layout
    }

    /** Spoken summary for TalkBack; the bitmap itself is opaque to accessibility. */
    fun describe(content: WidgetContent): String {
        val forecast = content.forecast ?: return context.getString(R.string.widget_no_data)
        val moment = forecast.momentAt(content.nowEpochSeconds)
        val zone = WeatherFormat.zoneOf(forecast.timezone, forecast.utcOffsetSeconds)
        val format = WeatherFormat(context, content.units, zone)
        val today = forecast.dayAt(content.nowEpochSeconds)
        val headline = format.headline(Headlines.pick(forecast, moment), content.nowEpochSeconds)
        return context.getString(
            R.string.widget_a11y,
            content.placeName.ifBlank { format.currentLocation() },
            format.temperature(moment.temperature),
            format.condition(moment.condition, moment.isDay),
            today?.let { format.highLow(it.temperatureMax, it.temperatureMin) }.orEmpty(),
            headline,
        )
    }

    private class Scene(
        val request: WidgetRenderRequest,
        val palette: WidgetPalette,
        val format: WeatherFormat,
        val forecast: Forecast,
        val moment: ForecastMoment,
        val headline: Headline?,
        val k: Float,
    ) {
        val config: WidgetConfig get() = request.config
        val now: Long get() = moment.epochSeconds
        val shadow: Boolean get() = palette.textShadow

        fun accentFor(celsius: Double): Int =
            if (config.accent == WidgetAccent.Temperature) TemperatureScale.colorFor(celsius).value else palette.accent
    }

    private fun layoutContentFor(forecast: Forecast?, moment: ForecastMoment?, now: Long): LayoutContent {
        if (forecast == null || moment == null) return LayoutContent(hasHeadline = false)
        val details = buildList {
            add(DetailKind.FeelsLike)
            add(DetailKind.Wind)
            add(DetailKind.Humidity)
            add(DetailKind.Precipitation)
            if (moment.isDay || moment.uvIndex > 0.5) add(DetailKind.Uv)
            add(DetailKind.Pressure)
            if (forecast.dayAt(now)?.sunset != null) add(DetailKind.SunEvent)
            if (forecast.air?.europeanAqi != null) add(DetailKind.Air)
            if (moment.visibility != null) add(DetailKind.Visibility)
        }
        val wetSoon = moment.precipitation >= 0.2 ||
            forecast.nowcast.any { it.time > now && it.time - now <= 7200 && it.precipitation >= 0.05 }
        return LayoutContent(
            hourlyAvailable = forecast.hoursFrom(now).size,
            dailyAvailable = forecast.daysFrom(now).size,
            details = details,
            precipitationSoon = wetSoon && forecast.nowcast.isNotEmpty(),
            hasHeadline = true,
        )
    }

    // region Hero

    private fun hero(canvas: Canvas, block: Block.Hero, s: Scene) {
        when (block.variant) {
            HeroVariant.Micro -> heroMicro(canvas, block.box, s)
            HeroVariant.Compact -> heroCompact(canvas, block, s)
            HeroVariant.Inline -> heroInline(canvas, block, s)
            HeroVariant.Full -> heroFull(canvas, block, s)
        }
    }

    private fun temperatureText(s: Scene): String {
        val value = if (s.config.showFeelsLike && abs(s.moment.apparentTemperature - s.moment.temperature) >= 8) {
            s.moment.apparentTemperature
        } else {
            s.moment.temperature
        }
        return s.format.temperature(value)
    }

    private fun heroMicro(canvas: Canvas, box: Box, s: Scene) {
        val text = temperatureText(s)
        val size = type.fitSize(text, box.w * 0.96f, 10f, box.h * 0.9f) { type.numerals(it, s.palette.ink) }
        val p = type.numerals(size, s.palette.ink)
        val baseline = box.y + box.h / 2 + WidgetType.capHeight(p) / 2
        numerals(canvas, text, box.x + box.w / 2, baseline, p, s, Paint.Align.CENTER)
        if (box.w >= 44 && box.h >= 44) {
            val g = min(box.w, box.h) * 0.34f
            glyph(canvas, s, RectF(box.right - g, box.y - g * 0.1f, box.right + g * 0.1f, box.y + g))
        }
    }

    private fun heroCompact(canvas: Canvas, block: Block.Hero, s: Scene) {
        val box = block.box
        val k = s.k
        val lines = mutableListOf<Pair<String, TextStyle>>()
        if (block.showCondition) lines += s.format.condition(s.moment.condition, s.moment.isDay) to TextStyle.Primary
        if (block.showHighLow) s.forecast.dayAt(s.now)?.let { lines += s.format.highLow(it.temperatureMax, it.temperatureMin) to TextStyle.Secondary }
        if (block.showHeadline) s.headline?.takeIf { it !is Headline.Steady }?.let { lines += s.format.headline(it, s.now) to TextStyle.Accent }
        val lineH = 15.5f * k
        val linesH = lines.size * lineH

        val glyphSize = min(box.w * 0.46f, (box.h - linesH) * 0.42f).coerceAtLeast(16f)
        glyph(canvas, s, RectF(box.x - glyphSize * 0.08f, box.y - glyphSize * 0.06f, box.x + glyphSize * 0.92f, box.y + glyphSize * 0.94f))
        if (s.config.showLocation && box.w - glyphSize >= 44 * k) {
            placeLabel(canvas, s, box.right, box.y + 12 * k, box.w - glyphSize - 4 * k, align = Paint.Align.RIGHT)
        }
        statusMark(canvas, s, box.right, box.y + if (s.config.showLocation) 26 * k else 8 * k)

        val tempTop = box.y + glyphSize * 0.9f
        val tempBottom = box.bottom - linesH
        val text = temperatureText(s)
        val size = type.fitSize(text, box.w, 14f, ((tempBottom - tempTop) * 1.05f).coerceAtLeast(14f)) { type.numerals(it, s.palette.ink) }
        val p = type.numerals(size, s.palette.ink)
        val baseline = tempBottom - (tempBottom - tempTop - WidgetType.capHeight(p)) / 2 - 2 * k
        numerals(canvas, text, box.x - size * 0.03f, baseline, p, s)

        lines.forEachIndexed { i, (line, style) ->
            val paint = paintFor(style, 12.5f * k, s)
            WidgetType.draw(canvas, line, box.x, tempBottom + (i + 1) * lineH - 4 * k, paint, box.w, shadow = s.shadow)
        }
    }

    private fun heroInline(canvas: Canvas, block: Block.Hero, s: Scene) {
        val box = block.box
        val k = s.k
        val g = min(box.h * 0.92f, 64 * k)
        glyph(canvas, s, RectF(box.x - g * 0.06f, box.y + (box.h - g) / 2, box.x + g * 0.94f, box.y + (box.h + g) / 2))
        val text = temperatureText(s)
        val textArea = box.w - g
        val maxTempW = if (block.showCondition) textArea * 0.46f else textArea
        val size = type.fitSize(text, maxTempW, 14f, box.h * 0.86f) { type.numerals(it, s.palette.ink) }
        val p = type.numerals(size, s.palette.ink)
        val tempX = box.x + g + 2 * k
        val baseline = box.y + box.h / 2 + WidgetType.capHeight(p) / 2
        val tempW = numerals(canvas, text, tempX, baseline, p, s)
        if (!block.showCondition) {
            statusMark(canvas, s, box.right, box.y + 6 * k)
            return
        }
        val colX = tempX + tempW + 10 * k
        val colW = box.right - colX
        if (colW < 40 * k) return
        val rows = mutableListOf<Pair<String, TextStyle>>()
        if (s.config.showLocation && box.h >= 46 * k) rows += placeText(s) to TextStyle.Secondary
        rows += s.format.condition(s.moment.condition, s.moment.isDay) to TextStyle.Primary
        if (block.showHeadline) {
            s.headline?.takeIf { it !is Headline.Steady }?.let { rows += s.format.headline(it, s.now) to TextStyle.Accent }
                ?: s.forecast.dayAt(s.now)?.let { rows += s.format.highLow(it.temperatureMax, it.temperatureMin) to TextStyle.Secondary }
        }
        val lineH = min(17f * k, box.h / rows.size)
        val startY = box.y + (box.h - lineH * rows.size) / 2
        rows.forEachIndexed { i, (line, style) ->
            val paint = paintFor(style, min(13f * k, lineH * 0.78f), s)
            WidgetType.draw(canvas, line, colX, startY + (i + 1) * lineH - lineH * 0.26f, paint, colW, shadow = s.shadow)
        }
        statusMark(canvas, s, box.right, box.y + 4 * k)
    }

    private fun heroFull(canvas: Canvas, block: Block.Hero, s: Scene) {
        val box = block.box
        val k = s.k
        val topRow = if (s.config.showLocation) 18f * k else 0f
        if (s.config.showLocation) placeLabel(canvas, s, box.x, box.y + 13 * k, box.w * 0.62f)
        statusMark(canvas, s, box.right, box.y + 7 * k)

        val lines = mutableListOf<Pair<String, TextStyle>>()
        lines += s.format.condition(s.moment.condition, s.moment.isDay) to TextStyle.Primary
        val today = s.forecast.dayAt(s.now)
        if (block.showHighLow && today != null) {
            val feels = if (s.config.showFeelsLike) " · " + s.format.headline(Headline.FeelsLike(s.moment.apparentTemperature), s.now) else ""
            lines += s.format.highLow(today.temperatureMax, today.temperatureMin) + feels to TextStyle.Secondary
        }
        if (block.showHeadline) s.headline?.takeIf { it !is Headline.Steady }?.let { lines += s.format.headline(it, s.now) to TextStyle.Accent }
        val lineH = 17f * k
        val linesH = lines.size * lineH + 2 * k

        val tempTop = box.y + topRow
        val tempBottom = box.bottom - linesH
        val available = tempBottom - tempTop
        val g = min(available * 0.95f, box.w * 0.34f).coerceAtMost(110 * k)
        glyph(canvas, s, RectF(box.right - g, tempTop + (available - g) / 2 - g * 0.04f, box.right, tempTop + (available + g) / 2 - g * 0.04f))

        val text = temperatureText(s)
        val size = type.fitSize(text, box.w - g - 6 * k, 18f, (available * 1.1f).coerceAtMost(104 * k)) { type.numerals(it, s.palette.ink) }
        val p = type.numerals(size, s.palette.ink)
        val baseline = tempTop + available / 2 + WidgetType.capHeight(p) / 2
        numerals(canvas, text, box.x - size * 0.035f, baseline, p, s)

        lines.forEachIndexed { i, (line, style) ->
            val paint = paintFor(style, if (style == TextStyle.Primary) 14f * k else 12.5f * k, s)
            if (style == TextStyle.Accent) {
                paint.color = s.palette.accent
                drawAccentDot(canvas, box.x + 3 * k, tempBottom + (i + 1) * lineH - 8.5f * k, s)
                WidgetType.draw(canvas, line, box.x + 11 * k, tempBottom + (i + 1) * lineH - 4 * k, paint, box.w - 11 * k, shadow = s.shadow)
            } else {
                WidgetType.draw(canvas, line, box.x, tempBottom + (i + 1) * lineH - 4 * k, paint, box.w, shadow = s.shadow)
            }
        }
    }

    /** The big temperature: glass where the style allows, flat ink otherwise. Returns its width. */
    private fun numerals(canvas: Canvas, text: String, x: Float, baseline: Float, paint: TextPaint, s: Scene, align: Paint.Align = Paint.Align.LEFT): Float {
        val glass = s.palette.glass ?: return WidgetType.draw(canvas, text, x, baseline, paint, align = align, shadow = s.shadow)
        val width = paint.measureText(text)
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - width / 2
            Paint.Align.RIGHT -> x - width
        }
        GlassNumerals.draw(canvas, text, left, baseline, paint, glass, strongShadow = s.shadow)
        return width
    }

    private enum class TextStyle { Primary, Secondary, Accent }

    private fun paintFor(style: TextStyle, size: Float, s: Scene): TextPaint = when (style) {
        TextStyle.Primary -> type.text(size, s.palette.ink, 600)
        TextStyle.Secondary -> type.text(size, s.palette.inkSoft, 500)
        TextStyle.Accent -> type.text(size, s.palette.accent, 600)
    }

    private fun placeText(s: Scene): String {
        val name = s.request.content.placeName.ifBlank { s.format.currentLocation() }
        return if (s.request.content.isCurrentLocation) "➤ $name" else name
    }

    private fun placeLabel(canvas: Canvas, s: Scene, x: Float, baseline: Float, maxWidth: Float, align: Paint.Align = Paint.Align.LEFT) {
        val name = s.request.content.placeName.ifBlank { s.format.currentLocation() }
        val paint = type.text(12.5f * s.k, s.palette.inkSoft, 600)
        if (s.request.content.isCurrentLocation && align == Paint.Align.LEFT) {
            drawLocationArrow(canvas, x + 4 * s.k, baseline - 4.2f * s.k, 4.6f * s.k, s.palette.inkSoft)
            WidgetType.draw(canvas, name, x + 11 * s.k, baseline, paint, maxWidth - 11 * s.k, shadow = s.shadow)
        } else {
            WidgetType.draw(canvas, name, x, baseline, paint, maxWidth, align, shadow = s.shadow)
        }
    }

    private fun drawLocationArrow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        path.reset()
        path.moveTo(cx + r, cy - r)
        path.lineTo(cx - r * 0.2f, cy + r)
        path.lineTo(cx - r * 0.05f, cy + r * 0.05f)
        path.lineTo(cx - r, cy - r * 0.2f)
        path.close()
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawAccentDot(canvas: Canvas, cx: Float, cy: Float, s: Scene) {
        paint.shader = null
        paint.color = WidgetPalette.withAlpha(s.palette.accent, 0.3f)
        canvas.drawCircle(cx, cy, 3.6f * s.k, paint)
        paint.color = s.palette.accent
        canvas.drawCircle(cx, cy, 2.1f * s.k, paint)
    }

    /** Tiny indicator: spinning arc while refreshing, faded age when data is stale. */
    private fun statusMark(canvas: Canvas, s: Scene, right: Float, top: Float) {
        val k = s.k
        if (s.request.content.refreshing) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 1.6f * k
            paint.color = s.palette.inkSoft
            val r = 4.5f * k
            canvas.drawArc(RectF(right - 2 * r, top, right, top + 2 * r), -90f, 280f, false, paint)
            paint.style = Paint.Style.FILL
        } else if (s.request.content.isStale) {
            val p = type.text(10f * k, s.palette.inkFaint, 500)
            val age = s.format.minutes(((s.now - s.forecast.fetchedAt) / 60).toInt())
            WidgetType.draw(canvas, "↻ $age", right, top + 8 * k, p, align = Paint.Align.RIGHT, shadow = s.shadow)
        }
    }

    private fun glyph(canvas: Canvas, s: Scene, rect: RectF, condition: WeatherCondition = s.moment.condition, isDay: Boolean = s.moment.isDay) {
        glyphs.draw(canvas, condition, isDay, rect, s.palette.glyphTone, s.palette.ink, s.moment.moonPhase.phase, onLightBackground = !s.palette.isDark)
    }

    // endregion

    // region Hourly

    private fun hourlyStrip(canvas: Canvas, block: Block.HourlyStrip, s: Scene) {
        val box = block.box
        val k = s.k
        val hours = s.forecast.hoursFrom(s.now).take(block.count)
        if (hours.isEmpty()) return
        val firstIndex = s.forecast.firstHourIndexFrom(s.now)
        fun chance(i: Int) = s.forecast.chanceForHourStarting(firstIndex + i)
        val colW = box.w / hours.size
        val labelSize = min(11f * k, colW * 0.3f)
        val tempSize = min(13.5f * k, colW * 0.34f)
        val labelBaseline = box.y + labelSize + 1 * k

        // "Now" gets a soft fill capsule (content on glass uses fills, never more glass). It
        // reaches a little past its column, and the word is sized to keep air on both sides.
        paint.shader = null
        paint.color = s.palette.fill
        val pillR = min(colW * 0.42f, 14 * k)
        val overhang = 3 * k
        canvas.drawRoundRect(RectF(box.x - overhang, box.y - 3 * k, box.x + colW + overhang, box.bottom + 2 * k), pillR, pillR, paint)
        val nowLabel = s.format.now()
        val nowSize = type.fitSize(nowLabel, colW + 2 * overhang - 14 * k, 7f * k, labelSize) { type.text(it, s.palette.inkSoft, 500) }
        fun labelPaint(i: Int) = type.text(if (i == 0) nowSize else labelSize, s.palette.inkSoft, 500)

        val glyphSize = if (block.withGlyph) min(colW * 0.62f, 26f * k).coerceAtMost(box.h * 0.32f) else 0f
        val glyphTop = labelBaseline + 5 * k

        if (!block.withCurve) {
            hours.forEachIndexed { i, hour ->
                val cx = box.x + colW * (i + 0.5f)
                val label = if (i == 0) nowLabel else s.format.hour(hour.time)
                WidgetType.draw(canvas, label, cx, labelBaseline, labelPaint(i), colW + 2 * overhang, Paint.Align.CENTER, s.shadow)
                if (block.withGlyph) {
                    glyph(canvas, s, RectF(cx - glyphSize / 2, glyphTop, cx + glyphSize / 2, glyphTop + glyphSize), WeatherCondition.fromWmo(hour.weatherCode), hour.isDay)
                }
                val temp = if (i == 0) s.moment.temperature else hour.temperature
                WidgetType.draw(canvas, s.format.temperature(temp), cx + tempSize * 0.1f, box.bottom - 3 * k, type.text(tempSize, s.palette.ink, 620), colW, Paint.Align.CENTER, s.shadow)
                if (chance(i) >= 30 && box.h - (glyphTop - box.y) - glyphSize > tempSize * 2.2f) {
                    WidgetType.draw(canvas, "${chance(i)}%", cx, glyphTop + glyphSize + 10 * k, type.text(9.5f * k, s.palette.rain, 600), colW, Paint.Align.CENTER, s.shadow)
                }
            }
            return
        }

        // Curve variant: label, glyph, then a temperature ribbon with values riding on it and
        // precipitation chances as droplets of height along the bottom.
        val curveTop = glyphTop + glyphSize + 16 * k
        val precipH = if (box.h > 118 * k) 12 * k else 0f
        val curveBottom = box.bottom - precipH - 6 * k
        val temps = hours.mapIndexed { i, h -> if (i == 0) s.moment.temperature else h.temperature }
        val tMin = temps.min()
        val tMax = temps.max()
        val span = max(tMax - tMin, 3.0)
        fun yOf(t: Double) = (curveBottom - ((t - tMin) / span).toFloat() * (curveBottom - curveTop))
        val points = temps.mapIndexed { i, t -> (box.x + colW * (i + 0.5f)) to yOf(t) }

        hours.forEachIndexed { i, hour ->
            val cx = points[i].first
            val label = if (i == 0) nowLabel else s.format.hour(hour.time)
            WidgetType.draw(canvas, label, cx, labelBaseline, labelPaint(i), colW + 2 * overhang, Paint.Align.CENTER, s.shadow)
            glyph(canvas, s, RectF(cx - glyphSize / 2, glyphTop, cx + glyphSize / 2, glyphTop + glyphSize), WeatherCondition.fromWmo(hour.weatherCode), hour.isDay)
        }

        smoothPath(points)
        // Area under the ribbon.
        val area = Path(path)
        area.lineTo(points.last().first, curveBottom + 6 * k)
        area.lineTo(points.first().first, curveBottom + 6 * k)
        area.close()
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = LinearGradient(0f, curveTop, 0f, curveBottom + 6 * k, WidgetPalette.withAlpha(s.accentFor(tMax), 0.26f), 0, Shader.TileMode.CLAMP)
        canvas.drawPath(area, paint)
        // Ribbon coloured by temperature along x.
        val colors = temps.map { TemperatureScale.colorFor(it).value }.toIntArray()
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = if (colors.size >= 2) {
            LinearGradient(points.first().first, 0f, points.last().first, 0f, colors, null, Shader.TileMode.CLAMP)
        } else {
            null
        }
        if (s.config.accent == WidgetAccent.Mono || s.palette.glyphTone == WeatherGlyphPainter.Tone.Mono) {
            paint.shader = null
            paint.color = s.palette.ink
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.2f * k
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.shader = null

        points.forEachIndexed { i, (x, y) ->
            val dotColor = if (s.palette.glyphTone == WeatherGlyphPainter.Tone.Mono) s.palette.ink else TemperatureScale.colorFor(temps[i]).value
            if (i == 0) {
                paint.color = WidgetPalette.withAlpha(dotColor, 0.3f)
                canvas.drawCircle(x, y, 6f * k, paint)
            }
            paint.color = s.palette.ink
            canvas.drawCircle(x, y, (if (i == 0) 3.6f else 2.4f) * k, paint)
            paint.color = dotColor
            canvas.drawCircle(x, y, (if (i == 0) 2.4f else 1.5f) * k, paint)
            WidgetType.draw(canvas, s.format.temperature(temps[i]), x + tempSize * 0.1f, y - 6 * k, type.text(tempSize * 0.92f, s.palette.ink, 620), colW, Paint.Align.CENTER, s.shadow)
        }

        if (precipH > 0f) {
            val maxBar = precipH
            hours.forEachIndexed { i, hour ->
                val p = chance(i) / 100f
                if (p < 0.05f) return@forEachIndexed
                val cx = points[i].first
                val bw = min(colW * 0.34f, 10 * k)
                val bh = max(2f * k, maxBar * p)
                paint.color = WidgetPalette.withAlpha(s.palette.rain, 0.35f + p * 0.6f)
                canvas.drawRoundRect(RectF(cx - bw / 2, box.bottom - bh, cx + bw / 2, box.bottom), bw / 2, bw / 2, paint)
            }
        }
    }

    private fun hourlyList(canvas: Canvas, block: Block.HourlyList, s: Scene) {
        val box = block.box
        val k = s.k
        val hours = s.forecast.hoursFrom(s.now).drop(1).take(block.rows)
        val rowH = box.h / block.rows
        val size = min(12.5f * k, rowH * 0.5f)
        val g = min(rowH * 0.86f, 22 * k)
        hours.forEachIndexed { i, hour ->
            val top = box.y + i * rowH
            val baseline = top + rowH / 2 + size * 0.36f
            WidgetType.draw(canvas, s.format.hour(hour.time), box.x, baseline, type.text(size, s.palette.inkSoft, 500), box.w * 0.34f, shadow = s.shadow)
            val gx = box.x + box.w * 0.46f
            glyph(canvas, s, RectF(gx - g / 2, top + (rowH - g) / 2, gx + g / 2, top + (rowH + g) / 2), WeatherCondition.fromWmo(hour.weatherCode), hour.isDay)
            WidgetType.draw(canvas, s.format.temperature(hour.temperature), box.right, baseline, type.text(size * 1.05f, s.palette.ink, 620), box.w * 0.4f, Paint.Align.RIGHT, s.shadow)
        }
    }

    // endregion

    // region Daily

    private fun dailyList(canvas: Canvas, block: Block.DailyList, s: Scene) {
        val box = block.box
        val k = s.k
        val days = s.forecast.daysFrom(s.now).take(block.rows)
        if (days.isEmpty()) return
        val rowH = box.h / block.rows
        val size = min(13f * k, rowH * 0.5f)
        val g = min(rowH * 0.9f, 24 * k)
        val lo = days.minOf { it.temperatureMin }
        val hi = days.maxOf { it.temperatureMax }
        val narrow = !block.withBars
        val dayW = if (narrow) min(box.w * 0.3f, 30 * k) else min(box.w * 0.26f, 78 * k)

        days.forEachIndexed { i, day ->
            val top = box.y + i * rowH
            val cy = top + rowH / 2
            val baseline = cy + size * 0.36f
            if (i > 0) {
                paint.shader = null
                paint.color = WidgetPalette.withAlpha(s.palette.ink, 0.08f)
                canvas.drawRect(box.x, top, box.right, top + 0.6f, paint)
            }
            // Narrow rows use weekday names only ("Вт") — "Сегодня" would be truncated there.
            val label = if (narrow) s.format.weekday(s.format.localDate(day.time)) else s.format.dayLabel(day.time, s.now)
            WidgetType.draw(canvas, label, box.x, baseline, type.text(size, if (i == 0) s.palette.ink else s.palette.inkSoft, if (i == 0) 700 else 550), dayW - 4 * k, shadow = s.shadow)
            val gx = box.x + dayW + g / 2
            glyph(canvas, s, RectF(gx - g / 2, cy - g / 2, gx + g / 2, cy + g / 2), WeatherCondition.fromWmo(day.weatherCode), true)
            var cursor = gx + g / 2 + 4 * k
            if (!narrow && day.precipitationProbabilityMax >= 30) {
                WidgetType.draw(canvas, "${day.precipitationProbabilityMax}%", cursor, baseline, type.text(size * 0.82f, s.palette.rain, 600), 34 * k, shadow = s.shadow)
            }
            if (!narrow) cursor += 32 * k

            if (narrow) {
                val text = "${s.format.temperature(day.temperatureMax)} ${s.format.temperature(day.temperatureMin)}"
                WidgetType.draw(canvas, text, box.right, baseline, type.text(size, s.palette.ink, 600), box.right - cursor, Paint.Align.RIGHT, s.shadow)
                return@forEachIndexed
            }
            val maxText = s.format.temperature(day.temperatureMax)
            val minText = s.format.temperature(day.temperatureMin)
            val numW = 30 * k
            WidgetType.draw(canvas, minText, cursor + numW, baseline, type.text(size, s.palette.inkSoft, 550), numW, Paint.Align.RIGHT, s.shadow)
            WidgetType.draw(canvas, maxText, box.right, baseline, type.text(size, s.palette.ink, 650), numW, Paint.Align.RIGHT, s.shadow)
            val barLeft = cursor + numW + 7 * k
            val barRight = box.right - numW - 7 * k
            if (barRight - barLeft > 16 * k) {
                rangeBar(
                    canvas, s, barLeft, barRight, cy, lo, hi, day.temperatureMin, day.temperatureMax,
                    current = if (i == 0) s.moment.temperature else null,
                )
            }
        }
    }

    private fun rangeBar(
        canvas: Canvas, s: Scene, left: Float, right: Float, cy: Float,
        lo: Double, hi: Double, min: Double, max: Double, current: Double?,
    ) {
        val k = s.k
        val h = 5f * k
        val span = (hi - lo).coerceAtLeast(1.0)
        fun x(t: Double) = left + ((t - lo) / span).toFloat().coerceIn(0f, 1f) * (right - left)
        paint.shader = null
        paint.color = WidgetPalette.withAlpha(s.palette.ink, 0.13f)
        canvas.drawRoundRect(RectF(left, cy - h / 2, right, cy + h / 2), h / 2, h / 2, paint)
        val x0 = x(min)
        val x1 = max(x(max), x0 + h)
        paint.color = 0xFFFFFFFF.toInt()
        paint.color = if (s.palette.glyphTone == WeatherGlyphPainter.Tone.Mono) s.palette.ink else 0xFFFFFFFF.toInt()
        paint.shader = if (s.palette.glyphTone == WeatherGlyphPainter.Tone.Mono) {
            null
        } else {
            LinearGradient(x0, 0f, x1, 0f, TemperatureScale.colorFor(min).value, TemperatureScale.colorFor(max).value, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(x0, cy - h / 2, x1, cy + h / 2), h / 2, h / 2, paint)
        paint.shader = null
        if (current != null) {
            val cx = x(current)
            paint.color = WidgetPalette.withAlpha(0xFF000000.toInt(), 0.25f)
            canvas.drawCircle(cx, cy, h * 0.95f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, h * 0.72f, paint)
        }
    }

    private fun dailyColumns(canvas: Canvas, block: Block.DailyColumns, s: Scene) {
        val box = block.box
        val k = s.k
        val days = s.forecast.daysFrom(s.now).take(block.count)
        val colW = box.w / days.size
        val size = min(11.5f * k, colW * 0.28f)
        val g = min(colW * 0.6f, box.h * 0.34f)
        days.forEachIndexed { i, day ->
            val cx = box.x + colW * (i + 0.5f)
            WidgetType.draw(canvas, s.format.dayLabel(day.time, s.now), cx, box.y + size + 1 * k, type.text(size, s.palette.inkSoft, 550), colW - 2 * k, Paint.Align.CENTER, s.shadow)
            val gy = box.y + size + 4 * k
            glyph(canvas, s, RectF(cx - g / 2, gy, cx + g / 2, gy + g), WeatherCondition.fromWmo(day.weatherCode), true)
            WidgetType.draw(canvas, s.format.temperature(day.temperatureMax), cx, box.bottom - size - 5 * k, type.text(size * 1.1f, s.palette.ink, 650), colW, Paint.Align.CENTER, s.shadow)
            WidgetType.draw(canvas, s.format.temperature(day.temperatureMin), cx, box.bottom - 1 * k, type.text(size, s.palette.inkSoft, 500), colW, Paint.Align.CENTER, s.shadow)
        }
    }

    // endregion

    // region Details, nowcast, sun path

    private fun details(canvas: Canvas, block: Block.Details, s: Scene) {
        val box = block.box
        val k = s.k
        val gap = 6 * k
        val rows = (block.items.size + block.columns - 1) / block.columns
        val tileW = (box.w - gap * (block.columns - 1)) / block.columns
        val tileH = (box.h - gap * (rows - 1)) / rows
        block.items.forEachIndexed { i, kind ->
            val col = i % block.columns
            val row = i / block.columns
            val rect = RectF(box.x + col * (tileW + gap), box.y + row * (tileH + gap), box.x + col * (tileW + gap) + tileW, box.y + row * (tileH + gap) + tileH)
            paint.shader = null
            paint.color = s.palette.fill
            val r = min(12 * k, tileH * 0.32f)
            canvas.drawRoundRect(rect, r, r, paint)
            val (label, value) = detail(kind, s)
            val pad = 8 * k
            WidgetType.draw(canvas, label, rect.left + pad, rect.top + pad + 9 * k, type.text(10f * k, s.palette.inkSoft, 550), rect.width() - 2 * pad, shadow = s.shadow)
            val valueSize = type.fitSize(value, rect.width() - 2 * pad, 9f * k, min(16f * k, tileH * 0.36f)) { type.text(it, s.palette.ink, 650) }
            WidgetType.draw(canvas, value, rect.left + pad, rect.bottom - pad, type.text(valueSize, s.palette.ink, 650), rect.width() - 2 * pad, shadow = s.shadow)
            if (kind == DetailKind.Wind) {
                windArrow(canvas, rect.right - pad - 6 * k, rect.top + pad + 6 * k, 6 * k, s.moment.windDirection, s.palette.inkSoft)
            }
        }
    }

    private fun detail(kind: DetailKind, s: Scene): Pair<String, String> {
        val f = s.format
        val m = s.moment
        val ctx = context
        val today = s.forecast.dayAt(s.now)
        return when (kind) {
            DetailKind.FeelsLike -> ctx.getString(DsR.string.detail_feels_like) to f.temperature(m.apparentTemperature)
            DetailKind.Wind -> ctx.getString(DsR.string.detail_wind) to "${f.wind(m.windSpeed)} ${f.compass(m.windDirection)}"
            DetailKind.Humidity -> ctx.getString(DsR.string.detail_humidity) to f.percent(m.humidity)
            DetailKind.Precipitation -> ctx.getString(DsR.string.detail_precipitation) to
                (today?.let { "${f.precipitation(it.precipitationSum)} · ${it.precipitationProbabilityMax}%" } ?: f.precipitation(0.0))
            DetailKind.Uv -> ctx.getString(DsR.string.detail_uv) to "${m.uvIndex.roundToInt()} · ${f.uvLevel(m.uvIndex)}"
            DetailKind.Pressure -> ctx.getString(DsR.string.detail_pressure) to f.pressure(m.pressure)
            DetailKind.SunEvent -> {
                val sunset = today?.sunset
                val sunrise = today?.sunrise
                if (sunset != null && s.now < sunset && (sunrise == null || s.now >= sunrise)) {
                    ctx.getString(DsR.string.detail_sunset) to f.time(sunset)
                } else {
                    val next = s.forecast.daily.firstOrNull { (it.sunrise ?: 0) > s.now }?.sunrise
                    ctx.getString(DsR.string.detail_sunrise) to (next?.let(f::time) ?: "—")
                }
            }
            DetailKind.Air -> ctx.getString(DsR.string.detail_air) to (s.forecast.air?.let { "${it.europeanAqi ?: "—"} · ${f.airLevel(it.level)}" } ?: "—")
            DetailKind.Visibility -> ctx.getString(DsR.string.detail_visibility) to (m.visibility?.let(f::visibility) ?: "—")
        }
    }

    private fun windArrow(canvas: Canvas, cx: Float, cy: Float, r: Float, fromDegrees: Int, color: Int) {
        // Meteorological direction is where wind comes *from*; the arrow points where it goes.
        val a = Math.toRadians(fromDegrees + 180.0 - 90.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        path.reset()
        path.moveTo(cx + dx * r, cy + dy * r)
        path.lineTo(cx - dx * r * 0.7f - dy * r * 0.6f, cy - dy * r * 0.7f + dx * r * 0.6f)
        path.lineTo(cx - dx * r * 0.3f, cy - dy * r * 0.3f)
        path.lineTo(cx - dx * r * 0.7f + dy * r * 0.6f, cy - dy * r * 0.7f - dx * r * 0.6f)
        path.close()
        paint.shader = null
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun nowcast(canvas: Canvas, block: Block.Nowcast, s: Scene) {
        val box = block.box
        val k = s.k
        val slots = s.forecast.nowcast.filter { it.time > s.now - 900 }.take(8)
        if (slots.isEmpty()) return
        val title = s.headline?.takeIf { it is Headline.PrecipitationStarts || it is Headline.PrecipitationEnds || it is Headline.Continuing }
            ?.let { s.format.headline(it, s.now) }
            ?: s.format.condition(s.moment.condition, s.moment.isDay)
        val titleSize = 11.5f * k
        WidgetType.draw(canvas, title, box.x, box.y + titleSize, type.text(titleSize, s.palette.ink, 600), box.w * 0.7f, shadow = s.shadow)
        val chartTop = box.y + titleSize + 5 * k
        val chartBottom = box.bottom - 1 * k
        val chartH = chartBottom - chartTop
        if (chartH < 6 * k) return
        val maxMm = max(slots.maxOf { it.precipitation }, 0.5)
        val slotW = box.w / slots.size
        slots.forEachIndexed { i, slot ->
            val fx = box.x + i * slotW
            paint.shader = null
            paint.color = WidgetPalette.withAlpha(s.palette.ink, 0.1f)
            canvas.drawRoundRect(RectF(fx + 1.5f * k, chartTop, fx + slotW - 1.5f * k, chartBottom), 3 * k, 3 * k, paint)
            val v = (slot.precipitation / maxMm).toFloat().coerceIn(0f, 1f)
            if (v > 0.02f) {
                paint.color = WidgetPalette.withAlpha(s.palette.rain, 0.45f + v * 0.5f)
                canvas.drawRoundRect(RectF(fx + 1.5f * k, chartBottom - chartH * v, fx + slotW - 1.5f * k, chartBottom), 3 * k, 3 * k, paint)
            }
        }
        val hint = s.format.minutes(slots.size * 15)
        WidgetType.draw(canvas, "+$hint", box.right, box.y + titleSize, type.text(10f * k, s.palette.inkFaint, 500), box.w * 0.3f, Paint.Align.RIGHT, s.shadow)
    }

    private fun sunPath(canvas: Canvas, block: Block.SunPath, s: Scene) {
        val box = block.box
        val k = s.k
        val day = s.forecast.dayAt(s.now) ?: return
        val rise = day.sunrise ?: return
        val set = day.sunset ?: return
        val left = box.x + 16 * k
        val right = box.right - 16 * k
        val base = box.bottom - 16 * k
        val height = box.h - 26 * k
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * k
        paint.color = WidgetPalette.withAlpha(s.palette.ink, 0.25f)
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3 * k, 3 * k), 0f)
        path.reset()
        path.moveTo(left, base)
        path.cubicTo(left + (right - left) * 0.2f, base - height * 1.3f, right - (right - left) * 0.2f, base - height * 1.3f, right, base)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        canvas.drawRect(box.x, base, box.right, base + 0.7f, paint)
        val t = ((s.now - rise).toFloat() / (set - rise)).coerceIn(0f, 1f)
        val sx = left + (right - left) * t
        val sy = base - height * 4f * t * (1 - t) * 0.97f
        paint.color = s.palette.sky.sun.value
        paint.maskFilter = BlurMaskFilter(5f * k, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(sx, sy, 7f * k, paint)
        paint.maskFilter = null
        paint.color = 0xFFFFF4D6.toInt()
        canvas.drawCircle(sx, sy, 4f * k, paint)
        val p = type.text(10.5f * k, s.palette.inkSoft, 550)
        WidgetType.draw(canvas, s.format.time(rise), box.x, box.bottom - 2 * k, p, box.w / 2, shadow = s.shadow)
        WidgetType.draw(canvas, s.format.time(set), box.right, box.bottom - 2 * k, type.text(10.5f * k, s.palette.inkSoft, 550), box.w / 2, Paint.Align.RIGHT, s.shadow)
    }

    // endregion

    private fun drawEmptyState(canvas: Canvas, request: WidgetRenderRequest, palette: WidgetPalette, layout: WidgetLayout) {
        val w = request.widthDp
        val h = request.heightDp
        val k = layout.scale
        val message = context.getString(
            when (request.content.status) {
                WidgetContent.Status.NeedsLocation -> R.string.widget_needs_location
                WidgetContent.Status.Loading -> R.string.widget_loading
                else -> if (request.content.refreshing) R.string.widget_loading else R.string.widget_no_data
            },
        )
        val g = min(w, h) * if (min(w, h) < 110) 0.42f else 0.3f
        val textRoom = h - g - layout.padding * 2
        val showText = textRoom >= 16 * k && w >= 70 * k
        val gy = if (showText) (h - g - min(textRoom, 34 * k)) / 2 else (h - g) / 2
        glyphs.draw(canvas, WeatherCondition.PartlyCloudy, true, RectF((w - g) / 2, gy, (w + g) / 2, gy + g), WeatherGlyphPainter.Tone.Mono, palette.inkSoft)
        if (!showText) return
        val paint = TextPaint(type.text(12f * k, palette.inkSoft, 550))
        val width = (w - layout.padding * 2).toInt().coerceAtLeast(1)
        val staticLayout = StaticLayout.Builder.obtain(message, 0, message.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.withTranslation(layout.padding, gy + g + 6 * k) { staticLayout.draw(this) }
    }

    /** Catmull–Rom spline through [points] into [path] (as cubic Béziers). */
    private fun smoothPath(points: List<Pair<Float, Float>>) {
        path.reset()
        if (points.isEmpty()) return
        path.moveTo(points[0].first, points[0].second)
        for (i in 0 until points.size - 1) {
            val p0 = points[max(i - 1, 0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[min(i + 2, points.size - 1)]
            val c1x = p1.first + (p2.first - p0.first) / 6f
            val c1y = p1.second + (p2.second - p0.second) / 6f
            val c2x = p2.first - (p3.first - p1.first) / 6f
            val c2y = p2.second - (p3.second - p1.second) / 6f
            path.cubicTo(c1x, c1y, c2x, c2y, p2.first, p2.second)
        }
    }
}
