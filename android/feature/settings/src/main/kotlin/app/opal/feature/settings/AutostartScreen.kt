package app.opal.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelButton
import app.opal.core.designsystem.component.PanelDivider
import app.opal.core.designsystem.component.SubScreenHeader
import app.opal.core.designsystem.icon.OpalIcons
import java.util.Locale

/** OEM instructions; the entry matching this device's manufacturer is shown first. */
@Composable
fun AutostartScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maker = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val entries =
        listOf(
                listOf("xiaomi", "redmi", "poco") to R.string.autostart_xiaomi,
                listOf("huawei") to R.string.autostart_huawei,
                listOf("honor") to R.string.autostart_honor,
                listOf("samsung") to R.string.autostart_samsung,
                listOf("oppo", "realme", "oneplus") to R.string.autostart_oppo,
                listOf("vivo", "iqoo") to R.string.autostart_vivo,
            )
            .sortedByDescending { (makers, _) -> makers.any { maker.contains(it) } }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            SubScreenHeader(
                stringResource(R.string.autostart_title),
                stringResource(R.string.settings_back),
                onBack,
            )
            Note(stringResource(R.string.autostart_intro), icon = OpalIcons.Info)
            Panel(Modifier.fillMaxWidth()) {
                entries.forEachIndexed { index, (_, text) ->
                    Note(
                        stringResource(text),
                        icon = if (index == 0) OpalIcons.CheckCircle else OpalIcons.ChevronRight,
                    )
                    if (index != entries.lastIndex) PanelDivider()
                }
            }
            Note(stringResource(R.string.autostart_other))
            PanelButton(
                stringResource(R.string.autostart_open_app_settings),
                onClick = { SystemIntents.openAppDetails(context) },
                icon = OpalIcons.OpenInNew,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
