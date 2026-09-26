package app.opal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.opal.core.designsystem.component.SheetHost
import app.opal.core.designsystem.glass.ToastState
import app.opal.core.designsystem.theme.AuroraMood
import app.opal.core.designsystem.theme.OpalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tab bar lens: a tap selects, sliding without lifting the finger selects the tab under it only
 * on release, and TalkBack selects through semantics. Run with live glass (the refracting lens) and
 * with simplified graphics (a flat pill) — the gesture is the same.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [36],
    qualifiers = "ru-w393dp-h852dp-hdpi",
    application = android.app.Application::class,
)
class TabBarTest(private val simplified: Boolean) {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val selections = mutableListOf<Int>()

    private fun show(initial: Int) {
        compose.setContent {
            var tab by remember { mutableIntStateOf(initial) }
            OpalTheme(dark = false, simplifiedGraphics = simplified) {
                OpalScaffold(
                    mood = AuroraMood.Idle,
                    tabIndex = tab,
                    compact = false,
                    onSelectTab = {
                        selections += it
                        tab = it
                    },
                    onScroll = {},
                    sheets = remember { SheetHost() },
                    toast = remember { ToastState() },
                    animateAurora = false,
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun centerOf(tag: String): Offset =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun `a tap selects the tab once`() {
        show(initial = 0)
        val target = centerOf(TABS[2])
        compose.onRoot().performTouchInput { click(target) }
        compose.waitForIdle()
        assertEquals(listOf(2), selections)
        compose.onNodeWithTag(TABS[2]).assertIsSelected()
        compose.onNodeWithTag(TABS[0]).assertIsNotSelected()
    }

    @Test
    fun `sliding without lifting selects the tab under the finger on release`() {
        show(initial = 0)
        val points = TABS.map(::centerOf)
        compose.onRoot().performTouchInput {
            down(points[0])
            moveTo(points[1])
            moveTo(points[2])
            moveTo(points[3])
        }
        compose.waitForIdle()
        // Nothing changes while the finger is down.
        assertEquals(emptyList<Int>(), selections)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf(3), selections)
    }

    @Test
    fun `sliding back to where it started selects nothing`() {
        show(initial = 1)
        val points = TABS.map(::centerOf)
        compose.onRoot().performTouchInput {
            down(points[1])
            moveTo(points[3])
            moveTo(points[0])
            moveTo(points[1])
            up()
        }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), selections)
    }

    @Test
    fun `the finger may leave the bar sideways and still picks the nearest tab`() {
        show(initial = 3)
        val first = centerOf(TABS[0])
        compose.onRoot().performTouchInput {
            down(centerOf(TABS[3]))
            // Past the left end of the bar.
            moveTo(Offset(1f, first.y))
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(0), selections)
    }

    @Test
    fun `TalkBack selects through semantics`() {
        show(initial = 0)
        compose
            .onNodeWithTag(TABS[1])
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsNotSelected()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf(1), selections)
        compose.onNodeWithTag(TABS[1]).assertIsSelected()
    }

    companion object {
        private val TABS = listOf("tab_home", "tab_apps", "tab_connection", "tab_settings")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "simplified={0}")
        fun variants() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
