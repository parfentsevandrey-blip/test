package com.claude.tokenwidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.claude.tokenwidget.data.SourceMode
import com.claude.tokenwidget.data.UsageConfig
import com.claude.tokenwidget.data.UsageData
import com.claude.tokenwidget.data.UsageDataStore
import com.claude.tokenwidget.data.UsageRepository
import com.claude.tokenwidget.widget.TokenWidget
import com.claude.tokenwidget.widget.UsageWorker
import com.claude.tokenwidget.widget.updateEveryInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val store = remember { UsageDataStore(context) }
    val repo = remember { UsageRepository(context) }
    val scope = rememberCoroutineScope()

    // Local editable state, seeded once from DataStore.
    var mode by remember { mutableStateOf(SourceMode.LOCAL) }
    var sessionUsed by remember { mutableStateOf("") }
    var sessionLimit by remember { mutableStateOf("") }
    var weeklyUsed by remember { mutableStateOf("") }
    var weeklyLimit by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(UsageData.PLACEHOLDER) }

    LaunchedEffect(Unit) {
        val cfg = store.configFlow.first()
        val data = store.usageFlow.first()
        mode = cfg.mode
        endpoint = cfg.apiEndpoint
        apiKey = cfg.apiKey
        sessionUsed = data.sessionTokensUsed.toString()
        sessionLimit = data.sessionTokenLimit.toString()
        weeklyUsed = data.weeklyTokensUsed.toString()
        weeklyLimit = data.weeklyTokenLimit.toString()
        preview = data
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Claude · расход токенов") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PreviewCard(preview)
            Spacer(Modifier.height(20.dp))

            Text("Источник данных", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == SourceMode.LOCAL,
                    onClick = { mode = SourceMode.LOCAL },
                    label = { Text("Локально / ручной ввод") },
                )
                FilterChip(
                    selected = mode == SourceMode.REMOTE,
                    onClick = { mode = SourceMode.REMOTE },
                    label = { Text("Удалённый API") },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (mode == SourceMode.LOCAL) {
                Text("Сессия (5-часовое окно)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                NumberField("Использовано токенов", sessionUsed) { sessionUsed = it }
                Spacer(Modifier.height(8.dp))
                NumberField("Лимит токенов", sessionLimit) { sessionLimit = it }

                Spacer(Modifier.height(16.dp))
                Text("Неделя", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                NumberField("Использовано токенов", weeklyUsed) { weeklyUsed = it }
                Spacer(Modifier.height(8.dp))
                NumberField("Лимит токенов", weeklyLimit) { weeklyLimit = it }
            } else {
                Text(
                    "Укажите HTTP-эндпоинт, отдающий JSON в формате UsageData " +
                        "(см. README). Виджет будет опрашивать его в фоне.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("URL эндпоинта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API-ключ (опционально)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        store.saveConfig(
                            UsageConfig(mode = mode, apiKey = apiKey.trim(), apiEndpoint = endpoint.trim()),
                        )
                        if (mode == SourceMode.LOCAL) {
                            val data = UsageData(
                                sessionTokensUsed = sessionUsed.toLongOrNull() ?: 0,
                                sessionTokenLimit = (sessionLimit.toLongOrNull() ?: 1).coerceAtLeast(1),
                                weeklyTokensUsed = weeklyUsed.toLongOrNull() ?: 0,
                                weeklyTokenLimit = (weeklyLimit.toLongOrNull() ?: 1).coerceAtLeast(1),
                                sessionResetAt = preview.sessionResetAt,
                                weeklyResetAt = preview.weeklyResetAt,
                                updatedAt = System.currentTimeMillis(),
                            )
                            repo.saveManual(data)
                            preview = data
                        } else {
                            preview = repo.refresh(System.currentTimeMillis())
                        }
                        UsageWorker.enqueuePeriodic(context)
                        TokenWidget().updateEveryInstance(context)
                        status = "Сохранено и виджеты обновлены"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить и обновить виджет")
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PreviewCard(data: UsageData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Предпросмотр", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            MeterRow("Сессия", data.sessionFraction, data.sessionPercent, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            MeterRow("Неделя", data.weeklyFraction, data.weeklyPercent, MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun MeterRow(title: String, fraction: Float, percent: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp),
                color = color,
            )
            Spacer(Modifier.width(12.dp))
            Text("$percent%", style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
