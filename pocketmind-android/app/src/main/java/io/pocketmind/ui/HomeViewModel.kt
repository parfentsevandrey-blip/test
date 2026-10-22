package io.pocketmind.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pocketmind.AnalysisState
import io.pocketmind.AppContainer
import io.pocketmind.download.DownloadState
import io.pocketmind.engine.EngineState
import io.pocketmind.models.LlmModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val analysis: AnalysisState,
    val downloads: Map<String, DownloadState>,
    val activeModelId: String?,
) {
    fun downloadState(model: LlmModel) = downloads[model.id] ?: DownloadState.NotDownloaded
}

class ModelActions(
    val onDownload: (LlmModel) -> Unit = {},
    val onCancel: (LlmModel) -> Unit = {},
    val onOpenChat: (LlmModel) -> Unit = {},
    val onDelete: (LlmModel) -> Unit = {},
)

/** Shared by the device and models screens: hardware analysis + model downloads. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        container.analysis,
        container.downloads.states,
        container.activeModelId,
        ::HomeUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(container.analysis.value, container.downloads.states.value, null),
    )

    fun rescan() = container.analyze()

    fun download(model: LlmModel) = container.downloads.start(model)

    fun cancel(model: LlmModel) = container.downloads.cancel(model)

    fun delete(model: LlmModel) {
        viewModelScope.launch {
            val engineState = container.engine.state.value
            if (engineState is EngineState.Ready && engineState.model.id == model.id) {
                container.engine.unload()
            }
            container.downloads.delete(model)
        }
    }

    fun select(model: LlmModel) {
        viewModelScope.launch { container.setActiveModel(model.id) }
    }
}
