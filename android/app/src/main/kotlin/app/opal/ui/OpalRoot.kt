package app.opal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.opal.AppGraph
import app.opal.R
import app.opal.core.designsystem.aurora.AuroraBackground
import app.opal.core.designsystem.component.LocalSheetHost
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.SheetHost
import app.opal.core.designsystem.glass.GlassQuality
import app.opal.core.designsystem.glass.GlassSheet
import app.opal.core.designsystem.glass.GlassTabBar
import app.opal.core.designsystem.glass.GlassToast
import app.opal.core.designsystem.glass.LocalGlassBackdrops
import app.opal.core.designsystem.glass.LocalGlassQuality
import app.opal.core.designsystem.glass.TabItem
import app.opal.core.designsystem.glass.ToastState
import app.opal.core.designsystem.glass.rememberGlassBackdrops
import app.opal.core.designsystem.glass.rememberToastState
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.AuroraMood
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.AppLanguage
import app.opal.core.model.tunnel.TunnelState
import app.opal.feature.apps.AppsRoute
import app.opal.feature.apps.AppsViewModel
import app.opal.feature.connection.ConnectionRoute
import app.opal.feature.connection.ConnectionViewModel
import app.opal.feature.connection.CustomBridgesRoute
import app.opal.feature.connection.CustomBridgesViewModel
import app.opal.feature.home.HomeRoute
import app.opal.feature.home.HomeViewModel
import app.opal.feature.onboarding.OnboardingHost
import app.opal.feature.onboarding.OnboardingRoute
import app.opal.feature.settings.AutostartScreen
import app.opal.feature.settings.DiagnosticsRoute
import app.opal.feature.settings.ExitCountryRoute
import app.opal.feature.settings.LicensesScreen
import app.opal.feature.settings.SettingsNavigator
import app.opal.feature.settings.SettingsRoute
import app.opal.feature.settings.SettingsViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.collections.immutable.persistentListOf

/** What the activity provides to the UI tree. */
interface ShellHost {
    val versionName: String
    val language: AppLanguage

    fun setLanguage(language: AppLanguage)

    fun onboardingFinished()

    fun openVpnSettings()

    fun requestBatteryExemption()

    fun isIgnoringBatteryOptimizations(): Boolean
}

/** Root of the UI: the back stack (Navigation 3) inside [OpalScaffold]. */
@Composable
fun OpalRoot(startWithOnboarding: Boolean, tunnelState: TunnelState, host: ShellHost) {
    val backStack = rememberNavBackStack(if (startWithOnboarding) Dest.Onboarding else Dest.Home)
    val sheets = remember { SheetHost() }
    val toast = rememberToastState()
    val reduced = LocalReducedMotion.current

    val top = backStack.lastOrNull()
    val tabIndex = Dest.tabs.indexOf(top)
    val showTabs = tabIndex >= 0
    var compact by remember { mutableStateOf(false) }
    val nav = remember(backStack) { Navigator(backStack) { compact = false } }

    OpalScaffold(
        mood = moodFor(tunnelState),
        tabIndex = tabIndex.takeIf { showTabs },
        compact = compact,
        onSelectTab = { nav.selectTab(Dest.tabs[it]) },
        onScroll = { down -> compact = down },
        sheets = sheets,
        toast = toast,
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { nav.back() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            transitionSpec = { push(reduced) },
            popTransitionSpec = { pop(reduced) },
            predictivePopTransitionSpec = { pop(reduced) },
            entryProvider =
                entryProvider {
                    entry<Dest.Onboarding> {
                        OnboardingRoute(
                            host =
                                object : OnboardingHost {
                                    override fun openVpnSettings() = host.openVpnSettings()

                                    override fun requestBatteryExemption() =
                                        host.requestBatteryExemption()

                                    override fun isIgnoringBatteryOptimizations() =
                                        host.isIgnoringBatteryOptimizations()

                                    override fun openAutostartHelp() = nav.push(Dest.Autostart)
                                },
                            onFinish = {
                                host.onboardingFinished()
                                nav.reset(Dest.Home)
                            },
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.Home>(metadata = tabTransitions(reduced)) {
                        HomeRoute(
                            viewModel =
                                viewModel { HomeViewModel(AppGraph.tunnel, AppGraph.settings) },
                            onOpenConnection = { nav.selectTab(Dest.Connection) },
                            onOpenCustomBridges = {
                                nav.selectTab(Dest.Connection, then = Dest.CustomBridges)
                            },
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.Apps>(metadata = tabTransitions(reduced)) {
                        AppsRoute(
                            viewModel {
                                AppsViewModel(AppGraph.settings, AppGraph.apps, AppGraph.tunnel)
                            },
                            padding,
                        )
                    }
                    entry<Dest.Connection>(metadata = tabTransitions(reduced)) {
                        ConnectionRoute(
                            viewModel =
                                viewModel {
                                    ConnectionViewModel(
                                        AppGraph.settings,
                                        AppGraph.memory,
                                        AppGraph.tunnel,
                                    )
                                },
                            onEditCustomBridges = { nav.push(Dest.CustomBridges) },
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.Settings>(metadata = tabTransitions(reduced)) {
                        SettingsRoute(
                            viewModel =
                                viewModel { SettingsViewModel(AppGraph.settings, AppGraph.tunnel) },
                            language = host.language,
                            onLanguageChange = host::setLanguage,
                            versionName = host.versionName,
                            navigator =
                                object : SettingsNavigator {
                                    override fun openExitCountry() = nav.push(Dest.ExitCountry)

                                    override fun openDiagnostics() = nav.push(Dest.Diagnostics)

                                    override fun openLicenses() = nav.push(Dest.Licenses)

                                    override fun openAutostart() = nav.push(Dest.Autostart)
                                },
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.CustomBridges> {
                        CustomBridgesRoute(
                            viewModel { CustomBridgesViewModel(AppGraph.settings) },
                            onBack = nav::back,
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.ExitCountry> {
                        ExitCountryRoute(
                            viewModel { SettingsViewModel(AppGraph.settings, AppGraph.tunnel) },
                            onBack = nav::back,
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.Diagnostics> {
                        DiagnosticsRoute(
                            loadDiagnostics = { AppGraph.tunnel.diagnostics() },
                            versionName = host.versionName,
                            onBack = nav::back,
                            contentPadding = padding,
                        )
                    }
                    entry<Dest.Licenses> {
                        LicensesScreen(contentPadding = padding, onBack = nav::back)
                    }
                    entry<Dest.Autostart> {
                        AutostartScreen(contentPadding = padding, onBack = nav::back)
                    }
                },
        )
    }
}

/**
 * The three layers, shared by the app and the screenshot tests:
 * 1. aurora — recorded as the backdrop that in-content glass refracts;
 * 2. content — recorded as the second backdrop;
 * 3. floating glass (tab bar, sheet, toast) — refracts aurora + content, never itself.
 */
@Composable
fun OpalScaffold(
    mood: AuroraMood,
    tabIndex: Int?,
    compact: Boolean,
    onSelectTab: (Int) -> Unit,
    onScroll: (down: Boolean) -> Unit,
    sheets: SheetHost,
    toast: ToastState,
    modifier: Modifier = Modifier,
    animateAurora: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val auroraLayer = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val backdrops = rememberGlassBackdrops(auroraLayer, contentLayer)
    val quality = LocalGlassQuality.current
    val showTabs = tabIndex != null
    val padding = contentPadding(showTabs)
    val currentOnScroll by rememberUpdatedState(onScroll)
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -SCROLL_SLOP) currentOnScroll(true)
                else if (available.y > SCROLL_SLOP) currentOnScroll(false)
                return Offset.Zero
            }
        }
    }
    CompositionLocalProvider(
        LocalGlassBackdrops provides backdrops,
        LocalSheetHost provides sheets,
        LocalToastState provides toast,
    ) {
        Box(
            modifier.fillMaxSize().background(OpalTheme.colors.background).semantics {
                // Test tags become resource ids for UiAutomator (baseline profile, benchmarks).
                testTagsAsResourceId = true
            }
        ) {
            AuroraBackground(
                mood = mood,
                modifier = Modifier.fillMaxSize().layerBackdrop(auroraLayer),
                animate = animateAurora && quality != GlassQuality.Fallback,
            )
            Box(Modifier.fillMaxSize().layerBackdrop(contentLayer).nestedScroll(scrollWatcher)) {
                content(padding)
            }

            AnimatedVisibility(
                visible = showTabs,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val items =
                    persistentListOf(
                        TabItem(
                            stringResource(R.string.tab_home),
                            OpalIcons.Home,
                            OpalIcons.HomeFilled,
                            testTag = "tab_home",
                        ),
                        TabItem(
                            stringResource(R.string.tab_apps),
                            OpalIcons.Apps,
                            OpalIcons.AppsFilled,
                            testTag = "tab_apps",
                        ),
                        TabItem(
                            stringResource(R.string.tab_connection),
                            OpalIcons.Route,
                            OpalIcons.RouteFilled,
                            testTag = "tab_connection",
                        ),
                        TabItem(
                            stringResource(R.string.tab_settings),
                            OpalIcons.Settings,
                            OpalIcons.SettingsFilled,
                            testTag = "tab_settings",
                        ),
                    )
                GlassTabBar(
                    items = items,
                    selectedIndex = tabIndex ?: 0,
                    onSelect = onSelectTab,
                    compact = compact,
                    modifier =
                        Modifier.navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = TAB_BAR_MARGIN)
                            .widthIn(max = 440.dp)
                            .fillMaxWidth(),
                )
            }
            GlassToast(
                toast,
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (showTabs) TAB_BAR_HEIGHT + TAB_BAR_MARGIN * 2 + 8.dp else 24.dp
                    ),
            )
            SheetLayer(sheets)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SheetLayer(sheets: SheetHost) {
    val spec = sheets.current
    var last by remember { mutableStateOf(spec) }
    if (spec != null) last = spec
    val shown = last
    GlassSheet(visible = spec != null, onDismiss = sheets::dismiss, title = shown?.title) {
        shown?.content?.invoke(this, sheets::dismiss)
    }
}

/** Back stack operations; tabs keep Home at the root so "back" from a tab returns home. */
private class Navigator(
    private val backStack: NavBackStack<NavKey>,
    private val onTabChange: () -> Unit,
) {
    fun push(dest: Dest) {
        if (backStack.lastOrNull() != dest) backStack.add(dest)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun reset(dest: Dest) {
        backStack.clear()
        backStack.add(dest)
    }

    fun selectTab(tab: Dest, then: Dest? = null) {
        onTabChange()
        val target = buildList {
            add(Dest.Home)
            if (tab != Dest.Home) add(tab)
            if (then != null) add(then)
        }
        if (backStack.toList() == target) return
        backStack.clear()
        backStack.addAll(target)
    }
}

private fun moodFor(state: TunnelState): AuroraMood =
    when (state) {
        TunnelState.Connected -> AuroraMood.Protected
        is TunnelState.Connecting,
        is TunnelState.Reconnecting,
        TunnelState.WaitingForNetwork -> AuroraMood.Connecting
        TunnelState.Blocked,
        is TunnelState.Error -> AuroraMood.Alert
        TunnelState.Off,
        TunnelState.Standby,
        TunnelState.Stopping -> AuroraMood.Idle
    }

@Composable
private fun contentPadding(tabBar: Boolean): PaddingValues {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val bottomBar = if (tabBar) TAB_BAR_HEIGHT + TAB_BAR_MARGIN * 2 else 0.dp
    return PaddingValues(
        start = insets.calculateStartPadding(direction),
        end = insets.calculateEndPadding(direction),
        top = insets.calculateTopPadding() + 8.dp,
        bottom = insets.calculateBottomPadding() + bottomBar + 16.dp,
    )
}

private fun push(reduced: Boolean): ContentTransform =
    if (reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    else
        (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(160)))

private fun pop(reduced: Boolean): ContentTransform =
    if (reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    else
        (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(160)))

/** Tabs replace each other: a quick cross-fade instead of a push. */
private fun tabTransitions(reduced: Boolean): Map<String, Any> {
    val fade: ContentTransform =
        fadeIn(tween(if (reduced) 120 else 220, delayMillis = if (reduced) 0 else 60)) togetherWith
            fadeOut(tween(if (reduced) 120 else 90))
    return NavDisplay.transitionSpec { fade } + NavDisplay.popTransitionSpec { fade }
}

private val TAB_BAR_HEIGHT = 64.dp
private val TAB_BAR_MARGIN = 12.dp
private const val SCROLL_SLOP = 2f
