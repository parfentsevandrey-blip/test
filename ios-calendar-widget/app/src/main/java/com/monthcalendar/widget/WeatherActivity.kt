package com.monthcalendar.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.monthcalendar.widget.ui.theme.CalendarTheme
import com.monthcalendar.widget.weather.GeoResult
import com.monthcalendar.widget.weather.WeatherCodes
import com.monthcalendar.widget.weather.WeatherConfig
import com.monthcalendar.widget.weather.WeatherData
import com.monthcalendar.widget.weather.WeatherRepository
import com.monthcalendar.widget.weather.WeatherStore
import com.monthcalendar.widget.weather.WeatherWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Settings screen for the weather widget: city search + units + preview. */
class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalendarTheme { WeatherSettingsScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherSettingsScreen() {
    val context = LocalContext.current
    val store = remember { WeatherStore(context) }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    val results: SnapshotStateList<GeoResult> = remember { emptyList<GeoResult>().toMutableStateList() }
    var metric by remember { mutableStateOf(true) }
    var locationName by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<WeatherData?>(null) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val cfg = store.config()
        metric = cfg.metric
        locationName = cfg.locationName
        preview = WeatherRepository.cached(context)
    }

    suspend fun applyAndRefresh(cfg: WeatherConfig) {
        store.saveConfig(cfg)
        status = "Обновление…"
        preview = WeatherRepository.refresh(context, System.currentTimeMillis()) ?: preview
        WeatherWidget().updateAll(context)
        status = if (preview != null) "Готово" else "Не удалось получить данные"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Погода · виджет") }) }) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            preview?.let { PreviewCard(it) }
            if (locationName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Текущий город: $locationName", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Text("Город", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск города") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        status = "Поиск…"
                        val found = WeatherRepository.geocode(query)
                        results.clear(); results.addAll(found)
                        status = if (found.isEmpty()) "Ничего не найдено" else ""
                    }
                }) { Text("Найти") }
            }

            results.forEach { r ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                locationName = r.display
                                applyAndRefresh(
                                    WeatherConfig(
                                        latitude = r.latitude,
                                        longitude = r.longitude,
                                        locationName = r.display,
                                        metric = metric,
                                    ),
                                )
                                results.clear()
                            }
                        },
                ) {
                    Text(r.display, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Единицы", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = metric,
                    onClick = {
                        metric = true
                        scope.launch { applyAndRefresh(currentConfig(store, true)) }
                    },
                    label = { Text("°C, км/ч") },
                )
                FilterChip(
                    selected = !metric,
                    onClick = {
                        metric = false
                        scope.launch { applyAndRefresh(currentConfig(store, false)) }
                    },
                    label = { Text("°F, mph") },
                )
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Данные предоставлены Open-Meteo (бесплатно, без API-ключа). " +
                    "Добавьте виджет «Погода» на рабочий стол; обновляется раз в час.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun currentConfig(store: WeatherStore, metric: Boolean): WeatherConfig {
    val c = store.config()
    return c.copy(metric = metric)
}

@Composable
private fun PreviewCard(data: WeatherData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                data.locationName.ifBlank { "Погода" },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Image(
                    painter = painterResource(WeatherCodes.iconRes(data.code, data.isDay)),
                    contentDescription = WeatherCodes.label(data.code),
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${data.temp.roundToInt()}${data.tempUnit}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(WeatherCodes.label(data.code), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val today = LocalDate.now()
            data.daily.take(5).forEach { d ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (d.date == today) "Сегодня" else d.date.toString().takeLast(5))
                    Spacer(Modifier.width(10.dp))
                    Image(
                        painter = painterResource(WeatherCodes.iconRes(d.code, true)),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${d.max.roundToInt()}°", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${d.min.roundToInt()}°",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
