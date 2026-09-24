package app.opal.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.component.CodeEditor
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelButton
import app.opal.core.designsystem.component.SubScreenHeader
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme

/**
 * Diagnostics export. The text comes from the tunnel's in-memory log (already redacted: no
 * destination addresses or domains); the user can edit it and sends it only explicitly.
 */
@Composable
fun DiagnosticsRoute(
    loadDiagnostics: suspend () -> String?,
    versionName: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val toast = LocalToastState.current
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(true) }
    val unavailable = stringResource(R.string.diag_unavailable)
    val copied = stringResource(R.string.diag_copied)
    val shareTitle = stringResource(R.string.diag_share_title)
    val load by rememberUpdatedState(loadDiagnostics)
    LaunchedEffect(Unit) {
        if (text == null) {
            val android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val header = "Opal $versionName · $android · $abi"
            val body = load()
            text = if (body.isNullOrBlank()) "$header\n$unavailable" else "$header\n$body"
        }
        loading = false
    }
    DiagnosticsScreen(
        text = text.orEmpty(),
        loading = loading,
        contentPadding = contentPadding,
        onBack = onBack,
        onTextChange = { text = it },
        onCopy = {
            context
                .getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(shareTitle, text.orEmpty()))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                toast.show(copied, OpalIcons.Check)
        },
        onShare = {
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, shareTitle)
                    .putExtra(Intent.EXTRA_TEXT, text.orEmpty())
            context.startActivity(
                Intent.createChooser(send, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
    )
}

@Composable
fun DiagnosticsScreen(
    text: String,
    loading: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            SubScreenHeader(
                stringResource(R.string.diag_title),
                stringResource(R.string.settings_back),
                onBack,
            )
            Panel(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Note(
                    stringResource(R.string.diag_note),
                    icon = OpalIcons.VisibilityOff,
                    tint = OpalTheme.colors.onBackgroundMuted,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelButton(
                    stringResource(R.string.diag_share),
                    onShare,
                    icon = OpalIcons.Share,
                    enabled = !loading,
                )
                PanelButton(
                    stringResource(R.string.diag_copy),
                    onCopy,
                    icon = OpalIcons.ContentPaste,
                    enabled = !loading,
                )
            }
            if (loading) {
                Note(stringResource(R.string.diag_loading))
            } else {
                CodeEditor(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = stringResource(R.string.diag_title),
                    minLines = 12,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}
