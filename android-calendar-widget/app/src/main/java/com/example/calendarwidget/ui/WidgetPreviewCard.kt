package com.example.calendarwidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.calendarwidget.data.CalendarMath
import com.example.calendarwidget.data.ColorUtils
import com.example.calendarwidget.data.WidgetSettings
import kotlin.math.min

/**
 * Compose re-creation of the LARGE widget bucket, rendered live inside the
 * settings screen (раздел 6.5). Driven entirely by [settings], so every change
 * (accent, opacity, radius, first day, agenda, font scale, theme) is reflected
 * instantly. Uses demo events so the preview always looks populated.
 */
@Composable
fun WidgetPreviewCard(settings: WidgetSettings) {
    val dark = settings.previewDark
    val wallpaper = if (dark) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF2C2945), Color(0xFF16151F), Color(0xFF09090D)),
            center = Offset(0.25f * 1000f, 0f),
            radius = 1100f,
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFFDF4EE), Color(0xFFECEEF6), Color(0xFFDFE3F0)),
            center = Offset(0.75f * 1000f, 0f),
            radius = 1100f,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(wallpaper)
            .padding(20.dp),
    ) {
        WidgetBody(settings, dark)
    }
}

@Composable
private fun WidgetBody(settings: WidgetSettings, dark: Boolean) {
    val accentInt = ColorUtils.parse(settings.accent)
    val accent = Color(accentInt)
    val onAccent = Color(ColorUtils.contrastOn(accentInt))
    val text = if (dark) Color(0xFFF1F1F6) else Color(0xFF1B1B23)
    val muted = if (dark) Color.White.copy(alpha = 0.42f) else Color(0xFF16141C).copy(alpha = 0.45f)
    val weekendNum = if (dark) Color.White.copy(alpha = 0.60f) else Color(0xFF16141C).copy(alpha = 0.52f)
    val outMonth = if (dark) Color.White.copy(alpha = 0.18f) else Color(0xFF14141C).copy(alpha = 0.20f)
    val hair = if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF141428).copy(alpha = 0.10f)
    val glass = if (dark) {
        Color(0xFF14141C).copy(alpha = settings.bgOpacity)
    } else {
        Color(0xFFFFFFFF).copy(alpha = min(0.95f, settings.bgOpacity + 0.32f))
    }
    val fs = settings.fontScale

    val today = CalendarMath.today()
    val demoDots = demoDots(accent)
    val firstMon = settings.firstDayMonday
    val offset = CalendarMath.leadingOffset(today.year, today.month, firstMon)
    val dim = CalendarMath.daysInMonth(today.year, today.month)
    val rows = CalendarMath.cellCount(today.year, today.month, firstMon) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(settings.radius.dp))
            .background(glass)
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.7f), RoundedCornerShape(settings.radius.dp))
            .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${CalendarMath.MONTHS[today.month]} ${today.year}",
                color = text,
                fontSize = 17.sp * fs,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            NavBubble("‹", muted, dark)
            Spacer(Modifier.width(6.dp))
            NavBubble("›", muted, dark)
        }
        Spacer(Modifier.height(10.dp))

        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            CalendarMath.weekdayLabels(firstMon).forEachIndexed { col, label ->
                val weekend = CalendarMath.isWeekendColumn(col, firstMon)
                Text(
                    label,
                    color = if (weekend) muted.copy(alpha = 0.7f) else muted,
                    fontSize = 10.sp * fs,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 7) {
                    val dnum = r * 7 + col - offset + 1
                    val inMonth = dnum in 1..dim
                    val isToday = inMonth && dnum == today.day
                    val weekend = CalendarMath.isWeekendColumn(col, firstMon)
                    val numColor = when {
                        !inMonth -> outMonth
                        isToday -> onAccent
                        weekend -> weekendNum
                        else -> text
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(if (isToday) Modifier.background(accent) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (inMonth) dnum.toString() else "",
                                color = numColor,
                                fontSize = 14.5.sp * fs,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(modifier = Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (inMonth) {
                                (demoDots[dnum] ?: emptyList()).take(3).forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isToday) Color.Black.copy(alpha = 0.42f) else c),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (settings.showAgenda) {
            Spacer(Modifier.height(11.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(hair))
            Spacer(Modifier.height(9.dp))
            Text(
                "${today.day} ${CalendarMath.MONTHS_GENITIVE[today.month]} · Сегодня".uppercase(),
                color = muted,
                fontSize = 10.sp * fs,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            AgendaRow(accent, "Demo Day", "17:00", text, muted, fs)
            Spacer(Modifier.height(7.dp))
            AgendaRow(Color(0xFFC9A6FF), "Позвонить маме", "20:00", text, muted, fs)
        }
    }
}

@Composable
private fun NavBubble(glyph: String, muted: Color, dark: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = muted, fontSize = 16.sp) }
}

@Composable
private fun AgendaRow(dot: Color, title: String, time: String, text: Color, muted: Color, fs: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.width(9.dp))
        Text(title, color = text, fontSize = 12.5.sp * fs, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(time, color = muted, fontSize = 11.sp * fs, fontWeight = FontWeight.Medium)
    }
}

private fun demoDots(accent: Color): Map<Int, List<Color>> {
    val personal = Color(0xFFC9A6FF)
    val health = Color(0xFFFF8A6B)
    val social = Color(0xFF54E6C0)
    val today = CalendarMath.today().day
    return mapOf(
        5 to listOf(health),
        9 to listOf(accent),
        12 to listOf(accent, personal),
        16 to listOf(social),
        18 to listOf(personal),
        20 to listOf(health),
        23 to listOf(accent),
        today to listOf(accent, personal),
        28 to listOf(personal),
    )
}
