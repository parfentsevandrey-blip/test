package app.papersky.weather.widget.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import app.papersky.weather.R
import app.papersky.weather.core.model.Day
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Hour
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.core.text.Narrator
import app.papersky.weather.core.text.NoteText
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.scene.ScenePalette
import app.papersky.weather.widget.RefreshAction
import app.papersky.weather.widget.TapAction
import app.papersky.weather.widget.WidgetBackground
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetData
import app.papersky.weather.widget.layout.Block
import app.papersky.weather.widget.layout.ChipKind
import app.papersky.weather.widget.layout.ChipStyle
import app.papersky.weather.widget.layout.ColumnPlan
import app.papersky.weather.widget.layout.DailyBlock
import app.papersky.weather.widget.layout.DetailsBlock
import app.papersky.weather.widget.layout.GapBlock
import app.papersky.weather.widget.layout.HeaderBlock
import app.papersky.weather.widget.layout.HeroBlock
import app.papersky.weather.widget.layout.HeroStyle
import app.papersky.weather.widget.layout.HourlyBlock
import app.papersky.weather.widget.layout.HourlyRowsBlock
import app.papersky.weather.widget.layout.InfoBlock
import app.papersky.weather.widget.layout.InfoLine
import app.papersky.weather.widget.layout.LINE
import app.papersky.weather.widget.layout.Mode
import app.papersky.weather.widget.layout.PlanInput
import app.papersky.weather.widget.layout.StripPlan
import app.papersky.weather.widget.layout.WFont
import app.papersky.weather.widget.layout.WhisperBlock
import app.papersky.weather.widget.layout.WidgetLayoutPlanner
import app.papersky.weather.widget.layout.WidgetPlan
import kotlin.math.roundToInt

/** Resolved ink for text on the sky and on paper panels, per background style. */
private class Look(
    val sky: Int,
    val skySoft: Int,
    val panel: Int,
    val panelSoft: Int,
    val accent: Int,
    val precip: Int,
    val track: Int,
    val shadowOnSky: Boolean,
    val glyphSky: GlyphColors,
    val glyphPanel: GlyphColors,
) {
    fun ink(onPanel: Boolean) = if (onPanel) panel else sky
    fun soft(onPanel: Boolean) = if (onPanel) panelSoft else skySoft
    fun shadow(onPanel: Boolean) = !onPanel && shadowOnSky
    fun glyphs(onPanel: Boolean) = if (onPanel) glyphPanel else glyphSky

    companion object {
        fun of(config: WidgetConfig, p: ScenePalette): Look = when (config.background) {
            WidgetBackground.Scene -> Look(
                sky = p.onSky, skySoft = ColorMath.withAlpha(p.onSky, 0.76f),
                panel = p.paperInk, panelSoft = ColorMath.withAlpha(p.paperInk, 0.62f),
                accent = p.accent, precip = rainInk(p, onPaper = true), track = ColorMath.withAlpha(p.paperInk, 0.13f),
                shadowOnSky = p.isDarkSky,
                glyphSky = GlyphColors.from(p, onPaper = false), glyphPanel = GlyphColors.from(p, onPaper = true),
            )
            WidgetBackground.Paper -> Look(
                sky = p.paperInk, skySoft = ColorMath.withAlpha(p.paperInk, 0.62f),
                panel = p.paperInk, panelSoft = ColorMath.withAlpha(p.paperInk, 0.62f),
                accent = p.accent, precip = rainInk(p, onPaper = true), track = ColorMath.withAlpha(p.paperInk, 0.13f),
                shadowOnSky = false,
                glyphSky = GlyphColors.from(p, onPaper = true), glyphPanel = GlyphColors.from(p, onPaper = true),
            )
            WidgetBackground.Clear -> if (config.opacity < 0.45f) {
                val white = 0xFFFFFFFF.toInt()
                Look(
                    sky = white, skySoft = ColorMath.withAlpha(white, 0.82f), panel = white, panelSoft = ColorMath.withAlpha(white, 0.82f),
                    accent = p.accent, precip = 0xFFA8D0F5.toInt(), track = ColorMath.withAlpha(white, 0.25f), shadowOnSky = true,
                    glyphSky = GlyphColors.from(p, onPaper = false, darkOverride = true), glyphPanel = GlyphColors.from(p, onPaper = false, darkOverride = true),
                )
            } else {
                Look(
                    sky = p.onSky, skySoft = ColorMath.withAlpha(p.onSky, 0.76f), panel = p.onSky, panelSoft = ColorMath.withAlpha(p.onSky, 0.76f),
                    accent = p.accent, precip = rainInk(p, onPaper = false), track = ColorMath.withAlpha(p.onSky, 0.2f), shadowOnSky = p.isDarkSky,
                    glyphSky = GlyphColors.from(p, onPaper = false), glyphPanel = GlyphColors.from(p, onPaper = false),
                )
            }
        }

        private fun rainInk(p: ScenePalette, onPaper: Boolean): Int {
            val bg = if (onPaper) p.paper else p.skyMid
            return if (ColorMath.luminance(bg) < 0.35f) 0xFF9CC4EC.toInt() else 0xFF3F77B3.toInt()
        }
    }
}

/** Everything formatted once per composition. */
private class Ctx(
    val context: Context,
    val config: WidgetConfig,
    val plan: WidgetPlan,
    val look: Look,
    val fmt: WeatherFormat,
    val forecast: Forecast,
    val moment: WeatherMoment,
    val nowSec: Long,
    val placeName: String,
    val hours: List<Hour>,
    val days: List<Day>,
    val whisper: List<String>,
    val refreshing: Boolean,
    val stale: Boolean,
) {
    val panels = config.background == WidgetBackground.Scene
    fun glyph(g: Glyph, sizeDp: Float, onPanel: Boolean, rotation: Float = 0f) =
        ImageProvider(WidgetArt.glyph(context, g, sizeDp, look.glyphs(onPanel), rotation))

    fun conditionGlyph(code: Int, isDay: Boolean): Glyph =
        Glyph.of(app.papersky.weather.core.model.Condition.fromWmo(code), isDay)

    fun hourLabel(h: Hour, index: Int): String = if (index == 0) context.getString(R.string.widget_now) else fmt.hour(h.time)

    fun info(line: InfoLine): String = when (line) {
        InfoLine.Location -> placeName
        InfoLine.Condition -> fmt.condition(moment.condition)
        InfoLine.HiLo -> forecast.dayAt(nowSec)?.let { "↑${fmt.temp(it.tempMax)}  ↓${fmt.temp(it.tempMin)}" } ?: ""
        InfoLine.FeelsLike -> context.getString(R.string.widget_feels, fmt.temp(moment.feelsLike))
    }
}

@Composable
fun WidgetContent(config: WidgetConfig, data: WidgetData, nowMillis: Long, openApp: Intent) {
    val context = LocalContext.current
    val size = LocalSize.current
    val forecast = data.forecast
    val nowSec = nowMillis / 1000
    val moment = forecast?.momentAt(nowSec, preferLive = forecast.ageMinutes(nowMillis) < 75)
    val tap: Action = if (config.tap == TapAction.Refresh) actionRunCallback<RefreshAction>() else actionStartActivity(openApp)

    if (forecast == null || moment == null) {
        EmptyWidget(config, data, tap)
        return
    }

    val units = data.settings.resolvedUnits(context.resources.configuration.locales[0])
    val fmt = WeatherFormat(context, units, forecast.zone)
    val scene = SceneState.from(moment, SceneState.seedFor(forecast.placeId), forecast)
    val palette = Palettes.resolve(config.palette, scene, context)
    val look = Look.of(config, palette)
    val hours = forecast.hoursFrom(nowSec, 12, config.hourStep.coerceIn(1, 3))
    val days = forecast.daysFrom(nowSec, 10)
    val heroText = fmt.temp(moment.temperature)
    val plan = WidgetLayoutPlanner.plan(
        PlanInput(
            width = size.width.value,
            height = size.height.value,
            config = config,
            heroText = heroText,
            clockSample = fmt.time(nowSec),
            hourLabelSample = hours.drop(1).maxByOrNull { fmt.hour(it.time).length }?.let { fmt.hour(it.time) } ?: "22",
            nowLabel = context.getString(R.string.widget_now),
            dayLabelSample = days.take(10).map { fmt.dayName(it.date + 43_200, nowSec, short = true) }
                .maxByOrNull { PaintMeasure.get(context).width(it, WFont.BodyBold, 12f) } ?: "Tmrw",
            hourlyAvailable = hours.size,
            dailyAvailable = days.size,
            fontScale = context.resources.configuration.fontScale,
            measure = PaintMeasure.get(context),
        ),
    )
    val placeName = data.place?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.here)
    val stale = forecast.ageMinutes(nowMillis) > 180
    val whisper = if (config.showWhisper) {
        Narrator.notes(forecast, nowSec).map { NoteText.resolve(it, fmt, context.resources) }.distinct().take(5)
            .ifEmpty { listOf(fmt.condition(moment.condition)) }
    } else emptyList()

    val ctx = Ctx(context, config, plan, look, fmt, forecast, moment, nowSec, placeName, hours, days, whisper, data.refreshing, stale)
    val charts = chartsFor(plan, hours)
    val fx = if (config.animate && config.background == WidgetBackground.Scene && plan.mode != Mode.Micro) {
        val hero = plan.heroRect()?.let { android.graphics.RectF(it.left, it.top, it.right, it.bottom) }
        WidgetFx.plan(scene, palette, plan, WidgetFx.sunDp(scene, plan, WidgetArt.sceneOptions(plan, scene, nowSec).copy(keepClear = listOfNotNull(hero))))
    } else FxPlan.None
    val art = remember(plan, config, scene, palette, charts.size, nowSec / 60) {
        WidgetArt.background(context, plan, config, scene, palette, charts, nowSec, fx)
    }

    var root = GlanceModifier.fillMaxSize().appWidgetBackground()
    root = if (config.corner < 0) root.cornerRadius(android.R.dimen.system_app_widget_background_radius) else root.cornerRadius(config.corner.dp)
    root = if (art != null) {
        root.background(ImageProvider(art), alpha = config.opacity.coerceIn(0.25f, 1f), contentScale = ContentScale.FillBounds)
    } else {
        root.background(ColorProvider(Color(ColorMath.withAlpha(palette.skyTop, config.opacity.coerceIn(0f, 1f) * 0.9f))))
    }
    val a11y = context.getString(R.string.widget_a11y, placeName, heroText, fmt.condition(moment.condition))
    root = root.clickable(tap).semantics { contentDescription = a11y }

    Box(root) {
        // Moving weather sits between the painted scene and the text.
        fx.layers.forEach { FxIsland(it) }
        when (plan.mode) {
            Mode.Strip -> StripLayout(ctx, plan.strip!!)
            else -> Row(GlanceModifier.fillMaxSize()) {
                plan.columns.forEachIndexed { i, col ->
                    if (i == 0) Spacer(GlanceModifier.width(col.x.dp))
                    else Spacer(GlanceModifier.width((col.x - plan.columns[i - 1].let { it.x + it.width }).dp))
                    ColumnLayout(ctx, col)
                }
            }
        }
    }
}

// ---- Columns -------------------------------------------------------------------------------------

/** A block's root: its own height plus the breathing room above it, as padding. */
private fun blockMod(top: Float, height: Float) = GlanceModifier.fillMaxWidth().height((top + height).dp).padding(top = top.dp)

@Composable
private fun ColumnLayout(ctx: Ctx, col: ColumnPlan) {
    // Gaps become top padding of the next block: Glance keeps at most 10 children per column.
    Column(GlanceModifier.width(col.width.dp).fillMaxHeight().padding(top = col.y.dp)) {
        var gap = 0f
        for (block in col.blocks) {
            if (block is GapBlock) {
                gap += block.height
                continue
            }
            BlockView(ctx, col, block, gap)
            gap = 0f
        }
    }
}

@Composable
private fun BlockView(ctx: Ctx, col: ColumnPlan, block: Block, top: Float) {
    when (block) {
        is GapBlock -> Unit
        is HeaderBlock -> Header(ctx, block, top)
        is HeroBlock -> Hero(ctx, block, top)
        is InfoBlock -> WText(
            ctx.info(block.line), WFont.BodyBold, block.textSize, ctx.look.soft(false),
            blockMod(top, block.height), TextAlign.Center, shadow = ctx.look.shadow(false),
        )
        is WhisperBlock -> WWhisper(
            ctx.whisper, block.textSize, ctx.look.ink(false),
            blockMod(top, block.height), shadow = ctx.look.shadow(false),
        )
        is HourlyBlock -> HourlyStrip(ctx, block, top)
        is HourlyRowsBlock -> HourlyRows(ctx, block, top)
        is DetailsBlock -> DetailsRow(ctx, block, top)
        is DailyBlock -> DailyRows(ctx, block, top)
    }
}

@Composable
private fun Header(ctx: Ctx, b: HeaderBlock, top: Float) {
    val look = ctx.look
    Row(blockMod(top, b.height), verticalAlignment = Alignment.CenterVertically) {
        if (b.showClock) {
            WClock(b.textSize * 1.2f, look.ink(false), ctx.forecast.zone.id, GlanceModifier.defaultWeight().height(b.height.dp), shadow = look.shadow(false))
        } else if (b.showLocation) {
            Image(ctx.glyph(Glyph.Pin, b.iconSize, false), null, GlanceModifier.size(b.iconSize.dp))
            Spacer(GlanceModifier.width(3.dp))
            WText(ctx.placeName, WFont.BodyBold, b.textSize, look.ink(false), GlanceModifier.defaultWeight().height(b.height.dp), shadow = look.shadow(false))
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        val time = when {
            ctx.stale -> ctx.context.getString(R.string.widget_stale, ctx.fmt.time(ctx.forecast.fetchedAt / 1000))
            b.showTime -> ctx.fmt.time(ctx.forecast.fetchedAt / 1000)
            else -> null
        }
        if (time != null) {
            Spacer(GlanceModifier.width(6.dp))
            WText(time, WFont.Body, b.textSize * 0.92f, look.soft(false), GlanceModifier.height(b.height.dp), TextAlign.End, shadow = look.shadow(false))
        }
        if (b.showRefresh) {
            Spacer(GlanceModifier.width(6.dp))
            RefreshButton(ctx, b.iconSize * 1.15f)
        }
    }
}

@Composable
private fun RefreshButton(ctx: Ctx, size: Float) {
    if (ctx.refreshing) {
        CircularProgressIndicator(GlanceModifier.size(size.dp), color = ColorProvider(Color(ctx.look.ink(false))))
    } else {
        Image(
            ctx.glyph(Glyph.Refresh, size, false),
            ctx.context.getString(R.string.widget_refresh),
            GlanceModifier.size(size.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

@Composable
private fun Hero(ctx: Ctx, b: HeroBlock, top: Float) {
    val look = ctx.look
    val temp = ctx.fmt.temp(ctx.moment.temperature)
    val glyph = ctx.conditionGlyph(ctx.moment.code, ctx.moment.isDay)
    val tempH = (b.tempSize * LINE).coerceAtMost(b.height)
    when (b.style) {
        HeroStyle.TempOnly -> Row(blockMod(top, b.height), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            WText(temp, WFont.DisplayLight, b.tempSize, look.ink(false), GlanceModifier.fillMaxWidth().height(tempH.dp), TextAlign.Center, shadow = look.shadow(false))
        }
        HeroStyle.Stacked -> Column(blockMod(top, b.height), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            if (b.glyphSize > 0) Image(ctx.glyph(glyph, b.glyphSize, false), null, GlanceModifier.size(b.glyphSize.dp))
            WText(temp, WFont.DisplayLight, b.tempSize, look.ink(false), GlanceModifier.fillMaxWidth().height(tempH.dp), TextAlign.Center, shadow = look.shadow(false))
            for (line in b.infoLines) {
                WText(ctx.info(line), WFont.BodyBold, b.infoSize, look.soft(false), GlanceModifier.fillMaxWidth().height((b.infoSize * LINE).dp), TextAlign.Center, shadow = look.shadow(false))
            }
        }
        HeroStyle.Row -> Row(blockMod(top, b.height), verticalAlignment = Alignment.CenterVertically) {
            WText(temp, WFont.DisplayLight, b.tempSize, look.ink(false), GlanceModifier.height(tempH.dp), shadow = look.shadow(false))
            if (b.glyphSize > 0) {
                Spacer(GlanceModifier.width(4.dp))
                Image(ctx.glyph(glyph, b.glyphSize, false), null, GlanceModifier.size(b.glyphSize.dp))
            }
            if (b.infoLines.isNotEmpty()) {
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    b.infoLines.forEachIndexed { i, line ->
                        WText(
                            ctx.info(line), if (i == 0) WFont.BodyBold else WFont.Body, if (i == 0) b.infoSize * 1.05f else b.infoSize,
                            if (i == 0) look.ink(false) else look.soft(false),
                            GlanceModifier.fillMaxWidth().height((b.infoSize * LINE * 1.08f).dp), shadow = look.shadow(false),
                        )
                    }
                }
            }
        }
        HeroStyle.Clock -> Row(blockMod(top, b.height), verticalAlignment = Alignment.CenterVertically) {
            WClock(b.clockSize, look.ink(false), ctx.forecast.zone.id, GlanceModifier.height((b.clockSize * LINE).coerceAtMost(b.height).dp), shadow = look.shadow(false))
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (b.glyphSize > 0) Image(ctx.glyph(glyph, b.glyphSize, false), null, GlanceModifier.size(b.glyphSize.dp))
                    Spacer(GlanceModifier.width(3.dp))
                    WText(temp, WFont.Display, b.tempSize, look.ink(false), GlanceModifier.height((b.tempSize * LINE).dp), TextAlign.End, shadow = look.shadow(false))
                }
                for (line in b.infoLines) {
                    WText(ctx.info(line), WFont.Body, b.infoSize, look.soft(false), GlanceModifier.height((b.infoSize * LINE).dp), TextAlign.End, shadow = look.shadow(false))
                }
            }
        }
            HeroStyle.ClockStacked -> Column(blockMod(top, b.height), verticalAlignment = Alignment.CenterVertically) {
            WClock(b.clockSize, look.ink(false), ctx.forecast.zone.id, GlanceModifier.height((b.clockSize * LINE).dp), shadow = look.shadow(false))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (b.glyphSize > 0) Image(ctx.glyph(glyph, b.glyphSize, false), null, GlanceModifier.size(b.glyphSize.dp))
                Spacer(GlanceModifier.width(3.dp))
                WText(temp, WFont.Display, b.tempSize, look.ink(false), GlanceModifier.height((b.tempSize * LINE).dp), shadow = look.shadow(false))
                for (line in b.infoLines) {
                    Spacer(GlanceModifier.width(8.dp))
                    WText(ctx.info(line), WFont.Body, b.infoSize, look.soft(false), GlanceModifier.defaultWeight().height((b.infoSize * LINE).dp), shadow = look.shadow(false))
                }
            }
        }
}
}

// ---- Dense sections --------------------------------------------------------------------------

private fun offsets(temps: List<Double>, band: Float): FloatArray {
    if (temps.isEmpty()) return FloatArray(0)
    val min = temps.min()
    val max = temps.max()
    return FloatArray(temps.size) { i -> if (max - min < 0.4) band / 2 else ((max - temps[i]) / (max - min) * band).toFloat() }
}

/** Where the hourly temperature dots sit, so the painted curve passes right under each label. */
private fun chartsFor(plan: WidgetPlan, hours: List<Hour>): List<ChartSpec> = plan.columns.flatMap { col ->
    col.blocks.filterIsInstance<HourlyBlock>().mapNotNull { b ->
        val top = plan.topOf(b) ?: return@mapNotNull null
        val shown = hours.take(b.count)
        if (shown.size < 2) return@mapNotNull null
        val off = offsets(shown.map { it.temperature }, b.band)
        val dotY = FloatArray(shown.size) { top + b.bandTop + off[it] + b.tempLine + 2.5f }
        val bottom = top + b.height - 3f
        ChartSpec(
            left = col.x + b.innerPad,
            top = top,
            width = col.width - 2 * b.innerPad,
            height = bottom - top,
            pointsY = dotY,
            bars = if (b.barHeight > 0) FloatArray(shown.size) { shown[it].precipProbability / 100f } else null,
            barsTop = bottom - b.barHeight,
        )
    }
}

@Composable
private fun HourlyStrip(ctx: Ctx, b: HourlyBlock, top: Float) {
    val onPanel = ctx.panels
    val look = ctx.look
    val shown = ctx.hours.take(b.count)
    val off = offsets(shown.map { it.temperature }, b.band)
    Row(blockMod(top, b.height).padding(horizontal = b.innerPad.dp)) {
        shown.forEachIndexed { i, h ->
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(GlanceModifier.height(b.padTop.dp))
                WText(
                    ctx.hourLabel(h, i), if (i == 0) WFont.BodyBold else WFont.Body, b.labelSize,
                    if (i == 0) look.ink(onPanel) else look.soft(onPanel),
                    GlanceModifier.fillMaxWidth().height(b.labelLine.dp), TextAlign.Center, shadow = look.shadow(onPanel),
                )
                Spacer(GlanceModifier.height(b.gap.dp))
                Image(ctx.glyph(ctx.conditionGlyph(h.code, h.isDay), b.glyphSize, onPanel), null, GlanceModifier.size(b.glyphSize.dp))
                Spacer(GlanceModifier.height((b.gap + off[i]).dp))
                WText(
                    ctx.fmt.temp(h.temperature), WFont.Display, b.tempSize, look.ink(onPanel),
                    GlanceModifier.fillMaxWidth().height(b.tempLine.dp), TextAlign.Center, shadow = look.shadow(onPanel),
                )
            }
        }
    }
}

@Composable
private fun HourlyRows(ctx: Ctx, b: HourlyRowsBlock, top: Float) {
    val onPanel = ctx.panels
    val look = ctx.look
    Column(blockMod(top, b.height).padding(horizontal = b.innerPad.dp, vertical = 3.dp)) {
        ctx.hours.take(b.rows).forEachIndexed { i, h ->
            Row(GlanceModifier.fillMaxWidth().height(b.rowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
                WText(ctx.hourLabel(h, i), WFont.Body, b.textSize * 0.9f, look.soft(onPanel), GlanceModifier.defaultWeight().height(b.rowHeight.dp), shadow = look.shadow(onPanel))
                if (b.showGlyph) {
                    Image(ctx.glyph(ctx.conditionGlyph(h.code, h.isDay), b.glyphSize, onPanel), null, GlanceModifier.size(b.glyphSize.dp))
                    Spacer(GlanceModifier.width(4.dp))
                }
                WText(ctx.fmt.temp(h.temperature), WFont.Display, b.textSize, look.ink(onPanel), GlanceModifier.height(b.rowHeight.dp), TextAlign.End, shadow = look.shadow(onPanel))
            }
        }
    }
}

private data class DetailItem(val glyph: Glyph, val text: String, val rotation: Float = 0f)

private fun detailItems(ctx: Ctx): List<DetailItem> {
    val m = ctx.moment
    val f = ctx.fmt
    return listOfNotNull(
        DetailItem(Glyph.Wind, f.wind(m.windSpeed), rotation = (m.windDirection + 180f) % 360f),
        DetailItem(Glyph.Umbrella, f.percent(m.precipProbability)),
        DetailItem(Glyph.Drop, f.percent(m.humidity)),
        if (m.uvIndex >= 1) DetailItem(Glyph.Uv, "UV ${m.uvIndex.roundToInt()}") else null,
        DetailItem(Glyph.Gauge, f.pressureNumber(m.pressure)),
        m.visibility?.let { DetailItem(Glyph.Eye, f.visibility(it)) },
    )
}

@Composable
private fun DetailsRow(ctx: Ctx, b: DetailsBlock, top: Float) {
    val onPanel = ctx.panels
    val look = ctx.look
    Row(blockMod(top, b.height).padding(horizontal = b.innerPad.dp), verticalAlignment = Alignment.CenterVertically) {
        detailItems(ctx).take(b.count).forEach { item ->
            Row(GlanceModifier.defaultWeight().height(b.height.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Image(ctx.glyph(item.glyph, b.glyphSize, onPanel, item.rotation), null, GlanceModifier.size(b.glyphSize.dp))
                Spacer(GlanceModifier.width(3.dp))
                WText(item.text, WFont.BodyBold, b.textSize, look.ink(onPanel), GlanceModifier.height(b.height.dp), shadow = look.shadow(onPanel))
            }
        }
    }
}

@Composable
private fun DailyRows(ctx: Ctx, b: DailyBlock, top: Float) {
    val onPanel = ctx.panels
    val look = ctx.look
    val shown = ctx.days.take(b.rows)
    val lo = shown.minOf { it.tempMin }
    val hi = shown.maxOf { it.tempMax }
    val span = (hi - lo).coerceAtLeast(1.0)
    val pv = ((b.height - b.rows * b.rowHeight) / 2).coerceAtLeast(0f)
    Column(blockMod(top, b.height).padding(horizontal = b.innerPad.dp, vertical = pv.dp)) {
        shown.forEach { d ->
            Row(GlanceModifier.fillMaxWidth().height(b.rowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
                val dayName = if (b.shortDays) ctx.fmt.weekdayShort(d.date + 43_200) else ctx.fmt.dayName(d.date + 43_200, ctx.nowSec, short = true)
                if (b.compact) {
                    WText(dayName, WFont.BodyBold, b.textSize * 1.05f, look.ink(onPanel), GlanceModifier.defaultWeight().height(b.rowHeight.dp), shadow = look.shadow(onPanel))
                    if (b.showGlyph) Image(ctx.glyph(ctx.conditionGlyph(d.code, true), b.glyphSize, onPanel), null, GlanceModifier.size(b.glyphSize.dp))
                    WText(
                        if (b.showMin) "${ctx.fmt.temp(d.tempMax)} ${ctx.fmt.temp(d.tempMin)}" else ctx.fmt.temp(d.tempMax),
                        WFont.Display, b.textSize, look.ink(onPanel), GlanceModifier.height(b.rowHeight.dp), TextAlign.End, shadow = look.shadow(onPanel), fill = false,
                    )
                } else {
                    WText(dayName, WFont.BodyBold, b.textSize * 1.05f, look.ink(onPanel), GlanceModifier.width(b.dayWidth.dp).height(b.rowHeight.dp), shadow = look.shadow(onPanel))
                    Image(ctx.glyph(ctx.conditionGlyph(d.code, true), b.glyphSize, onPanel), null, GlanceModifier.size(b.glyphSize.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    if (b.precipWidth > 0) {
                        WText(
                            if (d.precipProbability >= 20) ctx.fmt.percent(d.precipProbability) else "",
                            WFont.BodyBold, b.textSize * 0.82f, look.precip,
                            GlanceModifier.width(b.precipWidth.dp).height(b.rowHeight.dp), shadow = look.shadow(onPanel),
                        )
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    WText(ctx.fmt.temp(d.tempMin), WFont.Display, b.textSize, look.soft(onPanel), GlanceModifier.width(b.tempWidth.dp).height(b.rowHeight.dp), TextAlign.End, shadow = look.shadow(onPanel))
                    if (b.barWidth > 0) {
                        Spacer(GlanceModifier.width(6.dp))
                        RangeBar(b.barWidth, ((d.tempMin - lo) / span).toFloat(), ((d.tempMax - lo) / span).toFloat(), look)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    WText(ctx.fmt.temp(d.tempMax), WFont.Display, b.textSize, look.ink(onPanel), GlanceModifier.width(b.tempWidth.dp).height(b.rowHeight.dp), TextAlign.End, shadow = look.shadow(onPanel))
                }
            }
        }
    }
}

@Composable
private fun RangeBar(width: Float, from: Float, to: Float, look: Look) {
    val h = 5f
    val start = (from * width).coerceIn(0f, width - h)
    val len = ((to - from) * width).coerceAtLeast(h)
    Box(GlanceModifier.width(width.dp).height(h.dp).cornerRadius((h / 2).dp).background(ColorProvider(Color(look.track)))) {
        Row(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.width(start.dp))
            Box(GlanceModifier.width(len.coerceAtMost(width - start).dp).fillMaxHeight().cornerRadius((h / 2).dp).background(ColorProvider(Color(look.accent)))) {}
        }
    }
}

// ---- Strip -----------------------------------------------------------------------------------

@Composable
private fun StripLayout(ctx: Ctx, s: StripPlan) {
    val look = ctx.look
    val plan = ctx.plan
    val ch = plan.height - 2 * plan.pad
    Row(GlanceModifier.fillMaxSize().padding(horizontal = plan.pad.dp, vertical = plan.pad.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.clockWidth > 0) {
            WClock(s.clockSize, look.ink(false), ctx.forecast.zone.id, GlanceModifier.width(s.clockWidth.dp).height((s.clockSize * LINE).coerceAtMost(ch).dp), shadow = look.shadow(false))
            Spacer(GlanceModifier.width((s.gap * 2).dp))
        }
        if (s.glyphSize > 0) {
            Image(ctx.glyph(ctx.conditionGlyph(ctx.moment.code, ctx.moment.isDay), s.glyphSize, false), null, GlanceModifier.size(s.glyphSize.dp))
            Spacer(GlanceModifier.width(s.gap.dp))
        }
        WText(ctx.fmt.temp(ctx.moment.temperature), WFont.DisplayLight, s.tempSize, look.ink(false), GlanceModifier.height((s.tempSize * LINE).coerceAtMost(ch).dp), shadow = look.shadow(false))
        Spacer(GlanceModifier.width(s.gap.dp))
        if (s.infoWidth > 0) {
            Column(GlanceModifier.width(s.infoWidth.dp), verticalAlignment = Alignment.CenterVertically) {
                s.infoLines.forEachIndexed { i, line ->
                    WText(
                        ctx.info(line), if (i == 0) WFont.BodyBold else WFont.Body, s.infoSize,
                        if (i == 0) look.ink(false) else look.soft(false),
                        GlanceModifier.fillMaxWidth().height((s.infoSize * LINE).dp), shadow = look.shadow(false),
                    )
                }
            }
            Spacer(GlanceModifier.width(s.gap.dp))
        }
        if (s.chips > 0) Chips(ctx, s, ch)
    }
}

@Composable
private fun RowScope.Chips(ctx: Ctx, s: StripPlan, ch: Float) {
    val onPanel = ctx.panels
    val look = ctx.look
    Row(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        if (s.chipKind == ChipKind.Hourly) {
            ctx.hours.take(s.chips).forEachIndexed { i, h ->
                Chip(ctx, s, ch, ctx.hourLabel(h, i), ctx.conditionGlyph(h.code, h.isDay), ctx.fmt.temp(h.temperature), onPanel, look, first = i == 0)
            }
        } else {
            ctx.days.take(s.chips).forEachIndexed { i, d ->
                Chip(ctx, s, ch, ctx.fmt.dayName(d.date + 43_200, ctx.nowSec, short = true), ctx.conditionGlyph(d.code, true), ctx.fmt.temp(d.tempMax), onPanel, look, first = i == 0)
            }
        }
    }
}

@Composable
private fun RowScope.Chip(ctx: Ctx, s: StripPlan, ch: Float, label: String, glyph: Glyph, temp: String, onPanel: Boolean, look: Look, first: Boolean) {
    Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        if (s.chipStyle != ChipStyle.TempOnly) {
            WText(
                label, if (first) WFont.BodyBold else WFont.Body, s.chipLabelSize, if (first) look.ink(onPanel) else look.soft(onPanel),
                GlanceModifier.fillMaxWidth().height((s.chipLabelSize * LINE).dp), TextAlign.Center, shadow = look.shadow(onPanel),
            )
        }
        if (s.chipStyle == ChipStyle.Full && s.chipGlyphSize > 0) {
            Image(ctx.glyph(glyph, s.chipGlyphSize, onPanel), null, GlanceModifier.size(s.chipGlyphSize.dp))
        }
        WText(
            temp, WFont.Display, s.chipTempSize, look.ink(onPanel),
            GlanceModifier.fillMaxWidth().height((s.chipTempSize * LINE).dp), TextAlign.Center, shadow = look.shadow(onPanel),
        )
    }
}

// ---- Empty -----------------------------------------------------------------------------------

@Composable
private fun EmptyWidget(config: WidgetConfig, data: WidgetData, tap: Action) {
    val context = LocalContext.current
    val size = LocalSize.current
    val p = Palettes.resolve(config.palette, SceneState(), context)
    val ink = p.paperInk
    val small = size.width.value < 150 || size.height.value < 90
    val title = context.getString(if (data.place == null) R.string.widget_empty_title else R.string.widget_loading)
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(ColorProvider(Color(p.paper))).clickable(tap).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(WidgetArt.glyph(context, Glyph.SunCloud, if (small) 26f else 40f, GlyphColors.from(p, onPaper = true))), null, GlanceModifier.size((if (small) 26 else 40).dp))
            WText(title, WFont.BodyBold, if (small) 12f else 15f, ink, GlanceModifier.fillMaxWidth().height((if (small) 20 else 26).dp), TextAlign.Center)
            if (!small) {
                WText(context.getString(R.string.widget_empty_body), WFont.Body, 11f, ColorMath.withAlpha(ink, 0.65f), GlanceModifier.fillMaxWidth().height(16.dp), TextAlign.Center)
            }
            if (data.refreshing) {
                Spacer(GlanceModifier.height(6.dp))
                CircularProgressIndicator(GlanceModifier.size(18.dp), color = ColorProvider(Color(p.accent)))
            }
        }
    }
}
