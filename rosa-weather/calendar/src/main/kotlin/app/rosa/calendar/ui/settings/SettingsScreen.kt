package app.rosa.calendar.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.calendar.R
import app.rosa.calendar.data.PaintingLight
import app.rosa.calendar.ui.CalendarScreen
import app.rosa.calendar.ui.CalendarViewModel
import app.rosa.calendar.ui.LocalTabBarInset
import app.rosa.calendar.ui.month.WeatherSource
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.HapticsLevel
import app.rosa.weather.core.model.WeekStart

/** How the calendar looks and what it shows: the week, the events, the weather, the painting. */
@Composable
fun SettingsScreen(viewModel: CalendarViewModel) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val allowed by viewModel.eventsAllowed.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.resumed()
        if (granted) viewModel.update { it.copy(showEvents = true) }
    }
    CalendarScreen(stringResource(R.string.settings_title)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = LocalTabBarInset.current + 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(stringResource(R.string.settings_week)) {
                Label(stringResource(R.string.studio_week_start))
                val weekLabels = mapOf(
                    WeekStart.Auto to stringResource(R.string.week_start_auto),
                    WeekStart.Monday to stringResource(R.string.week_start_monday),
                    WeekStart.Sunday to stringResource(R.string.week_start_sunday),
                    WeekStart.Saturday to stringResource(R.string.week_start_saturday),
                )
                GlassSegmented(WeekStart.entries, settings.weekStart, { v -> viewModel.update { it.copy(weekStart = v) } }, { weekLabels.getValue(it) })
                ToggleLine(stringResource(R.string.toggle_week_numbers), settings.weekNumbers) { v -> viewModel.update { it.copy(weekNumbers = v) } }
            }
            Section(stringResource(R.string.settings_events)) {
                ToggleLine(stringResource(R.string.toggle_events), settings.showEvents && allowed) { on ->
                    when {
                        !on -> viewModel.update { it.copy(showEvents = false) }
                        allowed -> viewModel.update { it.copy(showEvents = true) }
                        else -> permission.launch(Manifest.permission.READ_CALENDAR)
                    }
                }
                Label(stringResource(R.string.settings_events_hint))
            }
            Section(stringResource(R.string.settings_weather)) {
                ToggleLine(stringResource(R.string.settings_show_weather), settings.showWeather) { v -> viewModel.update { it.copy(showWeather = v) } }
                WeatherSource(weather)
            }
            Section(stringResource(R.string.settings_painting)) {
                Label(stringResource(R.string.settings_light))
                val lightLabels = mapOf(
                    PaintingLight.Auto to stringResource(R.string.painting_auto),
                    PaintingLight.Day to stringResource(R.string.painting_day),
                    PaintingLight.Night to stringResource(R.string.painting_night),
                )
                GlassSegmented(PaintingLight.entries, settings.light, { v -> viewModel.update { it.copy(light = v) } }, { lightLabels.getValue(it) })
                ToggleLine(stringResource(R.string.settings_live), settings.livePainting) { v -> viewModel.update { it.copy(livePainting = v) } }
                Label(stringResource(R.string.settings_live_hint))
                ToggleLine(stringResource(R.string.settings_tilt), settings.tiltLighting) { v -> viewModel.update { it.copy(tiltLighting = v) } }
                Label(stringResource(R.string.settings_tilt_hint))
            }
            Section(stringResource(R.string.settings_haptics)) {
                val hapticLabels = mapOf(
                    HapticsLevel.Off to stringResource(R.string.haptics_off),
                    HapticsLevel.Subtle to stringResource(R.string.haptics_subtle),
                    HapticsLevel.Rich to stringResource(R.string.haptics_rich),
                )
                GlassSegmented(HapticsLevel.entries, settings.haptics, { v -> viewModel.update { it.copy(haptics = v) } }, { hapticLabels.getValue(it) })
            }
            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_licenses), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 28.dp, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = Rosa.type.caption, color = Rosa.colors.inkSoft, modifier = Modifier.padding(start = 4.dp))
}

@Composable
internal fun ToggleLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Rosa.type.body, color = Rosa.colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        GlassToggle(checked, onChange)
    }
}
