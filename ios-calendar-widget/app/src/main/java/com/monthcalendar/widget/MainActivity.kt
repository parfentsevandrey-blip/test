package com.monthcalendar.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monthcalendar.widget.ui.theme.CalendarTheme
import java.time.LocalDate

/**
 * Lightweight host screen. The widget is the product; this just shows a live
 * preview of the current month and tells the user how to place it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Календарь · виджет",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        MonthPreview()
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Долгое нажатие на рабочий стол → Виджеты → «Календарь». " +
                                "Потяните за края, чтобы изменить размер.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPreview() {
    val month = CalendarModel.monthFor(LocalDate.now())
    val red = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(month.title, color = red, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                month.weekdayHeaders.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            month.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .then(if (cell.isToday) Modifier.clip(CircleShape).background(red) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cell.day.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        cell.isToday -> Color.White
                                        !cell.inCurrentMonth -> Color(0xFFC7C7CC)
                                        cell.isWeekend -> Color(0xFF8E8E93)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
