package app.opal.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opal.core.designsystem.component.LocalSheetHost
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.PanelButton
import app.opal.core.designsystem.glass.GlassButton
import app.opal.core.designsystem.glass.GlassLayer
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.AppLanguage

/** Navigation targets of the settings screen, implemented by the app shell. */
interface SettingsNavigator {
    fun openExitCountry()

    fun openDiagnostics()

    fun openLicenses()

    fun openAutostart()
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    versionName: String,
    navigator: SettingsNavigator,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val system = rememberSystemStatus()
    val context = LocalContext.current
    val sheets = LocalSheetHost.current
    val toast = LocalToastState.current
    val tileLabel = stringResource(app.opal.core.tunnel.R.string.tile_label)
    val tileAdded = stringResource(R.string.settings_tile_added)
    val tileManual = stringResource(R.string.settings_tile_manual)
    val standbyTitle = stringResource(R.string.settings_hot_standby_sheet_title)
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) SystemIntents.openNotificationSettings(context)
        }

    SettingsScreen(
        modifier = modifier,
        state = state,
        system = system,
        language = language,
        versionName = versionName,
        contentPadding = contentPadding,
        onAction = { action ->
            when (action) {
                SettingsAction.OpenExitCountry -> navigator.openExitCountry()
                is SettingsAction.SetHotStandby ->
                    if (!action.on) {
                        viewModel.setHotStandby(false)
                    } else {
                        // Honest warning first: battery, visibility to the provider, permanent
                        // notification.
                        sheets.show(standbyTitle) { dismiss ->
                            Text(
                                stringResource(R.string.settings_hot_standby_sheet_body),
                                style = OpalTheme.type.body,
                                color = OpalTheme.colors.onGlass,
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PanelButton(
                                    stringResource(R.string.settings_cancel),
                                    onClick = dismiss,
                                )
                                GlassButton(
                                    onClick = {
                                        viewModel.setHotStandby(true)
                                        dismiss()
                                    },
                                    layer = GlassLayer.Floating,
                                    contentPadding =
                                        PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.settings_hot_standby_enable),
                                        style = OpalTheme.type.bodyStrong,
                                    )
                                }
                            }
                        }
                    }
                is SettingsAction.SetPrepareOnOpen -> viewModel.setPrepareOnOpen(action.on)
                is SettingsAction.SetBackgroundRefresh -> viewModel.setBackgroundRefresh(action.on)
                is SettingsAction.SetDataSaver -> viewModel.setDataSaver(action.on)
                SettingsAction.OpenVpnSettings -> SystemIntents.openVpnSettings(context)
                SettingsAction.RequestBatteryExemption ->
                    SystemIntents.requestBatteryExemption(context)
                SettingsAction.OpenAutostart -> navigator.openAutostart()
                SettingsAction.OpenNotifications ->
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SystemIntents.openNotificationSettings(context)
                    }
                SettingsAction.AddTile ->
                    if (system.canRequestTile)
                        SystemIntents.requestTile(context, tileLabel) {
                            toast.show(tileAdded, OpalIcons.CheckCircle)
                        }
                    else toast.show(tileManual, OpalIcons.Info)
                is SettingsAction.SetTheme -> viewModel.setTheme(action.mode)
                is SettingsAction.SetSimplifiedGraphics ->
                    viewModel.setSimplifiedGraphics(action.on)
                is SettingsAction.SetLanguage -> onLanguageChange(action.language)
                SettingsAction.OpenDiagnostics -> navigator.openDiagnostics()
                SettingsAction.OpenLicenses -> navigator.openLicenses()
            }
        },
    )
}
