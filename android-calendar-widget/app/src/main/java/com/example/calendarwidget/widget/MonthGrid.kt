package com.example.calendarwidget.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.calendarwidget.MainActivity
import com.example.calendarwidget.data.CalendarEvent
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.data.WidgetSettings

/**
 * The month grid: a weekday header plus a 7×N grid of day "bubbles" with up to
 * three event dots. Cell maths and state colours mirror `renderVals()` (раздел 6.1).
 * Tapping an in-month day opens [MainActivity] focused on that day.
 */
@Composable
fun MonthGrid(
    settings: WidgetSettings,
    palette: WidgetPalette,
    year: Int,
    month: Int,
    events: Map<Int, List<CalendarEvent>>,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val firstMon = settings.firstDayMonday
    val offset = CalendarMath.leadingOffset(year, month, firstMon)
    val dim = CalendarMath.daysInMonth(year, month)
    val rows = CalendarMath.cellCount(year, month, firstMon) / 7

    val today = CalendarMath.today()
    val isCurrentMonth = today.year == year && today.month == month
    val selectedDay = if (isCurrentMonth) today.day else -1

    Column(modifier = modifier) {
        // Weekday labels
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            CalendarMath.weekdayLabels(firstMon).forEachIndexed { col, label ->
                val weekend = CalendarMath.isWeekendColumn(col, firstMon)
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            color = palette.provider(if (weekend) palette.weekendMuted else palette.muted),
                            fontSize = (if (compact) 8.5f else 10f).sp * settings.fontScale,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(if (compact) 2.dp else 4.dp))

        for (r in 0 until rows) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (col in 0 until 7) {
                    val dnum = r * 7 + col - offset + 1
                    val inMonth = dnum in 1..dim
                    val weekend = CalendarMath.isWeekendColumn(col, firstMon)
                    DayCell(
                        settings = settings,
                        palette = palette,
                        year = year,
                        month = month,
                        day = dnum,
                        inMonth = inMonth,
                        weekend = weekend,
                        selected = inMonth && dnum == selectedDay,
                        events = if (inMonth) events[dnum].orEmpty() else emptyList(),
                        compact = compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.DayCell(
    settings: WidgetSettings,
    palette: WidgetPalette,
    year: Int,
    month: Int,
    day: Int,
    inMonth: Boolean,
    weekend: Boolean,
    selected: Boolean,
    events: List<CalendarEvent>,
    compact: Boolean,
) {
    val bubble = if (compact) 22 else 30
    val bubbleRadius = (bubble * 0.34f)
    val numColor = when {
        !inMonth -> palette.outMonth
        selected -> palette.contrast
        weekend -> palette.weekendNum
        else -> palette.text
    }
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal

    var cellModifier = GlanceModifier.defaultWeight()
    if (inMonth) {
        val intent = Intent(LocalContext.current, MainActivity::class.java).apply {
            data = Uri.parse("calendarwidget://day/$year/$month/$day")
            putExtra(MainActivity.EXTRA_YEAR, year)
            putExtra(MainActivity.EXTRA_MONTH, month)
            putExtra(MainActivity.EXTRA_DAY, day)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        cellModifier = cellModifier.clickable(actionStartActivity(intent))
    }

    Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(bubble.dp)
                    .background(palette.provider(if (selected) palette.accent else Color.Transparent))
                    .cornerRadius(bubbleRadius.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (inMonth) day.toString() else "",
                    style = TextStyle(
                        color = palette.provider(numColor),
                        fontSize = (if (compact) 11f else 14.5f).sp * settings.fontScale,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Spacer(GlanceModifier.height(if (compact) 1.dp else 2.dp))
            DotsRow(palette, events, selected, compact)
        }
    }
}

@Composable
private fun DotsRow(
    palette: WidgetPalette,
    events: List<CalendarEvent>,
    selected: Boolean,
    compact: Boolean,
) {
    val dotSize = if (compact) 3 else 4
    Row(verticalAlignment = Alignment.CenterVertically) {
        events.take(3).forEachIndexed { i, e ->
            if (i > 0) Spacer(GlanceModifier.width(3.dp))
            val color = if (selected) Color.Black.copy(alpha = 0.42f) else Color(e.color)
            Box(
                modifier = GlanceModifier
                    .size(dotSize.dp)
                    .background(palette.provider(color))
                    .cornerRadius((dotSize / 2f).dp),
            ) {}
        }
    }
}
