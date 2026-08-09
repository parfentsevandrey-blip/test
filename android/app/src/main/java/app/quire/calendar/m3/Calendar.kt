package app.quire.calendar.m3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.quire.calendar.core.DayLoad
import app.quire.calendar.core.MonthModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month grid: seven columns, six rows, every row the same height whatever the month, so the
 * geometry never shifts underneath a swipe.
 *
 * Everything is a Material role rather than a colour: today is a filled `primary` circle, the
 * selected day a `secondaryContainer` one, marks take `tertiary` unless the event's own calendar
 * colour is available and wanted. That is what makes the grid follow the wallpaper on Android 12
 * and up without a single colour of its own.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    cells: List<LocalDate>,
    weekdayLabels: List<String>,
    weekdayOrder: List<java.time.DayOfWeek>,
    today: LocalDate,
    selected: LocalDate,
    loads: Map<LocalDate, DayLoad>,
    settings: CalendarModel.Settings,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = rememberLocale()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (settings.weekNumbers) Spacer(Modifier.size(WeekNumberWidth))
            weekdayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (settings.dimWeekends && MonthModel.isWeekend(weekdayOrder[index])) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }

        for (row in 0 until MonthModel.ROWS) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (settings.weekNumbers) {
                    Text(
                        text = MonthModel.weekOfYear(
                            cells[row * MonthModel.COLUMNS],
                            locale,
                        ).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.size(WeekNumberWidth, CellHeight)
                            .padding(top = 14.dp),
                    )
                }
                for (column in 0 until MonthModel.COLUMNS) {
                    val date = cells[row * MonthModel.COLUMNS + column]
                    DayCell(
                        date = date,
                        month = month,
                        today = today,
                        selected = selected,
                        load = loads[date],
                        settings = settings,
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    load: DayLoad?,
    settings: CalendarModel.Settings,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inMonth = YearMonth.from(date) == month
    val isToday = date == today
    val isSelected = date == selected
    val scheme = MaterialTheme.colorScheme

    // Today wins the filled treatment; a selection elsewhere gets the quieter container. Both are
    // Material roles, so the pair stays legible whatever the wallpaper turns them into.
    val disc = when {
        isToday -> scheme.primary
        isSelected -> scheme.secondaryContainer
        else -> Color.Transparent
    }
    val onDisc = when {
        isToday -> scheme.onPrimary
        isSelected -> scheme.onSecondaryContainer
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        settings.dimWeekends && MonthModel.isWeekend(date.dayOfWeek) -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }

    val count = load?.count ?: 0
    // The density tint is the surface stepping up rather than a colour of its own, so a busy day
    // reads as raised paper instead of a stain.
    val ground = if (settings.density && count > 0 && !isToday && !isSelected) {
        scheme.surfaceContainerHighest.copy(alpha = (0.25f + 0.15f * count).coerceAtMost(0.9f))
    } else {
        Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(CellHeight)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(ground)
            .clickable(enabled = inMonth || settings.showAdjacent) { onPick(date) },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(DiscSize).clip(CircleShape).background(disc),
        ) {
            if (inMonth || settings.showAdjacent) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onDisc,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (count > 0 && (inMonth || settings.showAdjacent)) {
                val colours = load?.colours ?: IntArray(0)
                val shown = minOf(count, 3)
                repeat(shown) { index ->
                    val mark = if (settings.colouredMarks && index < colours.size) {
                        Color(colours[index])
                    } else if (isToday) {
                        scheme.onPrimary
                    } else {
                        scheme.tertiary
                    }
                    Box(Modifier.size(MarkSize).clip(CircleShape).background(mark))
                }
            }
        }
    }
}

/**
 * A month small enough that twelve fit on a screen: the year view's tile. It carries the day
 * numbers rather than a heat block, because a year you cannot read the dates in is a picture of
 * a year rather than one.
 */
@Composable
fun MiniMonth(
    month: YearMonth,
    cells: List<LocalDate>,
    weekdayInitials: List<String>,
    today: LocalDate,
    loads: Map<LocalDate, DayLoad>,
    onOpen: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = rememberLocale()
    val isThisMonth = YearMonth.from(today) == month
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { onOpen(month) }
            .padding(6.dp),
    ) {
        Text(
            text = MonthModel.monthName(month, locale),
            style = MaterialTheme.typography.titleSmall,
            color = if (isThisMonth) scheme.primary else scheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayInitials.forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // The weeks take whatever height the tile has left, so a year fills its page instead of
        // sitting in a band at the top of one.
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            for (row in 0 until MonthModel.ROWS) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (column in 0 until MonthModel.COLUMNS) {
                        val date = cells[row * MonthModel.COLUMNS + column]
                        val inMonth = YearMonth.from(date) == month
                        val isToday = date == today
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(MiniDiscSize)
                                    .clip(CircleShape)
                                    .background(
                                        if (isToday) scheme.primary else Color.Transparent,
                                    ),
                            ) {
                                Text(
                                    text = if (inMonth) date.dayOfMonth.toString() else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isToday -> scheme.onPrimary
                                        loads[date] != null -> scheme.onSurface
                                        else -> scheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CellHeight = 56.dp
private val DiscSize = 34.dp
private val MarkSize = 4.dp
private val WeekNumberWidth = 24.dp
private val MiniDiscSize = 18.dp
