package io.pocketmind.ui.models

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pocketmind.AnalysisState
import io.pocketmind.download.DownloadState
import io.pocketmind.models.LlmModel
import io.pocketmind.models.ModelAssessment
import io.pocketmind.models.Recommender
import io.pocketmind.models.Verdict
import io.pocketmind.ui.HomeUiState
import io.pocketmind.ui.HomeViewModel
import io.pocketmind.ui.ModelActions
import io.pocketmind.ui.components.ModelDownloadControls
import io.pocketmind.ui.components.StatBlock
import io.pocketmind.ui.components.VerdictPill
import io.pocketmind.ui.components.formatBytes
import io.pocketmind.ui.components.verdictColors
import kotlin.math.roundToInt

@Composable
fun ModelsScreen(viewModel: HomeViewModel, onOpenChat: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModelsScreen(
        state = state,
        actions = ModelActions(
            onDownload = { model ->
                viewModel.download(model)
                if (state.activeModelId == null) viewModel.select(model)
            },
            onCancel = viewModel::cancel,
            onOpenChat = { viewModel.select(it); onOpenChat() },
            onDelete = viewModel::delete,
        ),
    )
}

@Composable
fun ModelsScreen(state: HomeUiState, actions: ModelActions) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val done = state.analysis as? AnalysisState.Done

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Модели") },
                subtitle = { Text(done?.let { "Подобраны под ${it.profile.deviceName}" } ?: "Анализ устройства…") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (done == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        val recommendation = done.recommendation
        val bestId = recommendation.best?.model?.id
        val visible = recommendation.assessments
            .filter { showAll || it.verdict != Verdict.TooHeavy || state.downloadState(it.model) !is DownloadState.NotDownloaded }
            .sortedWith(compareByDescending<ModelAssessment> { it.model.id == bestId }.thenBy { it.verdict.ordinal }.thenByDescending { it.model.qualityRank })

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        ToggleButton(
                            checked = !showAll,
                            onCheckedChange = { showAll = false },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text("Подходящие") }
                        ToggleButton(
                            checked = showAll,
                            onCheckedChange = { showAll = true },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text("Все ${recommendation.assessments.size}") }
                    }
                }
                items(visible, key = { it.model.id }) { assessment ->
                    ModelCard(
                        assessment = assessment,
                        recommended = assessment.model.id == bestId,
                        active = state.activeModelId == assessment.model.id,
                        downloadState = state.downloadState(assessment.model),
                        onDownload = { actions.onDownload(assessment.model) },
                        onCancel = { actions.onCancel(assessment.model) },
                        onDelete = { pendingDelete = assessment.model.id },
                        onOpenChat = { actions.onOpenChat(assessment.model) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    Text(
                        "Оценка учитывает объём ОЗУ, измеренную скорость памяти и инструкции процессора. " +
                            "Модели скачиваются с Hugging Face и работают полностью офлайн.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    val toDelete = pendingDelete?.let { id -> done?.recommendation?.assessments?.firstOrNull { it.model.id == id }?.model }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
            title = { Text("Удалить ${toDelete.name}?") },
            text = { Text("Освободится ${formatBytes(toDelete.sizeBytes)}. Модель можно будет скачать снова.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(toDelete)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ModelCard(
    assessment: ModelAssessment,
    recommended: Boolean,
    active: Boolean,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: LlmModel = assessment.model
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(if (recommended) 32.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (recommended) scheme.secondaryContainer else scheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recommended) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Рекомендуем для вашего устройства", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleLargeEmphasized)
                        if (active && downloadState is DownloadState.Downloaded) {
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Активная модель", tint = scheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        "${model.developer} · ${model.parameters} · ${model.quantization}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                VerdictPill(assessment.verdict)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatBlock("≈${assessment.estimatedTokensPerSecond.roundToInt().coerceAtLeast(1)} ток/с", "скорость")
                StatBlock(Recommender.formatGb(model.runtimeRamBytes), "в памяти")
                StatBlock(formatBytes(model.sizeBytes), "файл")
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val colors = verdictColors(assessment.verdict)
                LinearProgressIndicator(
                    progress = { assessment.memoryLoad.toFloat().coerceIn(0f, 1f) },
                    color = if (assessment.verdict == Verdict.TooHeavy) scheme.error else colors.container,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Text(
                    "Нагрузка на память: ${(assessment.memoryLoad * 100).roundToInt()}% безопасного объёма",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                Text("${model.russian.label} · ${model.license}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }

            if (assessment.verdict == Verdict.TooHeavy || !assessment.fitsStorage) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (!assessment.fitsStorage) "Недостаточно свободного места для загрузки"
                        else "Слишком тяжёлая: возможны вылеты и сильный нагрев",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelDownloadControls(
                    model = model,
                    state = downloadState,
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onOpenChat = onOpenChat,
                    modifier = Modifier.weight(1f),
                )
                if (downloadState is DownloadState.Downloaded) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить модель")
                    }
                }
            }
        }
    }
}
