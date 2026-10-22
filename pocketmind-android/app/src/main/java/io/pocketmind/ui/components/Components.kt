package io.pocketmind.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.pocketmind.download.DownloadState
import io.pocketmind.models.LlmModel
import io.pocketmind.models.Verdict
import java.util.Locale

/** An icon on an expressive Material shape (cookie, clover, pill...). */
@Composable
fun ShapedIcon(
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.5f))
    }
}

data class VerdictColors(val container: Color, val content: Color)

@Composable
fun verdictColors(verdict: Verdict): VerdictColors {
    val scheme = MaterialTheme.colorScheme
    return when (verdict) {
        Verdict.Excellent -> VerdictColors(scheme.primary, scheme.onPrimary)
        Verdict.Good -> VerdictColors(scheme.secondary, scheme.onSecondary)
        Verdict.Slow -> VerdictColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Verdict.TooHeavy -> VerdictColors(scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
fun VerdictPill(verdict: Verdict, modifier: Modifier = Modifier) {
    val colors = verdictColors(verdict)
    Surface(color = colors.container, contentColor = colors.content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            verdict.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Download / progress / open-chat controls for a model, morphing between states. */
@Composable
fun ModelDownloadControls(
    model: LlmModel,
    state: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    AnimatedContent(targetState = state::class, modifier = modifier, label = "download") { kind ->
        when (kind) {
            DownloadState.Downloading::class, DownloadState.Queued::class, DownloadState.Paused::class ->
                DownloadProgress(state, onCancel)
            DownloadState.Downloaded::class -> {
                Button(
                    onClick = onOpenChat,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Начать диалог")
                }
            }
            else -> {
                val failed = state as? DownloadState.Failed
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failed != null) {
                        Text(failed.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    val label = if (failed != null) "Повторить" else "Скачать · ${formatBytes(model.sizeBytes)}"
                    val icon = if (failed != null) Icons.Filled.Refresh else Icons.Filled.Download
                    if (primary) {
                        Button(onClick = onDownload, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(label)
                        }
                    } else {
                        FilledTonalButton(onClick = onDownload, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: DownloadState, onCancel: () -> Unit) {
    val (progress, caption) = when (state) {
        is DownloadState.Downloading ->
            state.progress to "${formatBytes(state.downloadedBytes)} из ${formatBytes(state.totalBytes)} · ${(state.progress * 100).toInt()}%"
        is DownloadState.Paused -> (if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes else 0f) to state.reason
        else -> null to "В очереди…"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress == null) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(onClick = onCancel, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.Close, contentDescription = "Отменить загрузку")
        }
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024)
    return if (mb < 1000) String.format(Locale.ROOT, "%.0f МБ", mb)
    else String.format(Locale.ROOT, "%.1f ГБ", mb / 1024).replace('.', ',')
}
