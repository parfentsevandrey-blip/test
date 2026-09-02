package app.veil.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.veil.vpn.R
import app.veil.vpn.model.TunnelState
import app.veil.vpn.ui.screens.AppsScreen
import app.veil.vpn.ui.screens.BridgesScreen
import app.veil.vpn.ui.screens.DiagnosticsScreen
import app.veil.vpn.ui.screens.HomeScreen
import app.veil.vpn.ui.screens.RoutesScreen
import app.veil.vpn.ui.screens.SettingsScreen

/**
 * A navigation label that shrinks rather than wraps.
 *
 * A bottom bar divides the width into equal slots, and a word that does not fit
 * its slot is broken across two lines and then clipped by the bar's height —
 * which is how "Диагностика" arrived on screen as "Диагности / ка". Any label
 * longer than about nine characters hits this, in every language: "Diagnostics"
 * does it in English too, and a larger system font size brings the shorter ones
 * down with it.
 *
 * Truncating with an ellipsis would be the usual answer and is a poor one for
 * navigation, where the label is the only thing naming the destination. So the
 * text is held to one line and allowed to step down a little in size until it
 * fits. The range is narrow on purpose: most labels stay at the size Material
 * specifies, and the one long word in a bar loses a point or two rather than
 * losing its ending.
 */
@Composable
private fun NavigationLabel(text: String) {
    Text(
        text = text,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 9.sp,
            maxFontSize = 12.sp,
            stepSize = 0.5.sp,
        ),
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

/** The five places the bottom bar can take you, plus one pushed screen. */
enum class Destination(val labelRes: Int) {
    TUNNEL(R.string.nav_home),
    ROUTES(R.string.nav_transports),
    APPS(R.string.nav_apps),
    DIAGNOSTICS(R.string.nav_logs),
    SETTINGS(R.string.nav_settings),
}

@Composable
fun VeilScaffold(
    viewModel: VeilViewModel,
    onConnectRequested: () -> Unit,
) {
    var destination by remember { mutableStateOf(Destination.TUNNEL) }
    var showBridges by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val state by viewModel.tunnelState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val ladder by viewModel.ladder.collectAsStateWithLifecycle()
    val circuit by viewModel.circuit.collectAsStateWithLifecycle()
    val bootstrap by viewModel.bootstrap.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bridges by viewModel.knownBridges.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val selfTest by viewModel.selfTest.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val moat by viewModel.moat.collectAsStateWithLifecycle()
    val listeners by viewModel.localListeners.collectAsStateWithLifecycle()
    val cooldowns by viewModel.cooldowns.collectAsStateWithLifecycle()
    val message by viewModel.busyMessage.collectAsStateWithLifecycle()
    val snowflakeServed by viewModel.snowflakeServed.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = showBridges) { showBridges = false }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        if (showBridges) {
                            stringResource(R.string.bridges_title)
                        } else {
                            stringResource(destination.labelRes)
                        },
                    )
                },
                subtitle = { Text(subtitleFor(destination, showBridges, state)) },
                navigationIcon = {
                    if (showBridges) {
                        IconButton(onClick = { showBridges = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Destination.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = destination == entry && !showBridges,
                        onClick = {
                            showBridges = false
                            destination = entry
                        },
                        icon = { Icon(iconFor(entry), contentDescription = null) },
                        label = { NavigationLabel(stringResource(entry.labelRes)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AnimatedContent(
                targetState = if (showBridges) null else destination,
                transitionSpec = {
                    val forward = (targetState?.ordinal ?: 99) >= (initialState?.ordinal ?: 99)
                    val offset = if (forward) 1 else -1
                    (
                        slideInHorizontally { width -> offset * width / 8 } + fadeIn()
                        ) togetherWith (
                        slideOutHorizontally { width -> -offset * width / 8 } + fadeOut()
                        ) using SizeTransform(clip = false)
                },
                label = "destination",
            ) { target ->
                when (target) {
                    null -> BridgesScreen(
                        bridges = bridges,
                        moat = moat,
                        loadCustomText = viewModel::customBridgeText,
                        onRefresh = viewModel::refreshBridges,
                        onSaveCustom = viewModel::saveCustomBridges,
                        onRequestFromMoat = viewModel::requestBridgesFromMoat,
                        onSubmitSolution = viewModel::submitMoatSolution,
                        onDismissMoat = viewModel::dismissMoat,
                    )

                    Destination.TUNNEL -> HomeScreen(
                        state = state,
                        stats = stats,
                        probe = probe,
                        circuit = circuit,
                        bootstrapPercent = bootstrap.percent,
                        onToggle = {
                            if (state.isLive || state.isBusy) {
                                viewModel.stopTunnel()
                            } else {
                                onConnectRequested()
                            }
                        },
                        onNewCircuit = viewModel::requestNewCircuit,
                    )

                    Destination.ROUTES -> RoutesScreen(
                        mode = settings.routeMode,
                        manualTransport = settings.manualTransport,
                        ladder = ladder,
                        bridges = bridges,
                        onModeChange = viewModel::setRouteMode,
                        onTransportChange = viewModel::setManualTransport,
                        onOpenBridges = { showBridges = true },
                    )

                    Destination.APPS -> AppsScreen(
                        apps = apps,
                        mode = settings.appRoutingMode,
                        selected = settings.selectedApps,
                        onLoad = viewModel::loadApps,
                        onModeChange = viewModel::setAppRoutingMode,
                        onToggle = viewModel::toggleApp,
                    )

                    Destination.DIAGNOSTICS -> DiagnosticsScreen(
                        probe = probe,
                        logs = logs,
                        listeners = listeners,
                        cooldowns = cooldowns,
                        onClear = viewModel::clearLogs,
                        onCopy = viewModel::logDump,
                        onClearCooldowns = viewModel::clearCooldowns,
                        onSelfTest = viewModel::runSelfTest,
                        selfTest = selfTest,
                        onDismissSelfTest = viewModel::clearSelfTest,
                    )

                    Destination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        snowflakeServed = snowflakeServed,
                        onBlockUdp = viewModel::setBlockUdp,
                        onKillSwitch = viewModel::setKillSwitch,
                        onAutoStart = viewModel::setAutoStart,
                        onSnowflakeProxy = viewModel::setSnowflakeProxy,
                        onDnsMode = viewModel::setDnsMode,
                        onIsolation = viewModel::setIsolation,
                        onTlsProfile = viewModel::setTlsProfile,
                        onDtlsProfile = viewModel::setDtlsProfile,
                        onBypassLocal = viewModel::setBypassLocal,
                        onForgetRoutes = viewModel::forgetLearnedRoutes,
                    )
                }
            }
        }
    }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.TUNNEL -> Icons.Filled.Shield
    Destination.ROUTES -> Icons.Filled.Route
    Destination.APPS -> Icons.Filled.Apps
    Destination.DIAGNOSTICS -> Icons.Filled.Insights
    Destination.SETTINGS -> Icons.Filled.Tune
}

@Composable
private fun subtitleFor(
    destination: Destination,
    showBridges: Boolean,
    state: TunnelState,
): String = when {
    showBridges -> stringResource(R.string.bridges_builtin)
    destination == Destination.TUNNEL -> state.activeTransport?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.app_tagline)
    else -> stringResource(R.string.app_tagline)
}
