package io.pocketmind.ui.chat

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.pocketmind.engine.EngineState
import io.pocketmind.engine.Role
import io.pocketmind.models.LlmModel
import io.pocketmind.ui.components.ShapedIcon
import kotlinx.coroutines.launch
import java.util.Locale

private val Suggestions = listOf(
    "Объясни простыми словами, как работает нейросеть",
    "Придумай план тренировок на неделю",
    "Помоги написать вежливое письмо коллеге",
    "Какие есть идеи для ужина из курицы и риса?",
)

@Composable
fun ChatScreen(viewModel: ChatViewModel, onOpenModels: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()

    LaunchedEffect(state.selectedModel?.id) {
        if (state.selectedModel != null) viewModel.ensureLoaded()
    }
    ChatScreen(
        state = state,
        input = input,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onSelectModel = viewModel::selectModel,
        onNewChat = viewModel::newChat,
        onOpenModels = onOpenModels,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onSelectModel: (LlmModel) -> Unit,
    onNewChat: () -> Unit,
    onOpenModels: () -> Unit,
) {
    Scaffold(
        topBar = {
            ChatTopBar(
                state = state,
                onSelect = onSelectModel,
                onNewChat = onNewChat,
            )
        },
        bottomBar = {
            if (state.selectedModel != null) {
                InputBar(
                    value = input,
                    generating = state.generating,
                    enabled = state.engine !is EngineState.Failed,
                    onValueChange = onInputChange,
                    onSend = { onSend(input) },
                    onStop = onStop,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                state.selectedModel == null -> NoModelState(onOpenModels)
                state.messages.isEmpty() -> WelcomeState(state, onSuggestion = onSend)
                else -> MessageList(state.messages)
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    state: ChatUiState,
    onSelect: (LlmModel) -> Unit,
    onNewChat: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Box {
                Column(
                    Modifier
                        .clickable(enabled = state.downloadedModels.size > 1) { menuOpen = true }
                        .padding(vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.selectedModel?.name ?: "Диалог", style = MaterialTheme.typography.titleLargeEmphasized)
                        if (state.downloadedModels.size > 1) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Сменить модель")
                    }
                    val status = when (val engine = state.engine) {
                        is EngineState.Loading -> "Загружаю в память…"
                        is EngineState.Ready -> "Офлайн · контекст ${engine.contextSize} токенов"
                        is EngineState.Failed -> "Ошибка загрузки"
                        EngineState.Idle -> if (state.selectedModel != null) "Готовлю модель…" else "Модель не выбрана"
                    }
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    state.downloadedModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                menuOpen = false
                                onSelect(model)
                            },
                            trailingIcon = if (model.id == state.selectedModel?.id) {
                                { Icon(Icons.Filled.AutoAwesome, contentDescription = null) }
                            } else null,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onNewChat, enabled = state.messages.isNotEmpty()) {
                Icon(Icons.Filled.EditNote, contentDescription = "Новый диалог")
            }
        },
    )
}

@Composable
private fun NoModelState(onOpenModels: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapedIcon(
            icon = Icons.Filled.ViewInAr,
            shape = MaterialShapes.Cookie12Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            size = 112.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text("Сначала скачайте модель", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Мы уже подобрали нейросеть, которая подходит вашему телефону. Скачайте её один раз — и общайтесь без интернета.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenModels, shapes = ButtonDefaults.shapes()) { Text("Выбрать модель") }
    }
}

@Composable
private fun WelcomeState(state: ChatUiState, onSuggestion: (String) -> Unit) {
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(targetState = state.engine is EngineState.Loading, label = "loading") { loading ->
            if (loading) {
                LoadingIndicator(Modifier.size(112.dp))
            } else {
                ShapedIcon(
                    icon = Icons.Filled.AutoAwesome,
                    shape = MaterialShapes.SoftBurst.toShape(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 112.dp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Чем могу помочь?", style = MaterialTheme.typography.headlineMediumEmphasized)
        Spacer(Modifier.height(8.dp))
        Text(
            (state.engine as? EngineState.Failed)?.message
                ?: "${state.selectedModel?.name} работает прямо на вашем телефоне. Переписка никуда не отправляется.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.engine is EngineState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Suggestions.forEach { suggestion ->
                SuggestionChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>) {
    val listState = rememberLazyListState()
    val last = messages.lastOrNull()
    LaunchedEffect(messages.size, last?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE / 2)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == Role.User
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = when {
                message.error -> scheme.errorContainer
                isUser -> scheme.primary
                else -> scheme.surfaceContainerHigh
            },
            contentColor = when {
                message.error -> scheme.onErrorContainer
                isUser -> scheme.onPrimary
                else -> scheme.onSurface
            },
            shape = if (isUser) RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            if (message.streaming && message.text.isEmpty()) {
                LoadingIndicator(Modifier.padding(10.dp).size(36.dp))
            } else {
                Text(
                    text = if (isUser) AnnotatedString(message.text) else renderMarkdown(message.text, scheme.primary),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (!isUser && !message.streaming && !message.error) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                IconButton(
                    onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("PocketMind", message.text))) } },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                }
                message.tokensPerSecond?.let {
                    Text(
                        String.format(Locale.ROOT, "%.1f ток/с", it),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    generating: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(6.dp)) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("Сообщение") },
                    maxLines = 6,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(targetState = generating, label = "send") { busy ->
                    FilledIconButton(
                        onClick = if (busy) onStop else onSend,
                        enabled = busy || (enabled && value.isNotBlank()),
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (busy) "Остановить" else "Отправить",
                        )
                    }
                }
            }
        }
    }
}

/** Minimal Markdown: **bold**, `code`, headings and bullet lists, enough for chat answers. */
private fun renderMarkdown(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    var inCode = false
    lines.forEachIndexed { index, rawLine ->
        if (rawLine.trimStart().startsWith("```")) {
            inCode = !inCode
            return@forEachIndexed
        }
        if (inCode) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(rawLine) }
            if (index < lines.lastIndex) append('\n')
            return@forEachIndexed
        }
        var line = rawLine
        var heading = false
        when {
            line.startsWith("#") -> {
                line = line.trimStart('#').trimStart()
                heading = true
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val indent = line.length - line.trimStart().length
                line = " ".repeat(indent) + "• " + line.trimStart().drop(2)
            }
        }
        if (heading) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val end = line.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(line.substring(i)); i = line.length
                    }
                }
                line[i] == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent)) { append(line.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(line[i]); i++
                    }
                }
                else -> {
                    append(line[i]); i++
                }
            }
        }
        if (heading) pop()
        if (index < lines.lastIndex) append('\n')
    }
}
