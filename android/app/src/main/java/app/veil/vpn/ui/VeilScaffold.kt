package app.veil.vpn.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material3.MaterialTheme

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
private fun NavigationLabel(text: String, slotWidth: Dp) {
    Text(
        text = text,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 8.sp,
            maxFontSize = 12.sp,
            stepSize = 0.5.sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        // The width the label may occupy, worked out from the bar rather than
        // left to the item. Auto-sizing only shrinks text against a bound, and
        // without one the label is measured as wide as it likes and drawn over
        // its neighbours — which is how "Приложения Диагностика Настройки"
        // ended up as one run of touching words.
        modifier = Modifier.width(slotWidth),
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
    // A pager rather than a swap, so the five destinations can be moved between
    // with a finger that never leaves the glass — the bar and the swipe are two
    // views of the same position, not two ways of setting it.
    val pager = rememberPagerState(initialPage = 0) { Destination.entries.size }
    val destination = Destination.entries[pager.currentPage]
    var showBridges by remember { mutableStateOf(false) }
    val swipeScope = rememberCoroutineScope()
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
    val pulse by viewModel.pulse.collectAsStateWithLifecycle()

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
            // Two ways to the same place, and both of them keep the finger on
            // the glass. The pager below moves a whole page at a time; this
            // moves as the finger travels along the bar, so running a thumb
            // across the five destinations walks through them rather than
            // needing five separate taps.
            //
            // A tap has to keep working, so nothing is consumed until the
            // movement passes the system's own threshold for a drag. Below it
            // the gesture is still a press and reaches the item underneath.
            BoxWithConstraints(
                Modifier.pointerInput(Destination.entries.size) {
                    val slots = Destination.entries.size
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var last = -1
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val travelled = abs(change.position.x - down.position.x)
                            if (!dragging && travelled > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                val slot = ((change.position.x / size.width) * slots)
                                    .toInt()
                                    .coerceIn(0, slots - 1)
                                if (slot != last) {
                                    last = slot
                                    showBridges = false
                                    // Snapped rather than animated: an
                                    // animation started on every slot the
                                    // finger passes over arrives late and in
                                    // the wrong order.
                                    swipeScope.launch { pager.scrollToPage(slot) }
                                }
                                change.consume()
                            }
                        }
                    }
                },
            ) {
                // Equal slots, minus the breathing room each item keeps around
                // its own label.
                val slot = (maxWidth / Destination.entries.size) - 8.dp
                ShortNavigationBar {
                    Destination.entries.forEach { entry ->
                        ShortNavigationBarItem(
                            selected = destination == entry && !showBridges,
                            onClick = {
                                showBridges = false
                                swipeScope.launch { pager.animateScrollToPage(entry.ordinal) }
                            },
                            icon = { Icon(iconFor(entry), contentDescription = null) },
                            label = { NavigationLabel(stringResource(entry.labelRes), slot) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                // The bridges screen is pushed on top rather than being a sixth
                // page: it is opened from Routes and returned from, not swiped
                // between.
                userScrollEnabled = !showBridges,
                beyondViewportPageCount = 1,
            ) { page ->
                when (Destination.entries[page]) {
                    Destination.TUNNEL -> HomeScreen(
                        state = state,
                        stats = stats,
                        probe = probe,
                        circuit = circuit,
                        bootstrapPercent = bootstrap.percent,
                        pulse = pulse,
                        adsBlocked = settings.blockAds,
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
                        manualTransport = settings.manualTransport,
                        ladder = ladder,
                        bridges = bridges,
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
                        onBlockAds = viewModel::setBlockAds,
                        onPulse = viewModel::setPulse,
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

            // Bridges is pushed over the pager rather than being a sixth page:
            // it is opened from Routes and dismissed back to it, not somewhere
            // a swipe should land.
            if (showBridges) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                        BridgesScreen(
                            bridges = bridges,
                            moat = moat,
                            loadCustomText = viewModel::customBridgeText,
                            onRefresh = viewModel::refreshBridges,
                            onSaveCustom = viewModel::saveCustomBridges,
                            onRequestFromMoat = viewModel::requestBridgesFromMoat,
                            onSubmitSolution = viewModel::submitMoatSolution,
                            onDismissMoat = viewModel::dismissMoat,
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
