package com.example.calendarwidget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.example.calendarwidget.data.CalendarEvent
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.data.CalendarRepository
import com.example.calendarwidget.data.ColorUtils
import com.example.calendarwidget.data.SettingsRepository
import com.example.calendarwidget.data.WidgetSettings
import com.example.calendarwidget.ui.theme.Manrope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class WeekDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val label: String,
    val isToday: Boolean,
    val hasEvents: Boolean,
)

private data class DayData(val events: List<CalendarEvent>, val week: List<WeekDay>)

private val FULL_WEEKDAYS = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье",
)

@Composable
fun DayScreen(
    year: Int,
    month: Int,
    day: Int,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSelect: (Int, Int, Int) -> Unit,
    onChangeMonth: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val settings by SettingsRepository(context).settings.collectAsState(initial = WidgetSettings())
    val accent = ColorUtils.composeColor(settings.accent)
    val onAccent = Color(ColorUtils.contrastOn(ColorUtils.parse(settings.accent)))
    val today = CalendarMath.today()
    val textMuted = Color.White.copy(alpha = 0.42f)

    val data by produceState(
        initialValue = DayData(emptyList(), emptyList()),
        year, month, day, permissionGranted, settings.firstDayMonday,
    ) {
        value = withContext(Dispatchers.IO) {
            loadDayData(context, year, month, day, settings.firstDayMonday)
        }
    }

    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = topInset + 8.dp, start = 18.dp, end = 18.dp, bottom = 96.dp),
        ) {
            TopBar(
                title = "${CalendarMath.MONTHS[month]} $year",
                onPrev = { onChangeMonth(-1) },
                onNext = { onChangeMonth(1) },
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.height(18.dp))
            WeekStrip(
                week = data.week,
                selYear = year,
                selMonth = month,
                selDay = day,
                accent = accent,
                onAccent = onAccent,
                textMuted = textMuted,
                onSelect = onSelect,
            )
            Spacer(Modifier.height(26.dp))
            BigDate(
                day = day,
                month = month,
                isToday = year == today.year && month == today.month && day == today.day,
                year = year,
                accent = accent,
                onAccent = onAccent,
                textMuted = textMuted,
            )
            Spacer(Modifier.height(22.dp))
            EventsSection(
                events = data.events,
                permissionGranted = permissionGranted,
                onRequestPermission = onRequestPermission,
                accent = accent,
                textMuted = textMuted,
            )
        }

        // FAB «+» — раздел 6.4
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp)
                .shadow(18.dp, RoundedCornerShape(20.dp), spotColor = accent, ambientColor = accent)
                .clip(RoundedCornerShape(20.dp))
                .background(accent)
                .clickable { /* New-event entry point (out of scope for this handoff) */ },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = onAccent, fontSize = 30.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun TopBar(title: String, onPrev: () -> Unit, onNext: () -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        CircleButton("‹", onPrev)
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        CircleButton("›", onNext)
        Spacer(Modifier.width(8.dp))
        CircleButton("⚙", onOpenSettings)
    }
}

@Composable
private fun CircleButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
    }
}

@Composable
private fun WeekStrip(
    week: List<WeekDay>,
    selYear: Int,
    selMonth: Int,
    selDay: Int,
    accent: Color,
    onAccent: Color,
    textMuted: Color,
    onSelect: (Int, Int, Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEach { wd ->
            val selected = wd.year == selYear && wd.month == selMonth && wd.day == selDay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).clickable { onSelect(wd.year, wd.month, wd.day) },
            ) {
                Text(wd.label, color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .then(if (selected) Modifier.background(accent) else Modifier)
                        .then(
                            if (wd.isToday && !selected) {
                                Modifier.border(1.6.dp, accent, RoundedCornerShape(14.dp))
                            } else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        wd.day.toString(),
                        color = if (selected) onAccent else Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (selected || wd.isToday) FontWeight.Bold else FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (wd.hasEvents) accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun BigDate(
    day: Int,
    month: Int,
    isToday: Boolean,
    year: Int,
    accent: Color,
    onAccent: Color,
    textMuted: Color,
) {
    Column {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                if (isToday) "СЕГОДНЯ" else "ВЫБРАНО",
                color = onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.toString(),
                color = Color.White,
                fontSize = 60.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Manrope,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    weekdayName(year, month, day),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    CalendarMath.MONTHS_GENITIVE[month],
                    color = textMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EventsSection(
    events: List<CalendarEvent>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    accent: Color,
    textMuted: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "СОБЫТИЯ",
            color = textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.weight(1f))
        if (events.isNotEmpty()) {
            Text(eventCountLabel(events.size), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(12.dp))

    when {
        !permissionGranted -> PermissionCard(onRequestPermission, accent)
        events.isEmpty() -> EmptyDay(textMuted)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            events.forEach { EventCard(it, accent, textMuted) }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, accent: Color, textMuted: Color) {
    val color = if (event.category == com.example.calendarwidget.data.EventCategory.WORK) {
        accent
    } else {
        Color(event.category.fallbackColor)
    }
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(BorderStroke(1.dp, CardOutline), RoundedCornerShape(18.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (event.allDay) "весь день" else timeFmt.format(Date(event.start)),
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                event.title,
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(event.category.title, color = textMuted, fontSize = 12.sp)
        }
        Text("›", color = textMuted, fontSize = 20.sp)
    }
}

@Composable
private fun EmptyDay(textMuted: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .border(1.5.dp, textMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = textMuted, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("Свободный день", color = textMuted, fontSize = 13.sp)
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSurface)
            .border(BorderStroke(1.dp, CardOutline), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            "Разрешите доступ к календарю, чтобы видеть события дня.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(onClick = onRequestPermission)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                "Разрешить",
                color = Color(ColorUtils.contrastOn(accent.toArgb())),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---- helpers ----

private fun eventCountLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "событие"
        mod10 in 2..4 && mod100 !in 12..14 -> "события"
        else -> "событий"
    }
    return "$count $word"
}

private fun weekdayName(year: Int, month: Int, day: Int): String {
    val cal = Calendar.getInstance().apply { clear(); set(year, month, day) }
    val idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 .. Sun=6
    return FULL_WEEKDAYS[idx]
}

private fun loadDayData(
    context: android.content.Context,
    year: Int,
    month: Int,
    day: Int,
    firstDayMonday: Boolean,
): DayData {
    val repo = CalendarRepository(context)
    val events = repo.eventsForDay(year, month, day)

    // Build the 7-day strip for the week containing the selected date.
    val cal = Calendar.getInstance().apply { clear(); set(year, month, day) }
    val dow = (cal.get(Calendar.DAY_OF_WEEK) - 1) // Sun=0..Sat=6
    val back = if (firstDayMonday) (dow + 6) % 7 else dow
    cal.add(Calendar.DAY_OF_MONTH, -back)

    val today = CalendarMath.today()
    val labels = CalendarMath.weekdayLabels(firstDayMonday)
    val week = (0 until 7).map { i ->
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val hasEvents = repo.eventsForDay(y, m, d).isNotEmpty()
        val wd = WeekDay(
            year = y, month = m, day = d,
            label = labels[i],
            isToday = y == today.year && m == today.month && d == today.day,
            hasEvents = hasEvents,
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        wd
    }
    return DayData(events, week)
}
