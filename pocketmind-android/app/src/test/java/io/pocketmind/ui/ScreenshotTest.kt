package io.pocketmind.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import io.pocketmind.AnalysisState
import io.pocketmind.download.DownloadState
import io.pocketmind.engine.EngineState
import io.pocketmind.engine.Role
import io.pocketmind.hardware.CpuCluster
import io.pocketmind.models.ModelCatalog
import io.pocketmind.models.Recommender
import io.pocketmind.models.TestProfiles
import io.pocketmind.ui.chat.ChatMessage
import io.pocketmind.ui.chat.ChatScreen
import io.pocketmind.ui.chat.ChatUiState
import io.pocketmind.ui.device.DeviceScreen
import io.pocketmind.ui.models.ModelsScreen
import io.pocketmind.ui.theme.PocketMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens to PNG (`./gradlew recordRoborazziDebug`) for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val profile = TestProfiles.profile(
        ramGib = 11.2,
        bandwidth = 38.0,
        features = setOf("asimd", "asimdhp", "asimddp", "i8mm", "sve2"),
        clusters = listOf(
            CpuCluster("Cortex-X4", 1, 3300),
            CpuCluster("Cortex-A720", 3, 3150),
            CpuCluster("Cortex-A720", 2, 2960),
            CpuCluster("Cortex-A520", 2, 2270),
        ),
    ).copy(manufacturer = "Samsung", model = "Galaxy S25", socName = "Snapdragon 8 Elite")
    private val recommendation = Recommender.recommend(profile)
    private val best = recommendation.best!!.model

    private fun home(downloads: Map<String, DownloadState>) = HomeUiState(
        analysis = AnalysisState.Done(profile, recommendation),
        downloads = downloads,
        activeModelId = best.id,
    )

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            PocketMindTheme(darkTheme = dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { content() }
            }
        }
        compose.onRoot().captureRoboImage(File("screenshots/$name.png").path)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())

    @Test
    fun device() = capture("1_device") {
        DeviceScreen(
            state = home(mapOf(best.id to DownloadState.Downloading(1, best.sizeBytes * 42 / 100, best.sizeBytes))),
            actions = ModelActions(),
            onRescan = {},
            onOpenModels = {},
        )
    }

    @Test
    fun deviceDark() = capture("2_device_dark", dark = true) {
        DeviceScreen(state = home(emptyMap()), actions = ModelActions(), onRescan = {}, onOpenModels = {})
    }

    @Test
    fun analysis() = capture("3_analysis") {
        DeviceScreen(
            state = HomeUiState(AnalysisState.Running("Измеряю пропускную способность памяти"), emptyMap(), null),
            actions = ModelActions(),
            onRescan = {},
            onOpenModels = {},
        )
    }

    @Test
    fun models() = capture("4_models") {
        val downloaded = ModelCatalog.byId("qwen3.5-2b")!!
        ModelsScreen(
            state = home(mapOf(downloaded.id to DownloadState.Downloaded(File("x")))).copy(activeModelId = downloaded.id),
            actions = ModelActions(),
        )
    }

    @Test
    fun chat() = capture("5_chat") {
        val model = ModelCatalog.byId("gemma4-e2b")!!
        ChatScreen(
            state = ChatUiState(
                messages = listOf(
                    ChatMessage(0, Role.User, "Объясни простыми словами, что такое квантование нейросети"),
                    ChatMessage(
                        1, Role.Assistant,
                        "**Квантование** — это сжатие нейросети за счёт хранения весов с меньшей точностью.\n\n" +
                            "- Вместо 16 бит на число используется `4 бита`\n" +
                            "- Модель становится в 3–4 раза меньше\n" +
                            "- Качество ответов почти не страдает\n\n" +
                            "Именно поэтому большие модели помещаются в память телефона.",
                        tokensPerSecond = 14.7,
                    ),
                    ChatMessage(2, Role.User, "А какой формат у моделей в этом приложении?"),
                    ChatMessage(3, Role.Assistant, "", streaming = true),
                ),
                generating = true,
                downloadedModels = listOf(model, ModelCatalog.byId("qwen3.5-2b")!!),
                selectedModel = model,
                engine = EngineState.Ready(model, 8192),
            ),
            input = "",
            onInputChange = {}, onSend = {}, onStop = {}, onSelectModel = {}, onNewChat = {}, onOpenModels = {},
        )
    }

    @Test
    fun chatEmpty() = capture("6_chat_welcome", dark = true) {
        val model = ModelCatalog.byId("gemma4-e2b")!!
        ChatScreen(
            state = ChatUiState(downloadedModels = listOf(model), selectedModel = model, engine = EngineState.Ready(model, 8192)),
            input = "",
            onInputChange = {}, onSend = {}, onStop = {}, onSelectModel = {}, onNewChat = {}, onOpenModels = {},
        )
    }
}
