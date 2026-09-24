package app.opal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.feature.apps.AppRow
import app.opal.feature.apps.AppsAction
import app.opal.feature.apps.AppsScreen
import app.opal.feature.apps.AppsUiState
import app.opal.feature.connection.ConnectionAction
import app.opal.feature.connection.ConnectionScreen
import app.opal.feature.connection.ConnectionUiState
import app.opal.feature.home.HomeAction
import app.opal.feature.home.HomeScreen
import app.opal.feature.home.HomeUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stateless screens emit the right actions and expose the right semantics (TalkBack). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru", application = android.app.Application::class)
class InteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `sphere connects when off and disconnects when connected`() {
        val actions = mutableListOf<HomeAction>()
        var state = HomeUiState(snapshot = TunnelSnapshot(state = TunnelState.Off))
        compose.setContent {
            OpalTheme(dark = false, simplifiedGraphics = true) {
                HomeScreen(state, PaddingValues(), { actions += it })
            }
        }
        compose
            .onNodeWithContentDescription("Подключить")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Не защищено")
            )
            .performClick()
        assertEquals(listOf<HomeAction>(HomeAction.Toggle), actions)
    }

    @Test
    fun `connected sphere offers disconnect`() {
        compose.setContent {
            OpalTheme(dark = false, simplifiedGraphics = true) {
                HomeScreen(
                    HomeUiState(
                        snapshot = TunnelSnapshot(state = TunnelState.Connected, connectedSince = 0)
                    ),
                    PaddingValues(),
                    {},
                )
            }
        }
        compose
            .onNodeWithContentDescription("Отключить")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Защищено"))
    }

    @Test
    fun `choosing a transport emits SetMode`() {
        val actions = mutableListOf<ConnectionAction>()
        compose.setContent {
            OpalTheme(dark = false, simplifiedGraphics = true) {
                ConnectionScreen(ConnectionUiState(), PaddingValues(), onAction = { actions += it })
            }
        }
        compose.onNodeWithText("Snowflake").performClick()
        assertEquals(
            listOf<ConnectionAction>(ConnectionAction.SetMode(ConnectionMode.Snowflake)),
            actions,
        )
    }

    @Test
    fun `checking an app emits SetChecked`() {
        val actions = mutableListOf<AppsAction>()
        val state =
            AppsUiState(
                loading = false,
                rows =
                    persistentListOf(
                        AppRow("org.mozilla.firefox", "Firefox", isSystem = false, checked = false)
                    ),
            )
        compose.setContent {
            OpalTheme(dark = false, simplifiedGraphics = true) {
                AppsScreen(state, PaddingValues(), onAction = { actions += it })
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Firefox"))
        compose.onNodeWithText("Firefox").performClick()
        assertEquals(
            listOf<AppsAction>(AppsAction.SetChecked("org.mozilla.firefox", true)),
            actions,
        )
    }
}
