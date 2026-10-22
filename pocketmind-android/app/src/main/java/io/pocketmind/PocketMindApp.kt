package io.pocketmind

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pocketmind.download.ModelDownloads
import io.pocketmind.engine.LlamaEngine
import io.pocketmind.hardware.DeviceProfile
import io.pocketmind.hardware.HardwareAnalyzer
import io.pocketmind.models.Recommendation
import io.pocketmind.models.Recommender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "pocketmind")

class PocketMindApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

sealed interface AnalysisState {
    data class Running(val step: String) : AnalysisState
    data class Done(val profile: DeviceProfile, val recommendation: Recommendation) : AnalysisState
}

/** Manual dependency container: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.dataStore

    val engine = LlamaEngine(context)
    val downloads = ModelDownloads(context, prefs, scope)
    private val analyzer = HardwareAnalyzer(context, engine)

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Running("Подготовка"))
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val activeModelKey = stringPreferencesKey("active_model")
    val activeModelId: Flow<String?> = prefs.data.map { it[activeModelKey] }

    private var analysisJob: Job? = null

    init {
        analyze()
    }

    fun analyze() {
        if (analysisJob?.isActive == true) return
        analysisJob = scope.launch {
            _analysis.value = AnalysisState.Running("Подготовка")
            val profile = analyzer.analyze { step -> _analysis.value = AnalysisState.Running(step) }
            _analysis.value = AnalysisState.Done(profile, Recommender.recommend(profile))
        }
    }

    suspend fun setActiveModel(id: String) {
        prefs.edit { it[activeModelKey] = id }
    }
}

val Context.container: AppContainer get() = (applicationContext as PocketMindApp).container
