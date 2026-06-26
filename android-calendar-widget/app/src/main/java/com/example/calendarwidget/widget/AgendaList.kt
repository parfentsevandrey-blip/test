package com.example.calendarwidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
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
import androidx.glance.text.TextStyle
import com.example.calendarwidget.data.CalendarEvent
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.data.WidgetSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Лента дел дня" shown under the grid in the LARGE bucket (раздел 6.1).
 * Hairline divider, caps title («26 ИЮНЯ · СЕГОДНЯ»), then up to three rows.
 */
@Composable
fun AgendaList(
    settings: WidgetSettings,
    palette: WidgetPalette,
    month: Int,
    day: Int,
    events: List<CalendarEvent>,
) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 11.dp)) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.provider(palette.hairline)),
        ) {}
        Spacer(GlanceModifier.height(9.dp))
        Text(
            text = "$day ${CalendarMath.MONTHS_GENITIVE[month]} · Сегодня".uppercase(Locale.getDefault()),
            style = TextStyle(
                color = palette.provider(palette.muted),
                fontSize = 10.sp * settings.fontScale,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (events.isEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Свободный день",
                style = TextStyle(
                    color = palette.provider(palette.muted),
                    fontSize = 12.sp * settings.fontScale,
                ),
            )
        } else {
            events.take(3).forEach { event ->
                Spacer(GlanceModifier.height(7.dp))
                AgendaRow(settings, palette, event, timeFmt)
            }
        }
    }
}

@Composable
private fun AgendaRow(
    settings: WidgetSettings,
    palette: WidgetPalette,
    event: CalendarEvent,
    timeFmt: SimpleDateFormat,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(7.dp)
                .background(palette.provider(palette.categoryColor(event.category)))
                .cornerRadius(3.dp),
        ) {}
        Spacer(GlanceModifier.width(9.dp))
        Text(
            text = event.title,
            maxLines = 1,
            style = TextStyle(
                color = palette.provider(palette.text),
                fontSize = 12.5f.sp * settings.fontScale,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = if (event.allDay) "весь день" else timeFmt.format(Date(event.start)),
            maxLines = 1,
            style = TextStyle(
                color = palette.provider(palette.muted),
                fontSize = 11.sp * settings.fontScale,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
