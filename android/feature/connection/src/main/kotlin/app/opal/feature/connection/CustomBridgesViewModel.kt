package app.opal.feature.connection

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.opal.core.data.SettingsRepository
import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.settings.ConnectionMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable data class InvalidLine(val number: Int, val reason: BridgeLine.Reason)

@Immutable
data class CustomBridgesUiState(
    val text: String = "",
    val valid: ImmutableList<BridgeLine> = persistentListOf(),
    val invalid: ImmutableList<InvalidLine> = persistentListOf(),
    val loaded: Boolean = false,
    val saved: Boolean = true,
)

class CustomBridgesViewModel(private val settings: SettingsRepository) : ViewModel() {

    private val _state = MutableStateFlow(CustomBridgesUiState())
    val state: StateFlow<CustomBridgesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lines = settings.current().customBridges
            _state.value = validate(lines.joinToString("\n")).copy(loaded = true, saved = true)
        }
    }

    fun setText(text: String) {
        _state.update { validate(text).copy(loaded = true, saved = false) }
    }

    /** Appends bridges found in scanned or pasted content; returns how many were new. */
    fun addFrom(content: String): Int {
        val found = BridgeLine.extractAll(content)
        val current = _state.value
        val known = current.valid.map { it.raw }.toSet()
        val fresh = found.filter { it.raw !in known }
        if (fresh.isNotEmpty()) {
            val text =
                (current.text.trimEnd().lines().filter { it.isNotBlank() } + fresh.map { it.raw })
                    .joinToString("\n")
            setText(text)
        }
        return fresh.size
    }

    /**
     * Saves the valid lines; a non-empty list switches to "own bridges", an empty one back to Auto.
     */
    fun save(onDone: (switchedToCustom: Boolean) -> Unit) {
        val lines = _state.value.valid.map { it.raw }
        viewModelScope.launch {
            settings.update { s ->
                s.copy(
                    customBridges = lines,
                    connectionMode =
                        when {
                            lines.isNotEmpty() -> ConnectionMode.Custom
                            s.connectionMode == ConnectionMode.Custom -> ConnectionMode.Snowflake
                            else -> s.connectionMode
                        },
                )
            }
            _state.update { it.copy(saved = true) }
            onDone(lines.isNotEmpty())
        }
    }

    companion object {
        fun validate(text: String): CustomBridgesUiState {
            val valid = mutableListOf<BridgeLine>()
            val invalid = mutableListOf<InvalidLine>()
            text.lines().forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed
                when (val result = BridgeLine.parse(line)) {
                    is BridgeLine.ParseResult.Ok ->
                        if (valid.none { it.raw == result.line.raw }) valid += result.line
                    is BridgeLine.ParseResult.Invalid ->
                        invalid += InvalidLine(index + 1, result.reason)
                }
            }
            return CustomBridgesUiState(
                text = text,
                valid = valid.toImmutableList(),
                invalid = invalid.toImmutableList(),
            )
        }
    }
}
