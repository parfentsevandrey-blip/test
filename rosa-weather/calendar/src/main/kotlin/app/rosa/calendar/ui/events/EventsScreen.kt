package app.rosa.calendar.ui.events

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.calendar.R
import app.rosa.calendar.data.Occurrence
import app.rosa.calendar.ui.CalendarScreen
import app.rosa.calendar.ui.CalendarViewModel
import app.rosa.calendar.ui.LocalTabBarInset
import app.rosa.calendar.ui.WeatherState
import app.rosa.calendar.ui.currentLocale
import app.rosa.calendar.ui.month.EventRow
import app.rosa.calendar.ui.month.PermissionLine
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.WeatherCondition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * The month ahead as a list: each day that has something on it, on its own pane of glass, with
 * the forecast's weather beside its name. A day's name opens it on the month.
 */
@Composable
fun EventsRoute(viewModel: CalendarViewModel, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val upcoming by viewModel.upcoming.collectAsStateWithLifecycle()
    val allowed by viewModel.eventsAllowed.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.resumed() }
    EventsScreen(
        showEvents = settings.showEvents,
        upcoming = upcoming,
        allowed = allowed,
        weather = weather,
        today = today,
        onOpenDay = onOpenDay,
        onAllowEvents = { permission.launch(Manifest.permission.READ_CALENDAR) },
    )
}

@Composable
fun EventsScreen(
    showEvents: Boolean,
    upcoming: Map<LocalDate, List<Occurrence>>?,
    allowed: Boolean,
    weather: WeatherState,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onAllowEvents: () -> Unit,
) {
    val bottom = LocalTabBarInset.current
    CalendarScreen(stringResource(R.string.events_title)) {
        val days = upcoming
        when {
            !showEvents -> Note(stringResource(R.string.events_off))
            !allowed -> GlassSurface(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                style = GlassStyle.Frosted,
                cornerRadius = 28.dp,
                contentPadding = PaddingValues(18.dp),
            ) { PermissionLine(onAllowEvents) }
            days == null -> Unit
            days.isEmpty() -> Note(stringResource(R.string.events_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottom + 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(days.keys.sorted(), key = { it.toEpochDay() }) { day ->
                    DayGroup(day, today, days.getValue(day), weather, onOpenDay)
                }
            }
        }
    }
}

@Composable
private fun DayGroup(date: LocalDate, today: LocalDate, occurrences: List<Occurrence>, weather: WeatherState, onOpenDay: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val locale = currentLocale()
    val lent = (weather as? WeatherState.Lent)?.weather
    val format = remember(lent?.units, lent?.zone) { lent?.let { WeatherFormat(context, it.units, it.zone) } }
    val forecast = lent?.days?.get(date)
    GlassSurface(
        Modifier.fillMaxWidth(),
        style = GlassStyle.Frosted,
        cornerRadius = 26.dp,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                        haptics?.tick()
                        onOpenDay(date)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = when (date) {
                    today -> stringResource(R.string.events_today)
                    today.plusDays(1) -> stringResource(R.string.events_tomorrow)
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
                    Text(DateTimeFormatter.ofPattern("d MMMM", locale).format(date), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
                }
                if (forecast != null && format != null) {
                    WeatherGlyph(WeatherCondition.fromWmo(forecast.weatherCode), isDay = true, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(format.temperature(forecast.temperatureMax), style = Rosa.type.headline, color = Rosa.colors.ink)
                    Text(" / " + format.temperature(forecast.temperatureMin), style = Rosa.type.label, color = Rosa.colors.inkSoft)
                }
            }
            occurrences.forEach { EventRow(it, date) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = Rosa.type.body, color = Rosa.colors.inkSoft, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}
