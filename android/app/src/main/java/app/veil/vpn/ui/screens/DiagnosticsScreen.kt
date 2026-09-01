package app.veil.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.core.VeilLog
import app.veil.vpn.net.ProbeReport
import app.veil.vpn.ui.components.SectionHeader

/**
 * The screen that answers "why isn't it connecting".
 *
 * The probe results are shown as measurements rather than a verdict, because
 * when a tool like this fails the useful thing is the raw evidence: which
 * endpoints answered, which did not, and how long each took.
 */
@Composable
fun DiagnosticsScreen(
    probe: ProbeReport,
    logs: List<VeilLog.Entry>,
    onClear: () -> Unit,
    onCopy: () -> String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.logs_probe)) }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = probe.summary(),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    probe.results.forEach { result ->
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (result.ok) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Cancel
                                },
                                contentDescription = null,
                                tint = if (result.ok) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = result.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${result.detail}  ·  ${result.millis} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(onCopy())) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(
                        stringResource(R.string.action_copy),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Text("Clear", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item { SectionHeader(stringResource(R.string.logs_tor)) }

        if (logs.isEmpty()) {
            item { EmptyHint(stringResource(R.string.logs_empty)) }
        }

        items(count = logs.size) { index ->
            val entry = logs[logs.size - 1 - index]
            Text(
                text = entry.format(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = when (entry.level) {
                    VeilLog.Level.ERROR -> MaterialTheme.colorScheme.error
                    VeilLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
                    VeilLog.Level.INFO -> MaterialTheme.colorScheme.onSurface
                    VeilLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
