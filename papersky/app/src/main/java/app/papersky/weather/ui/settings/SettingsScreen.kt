package app.papersky.weather.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.papersky.weather.AppContainer
import app.papersky.weather.BuildConfig
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.core.model.PrecipUnit
import app.papersky.weather.core.model.PressureUnit
import app.papersky.weather.core.model.TempUnit
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.core.model.Units
import app.papersky.weather.core.model.WindUnit
import app.papersky.weather.core.sync.SyncScheduler
import app.papersky.weather.design.Chip
import app.papersky.weather.design.Label
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PaperRule
import app.papersky.weather.design.PaperSegmented
import app.papersky.weather.design.PaperSlider
import app.papersky.weather.design.PaperSwitch
import app.papersky.weather.design.SettingRow
import app.papersky.weather.design.laidDown
import app.papersky.weather.design.pressable
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.ui.common.PaperPage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { c.settings.update(transform) }
    }

    fun units(locale: Locale, transform: (Units) -> Units) = update { it.copy(units = transform(it.resolvedUnits(locale))) }

    fun hasBackgroundLocation() = c.locator.hasBackgroundPermission()
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, scene: SceneState, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val units = s.resolvedUnits(locale)
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(backgroundLocation = granted) }
    }

    PaperPage(stringResource(R.string.settings_title), scene, s.motion, onBack, village = s.village) {
        item("units") {
            PaperCard(Modifier.laidDown(0), seed = 41, tape = true) {
                Label(stringResource(R.string.settings_units))
                Spacer(Modifier.height(12.dp))
                Caption(stringResource(R.string.settings_temperature))
                PaperSegmented(listOf("°C", "°F"), units.temperature.ordinal, { i -> vm.units(locale) { it.copy(temperature = TempUnit.entries[i]) } })
                Gap()
                Caption(stringResource(R.string.settings_wind))
                PaperSegmented(
                    listOf(stringResource(R.string.unit_ms), stringResource(R.string.unit_kmh), stringResource(R.string.unit_mph), stringResource(R.string.unit_kn)),
                    units.wind.ordinal, { i -> vm.units(locale) { it.copy(wind = WindUnit.entries[i]) } },
                )
                Gap()
                Caption(stringResource(R.string.settings_pressure))
                PaperSegmented(
                    listOf(stringResource(R.string.unit_hpa), stringResource(R.string.unit_mmhg), stringResource(R.string.unit_inhg)),
                    units.pressure.ordinal, { i -> vm.units(locale) { it.copy(pressure = PressureUnit.entries[i]) } },
                )
                Gap()
                Caption(stringResource(R.string.settings_precip))
                PaperSegmented(
                    listOf(stringResource(R.string.unit_mm), stringResource(R.string.unit_in)),
                    units.precipitation.ordinal, { i -> vm.units(locale) { it.copy(precipitation = PrecipUnit.entries[i]) } },
                )
            }
        }
        item("updates") {
            PaperCard(Modifier.laidDown(1), seed = 42, tilt = 0.4f) {
                Label(stringResource(R.string.settings_updates))
                Spacer(Modifier.height(8.dp))
                BasicText(stringResource(R.string.settings_interval), style = Paper.type.body.copy(color = Paper.colors.paperInk))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserSettings.IntervalChoices.forEach { m ->
                        Chip(
                            if (m < 60) stringResource(R.string.minutes_short, m) else stringResource(R.string.hours_short, m / 60),
                            selected = s.updateIntervalMinutes == m,
                            onClick = { vm.update { it.copy(updateIntervalMinutes = m) } },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    stringResource(R.string.settings_bg_location),
                    subtitle = stringResource(R.string.settings_bg_location_body),
                    glyph = Glyph.Pin,
                ) {
                    PaperSwitch(s.backgroundLocation && vm.hasBackgroundLocation(), { on ->
                        if (on && !vm.hasBackgroundLocation()) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            vm.update { it.copy(backgroundLocation = on) }
                        }
                    })
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    stringResource(R.string.settings_refresh_now),
                    Modifier.pressable({ SyncScheduler.refreshNow(context) }).padding(vertical = 6.dp),
                    style = Paper.type.bodyStrong.copy(color = Paper.colors.accent),
                )
            }
        }
        item("diorama") {
            PaperCard(Modifier.laidDown(2), seed = 45, tilt = -0.3f) {
                Label(stringResource(R.string.settings_diorama))
                SettingRow(stringResource(R.string.settings_village), subtitle = stringResource(R.string.settings_village_body)) {
                    PaperSwitch(s.village, { on -> vm.update { it.copy(village = on) } })
                }
                PaperRule()
                SettingRow(stringResource(R.string.settings_tilt), subtitle = stringResource(R.string.settings_tilt_body)) {
                    PaperSwitch(s.tiltParallax, { on -> vm.update { it.copy(tiltParallax = on) } })
                }
                Spacer(Modifier.height(10.dp))
                BasicText(stringResource(R.string.settings_motion), style = Paper.type.body.copy(color = Paper.colors.paperInk))
                Spacer(Modifier.height(8.dp))
                PaperSegmented(
                    listOf(stringResource(R.string.motion_full), stringResource(R.string.motion_gentle), stringResource(R.string.motion_still)),
                    s.motion.ordinal, { i -> vm.update { it.copy(motion = MotionLevel.entries[i]) } },
                )
            }
        }
        item("feel") {
            PaperCard(Modifier.laidDown(3), seed = 43, tilt = -0.5f) {
                Label(stringResource(R.string.settings_feel))
                SettingRow(stringResource(R.string.settings_haptics), subtitle = stringResource(R.string.settings_haptics_body)) {
                    PaperSwitch(s.haptics, { on -> vm.update { it.copy(haptics = on) } })
                }
                if (s.haptics) {
                    PaperSlider(s.hapticStrength, { v -> vm.update { it.copy(hapticStrength = v) } }, range = 0.2f..1f, steps = 7)
                }
            }
        }
        item("about") {
            PaperCard(Modifier.laidDown(4), seed = 44, tilt = 0.3f) {
                Label(stringResource(R.string.settings_about))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LinkRow(PaperIcon.Language, stringResource(R.string.settings_language)) {
                        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                }
                LinkRow(PaperIcon.Info, stringResource(R.string.credits_data)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://open-meteo.com/".toUri()))
                }
                Spacer(Modifier.height(6.dp))
                BasicText(stringResource(R.string.settings_fonts), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
                Spacer(Modifier.height(4.dp))
                BasicText("Papersky ${BuildConfig.VERSION_NAME}", style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    BasicText(text, Modifier.padding(bottom = 6.dp), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
}

@Composable
private fun Gap() = Spacer(Modifier.height(14.dp))

@Composable
private fun LinkRow(icon: PaperIcon, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperIconView(icon, Paper.colors.paperInk, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        BasicText(text, style = Paper.type.body.copy(color = Paper.colors.paperInk))
    }
}
