package app.veil.vpn.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.Transport
import app.veil.vpn.ui.MoatFlow
import app.veil.vpn.ui.components.SectionHeader

@Composable
fun BridgesScreen(
    bridges: Map<Transport, List<BridgeLine>>,
    moat: MoatFlow,
    loadCustomText: ((String) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onSaveCustom: (String) -> Unit,
    onRequestFromMoat: () -> Unit,
    onSubmitSolution: (String) -> Unit,
    onDismissMoat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!loaded) {
            loadCustomText { customText = it; loaded = true }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = "Bridges are unlisted entry points into Tor. Veil ships the " +
                            "public set, refreshes it from the Tor Project when it can, and " +
                            "can ask for private ones that no blocklist has yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Text(
                                stringResource(R.string.bridges_refresh),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestFromMoat, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Key, contentDescription = null)
                        Text(
                            stringResource(R.string.bridges_request),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.bridges_builtin)) }

        bridges.forEach { (transport, lines) ->
            if (lines.isEmpty()) return@forEach
            item(key = "header-${transport.name}") {
                Text(
                    text = "${transport.label}  ·  ${lines.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            items(count = lines.size, key = { "${transport.name}-$it" }) { index ->
                BridgeRow(lines[index])
            }
        }

        item { SectionHeader(stringResource(R.string.bridges_custom)) }
        item {
            OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text(stringResource(R.string.bridges_paste_hint)) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                shape = MaterialTheme.shapes.large,
            )
        }
        item {
            Button(
                onClick = { onSaveCustom(customText) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    when (moat) {
        is MoatFlow.Idle -> Unit
        is MoatFlow.Loading -> MoatDialog(onDismiss = onDismissMoat) {
            LoadingIndicator()
        }
        is MoatFlow.Solving -> CaptchaDialog(
            flow = moat,
            onSubmit = onSubmitSolution,
            onDismiss = onDismissMoat,
        )
        is MoatFlow.Done -> MoatDialog(onDismiss = onDismissMoat) {
            Text("Added ${moat.added} new bridges.")
        }
        is MoatFlow.Error -> MoatDialog(onDismiss = onDismissMoat) {
            Text(moat.message)
        }
    }
}

@Composable
private fun BridgeRow(bridge: BridgeLine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = if (bridge.hasRoutableAddress) {
                    "${bridge.host}:${bridge.port}"
                } else {
                    "via broker"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            bridge.fingerprint?.let {
                Text(
                    text = it.chunked(10).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoatDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        title = { Text(stringResource(R.string.bridges_request)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * The CAPTCHA the bridge authority uses to keep a censor from enumerating every
 * bridge it has. Solving it in the app, rather than sending the user to a
 * website, matters precisely when that website is unreachable.
 */
@Composable
private fun CaptchaDialog(
    flow: MoatFlow.Solving,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var answer by remember { mutableStateOf("") }
    val bitmap = remember(flow.challenge.imagePngBase64) {
        runCatching {
            val bytes = Base64.decode(flow.challenge.imagePngBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bridges_request)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.bridges_captcha_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(stringResource(R.string.bridges_captcha_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(answer) }, enabled = answer.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
