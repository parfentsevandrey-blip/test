package app.opal.feature.connection

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opal.core.designsystem.component.CodeEditor
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelButton
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SubScreenHeader
import app.opal.core.designsystem.glass.GlassButton
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.bridge.BridgeLine
import kotlinx.coroutines.launch

@Composable
fun CustomBridgesRoute(
    viewModel: CustomBridgesViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = LocalToastState.current
    val scope = rememberCoroutineScope()
    var scanning by rememberSaveable { mutableStateOf(false) }

    val savedText = stringResource(R.string.bridges_saved)
    val removedText = stringResource(R.string.bridges_removed)
    val noneText = stringResource(R.string.bridges_nothing_found)
    val qrNoneText = stringResource(R.string.bridges_qr_none)
    val deniedText = stringResource(R.string.bridges_camera_denied)
    val resources = LocalResources.current
    fun reportAdded(count: Int) {
        toast.show(
            if (count > 0) resources.getQuantityString(R.plurals.bridges_qr_added, count, count)
            else noneText,
            OpalIcons.Key,
        )
    }

    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanning = true else toast.show(deniedText, OpalIcons.Warning)
        }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = decodeQrFromImage(context, uri)
                    if (text == null) toast.show(qrNoneText, OpalIcons.Warning)
                    else reportAdded(viewModel.addFrom(text))
                }
            }
        }

    Box(modifier.fillMaxSize()) {
        CustomBridgesScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = onBack,
            onTextChange = viewModel::setText,
            onPaste = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val text =
                    clip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                reportAdded(viewModel.addFrom(text))
            },
            onScan = {
                if (
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                )
                    scanning = true
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onPickImage = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClear = { viewModel.setText("") },
            onSave = {
                viewModel.save { custom ->
                    toast.show(if (custom) savedText else removedText, OpalIcons.CheckCircle)
                }
            },
        )
        if (scanning) {
            QrScanner(
                onResult = { text ->
                    scanning = false
                    reportAdded(viewModel.addFrom(text))
                },
                onClose = { scanning = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomBridgesScreen(
    state: CustomBridgesUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onPickImage: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            SubScreenHeader(
                stringResource(R.string.bridges_title),
                stringResource(R.string.bridges_back),
                onBack,
            )
            CodeEditor(
                value = state.text,
                onValueChange = onTextChange,
                placeholder = stringResource(R.string.bridges_placeholder),
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelButton(
                    stringResource(R.string.bridges_paste),
                    onPaste,
                    icon = OpalIcons.ContentPaste,
                )
                PanelButton(
                    stringResource(R.string.bridges_scan),
                    onScan,
                    icon = OpalIcons.QrCodeScanner,
                )
                PanelButton(
                    stringResource(R.string.bridges_image),
                    onPickImage,
                    icon = OpalIcons.Image,
                )
                if (state.text.isNotEmpty())
                    PanelButton(
                        stringResource(R.string.bridges_clear),
                        onClear,
                        icon = OpalIcons.Delete,
                    )
            }
            Column(Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                if (state.valid.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            R.plurals.bridges_recognized,
                            state.valid.size,
                            state.valid.size,
                        ),
                        style = OpalTheme.type.callout,
                        color = colors.connected,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                state.invalid.forEach { line ->
                    Text(
                        stringResource(
                            R.string.bridges_invalid_line,
                            line.number,
                            stringResource(reasonText(line.reason)),
                        ),
                        style = OpalTheme.type.callout,
                        color = colors.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            GlassButton(
                onClick = onSave,
                // Unparsable lines are listed above and left out; valid ones can always be saved.
                enabled =
                    state.loaded &&
                        !state.saved &&
                        (state.valid.isNotEmpty() || state.text.isBlank()),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            ) {
                Icon(OpalIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.bridges_save), style = OpalTheme.type.bodyStrong)
            }
            SectionHeader(
                stringResource(R.string.bridges_where_title),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Note(stringResource(R.string.bridges_where_body), icon = OpalIcons.Help)
            }
        }
    }
}

private fun reasonText(reason: BridgeLine.Reason) =
    when (reason) {
        BridgeLine.Reason.Empty,
        BridgeLine.Reason.BadAddress -> R.string.bridges_reason_address
        BridgeLine.Reason.UnknownTransport -> R.string.bridges_reason_transport
        BridgeLine.Reason.BadPort -> R.string.bridges_reason_port
        BridgeLine.Reason.BadFingerprint -> R.string.bridges_reason_fingerprint
        BridgeLine.Reason.BadArgument -> R.string.bridges_reason_argument
        BridgeLine.Reason.MissingRequiredArgument -> R.string.bridges_reason_missing
    }
