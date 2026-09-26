package app.opal.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import app.opal.core.designsystem.component.SheetHost
import app.opal.core.designsystem.glass.ToastState
import app.opal.core.designsystem.theme.AuroraMood
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppLanguage
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.settings.SplitTunnelMode
import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.BootstrapPhase
import app.opal.core.model.tunnel.CircuitHop
import app.opal.core.model.tunnel.CircuitInfo
import app.opal.core.model.tunnel.HopRole
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.model.tunnel.WarmState
import app.opal.feature.apps.AppRow
import app.opal.feature.apps.AppsScreen
import app.opal.feature.apps.AppsUiState
import app.opal.feature.connection.ConnectionScreen
import app.opal.feature.connection.ConnectionUiState
import app.opal.feature.connection.CustomBridgesScreen
import app.opal.feature.connection.CustomBridgesViewModel
import app.opal.feature.connection.SettingsApiInfo
import app.opal.feature.home.HomeScreen
import app.opal.feature.home.HomeUiState
import app.opal.feature.onboarding.OnboardingHost
import app.opal.feature.onboarding.OnboardingRoute
import app.opal.feature.settings.SettingsScreen
import app.opal.feature.settings.SettingsUiState
import app.opal.feature.settings.SystemStatus
import app.opal.ui.OpalScaffold
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Key screens in light, dark and simplified-graphics rendering. States are test fixtures built from
 * the real UI state classes; nothing here ships in the app.
 *
 * Record: ./gradlew :app:recordRoborazziDebug — compare: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [36],
    qualifiers = "ru-w393dp-h852dp-hdpi",
    application = android.app.Application::class,
)
class ScreensScreenshotTest(private val variant: Variant) {

    enum class Variant(val dark: Boolean, val simplified: Boolean) {
        Light(false, false),
        Dark(true, false),
        Simplified(false, true),
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Goldens are committed: store them at 60 % scale to keep the repository light.
    private val options =
        RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.6))

    private fun shot(
        name: String,
        mood: AuroraMood,
        tab: Int?,
        /** A finger still on the screen when the picture is taken (tab centers by test tag). */
        touch: (TouchInjectionScope.(tabCenter: (String) -> Offset) -> Unit)? = null,
        content: @Composable (PaddingValues) -> Unit,
    ) {
        compose.setContent {
            OpalTheme(dark = variant.dark, simplifiedGraphics = variant.simplified) {
                OpalScaffold(
                    mood = mood,
                    tabIndex = tab,
                    compact = false,
                    onSelectTab = {},
                    onScroll = {},
                    sheets = remember { SheetHost() },
                    toast = remember { ToastState() },
                    animateAurora = false,
                    content = content,
                )
            }
        }
        if (touch != null) {
            val centers = TAB_TAGS.associateWith {
                compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.center
            }
            compose.onRoot().performTouchInput { touch(centers::getValue) }
            compose.waitForIdle()
        }
        compose
            .onRoot()
            .captureRoboImage(
                "src/test/screenshots/${name}_${variant.name.lowercase()}.png",
                options,
            )
    }

    @Test
    fun homeOff() =
        shot("home_off", AuroraMood.Idle, 0) { padding ->
            HomeScreen(
                HomeUiState(
                    snapshot =
                        TunnelSnapshot(
                            state = TunnelState.Off,
                            warm = WarmState.Cold,
                            network = NetworkKind.Wifi,
                        )
                ),
                padding,
                {},
            )
        }

    @Test
    fun homeConnecting() =
        shot("home_connecting", AuroraMood.Connecting, 0) { padding ->
            HomeScreen(
                HomeUiState(
                    snapshot =
                        TunnelSnapshot(
                            state = TunnelState.Connecting(),
                            bootstrap = BootstrapInfo(45, BootstrapPhase.LoadingDirectory),
                            racing =
                                listOf(
                                    TransportKind.Snowflake,
                                    TransportKind.Obfs4,
                                    TransportKind.Meek,
                                ),
                            network = NetworkKind.Cellular,
                        )
                ),
                padding,
                {},
            )
        }

    @Test
    fun homeConnected() =
        shot("home_connected", AuroraMood.Protected, 0) { padding -> ConnectedHome(padding) }

    /** The tab bar lens lifted and dragged between two tabs, the finger still down. */
    @Test
    fun tabBarDragging() =
        shot(
            "tab_bar_dragging",
            AuroraMood.Protected,
            0,
            touch = { tabCenter ->
                down(tabCenter("tab_home"))
                moveTo(lerp(tabCenter("tab_apps"), tabCenter("tab_connection"), 0.35f))
            },
        ) { padding ->
            ConnectedHome(padding)
        }

    @Composable
    private fun ConnectedHome(padding: PaddingValues) {
        val down =
            List(60) { i ->
                (180_000 + 140_000 * sin(i / 60.0 * 4 * PI)).toLong().coerceAtLeast(0)
            }
        val up =
            List(60) { i ->
                (24_000 + 18_000 * sin(i / 60.0 * 3 * PI + 1)).toLong().coerceAtLeast(0)
            }
        HomeScreen(
            HomeUiState(
                snapshot =
                    TunnelSnapshot(
                        state = TunnelState.Connected,
                        transport = TransportKind.Snowflake,
                        circuit =
                            CircuitInfo(
                                listOf(
                                    CircuitHop(
                                        HopRole.Bridge,
                                        nickname = "flakey",
                                        country = null,
                                    ),
                                    CircuitHop(
                                        HopRole.Middle,
                                        nickname = "relayMiddle",
                                        country = "de",
                                    ),
                                    CircuitHop(
                                        HopRole.Exit,
                                        nickname = "relayExit",
                                        country = "nl",
                                        address = "192.0.2.44",
                                    ),
                                )
                            ),
                        connectedSince = System.currentTimeMillis() - 754_000,
                        network = NetworkKind.Wifi,
                    ),
                down = down.toImmutableList(),
                up = up.toImmutableList(),
                traffic = TrafficSample(down.last(), up.last(), 48_300_000, 3_900_000, 60),
            ),
            padding,
            {},
        )
    }

    @Test
    fun homeBlocked() =
        shot("home_blocked", AuroraMood.Alert, 0) { padding ->
            HomeScreen(
                HomeUiState(
                    snapshot =
                        TunnelSnapshot(state = TunnelState.Blocked, network = NetworkKind.Cellular)
                ),
                padding,
                {},
            )
        }

    @Test
    fun apps() =
        shot("apps", AuroraMood.Idle, 1) { padding ->
            AppsScreen(
                AppsUiState(
                    loading = false,
                    mode = SplitTunnelMode.AllExcept,
                    rows =
                        persistentListOf(
                            AppRow(
                                "org.mozilla.firefox",
                                "Firefox",
                                isSystem = false,
                                checked = false,
                            ),
                            AppRow(
                                "ru.gosuslugi.pos",
                                "Госуслуги Решаем вместе",
                                isSystem = false,
                                checked = true,
                            ),
                            AppRow("ru.rostel", "Госуслуги", isSystem = false, checked = true),
                            AppRow(
                                "ru.sberbankmobile",
                                "СберБанк Онлайн",
                                isSystem = false,
                                checked = true,
                            ),
                            AppRow(
                                "org.telegram.messenger",
                                "Telegram",
                                isSystem = false,
                                checked = false,
                            ),
                        ),
                    selectedCount = 3,
                    presetInstalled = 3,
                    presetApplied = true,
                ),
                padding,
                onAction = {},
            )
        }

    @Test
    fun connection() =
        shot("connection", AuroraMood.Idle, 2) { padding ->
            ConnectionScreen(
                ConnectionUiState(
                    mode = ConnectionMode.Snowflake,
                    customCount = 0,
                    settingsApi =
                        SettingsApiInfo(
                            System.currentTimeMillis() - 2 * 3_600_000,
                            "ru",
                            persistentListOf("webtunnel", "obfs4", "snowflake"),
                        ),
                    winners =
                        persistentMapOf(
                            NetworkKind.Wifi to TransportKind.Snowflake,
                            NetworkKind.Cellular to TransportKind.WebTunnel,
                        ),
                ),
                padding,
                onAction = {},
            )
        }

    @Test
    fun customBridges() =
        shot("custom_bridges", AuroraMood.Idle, null) { padding ->
            val text =
                "obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA iat-mode=0\n" +
                    "webtunnel [2001:db8::1]:443 89ABCDEF0123456789ABCDEF0123456789ABCDEF url=https://example.com/path ver=0.0.1\n" +
                    "obfs4 192.0.2.300:443"
            CustomBridgesScreen(
                state = CustomBridgesViewModel.validate(text).copy(loaded = true, saved = false),
                contentPadding = padding,
                onBack = {},
                onTextChange = {},
                onPaste = {},
                onScan = {},
                onPickImage = {},
                onClear = {},
                onSave = {},
            )
        }

    @Test
    fun settings() =
        shot("settings", AuroraMood.Protected, 3) { padding ->
            SettingsScreen(
                state =
                    SettingsUiState(
                        settings = AppSettings(exitCountry = "de"),
                        connected = true,
                        alwaysOn = true,
                        lockdown = false,
                        loaded = true,
                    ),
                system =
                    SystemStatus(
                        ignoringBatteryOptimizations = false,
                        notificationsEnabled = true,
                        canRequestTile = true,
                    ),
                language = AppLanguage.System,
                versionName = "1.0.0",
                contentPadding = padding,
                onAction = {},
            )
        }

    @Test
    fun onboarding() =
        shot("onboarding", AuroraMood.Idle, null) { padding ->
            OnboardingRoute(
                host =
                    object : OnboardingHost {
                        override fun openVpnSettings() = Unit

                        override fun requestBatteryExemption() = Unit

                        override fun isIgnoringBatteryOptimizations() = false

                        override fun openAutostartHelp() = Unit
                    },
                onFinish = {},
                contentPadding = padding,
            )
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = Variant.entries.map { arrayOf<Any>(it) }

        private val TAB_TAGS = listOf("tab_home", "tab_apps", "tab_connection", "tab_settings")
    }
}
