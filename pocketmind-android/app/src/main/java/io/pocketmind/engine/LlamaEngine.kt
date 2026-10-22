package io.pocketmind.engine

import android.content.Context
import io.pocketmind.models.LlmModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

enum class Role(val wire: String) { System("system"), User("user"), Assistant("assistant") }

data class ChatTurn(val role: Role, val content: String)

sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val model: LlmModel) : EngineState
    data class Ready(val model: LlmModel, val contextSize: Int) : EngineState
    data class Failed(val model: LlmModel, val message: String) : EngineState
}

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Finished(val tokens: Int, val tokensPerSecond: Double, val contextFull: Boolean) : GenerationEvent
}

/**
 * Owns the single native chat session. Every native call runs on one dedicated
 * thread, so loading, generation and unloading never overlap.
 */
class LlamaEngine(context: Context) {
    private val nativeLibDir = context.applicationInfo.nativeLibraryDir
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "llama-engine") }.asCoroutineDispatcher()

    private var initialized = false
    @Volatile private var handle = 0L

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private fun ensureInitialized() {
        if (!initialized) {
            LlamaNative.init(nativeLibDir)
            initialized = true
        }
    }

    suspend fun load(model: LlmModel, file: File, contextSize: Int, threads: Int) = withContext(dispatcher) {
        val current = _state.value
        if (current is EngineState.Ready && current.model.id == model.id) return@withContext
        releaseHandle()
        _state.value = EngineState.Loading(model)
        ensureInitialized()
        val newHandle = LlamaNative.load(file.absolutePath, contextSize, threads)
        if (newHandle == 0L) {
            _state.value = EngineState.Failed(model, "Не удалось загрузить модель. Возможно, файл повреждён или не хватает памяти.")
        } else {
            handle = newHandle
            _state.value = EngineState.Ready(model, LlamaNative.contextSize(newHandle))
        }
    }

    suspend fun unload() = withContext(dispatcher) {
        releaseHandle()
        _state.value = EngineState.Idle
    }

    /** Interrupts prompt processing or generation; safe to call from any thread. */
    fun stop() {
        val h = handle
        if (h != 0L) LlamaNative.cancel(h)
    }

    /**
     * Streams the assistant reply. If the conversation no longer fits the
     * context window, the oldest exchanges are dropped (the system prompt stays).
     */
    fun generate(conversation: List<ChatTurn>, maxTokens: Int = 2048): Flow<GenerationEvent> = flow {
        val h = handle
        check(h != 0L) { "Модель не загружена" }

        var turns = conversation
        var promptTokens: Int
        while (true) {
            promptTokens = LlamaNative.beginTurn(
                h,
                turns.map { it.role.wire }.toTypedArray(),
                turns.map { it.content }.toTypedArray(),
                false,
            )
            if (promptTokens != LlamaNative.TURN_CONTEXT_OVERFLOW) break
            val firstDroppable = turns.indexOfFirst { it.role != Role.System }
            // Never drop the latest user message.
            if (firstDroppable < 0 || firstDroppable >= turns.lastIndex) break
            turns = turns.toMutableList().apply { removeAt(firstDroppable) }
        }
        when {
            promptTokens == LlamaNative.TURN_CANCELLED -> {
                emit(GenerationEvent.Finished(0, 0.0, contextFull = false))
                return@flow
            }
            promptTokens == LlamaNative.TURN_CONTEXT_OVERFLOW ->
                error("Сообщение слишком длинное для контекста модели")
            promptTokens < 0 -> error("Ошибка обработки запроса (код $promptTokens)")
        }

        val start = System.nanoTime()
        var produced = 0
        while (produced < maxTokens) {
            currentCoroutineContext().ensureActive()
            val bytes = LlamaNative.nextToken(h) ?: break
            produced++
            if (bytes.isNotEmpty()) emit(GenerationEvent.Token(String(bytes, Charsets.UTF_8)))
        }
        val seconds = (System.nanoTime() - start) / 1e9
        emit(
            GenerationEvent.Finished(
                tokens = produced,
                tokensPerSecond = if (seconds > 0) produced / seconds else 0.0,
                contextFull = LlamaNative.contextFull(h),
            ),
        )
    }.flowOn(dispatcher)

    /** Measures sustained memory read bandwidth in GB/s (the main limit for LLM speed). */
    suspend fun benchmarkMemory(threads: Int): Double = withContext(dispatcher) {
        // Two short runs: the first one wakes up the big cores and the memory controller.
        LlamaNative.benchmarkMemory(32, threads)
        LlamaNative.benchmarkMemory(96, threads)
    }

    private fun releaseHandle() {
        val h = handle
        if (h != 0L) {
            handle = 0L
            LlamaNative.free(h)
        }
    }
}
