package com.monthcalendar.widget

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.monthcalendar.widget.ui.theme.CalendarTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Material 3 settings hub for the widget: calendar-permission handling, week
 * start, event display, accent picker — with a live preview of the month that
 * mirrors the current choices. Saving pushes an update to every placed widget.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { CalendarSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(CalendarSettings()) }
    var hasPerm by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPerm = granted
        scope.launch { CalendarWidget().updateAll(context) }
    }

    LaunchedEffect(Unit) { settings = store.get() }

    fun apply(next: CalendarSettings) {
        settings = next
        scope.launch {
            store.save(next)
            CalendarWidget().updateAll(context)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.ma_title)) }) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            MonthPreview(settings, hasPerm)
            Spacer(Modifier.height(20.dp))

            if (!hasPerm) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.ma_grant_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.ma_grant_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                            Text(stringResource(R.string.ma_grant_btn))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.ma_events),
                    subtitle = stringResource(R.string.ma_events_sub),
                    checked = settings.showEvents,
                    onChange = { apply(settings.copy(showEvents = it)) },
                )
                ToggleRow(
                    title = stringResource(R.string.ma_week_start),
                    subtitle = stringResource(if (settings.mondayFirst) R.string.ma_week_mon else R.string.ma_week_sun),
                    checked = settings.mondayFirst,
                    onChange = { apply(settings.copy(mondayFirst = it)) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.ma_accent), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { accent ->
                    FilterChip(
                        selected = settings.accent == accent,
                        onClick = { apply(settings.copy(accent = accent)) },
                        label = { Text(stringResource(accentLabelRes(accent))) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.ma_weather_card),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ma_weather_card_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { context.startActivity(Intent(context, WeatherActivity::class.java)) }) {
                        Text(stringResource(R.string.ma_weather_btn))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.ma_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun accentLabelRes(a: Accent): Int = when (a) {
    Accent.DYNAMIC -> R.string.ma_accent_dynamic
    Accent.INDIGO -> R.string.ma_accent_indigo
    Accent.GREEN -> R.string.ma_accent_green
    Accent.ROSE -> R.string.ma_accent_rose
    Accent.AMBER -> R.string.ma_accent_amber
}

private fun accentColor(a: Accent, fallback: Color): Color = when (a) {
    Accent.DYNAMIC -> fallback
    Accent.INDIGO -> Color(0xFF4A52CC)
    Accent.GREEN -> Color(0xFF2E6B4F)
    Accent.ROSE -> Color(0xFFB3255F)
    Accent.AMBER -> Color(0xFF855400)
}

@Composable
private fun MonthPreview(settings: CalendarSettings, hasPerm: Boolean) {
    val today = LocalDate.now()
    val month = CalendarModel.monthFor(YearMonth.now(), today, settings.mondayFirst)
    val accent = accentColor(settings.accent, MaterialTheme.colorScheme.primary)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(month.title, color = accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                month.weekdayHeaders.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    .then(if (cell.isToday) Modifier.clip(CircleShape).background(accent) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cell.day.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        cell.isToday -> Color.White
                                        !cell.inCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
