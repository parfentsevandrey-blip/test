package app.rosa.weather.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.R
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.designsystem.R as DsR
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.EffectsQuality
import app.rosa.weather.core.model.HapticsLevel
import app.rosa.weather.core.model.PrecipitationUnit
import app.rosa.weather.core.model.PressureUnit
import app.rosa.weather.core.model.TemperatureUnit
import app.rosa.weather.core.model.WindUnit
import app.rosa.weather.ui.common.GlassScreen

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val units = settings.resolvedUnits()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.update { it.copy(backgroundLocation = granted) }
    }

    val windLabels = WindUnit.entries.associateWith { windLabel(it) }
    val pressureLabels = PressureUnit.entries.associateWith { pressureLabel(it) }
    val precipLabels = mapOf(PrecipitationUnit.Millimeters to mm(), PrecipitationUnit.Inches to inch())
    val hapticLabels = HapticsLevel.entries.associateWith { hapticsLabel(it) }
    val effectLabels = EffectsQuality.entries.associateWith { effectsLabel(it) }
    val refreshLabels = AppSettings.RefreshChoices.associateWith { m ->
        if (m < 60) stringResource(R.string.minutes_value, m) else stringResource(R.string.hours_value, m / 60)
    }

    GlassScreen(stringResource(R.string.settings_title), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section(stringResource(R.string.settings_units)) {
                Labeled(stringResource(R.string.settings_temperature)) {
                    GlassSegmented(TemperatureUnit.entries, units.temperature, { v -> viewModel.updateUnits { it.copy(temperature = v) } }, {
                        if (it == TemperatureUnit.Celsius) "°C" else "°F"
                    })
                }
                Labeled(stringResource(R.string.settings_wind)) {
                    GlassSegmented(
                        listOf(WindUnit.MetersPerSecond, WindUnit.KilometersPerHour, WindUnit.MilesPerHour, WindUnit.Knots),
                        units.wind,
                        { v -> viewModel.updateUnits { it.copy(wind = v) } },
                        { windLabels.getValue(it) },
                    )
                }
                Labeled(stringResource(R.string.settings_pressure)) {
                    GlassSegmented(PressureUnit.entries, units.pressure, { v -> viewModel.updateUnits { it.copy(pressure = v) } }, { pressureLabels.getValue(it) })
                }
                Labeled(stringResource(R.string.settings_precipitation)) {
                    GlassSegmented(PrecipitationUnit.entries, units.precipitation, { v -> viewModel.updateUnits { it.copy(precipitation = v) } }, { precipLabels.getValue(it) })
                }
            }
            Section(stringResource(R.string.settings_refresh)) {
                GlassSegmented(AppSettings.RefreshChoices, settings.refreshIntervalMinutes, { v -> viewModel.update { it.copy(refreshIntervalMinutes = v) } }, { refreshLabels.getValue(it) })
            }
            Section(stringResource(R.string.settings_haptics)) {
                GlassSegmented(HapticsLevel.entries, settings.haptics, { v -> viewModel.update { it.copy(haptics = v) } }, { hapticLabels.getValue(it) })
            }
            Section(stringResource(R.string.settings_effects)) {
                GlassSegmented(EffectsQuality.entries, settings.effects, { v -> viewModel.update { it.copy(effects = v) } }, { effectLabels.getValue(it) })
                ToggleRow(stringResource(R.string.settings_tilt), stringResource(R.string.settings_tilt_hint), settings.tiltLighting) { on ->
                    viewModel.update { it.copy(tiltLighting = on) }
                }
            }
            Section(stringResource(R.string.settings_background_location)) {
                ToggleRow(stringResource(R.string.settings_background_location), stringResource(R.string.settings_background_location_hint), settings.backgroundLocation) { on ->
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (on && coarse) {
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        viewModel.update { it.copy(backgroundLocation = false) }
                    }
                }
            }
            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_licenses), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 28.dp, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = Rosa.type.caption, color = Rosa.colors.inkSoft, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Rosa.type.body, color = Rosa.colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(hint, style = Rosa.type.caption, color = Rosa.colors.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        GlassToggle(checked, onChange)
    }
}

@Composable
private fun windLabel(unit: WindUnit) = stringResource(
    when (unit) {
        WindUnit.MetersPerSecond -> DsR.string.unit_ms
        WindUnit.KilometersPerHour -> DsR.string.unit_kmh
        WindUnit.MilesPerHour -> DsR.string.unit_mph
        WindUnit.Knots -> DsR.string.unit_knots
        WindUnit.Beaufort -> DsR.string.unit_beaufort
    },
)

@Composable
private fun pressureLabel(unit: PressureUnit) = stringResource(
    when (unit) {
        PressureUnit.MillimetersOfMercury -> DsR.string.unit_mmhg
        PressureUnit.Hectopascal -> DsR.string.unit_hpa
        PressureUnit.InchesOfMercury -> DsR.string.unit_inhg
    },
)

@Composable
private fun mm() = stringResource(DsR.string.unit_mm)

@Composable
private fun inch() = stringResource(DsR.string.unit_in)

@Composable
private fun hapticsLabel(level: HapticsLevel) = stringResource(
    when (level) {
        HapticsLevel.Off -> R.string.haptics_off
        HapticsLevel.Subtle -> R.string.haptics_subtle
        HapticsLevel.Rich -> R.string.haptics_rich
    },
)

@Composable
private fun effectsLabel(level: EffectsQuality) = stringResource(
    when (level) {
        EffectsQuality.Auto -> R.string.effects_auto
        EffectsQuality.Battery -> R.string.effects_battery
        EffectsQuality.Balanced -> R.string.effects_balanced
        EffectsQuality.Cinematic -> R.string.effects_cinematic
    },
)
