package io.pocketmind.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pocketmind.AnalysisState
import io.pocketmind.AppContainer
import io.pocketmind.download.DownloadState
import io.pocketmind.engine.ChatTurn
import io.pocketmind.engine.EngineState
import io.pocketmind.engine.GenerationEvent
import io.pocketmind.engine.Role
import io.pocketmind.models.LlmModel
import io.pocketmind.models.ModelCatalog
import io.pocketmind.models.Recommender
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val tokensPerSecond: Double? = null,
    val error: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val downloadedModels: List<LlmModel> = emptyList(),
    val selectedModel: LlmModel? = null,
    val engine: EngineState = EngineState.Idle,
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val generating = MutableStateFlow(false)
    private var generationJob: Job? = null
    private var nextId = 0L

    private val downloadedModels = container.downloads.states.combine(container.analysis) { states, _ ->
        ModelCatalog.models.filter { states[it.id] is DownloadState.Downloaded }
    }

    val state: StateFlow<ChatUiState> = combine(
        messages,
        generating,
        downloadedModels,
        container.activeModelId,
        container.engine.state,
    ) { messages, generating, downloaded, activeId, engine ->
        ChatUiState(
            messages = messages,
            generating = generating,
            downloadedModels = downloaded,
            selectedModel = downloaded.firstOrNull { it.id == activeId } ?: downloaded.lastOrNull(),
            engine = engine,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun selectModel(model: LlmModel) {
        viewModelScope.launch { container.setActiveModel(model.id) }
    }

    /** Loads the selected model into memory if it is not there yet. */
    fun ensureLoaded() {
        viewModelScope.launch { loadSelected() }
    }

    private suspend fun loadSelected(): Boolean {
        val model = state.value.selectedModel ?: return false
        val engine = container.engine.state.value
        if (engine is EngineState.Ready && engine.model.id == model.id) return true
        if (engine is EngineState.Loading) {
            return container.engine.state.first { it !is EngineState.Loading } is EngineState.Ready
        }
        val file = container.downloads.downloadedFile(model) ?: return false
        val analysis = container.analysis.first { it is AnalysisState.Done } as AnalysisState.Done
        container.engine.load(
            model = model,
            file = file,
            contextSize = Recommender.contextSize(analysis.profile),
            threads = Recommender.threads(analysis.profile),
        )
        return container.engine.state.value is EngineState.Ready
    }

    fun send(text: String = _input.value) {
        val prompt = text.trim()
        if (prompt.isEmpty() || generating.value) return
        _input.value = ""
        val assistantId = nextId + 1
        messages.update {
            it + ChatMessage(nextId++, Role.User, prompt) + ChatMessage(nextId++, Role.Assistant, "", streaming = true)
        }
        generating.value = true

        generationJob = viewModelScope.launch {
            try {
                if (!loadSelected()) {
                    finishWithError(assistantId, "Не удалось загрузить модель в память.")
                    return@launch
                }
                val conversation = buildList {
                    add(ChatTurn(Role.System, SYSTEM_PROMPT))
                    messages.value.filter { !it.error && !it.streaming && it.text.isNotBlank() }
                        .forEach { add(ChatTurn(it.role, it.text)) }
                }
                val reply = StringBuilder()
                container.engine.generate(conversation).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            reply.append(event.text)
                            updateMessage(assistantId) { it.copy(text = cleanReply(reply.toString())) }
                        }
                        is GenerationEvent.Finished -> updateMessage(assistantId) {
                            it.copy(
                                text = cleanReply(reply.toString()).ifBlank { "…" },
                                streaming = false,
                                tokensPerSecond = event.tokensPerSecond.takeIf { event.tokens > 2 },
                            )
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateMessage(assistantId) { it.copy(text = it.text.ifBlank { "Остановлено" }, streaming = false) }
                throw e
            } catch (e: Exception) {
                finishWithError(assistantId, e.message ?: "Ошибка генерации")
            } finally {
                generating.value = false
            }
        }
    }

    fun stop() {
        container.engine.stop()
        generationJob?.cancel()
    }

    fun newChat() {
        stop()
        messages.value = emptyList()
    }

    private fun finishWithError(id: Long, message: String) =
        updateMessage(id) { it.copy(text = message, streaming = false, error = true) }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    companion object {
        const val SYSTEM_PROMPT =
            "Ты — PocketMind, дружелюбный и точный ИИ-ассистент. Ты работаешь полностью офлайн прямо на " +
                "телефоне пользователя. Отвечай на языке пользователя, понятно и по существу. " +
                "Используй списки, когда это помогает."

        /** Hides reasoning blocks some models emit even when thinking is disabled. */
        fun cleanReply(raw: String): String {
            var text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
            val open = text.indexOf("<think>")
            if (open >= 0) text = text.substring(0, open)
            return text.trimStart()
        }
    }
}
