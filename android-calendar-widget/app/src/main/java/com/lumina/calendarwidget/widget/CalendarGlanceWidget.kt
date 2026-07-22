package com.lumina.calendarwidget.widget

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.lumina.calendarwidget.R
import com.lumina.calendarwidget.calendar.CalendarModel
import com.lumina.calendarwidget.calendar.DayCell
import com.lumina.calendarwidget.calendar.EventRepository
import com.lumina.calendarwidget.calendar.MonthGrid
import com.lumina.calendarwidget.calendar.WeekRow
import com.lumina.calendarwidget.data.CalendarViewMode
import com.lumina.calendarwidget.data.Density
import com.lumina.calendarwidget.data.SettingsRepository
import com.lumina.calendarwidget.data.SelectedStyle
import com.lumina.calendarwidget.data.ThemeCatalog
import com.lumina.calendarwidget.data.ThemeColors
import com.lumina.calendarwidget.data.TapDayAction
import com.lumina.calendarwidget.data.TapHeaderAction
import com.lumina.calendarwidget.data.TodayStyle
import com.lumina.calendarwidget.data.WeekdayLabelFormat
import com.lumina.calendarwidget.data.WeekendOption
import com.lumina.calendarwidget.data.WidgetSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle

/**
 * The home-screen widget. A static snapshot re-rendered on day rollover, settings change, or tap.
 * [SizeMode.Exact] lets the content read [LocalSize] and adapt density / weekday labels to the
 * placement without any user interaction.
 */
class CalendarGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val settings = SettingsRepository(context).get()
        val systemDark = context.isSystemDark()
        val eventDays = if (settings.showEventIndicators) {
            EventRepository.eventDays(context, YearMonth.now())
        } else {
            emptySet()
        }
        provideContent {
            WidgetRoot(settings, systemDark, eventDays)
        }
    }
}

/* Resolved per-render sizing derived from settings + measured widget size. */
private data class RenderConfig(
    val padStart: Dp,
    val padTop: Dp,
    val padEnd: Dp,
    val padBottom: Dp,
    val headerSize: androidx.compose.ui.unit.TextUnit,
    val yearSize: androidx.compose.ui.unit.TextUnit,
    val weekdaySize: androidx.compose.ui.unit.TextUnit,
    val daySize: androidx.compose.ui.unit.TextUnit,
    val rowGap: Dp,
    val weekdayGap: Dp,
    val hairlineGap: Dp,
    val tileSize: Dp,
    val weekNumWidth: Dp,
    val weekdayFormat: WeekdayLabelFormat,
    val dropYear: Boolean,
)

@androidx.compose.runtime.Composable
private fun WidgetRoot(settings: WidgetSettings, systemDark: Boolean, eventDays: Set<Int>) {
    val context = LocalContext.current
    val size = LocalSize.current

    val colors = if (settings.dynamicColor) {
        dynamicThemeColors(context, systemDark) ?: ThemeCatalog.resolve(settings, systemDark)
    } else {
        ThemeCatalog.resolve(settings, systemDark)
    }

    val compact = size.height < 168.dp || size.width < 178.dp
    val effDensity = if (compact) Density.COMPACT else settings.density
    val ts = settings.fontScalePercent / 100f

    val weekdayFormat = if (settings.weekdayLabelFormat == WeekdayLabelFormat.SINGLE && size.width >= 320.dp) {
        WeekdayLabelFormat.TWO
    } else {
        settings.weekdayLabelFormat
    }

    val cfg = RenderConfig(
        padStart = if (effDensity == Density.COMPACT) 14.dp else if (effDensity == Density.SPACIOUS) 24.dp else 20.dp,
        padTop = if (effDensity == Density.COMPACT) 16.dp else if (effDensity == Density.SPACIOUS) 26.dp else 22.dp,
        padEnd = if (effDensity == Density.COMPACT) 14.dp else if (effDensity == Density.SPACIOUS) 24.dp else 20.dp,
        padBottom = if (effDensity == Density.COMPACT) 14.dp else if (effDensity == Density.SPACIOUS) 22.dp else 18.dp,
        headerSize = ((if (compact) 18f else 22f) * ts).sp,
        yearSize = (15f * ts).sp,
        weekdaySize = (11f * ts).sp,
        daySize = ((if (compact) 13f else 15f) * ts).sp,
        rowGap = when (effDensity) { Density.COMPACT -> 2.dp; Density.SPACIOUS -> 7.dp; else -> 4.dp },
        weekdayGap = when (effDensity) { Density.COMPACT -> 8.dp; Density.SPACIOUS -> 16.dp; else -> 12.dp },
        hairlineGap = when (effDensity) { Density.COMPACT -> 6.dp; Density.SPACIOUS -> 12.dp; else -> 10.dp },
        tileSize = when (effDensity) { Density.COMPACT -> 26.dp; Density.SPACIOUS -> 36.dp; else -> 32.dp },
        weekNumWidth = if (settings.showWeekNumbers) (if (compact) 16.dp else 20.dp) else 0.dp,
        weekdayFormat = weekdayFormat,
        dropYear = compact,
    )

    var mod = GlanceModifier.fillMaxSize().appWidgetBackground()
    val gradientRes = gradientDrawable(settings.gradient)
    mod = if (gradientRes != null) {
        mod.background(ImageProvider(gradientRes))
    } else {
        mod.background(colors.surface.provider())
    }
    mod = mod.cornerRadius(settings.cornerRadiusDp.dp)
        .padding(start = cfg.padStart, top = cfg.padTop, end = cfg.padEnd, bottom = cfg.padBottom)

    Box(modifier = mod) {
        when (settings.viewMode) {
            CalendarViewMode.AGENDA -> AgendaView(settings, colors, cfg, eventDays)
            else -> GridView(settings, colors, cfg, eventDays)
        }
    }
}

@androidx.compose.runtime.Composable
private fun GridView(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    eventDays: Set<Int>,
) {
    val locale = settings.resolvedLocale()
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val firstDay = settings.firstDayOfWeek.resolve(locale)

    val grid = CalendarModel.buildMonth(
        yearMonth = yearMonth,
        today = today,
        firstDayOfWeek = firstDay,
        weekendDays = settings.weekend.days,
        weekCount = if (settings.lockSixRows) CalendarModel.WeekCount.FIXED_SIX else CalendarModel.WeekCount.AUTO,
    )

    val weeks: List<WeekRow> = when (settings.viewMode) {
        CalendarViewMode.WEEK -> listOf(weekContaining(grid, today))
        CalendarViewMode.TWO_WEEKS -> twoWeeks(grid, today)
        else -> grid.weeks
    }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(settings, colors, cfg, yearMonth, locale)
        Spacer(GlanceModifier.height(cfg.weekdayGap))
        WeekdayRow(settings, colors, cfg, grid.weekdayOrder, locale)
        if (settings.showHairline) {
            Spacer(GlanceModifier.height(6.dp))
            Box(
                GlanceModifier.fillMaxWidth().height(1.dp).background(colors.gridLine.provider())
            ) {}
        }
        Spacer(GlanceModifier.height(cfg.hairlineGap))
        weeks.forEachIndexed { i, week ->
            // defaultWeight() must be created here, inside the Column (ColumnScope), then handed down.
            WeekRowView(settings, colors, cfg, week, eventDays, yearMonth, GlanceModifier.fillMaxWidth().defaultWeight())
            if (i != weeks.lastIndex) {
                if (settings.showGridLines) {
                    Spacer(GlanceModifier.height(cfg.rowGap.value.div(2f).dp))
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.gridLine.provider())) {}
                    Spacer(GlanceModifier.height(cfg.rowGap.value.div(2f).dp))
                } else {
                    Spacer(GlanceModifier.height(cfg.rowGap))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Header(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    yearMonth: YearMonth,
    locale: java.util.Locale,
) {
    if (settings.headerFormat == com.lumina.calendarwidget.data.HeaderFormat.HIDDEN) return
    val text = headerText(settings.headerFormat, yearMonth, locale, cfg.dropYear)
    val horizontal = when (settings.headerAlignment) {
        com.lumina.calendarwidget.data.HeaderAlignment.START -> Alignment.Start
        com.lumina.calendarwidget.data.HeaderAlignment.CENTER -> Alignment.CenterHorizontally
        com.lumina.calendarwidget.data.HeaderAlignment.END -> Alignment.End
    }
    val headerAction = when (settings.tapHeaderAction) {
        TapHeaderAction.JUMP_TODAY -> actionRunCallback<JumpTodayAction>()
        TapHeaderAction.OPEN_APP -> actionStartActivity(openAppIntent(LocalContext.current))
        TapHeaderAction.NOTHING -> null
    }
    var rowMod = GlanceModifier.fillMaxWidth()
    if (headerAction != null) rowMod = rowMod.clickable(headerAction)

    Row(
        modifier = rowMod,
        horizontalAlignment = horizontal,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.month,
            style = TextStyle(
                color = colors.headerText.provider(),
                fontSize = cfg.headerSize,
                fontWeight = settings.headerWeight.toGlance(),
            ),
        )
        if (text.year != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = text.year,
                style = TextStyle(
                    color = colors.muted.provider(),
                    fontSize = cfg.yearSize,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeekdayRow(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    order: List<java.time.DayOfWeek>,
    locale: java.util.Locale,
) {
    if (cfg.weekdayFormat == WeekdayLabelFormat.HIDDEN) return
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        if (cfg.weekNumWidth.value > 0f) Spacer(GlanceModifier.width(cfg.weekNumWidth))
        order.forEach { dow ->
            val isWeekend = settings.highlightWeekends && dow in settings.weekend.days
            Text(
                text = weekdayLabel(dow, cfg.weekdayFormat, settings.weekdayLabelCase, locale),
                style = TextStyle(
                    color = (if (isWeekend) colors.weekendText else colors.muted).provider(),
                    fontSize = cfg.weekdaySize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeekRowView(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    week: WeekRow,
    eventDays: Set<Int>,
    yearMonth: YearMonth,
    rowModifier: GlanceModifier,
) {
    Row(modifier = rowModifier) {
        if (cfg.weekNumWidth.value > 0f) {
            Box(
                modifier = GlanceModifier.width(cfg.weekNumWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = week.weekNumber.toString(),
                    style = TextStyle(
                        color = colors.muted.provider(),
                        fontSize = (cfg.weekdaySize.value * 0.92f).sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        week.days.forEach { cell ->
            // defaultWeight() created here, inside the Row (RowScope), then handed to the cell.
            DayCellView(settings, colors, cfg, cell, eventDays, yearMonth, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@androidx.compose.runtime.Composable
private fun DayCellView(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    cell: DayCell,
    eventDays: Set<Int>,
    yearMonth: YearMonth,
    cellModifier: GlanceModifier,
) {
    if (!settings.showOtherMonthDays && !cell.inCurrentMonth) {
        Box(modifier = cellModifier) {}
        return
    }

    val selectedDate = if (settings.selectedEpochDay >= 0) LocalDate.ofEpochDay(settings.selectedEpochDay) else null
    val isSelected = selectedDate != null && selectedDate == cell.date && !cell.isToday
    val hasEvent = settings.showEventIndicators &&
        cell.inCurrentMonth &&
        YearMonth.from(cell.date) == yearMonth &&
        eventDays.contains(cell.dayOfMonth)

    val todayFilled = cell.isToday && settings.todayStyle in FILLED_TODAY
    val numberColor = when {
        todayFilled -> colors.todayText
        cell.isToday && settings.todayStyle == TodayStyle.BOLD_NUMBER -> colors.accent
        cell.isToday && settings.todayStyle == TodayStyle.OUTLINE_RING -> colors.onSurface
        !cell.inCurrentMonth -> colors.muted
        cell.isWeekend && settings.highlightWeekends && settings.weekend != WeekendOption.NONE -> colors.weekendText
        else -> colors.onSurface
    }
    val numberWeight = when {
        cell.isToday && (todayFilled || settings.todayStyle == TodayStyle.BOLD_NUMBER) -> FontWeight.Bold
        else -> settings.dayNumberWeight.toGlance()
    }

    val roundedSquareRadius = (settings.cornerRadiusDp / 2).coerceIn(6, 15).dp
    val circleRadius = cfg.tileSize.value.div(2f).dp

    var cellMod = cellModifier
    dayAction(LocalContext.current, settings, cell.date)?.let { cellMod = cellMod.clickable(it) }

    Box(modifier = cellMod, contentAlignment = Alignment.Center) {
        // Background marker (today or selection) sits behind the number.
        when {
            cell.isToday && settings.todayStyle in FILLED_TODAY -> {
                val radius = if (settings.todayStyle == TodayStyle.FILLED_CIRCLE) circleRadius else roundedSquareRadius
                Box(
                    modifier = GlanceModifier.size(cfg.tileSize)
                        .background(colors.todayBg.provider())
                        .cornerRadius(radius),
                ) {}
            }
            cell.isToday && settings.todayStyle == TodayStyle.OUTLINE_RING -> {
                RingImage(colors.accent, cfg.tileSize)
            }
            isSelected && settings.selectedStyle == SelectedStyle.FILLED -> {
                Box(
                    modifier = GlanceModifier.size(cfg.tileSize)
                        .background(colors.gridLine.provider())
                        .cornerRadius(roundedSquareRadius),
                ) {}
            }
            isSelected && settings.selectedStyle == SelectedStyle.OUTLINE_RING -> {
                RingImage(colors.accent, cfg.tileSize)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = TextStyle(
                    color = numberColor.provider(),
                    fontSize = cfg.daySize,
                    fontWeight = numberWeight,
                    textAlign = TextAlign.Center,
                ),
            )
            if (hasEvent && !cell.isToday) {
                Spacer(GlanceModifier.height(2.dp))
                EventIndicator(settings, colors)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RingImage(color: Long, size: Dp) {
    Image(
        provider = ImageProvider(R.drawable.bg_ring_circle),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color.provider()),
        modifier = GlanceModifier.size(size),
    )
}

@androidx.compose.runtime.Composable
private fun EventIndicator(settings: WidgetSettings, colors: ThemeColors) {
    when (settings.eventIndicatorStyle) {
        com.lumina.calendarwidget.data.EventIndicatorStyle.BAR ->
            Box(GlanceModifier.width(10.dp).height(3.dp).background(colors.accent.provider()).cornerRadius(2.dp)) {}
        else ->
            Box(GlanceModifier.size(4.dp).background(colors.accent.provider()).cornerRadius(2.dp)) {}
    }
}

/* ---- Agenda view ----------------------------------------------------------------------- */

@androidx.compose.runtime.Composable
private fun AgendaView(
    settings: WidgetSettings,
    colors: ThemeColors,
    cfg: RenderConfig,
    eventDays: Set<Int>,
) {
    val locale = settings.resolvedLocale()
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val dates = (0 until 12).map { today.plusDays(it.toLong()) }

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Header(settings, colors, cfg, yearMonth, locale)
        Spacer(GlanceModifier.height(cfg.weekdayGap))
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(dates.size) { index ->
                val date = dates[index]
                val hasEvent = eventDays.contains(date.dayOfMonth) && YearMonth.from(date) == yearMonth
                AgendaRow(colors, cfg, date, locale, date == today, hasEvent, settings)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AgendaRow(
    colors: ThemeColors,
    cfg: RenderConfig,
    date: LocalDate,
    locale: java.util.Locale,
    isToday: Boolean,
    hasEvent: Boolean,
    settings: WidgetSettings,
) {
    val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    var mod = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
    dayAction(LocalContext.current, settings, date)?.let { mod = mod.clickable(it) }
    Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier.size(cfg.tileSize)
                .then(if (isToday) GlanceModifier.background(colors.todayBg.provider()).cornerRadius((cfg.tileSize.value / 2f).dp) else GlanceModifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = (if (isToday) colors.todayText else colors.onSurface).provider(),
                    fontSize = cfg.daySize,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Spacer(GlanceModifier.width(12.dp))
        Text(
            text = weekday,
            style = TextStyle(color = colors.onSurface.provider(), fontSize = cfg.daySize, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (hasEvent) EventIndicator(settings, colors)
    }
}

/* ---- Helpers --------------------------------------------------------------------------- */

private val FILLED_TODAY = setOf(TodayStyle.ROUNDED_SQUARE, TodayStyle.FILLED_CIRCLE, TodayStyle.INLAID_JEWEL)

private fun dayAction(context: Context, settings: WidgetSettings, date: LocalDate): Action? {
    return when (settings.tapDayAction) {
        TapDayAction.OPEN_CALENDAR -> {
            val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            actionStartActivity(openCalendarIntent(millis))
        }
        TapDayAction.OPEN_APP -> actionStartActivity(openAppIntent(context))
        TapDayAction.SELECT -> actionRunCallback<SelectDateAction>(
            actionParametersOf(EpochDayKey to date.toEpochDay())
        )
        TapDayAction.NOTHING -> null
    }
}

private fun weekContaining(grid: MonthGrid, day: LocalDate): WeekRow =
    grid.weeks.firstOrNull { row -> row.days.any { it.date == day } } ?: grid.weeks.first()

private fun twoWeeks(grid: MonthGrid, day: LocalDate): List<WeekRow> {
    val idx = grid.weeks.indexOfFirst { row -> row.days.any { it.date == day } }.coerceAtLeast(0)
    return grid.weeks.subList(idx, (idx + 2).coerceAtMost(grid.weeks.size))
}
