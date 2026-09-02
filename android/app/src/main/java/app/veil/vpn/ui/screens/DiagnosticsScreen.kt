package app.veil.vpn.ui.screens

import android.content.Intent

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.ui.resolve
import app.veil.vpn.core.VeilLog
import app.veil.vpn.net.PathVerdict
import app.veil.vpn.net.ProbeReport
import app.veil.vpn.vpn.LocalListener
import app.veil.vpn.ui.components.SectionHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import app.veil.vpn.ui.SelfTestUi

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
    listeners: List<LocalListener>,
    cooldowns: List<String>,
    onClear: () -> Unit,
    onCopy: () -> String,
    onClearCooldowns: () -> Unit,
    onSelfTest: () -> Unit,
    selfTest: SelfTestUi,
    @Suppress("UNUSED_PARAMETER") onDismissSelfTest: () -> Unit,
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
                        text = stringResource(probe.summaryRes),
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
                                    text = result.name.resolve(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${result.detail.resolve()}  ·  ${result.millis} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (result.verdict == PathVerdict.TLS_FROZEN) {
                                    Text(
                                        text = stringResource(R.string.diag_frozen_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Deliberately the first thing on the screen after the summary: when
        // nothing connects, the useful question is which stage failed, and this
        // is what answers it without needing a connection to have been made.
        item { SelfTestCard(selfTest, onSelfTest) }

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
                // The share sheet, because pasting a log from a phone into a
                // chat is the step this evidence keeps failing to survive.
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, onCopy())
                        runCatching { context.startActivity(Intent.createChooser(send, null)) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Text(stringResource(R.string.action_clear), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (cooldowns.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.logs_cooldowns)) }
            item {
                Text(
                    text = stringResource(R.string.logs_cooldowns_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            items(count = cooldowns.size) { index ->
                Text(
                    text = cooldowns[index],
                    style = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                OutlinedButton(
                    onClick = onClearCooldowns,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.action_clear_cooldowns))
                }
            }
        }

        if (listeners.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.logs_exposure)) }
            item {
                Text(
                    text = stringResource(R.string.logs_exposure_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
            items(count = listeners.size) { index ->
                val listener = listeners[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "${listener.name}  ·  ${listener.endpoint}",
                            style = MaterialTheme.typography.bodyMedium
                                .copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listener.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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


@Composable
private fun SelfTestCard(
    state: SelfTestUi,
    onRun: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                is SelfTestUi.Idle -> {
                    Text(
                        stringResource(R.string.diag_selftest_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Bolt, contentDescription = null)
                        Text(
                            stringResource(R.string.diag_selftest),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                is SelfTestUi.Running -> {
                    Text(
                        stringResource(R.string.diag_selftest_step, state.percent, state.label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is SelfTestUi.Done -> {
                    Text(
                        stringResource(R.string.diag_selftest_ready),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.report,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(state.report)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text(
                                stringResource(R.string.action_copy),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, state.report)
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text(
                                stringResource(R.string.action_share),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    OutlinedButton(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.diag_selftest_again))
                    }
                }
            }
        }
    }
}
