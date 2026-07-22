package com.lumina.calendarwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumina.calendarwidget.calendar.CalendarModel
import com.lumina.calendarwidget.calendar.DayCell
import com.lumina.calendarwidget.calendar.WeekRow
import com.lumina.calendarwidget.data.CalendarViewMode
import com.lumina.calendarwidget.data.Density
import com.lumina.calendarwidget.data.EventIndicatorStyle
import com.lumina.calendarwidget.data.GradientOption
import com.lumina.calendarwidget.data.HeaderAlignment
import com.lumina.calendarwidget.data.HeaderFormat
import com.lumina.calendarwidget.data.SelectedStyle
import com.lumina.calendarwidget.data.ThemeCatalog
import com.lumina.calendarwidget.data.ThemeColors
import com.lumina.calendarwidget.data.TodayStyle
import com.lumina.calendarwidget.data.WeekdayLabelFormat
import com.lumina.calendarwidget.data.WeekendOption
import com.lumina.calendarwidget.data.WidgetSettings
import com.lumina.calendarwidget.widget.headerText
import com.lumina.calendarwidget.widget.weekdayLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle

/** Sample days used only in the preview to show what event indicators look like. */
private val SAMPLE_EVENT_DAYS = setOf(4, 9, 16, 23, 28)

private fun Long.c(): Color = Color(this)

fun gradientBrush(option: GradientOption): Brush? = when (option) {
    GradientOption.NONE -> null
    GradientOption.SUNRISE -> Brush.linearGradient(listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)))
    GradientOption.OCEAN -> Brush.linearGradient(listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)))
    GradientOption.FOREST -> Brush.linearGradient(listOf(Color(0xFF134E5E), Color(0xFF71B280)))
    GradientOption.TWILIGHT -> Brush.linearGradient(listOf(Color(0xFF4B2E83), Color(0xFF7B4BC9), Color(0xFFC471ED)))
    GradientOption.SLATE -> Brush.linearGradient(listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)))
    GradientOption.OBSIDIAN -> Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
}

/**
 * A pixel-close Compose replica of the Glance widget, driven by the same [WidgetSettings] and
 * [CalendarModel]. Lets the user see every change land instantly before it reaches the home screen.
 */
@Composable
fun WidgetPreview(settings: WidgetSettings, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = ThemeCatalog.resolve(settings, dark)
    val shape = RoundedCornerShape(settings.cornerRadiusDp.dp)
    val brush = gradientBrush(settings.gradient)

    val pad = when (settings.density) {
        Density.COMPACT -> 14.dp
        Density.SPACIOUS -> 22.dp
        else -> 18.dp
    }

    var base = modifier.clip(shape)
    base = if (brush != null) base.background(brush) else base.background(colors.surface.c())
    base = base.border(1.dp, colors.gridLine.c().copy(alpha = 0.6f), shape)

    Box(base.padding(start = pad, top = pad + 2.dp, end = pad, bottom = pad)) {
        if (settings.viewMode == CalendarViewMode.AGENDA) {
            PreviewAgenda(settings, colors)
        } else {
            PreviewGrid(settings, colors)
        }
    }
}

@Composable
private fun PreviewGrid(settings: WidgetSettings, colors: ThemeColors) {
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
        CalendarViewMode.WEEK -> listOf(grid.weeks.first { r -> r.days.any { it.date == today } })
        CalendarViewMode.TWO_WEEKS -> {
            val i = grid.weeks.indexOfFirst { r -> r.days.any { it.date == today } }.coerceAtLeast(0)
            grid.weeks.subList(i, (i + 2).coerceAtMost(grid.weeks.size))
        }
        else -> grid.weeks
    }
    val ts = settings.fontScalePercent / 100f

    Column(Modifier.fillMaxSize()) {
        PreviewHeader(settings, colors, yearMonth, locale, ts)
        Spacer(Modifier.height(if (settings.density == Density.SPACIOUS) 14.dp else 10.dp))
        if (settings.weekdayLabelFormat != WeekdayLabelFormat.HIDDEN) {
            Row(Modifier.fillMaxWidth()) {
                if (settings.showWeekNumbers) Spacer(Modifier.width(18.dp))
                grid.weekdayOrder.forEach { dow ->
                    val weekend = settings.highlightWeekends && dow in settings.weekend.days
                    Text(
                        text = weekdayLabel(dow, settings.weekdayLabelFormat, settings.weekdayLabelCase, locale),
                        color = if (weekend) colors.weekendText.c() else colors.muted.c(),
                        fontSize = (11f * ts).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (settings.showHairline) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.gridLine.c()))
        }
        Spacer(Modifier.height(if (settings.density == Density.COMPACT) 6.dp else 10.dp))
        weeks.forEachIndexed { i, week ->
            if (settings.showGridLines && i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.gridLine.c()))
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (settings.showWeekNumbers) {
                    Box(Modifier.width(18.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = week.weekNumber.toString(),
                            color = colors.muted.c(),
                            fontSize = (10f * ts).sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                week.days.forEach { cell ->
                    PreviewDayCell(settings, colors, cell, yearMonth, ts, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    settings: WidgetSettings,
    colors: ThemeColors,
    yearMonth: YearMonth,
    locale: java.util.Locale,
    ts: Float,
) {
    if (settings.headerFormat == HeaderFormat.HIDDEN) return
    val text = headerText(settings.headerFormat, yearMonth, locale, dropYear = false)
    val arrangement = when (settings.headerAlignment) {
        HeaderAlignment.START -> Arrangement.Start
        HeaderAlignment.CENTER -> Arrangement.Center
        HeaderAlignment.END -> Arrangement.End
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.month,
            color = colors.headerText.c(),
            fontSize = (22f * ts).sp,
            fontWeight = if (settings.headerWeight == com.lumina.calendarwidget.data.HeaderWeight.BOLD) FontWeight.Bold else FontWeight.Medium,
        )
        if (text.year != null) {
            Spacer(Modifier.width(6.dp))
            Text(text = text.year, color = colors.muted.c(), fontSize = (15f * ts).sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun PreviewDayCell(
    settings: WidgetSettings,
    colors: ThemeColors,
    cell: DayCell,
    yearMonth: YearMonth,
    ts: Float,
    modifier: Modifier,
) {
    if (!settings.showOtherMonthDays && !cell.inCurrentMonth) {
        Box(modifier.fillMaxSize())
        return
    }
    val selectedDate = if (settings.selectedEpochDay >= 0) LocalDate.ofEpochDay(settings.selectedEpochDay) else null
    val isSelected = selectedDate == cell.date && !cell.isToday
    val hasEvent = settings.showEventIndicators && cell.inCurrentMonth &&
        YearMonth.from(cell.date) == yearMonth && cell.dayOfMonth in SAMPLE_EVENT_DAYS

    val tileSize = when (settings.density) {
        Density.COMPACT -> 24.dp
        Density.SPACIOUS -> 32.dp
        else -> 28.dp
    }
    val filled = cell.isToday && settings.todayStyle in setOf(TodayStyle.ROUNDED_SQUARE, TodayStyle.FILLED_CIRCLE, TodayStyle.INLAID_JEWEL)
    val tileShape = if (settings.todayStyle == TodayStyle.FILLED_CIRCLE) RoundedCornerShape(50)
    else RoundedCornerShape((settings.cornerRadiusDp / 2).coerceIn(6, 15).dp)

    val numberColor = when {
        filled -> colors.todayText.c()
        cell.isToday && settings.todayStyle == TodayStyle.BOLD_NUMBER -> colors.accent.c()
        cell.isToday && settings.todayStyle == TodayStyle.OUTLINE_RING -> colors.onSurface.c()
        !cell.inCurrentMonth -> colors.muted.c()
        cell.isWeekend && settings.highlightWeekends && settings.weekend != WeekendOption.NONE -> colors.weekendText.c()
        else -> colors.onSurface.c()
    }
    val weight = if (cell.isToday && (filled || settings.todayStyle == TodayStyle.BOLD_NUMBER)) FontWeight.Bold
    else when (settings.dayNumberWeight) {
        com.lumina.calendarwidget.data.FontWeightOption.BOLD -> FontWeight.Bold
        com.lumina.calendarwidget.data.FontWeightOption.MEDIUM -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            filled -> Box(Modifier.size(tileSize).clip(tileShape).background(colors.todayBg.c()))
            cell.isToday && settings.todayStyle == TodayStyle.OUTLINE_RING ->
                Box(Modifier.size(tileSize).border(2.dp, colors.accent.c(), RoundedCornerShape(50)))
            isSelected && settings.selectedStyle == SelectedStyle.FILLED ->
                Box(Modifier.size(tileSize).clip(tileShape).background(colors.gridLine.c()))
            isSelected && settings.selectedStyle == SelectedStyle.OUTLINE_RING ->
                Box(Modifier.size(tileSize).border(2.dp, colors.accent.c(), RoundedCornerShape(50)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.dayOfMonth.toString(),
                color = numberColor,
                fontSize = (15f * ts).sp,
                fontWeight = weight,
                textAlign = TextAlign.Center,
            )
            if (hasEvent && !cell.isToday) {
                Spacer(Modifier.height(2.dp))
                if (settings.eventIndicatorStyle == EventIndicatorStyle.BAR) {
                    Box(Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent.c()))
                } else {
                    Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(colors.accent.c()))
                }
            }
        }
    }
}

@Composable
private fun PreviewAgenda(settings: WidgetSettings, colors: ThemeColors) {
    val locale = settings.resolvedLocale()
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    Column(Modifier.fillMaxSize()) {
        PreviewHeader(settings, colors, yearMonth, locale, settings.fontScalePercent / 100f)
        Spacer(Modifier.height(10.dp))
        (0 until 6).forEach { i ->
            val date = today.plusDays(i.toLong())
            val isToday = i == 0
            val hasEvent = settings.showEventIndicators && date.dayOfMonth in SAMPLE_EVENT_DAYS && YearMonth.from(date) == yearMonth
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isToday) colors.todayBg.c() else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (isToday) colors.todayText.c() else colors.onSurface.c(),
                        fontSize = 14.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
                    color = colors.onSurface.c(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (hasEvent) Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(colors.accent.c()))
            }
        }
    }
}
