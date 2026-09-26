package app.opal.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.opal.core.designsystem.component.SheetHost
import app.opal.core.designsystem.glass.ToastState
import app.opal.core.designsystem.theme.AuroraMood
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.feature.home.HomeAction
import app.opal.feature.home.HomeScreen
import app.opal.feature.home.HomeUiState
import app.opal.feature.home.NEW_IDENTITY_TAG
import app.opal.feature.home.POWER_BUTTON_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The power button in the thumb zone (above the tab bar) does what the sphere does, and once
 * connected a new identity sits next to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru", application = android.app.Application::class)
class PowerAccessoryTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<HomeAction>()

    private fun show(state: TunnelState) {
        compose.setContent {
            OpalTheme(dark = false, simplifiedGraphics = true) {
                OpalScaffold(
                    mood = AuroraMood.Idle,
                    tabIndex = 0,
                    compact = false,
                    onSelectTab = {},
                    onScroll = {},
                    sheets = remember { SheetHost() },
                    toast = remember { ToastState() },
                    animateAurora = false,
                ) { padding ->
                    HomeScreen(
                        HomeUiState(snapshot = TunnelSnapshot(state = state, connectedSince = 0)),
                        padding,
                        { actions += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `off - connect from the bottom`() {
        show(TunnelState.Off)
        compose.onNodeWithTag(POWER_BUTTON_TAG, useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithTag(POWER_BUTTON_TAG).assertTextEquals("Подключить").performClick()
        assertEquals(listOf<HomeAction>(HomeAction.Toggle), actions)
        compose.onNodeWithTag(NEW_IDENTITY_TAG).assertDoesNotExist()
    }

    @Test
    fun `connected - disconnect and new identity from the bottom`() {
        show(TunnelState.Connected)
        compose.onNodeWithTag(POWER_BUTTON_TAG).assertTextEquals("Отключить").performClick()
        compose.onNodeWithTag(NEW_IDENTITY_TAG).performClick()
        assertEquals(listOf(HomeAction.Toggle, HomeAction.NewIdentity), actions)
    }

    @Test
    fun `disconnecting - the button waits`() {
        show(TunnelState.Stopping)
        compose.onNodeWithTag(POWER_BUTTON_TAG).assertTextEquals("Отключение…").assertIsNotEnabled()
    }
}
