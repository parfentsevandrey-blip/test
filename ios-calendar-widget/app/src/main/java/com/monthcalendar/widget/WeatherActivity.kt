package com.monthcalendar.widget

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.monthcalendar.widget.weather.WeatherWorker
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val activity = context as? Activity
    val searchingLabel = stringResource(R.string.wa_searching)
    val notFoundLabel = stringResource(R.string.wa_not_found)

    fun doSearch() {
        scope.launch {
            status = searchingLabel
            val found = WeatherRepository.geocode(query)
            results.clear(); results.addAll(found)
            status = if (found.isEmpty()) notFoundLabel else ""
        }
    }

    LaunchedEffect(Unit) {
        val cfg = store.config()
        metric = cfg.metric
        locationName = cfg.locationName
        preview = WeatherRepository.cached(context)
    }

    // City selection: persist in a NonCancellable block (so a quick exit can't
    // drop the write), push an instant widget update with the new city, kick a
    // background fetch, then close — the widget reloads itself on the home screen.
    fun selectCity(r: GeoResult) {
        val cfg = WeatherConfig(r.latitude, r.longitude, r.display, metric)
        scope.launch {
            // Persist + clear the previous city's cache so the widget shows the
            // new city in a clean "loading" state (never the old data or the
            // pick-a-city prompt). NonCancellable survives the activity closing.
            withContext(NonCancellable) {
                store.saveConfig(cfg)
                store.clearCache()
                WeatherWidget().updateAll(context)
            }
            WeatherWorker.enqueueExpedited(context)
            activity?.finish()
        }
    }

    fun changeUnits(m: Boolean) {
        metric = m
        scope.launch {
            withContext(NonCancellable) {
                store.saveConfig(store.config().copy(metric = m))
                store.clearCache() // cached data is in the old units; refetch
                WeatherWidget().updateAll(context)
            }
            WeatherRepository.refresh(context, System.currentTimeMillis())?.let { preview = it }
            WeatherWidget().updateAll(context)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.wa_title)) }) }) { inner ->
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
                Text(stringResource(R.string.wa_current_city, locationName), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.wa_search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { doSearch() }) {
                    Text(stringResource(R.string.wa_search))
                }
            }

            results.forEach { r ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectCity(r) },
                ) {
                    Text(r.display, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.wa_units), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = metric, onClick = { changeUnits(true) }, label = { Text(stringResource(R.string.wa_metric)) })
                FilterChip(selected = !metric, onClick = { changeUnits(false) }, label = { Text(stringResource(R.string.wa_imperial)) })
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.wa_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewCard(data: WeatherData) {
    val label = stringResource(WeatherCodes.labelRes(data.code))
    val todayLabel = stringResource(R.string.cal_today)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                data.locationName.ifBlank { stringResource(R.string.w_title) },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Image(
                    painter = painterResource(WeatherCodes.iconRes(data.code, data.isDay)),
                    contentDescription = label,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "${data.temp.roundToInt()}${data.tempUnit}",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val today = LocalDate.now()
            data.daily.take(5).forEach { d ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (d.date == today) todayLabel else d.date.toString().takeLast(5))
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
