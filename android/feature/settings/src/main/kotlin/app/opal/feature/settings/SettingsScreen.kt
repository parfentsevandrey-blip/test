package app.opal.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.component.ChevronRow
import app.opal.core.designsystem.component.Countries
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelDivider
import app.opal.core.designsystem.component.ScreenTitle
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SettingRow
import app.opal.core.designsystem.component.SwitchRow
import app.opal.core.designsystem.glass.GlassSegmented
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.AppLanguage
import app.opal.core.model.settings.ThemeMode
import kotlinx.collections.immutable.persistentListOf

sealed interface SettingsAction {
    data object OpenExitCountry : SettingsAction

    data class SetHotStandby(val on: Boolean) : SettingsAction

    data class SetPrepareOnOpen(val on: Boolean) : SettingsAction

    data class SetBackgroundRefresh(val on: Boolean) : SettingsAction

    data class SetDataSaver(val on: Boolean) : SettingsAction

    data object OpenVpnSettings : SettingsAction

    data object RequestBatteryExemption : SettingsAction

    data object OpenAutostart : SettingsAction

    data object OpenNotifications : SettingsAction

    data object AddTile : SettingsAction

    data class SetTheme(val mode: ThemeMode) : SettingsAction

    data class SetSimplifiedGraphics(val on: Boolean) : SettingsAction

    data class SetLanguage(val language: AppLanguage) : SettingsAction

    data object OpenDiagnostics : SettingsAction

    data object OpenLicenses : SettingsAction
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    system: SystemStatus,
    language: AppLanguage,
    versionName: String,
    contentPadding: PaddingValues,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = state.settings
    val colors = OpalTheme.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            ScreenTitle(
                stringResource(R.string.settings_title),
                Modifier.padding(horizontal = 0.dp),
            )

            SectionHeader(
                stringResource(R.string.settings_section_connection),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                val country = s.exitCountry
                val value =
                    country?.let { "${Countries.flag(it)} ${Countries.name(it)}" }
                        ?: stringResource(R.string.settings_exit_any)
                SettingRow(
                    title = stringResource(R.string.settings_exit_country),
                    subtitle =
                        if (state.connected) value
                        else "$value · ${stringResource(R.string.settings_exit_after_connect)}",
                    icon = OpalIcons.TravelExplore,
                    enabled = state.connected,
                    onClick = { onAction(SettingsAction.OpenExitCountry) },
                ) {
                    Icon(
                        OpalIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.onBackgroundMuted,
                    )
                }
                PanelDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_hot_standby),
                    subtitle = stringResource(R.string.settings_hot_standby_desc),
                    icon = OpalIcons.Bolt,
                    checked = s.hotStandby,
                    onCheckedChange = { onAction(SettingsAction.SetHotStandby(it)) },
                )
                PanelDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_prepare),
                    subtitle = stringResource(R.string.settings_prepare_desc),
                    icon = OpalIcons.RocketLaunch,
                    checked = s.prepareOnOpen,
                    onCheckedChange = { onAction(SettingsAction.SetPrepareOnOpen(it)) },
                )
                PanelDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_refresh),
                    subtitle = stringResource(R.string.settings_refresh_desc),
                    icon = OpalIcons.Download,
                    checked = s.backgroundDirectoryRefresh,
                    onCheckedChange = { onAction(SettingsAction.SetBackgroundRefresh(it)) },
                )
                PanelDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_data_saver),
                    subtitle = stringResource(R.string.settings_data_saver_desc),
                    icon = OpalIcons.DataSaverOn,
                    checked = s.dataSaver,
                    onCheckedChange = { onAction(SettingsAction.SetDataSaver(it)) },
                )
            }

            SectionHeader(
                stringResource(R.string.settings_section_protection),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                val on = stringResource(R.string.settings_on)
                val off = stringResource(R.string.settings_off)
                val flags =
                    if (state.alwaysOn != null && state.lockdown != null) {
                        stringResource(
                            R.string.settings_always_on_state,
                            if (state.alwaysOn) on else off,
                            if (state.lockdown) on else off,
                        )
                    } else {
                        null
                    }
                ChevronRow(
                    title = stringResource(R.string.settings_kill_switch),
                    subtitle =
                        listOfNotNull(flags, stringResource(R.string.settings_kill_switch_desc))
                            .joinToString("\n"),
                    icon = OpalIcons.ShieldLock,
                    onClick = { onAction(SettingsAction.OpenVpnSettings) },
                )
                PanelDivider()
                ChevronRow(
                    title = stringResource(R.string.settings_battery),
                    subtitle =
                        stringResource(
                            if (system.ignoringBatteryOptimizations) R.string.settings_battery_ok
                            else R.string.settings_battery_limited
                        ),
                    icon = OpalIcons.BatterySaver,
                    onClick = { onAction(SettingsAction.RequestBatteryExemption) },
                )
                PanelDivider()
                ChevronRow(
                    title = stringResource(R.string.settings_autostart),
                    subtitle = stringResource(R.string.settings_autostart_desc),
                    icon = OpalIcons.RestartAlt,
                    onClick = { onAction(SettingsAction.OpenAutostart) },
                )
                PanelDivider()
                ChevronRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle =
                        stringResource(
                            if (system.notificationsEnabled) R.string.settings_notifications_on
                            else R.string.settings_notifications_off
                        ),
                    icon = OpalIcons.Notifications,
                    onClick = { onAction(SettingsAction.OpenNotifications) },
                )
                PanelDivider()
                ChevronRow(
                    title = stringResource(R.string.settings_tile),
                    subtitle =
                        stringResource(
                            if (system.canRequestTile) R.string.settings_tile_desc
                            else R.string.settings_tile_manual
                        ),
                    icon = OpalIcons.VpnLock,
                    onClick = { onAction(SettingsAction.AddTile) },
                )
            }

            SectionHeader(
                stringResource(R.string.settings_section_appearance),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                LabeledSegmented(
                    title = stringResource(R.string.settings_theme),
                    options =
                        persistentListOf(
                            stringResource(R.string.settings_theme_system),
                            stringResource(R.string.settings_theme_light),
                            stringResource(R.string.settings_theme_dark),
                        ),
                    selected = ThemeMode.entries.indexOf(s.theme),
                    onSelect = { onAction(SettingsAction.SetTheme(ThemeMode.entries[it])) },
                )
                PanelDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_simplified),
                    subtitle = stringResource(R.string.settings_simplified_desc),
                    icon = OpalIcons.BlurOn,
                    checked = s.simplifiedGraphics,
                    onCheckedChange = { onAction(SettingsAction.SetSimplifiedGraphics(it)) },
                )
                PanelDivider()
                LabeledSegmented(
                    title = stringResource(R.string.settings_language),
                    options =
                        persistentListOf(
                            stringResource(R.string.settings_language_system),
                            stringResource(R.string.settings_language_ru),
                            stringResource(R.string.settings_language_en),
                        ),
                    selected = AppLanguage.entries.indexOf(language),
                    onSelect = { onAction(SettingsAction.SetLanguage(AppLanguage.entries[it])) },
                )
            }

            SectionHeader(
                stringResource(R.string.settings_section_about),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                ChevronRow(
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = stringResource(R.string.settings_diagnostics_desc),
                    icon = OpalIcons.Description,
                    onClick = { onAction(SettingsAction.OpenDiagnostics) },
                )
                PanelDivider()
                ChevronRow(
                    title = stringResource(R.string.settings_licenses),
                    icon = OpalIcons.Info,
                    onClick = { onAction(SettingsAction.OpenLicenses) },
                )
            }
            Text(
                stringResource(R.string.settings_version, versionName) +
                    "\n" +
                    stringResource(R.string.settings_license_app),
                style = OpalTheme.type.caption,
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledSegmented(
    title: String,
    options: kotlinx.collections.immutable.ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = OpalTheme.type.body, color = OpalTheme.colors.onBackground)
        }
        GlassSegmented(
            options = options,
            selectedIndex = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
