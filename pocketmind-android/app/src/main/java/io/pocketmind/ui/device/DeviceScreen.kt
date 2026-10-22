package io.pocketmind.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pocketmind.AnalysisState
import io.pocketmind.download.DownloadState
import io.pocketmind.hardware.DeviceProfile
import io.pocketmind.models.ModelAssessment
import io.pocketmind.models.Recommendation
import io.pocketmind.models.Recommender
import io.pocketmind.ui.HomeUiState
import io.pocketmind.ui.HomeViewModel
import io.pocketmind.ui.ModelActions
import io.pocketmind.ui.components.ModelDownloadControls
import io.pocketmind.ui.components.ShapedIcon
import io.pocketmind.ui.components.StatBlock
import io.pocketmind.ui.components.VerdictPill
import io.pocketmind.ui.components.formatBytes
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DeviceScreen(
    viewModel: HomeViewModel,
    onOpenModels: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceScreen(
        state = state,
        actions = ModelActions(
            onDownload = { viewModel.download(it); viewModel.select(it) },
            onCancel = viewModel::cancel,
            onOpenChat = { viewModel.select(it); onOpenChat() },
        ),
        onRescan = viewModel::rescan,
        onOpenModels = onOpenModels,
    )
}

@Composable
fun DeviceScreen(
    state: HomeUiState,
    actions: ModelActions,
    onRescan: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val analysis = state.analysis
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Ваше устройство") },
                subtitle = {
                    Text((analysis as? AnalysisState.Done)?.profile?.deviceName ?: "Анализ железа")
                },
                actions = {
                    IconButton(onClick = onRescan, enabled = analysis is AnalysisState.Done) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Повторить анализ")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = analysis,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "analysis",
        ) { current ->
            when (current) {
                is AnalysisState.Running -> AnalysisProgress(current.step, Modifier.padding(padding))
                is AnalysisState.Done -> DeviceReport(
                    profile = current.profile,
                    recommendation = current.recommendation,
                    state = state,
                    actions = actions,
                    contentPadding = padding,
                    onOpenModels = onOpenModels,
                )
            }
        }
    }
}

@Composable
private fun AnalysisProgress(step: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Text("Анализирую железо", style = MaterialTheme.typography.headlineSmallEmphasized)
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = step, label = "step") {
            Text(
                "$it…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceReport(
    profile: DeviceProfile,
    recommendation: Recommendation,
    state: HomeUiState,
    actions: ModelActions,
    contentPadding: PaddingValues,
    onOpenModels: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScoreCard(profile, recommendation) }
            item {
                BestModelCard(
                    best = recommendation.best,
                    downloadState = recommendation.best?.let { state.downloadState(it.model) },
                    onDownload = { recommendation.best?.let { actions.onDownload(it.model) } },
                    onCancel = { recommendation.best?.let { actions.onCancel(it.model) } },
                    onOpenChat = { recommendation.best?.let { actions.onOpenChat(it.model) } },
                    onOpenModels = onOpenModels,
                )
            }
            item { CpuSection(profile) }
            item { MemorySection(profile, recommendation) }
            item { GpuSection(profile) }
            item { SystemSection(profile) }
        }
    }
}

@Composable
private fun ScoreCard(profile: DeviceProfile, recommendation: Recommendation) {
    val progress by animateFloatAsState(recommendation.score / 100f, label = "score")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(112.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${recommendation.score}", style = MaterialTheme.typography.displaySmallEmphasized)
                    Text("из 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ИИ-класс устройства", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recommendation.tier.label, style = MaterialTheme.typography.headlineSmallEmphasized)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(onClick = {}, label = { Text("${profile.marketedRamGb} ГБ ОЗУ") })
                    SuggestionChip(onClick = {}, label = { Text("${profile.cpu.cores} ядер") })
                    profile.socName?.let { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
    }
}

@Composable
private fun BestModelCard(
    best: ModelAssessment?,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    icon = Icons.Filled.AutoAwesome,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = 44.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text("Лучший выбор для вас", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                best?.let { VerdictPill(it.verdict) }
            }
            if (best == null) {
                Text(
                    "Не нашлось модели, которая будет комфортно работать: не хватает памяти или свободного места. " +
                        "Освободите место и повторите анализ.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }
            val model = best.model
            Column {
                Text(model.name, style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(
                    "${model.developer} · ${model.parameters} · ${model.quantization}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatBlock("≈${best.estimatedTokensPerSecond.roundToInt().coerceAtLeast(1)} ток/с", "скорость")
                StatBlock("${(best.memoryLoad * 100).roundToInt()}%", "нагрузка ОЗУ")
                StatBlock(formatBytes(model.sizeBytes), "размер")
            }
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            AssistChip(onClick = {}, label = { Text(model.russian.label) })
            ModelDownloadControls(
                model = model,
                state = downloadState ?: DownloadState.NotDownloaded,
                onDownload = onDownload,
                onCancel = onCancel,
                onOpenChat = onOpenChat,
                primary = true,
            )
            TextButton(onClick = onOpenModels, modifier = Modifier.align(Alignment.End)) {
                Text("Все модели")
                Spacer(Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SpecSection(
    title: String,
    icon: ImageVector,
    shapeIndex: Int,
    rows: List<Pair<String, String>>,
    footer: (@Composable () -> Unit)? = null,
) {
    val shapes = listOf(MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon, MaterialShapes.Gem)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapedIcon(
                    icon = icon,
                    shape = shapes[shapeIndex % shapes.size].toShape(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 40.dp,
                )
                Spacer(Modifier.size(12.dp))
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
            }
            Spacer(Modifier.height(12.dp))
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.45f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.55f),
                    )
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun CpuSection(profile: DeviceProfile) {
    val cpu = profile.cpu
    SpecSection(
        title = "Процессор",
        icon = Icons.Filled.DeveloperBoard,
        shapeIndex = 0,
        rows = buildList {
            profile.socName?.let { add("Чипсет" to it) }
            add("Ядра" to cpu.layout.ifBlank { "${cpu.cores}" })
            cpu.maxFreqMhz?.let { add("Макс. частота" to String.format(Locale.ROOT, "%.2f ГГц", it / 1000.0)) }
            add("Потоков для ИИ" to "${Recommender.threads(profile)}")
            add("Архитектура" to profile.abi)
        },
        footer = {
            val features = buildList {
                if (cpu.hasDotProd) add("DotProd")
                if (cpu.hasI8mm) add("I8MM")
                if (cpu.hasFp16) add("FP16")
                if (cpu.hasSve) add("SVE")
            }
            if (features.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Ускорители нейросетей", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }
        },
    )
}

@Composable
private fun MemorySection(profile: DeviceProfile, recommendation: Recommendation) {
    SpecSection(
        title = "Память",
        icon = Icons.Filled.Memory,
        shapeIndex = 1,
        rows = buildList {
            add("Объём ОЗУ" to "${profile.marketedRamGb} ГБ (доступно системе ${formatBytes(profile.totalRamBytes)})")
            add("Свободно сейчас" to formatBytes(profile.availableRamBytes))
            add("Безопасно для ИИ" to formatBytes(recommendation.memoryBudgetBytes))
            add(
                "Пропускная способность" to
                    (profile.memoryBandwidthGbs?.let { String.format(Locale.ROOT, "%.1f ГБ/с", it) } ?: "не измерена"),
            )
            add("Контекст диалога" to "${recommendation.contextSize} токенов")
        },
    )
}

@Composable
private fun GpuSection(profile: DeviceProfile) {
    val gpu = profile.gpu
    SpecSection(
        title = "Графика",
        icon = Icons.Filled.Videocam,
        shapeIndex = 2,
        rows = if (gpu == null) {
            listOf("GPU" to "Нет данных")
        } else {
            buildList {
                add("GPU" to gpu.renderer)
                add("Производитель" to gpu.vendor)
                add("OpenGL ES" to gpu.glVersion)
                add("Vulkan" to (gpu.vulkanVersion ?: "не поддерживается"))
            }
        },
    )
}

@Composable
private fun SystemSection(profile: DeviceProfile) {
    SpecSection(
        title = "Система",
        icon = Icons.Filled.SdStorage,
        shapeIndex = 3,
        rows = buildList {
            add("Свободно в хранилище" to "${formatBytes(profile.storageFreeBytes)} из ${formatBytes(profile.storageTotalBytes)}")
            add("Android" to "${profile.androidRelease} (API ${profile.sdkInt})")
            add("Температура" to profile.thermal.label)
            profile.batteryPercent?.let { add("Батарея" to "$it%" + if (profile.isCharging) " · заряжается" else "") }
            if (profile.mediaPerformanceClass > 0) add("Performance class" to "${profile.mediaPerformanceClass}")
        },
        footer = {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "Нейросеть работает локально: без интернета и без отправки данных.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
