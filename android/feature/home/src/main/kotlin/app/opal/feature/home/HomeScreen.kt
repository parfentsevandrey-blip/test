package app.opal.feature.home

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.opal.core.designsystem.component.ConnectionSphere
import app.opal.core.designsystem.component.Countries
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SpeedGraph
import app.opal.core.designsystem.component.SphereMode
import app.opal.core.designsystem.glass.GlassButton
import app.opal.core.designsystem.glass.GlassChip
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.LocalHaptics
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tunnel.CircuitHop
import app.opal.core.model.tunnel.CircuitInfo
import app.opal.core.model.tunnel.HopRole
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.model.tunnel.ReconnectReason
import app.opal.core.model.tunnel.TunnelError
import app.opal.core.model.tunnel.TunnelProblem
import app.opal.core.model.tunnel.TunnelState
import app.opal.core.model.tunnel.WarmState
import app.opal.core.model.tunnel.isTunnelActive
import app.opal.core.tunnel.Labels
import kotlinx.coroutines.delay

sealed interface HomeAction {
    data object Toggle : HomeAction

    data object NewIdentity : HomeAction

    data object OpenConnection : HomeAction

    data object OpenCustomBridges : HomeAction
}

/** Stateful entry: consent flow, haptics and toasts around the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenConnection: () -> Unit,
    onOpenCustomBridges: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = LocalToastState.current
    val deniedText = stringResource(R.string.home_consent_denied)
    val newIdentityText = stringResource(R.string.home_new_identity_done)
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) viewModel.connect()
            else toast.show(deniedText, OpalIcons.VpnLock)
        }
    StateHaptics(state.snapshot.state)
    HomeScreen(
        state = state,
        contentPadding = contentPadding,
        onAction = { action ->
            when (action) {
                HomeAction.Toggle -> {
                    val current = state.snapshot.state
                    when {
                        current == TunnelState.Stopping -> Unit
                        current.isTunnelActive -> viewModel.disconnect()
                        else ->
                            viewModel.consentIntent()?.let(consent::launch) ?: viewModel.connect()
                    }
                }
                HomeAction.NewIdentity -> {
                    viewModel.newIdentity()
                    toast.show(newIdentityText, OpalIcons.Autorenew)
                }
                HomeAction.OpenConnection -> onOpenConnection()
                HomeAction.OpenCustomBridges -> onOpenCustomBridges()
            }
        },
    )
}

/** Tactile confirmation of state changes (not of taps: the controls give their own tick). */
@Composable
private fun StateHaptics(state: TunnelState) {
    val haptics = LocalHaptics.current ?: return
    val previous = remember { arrayOfNulls<TunnelState>(1) }
    LaunchedEffect(state) {
        val before = previous[0]
        previous[0] = state
        if (before == null || before::class == state::class) return@LaunchedEffect
        when (state) {
            TunnelState.Connected -> haptics.connected()
            TunnelState.Off,
            TunnelState.Standby -> if (before.isTunnelActive) haptics.disconnected()
            TunnelState.Blocked,
            is TunnelState.Error -> haptics.error()
            else -> Unit
        }
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val scroll = rememberScrollState()
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Hero(state, onAction)
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(vertical = 16.dp)) {
                    Details(state, onAction)
                }
            }
        } else {
            // Short content (off, connecting) sits in the optical centre; once the details expand
            // the column outgrows the viewport and the capsule glides to the top.
            val minHeight =
                maxHeight -
                    contentPadding.calculateTopPadding() -
                    contentPadding.calculateBottomPadding()
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = minHeight).padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Hero(state, onAction)
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().animateContentSize()) {
                        Details(state, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Hero(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val snapshot = state.snapshot
    val tunnelState = snapshot.state
    val status = statusText(state)
    val mode =
        when (tunnelState) {
            TunnelState.Connected -> SphereMode.Protected
            is TunnelState.Connecting,
            is TunnelState.Reconnecting,
            TunnelState.WaitingForNetwork -> SphereMode.Working
            TunnelState.Blocked,
            is TunnelState.Error -> SphereMode.Alert
            TunnelState.Off,
            TunnelState.Standby,
            TunnelState.Stopping -> SphereMode.Idle
        }
    val progress =
        when (tunnelState) {
            is TunnelState.Connecting -> (snapshot.bootstrap?.progress ?: 0) / 100f
            else -> null
        }
    val action =
        stringResource(
            if (tunnelState.isTunnelActive) R.string.home_action_disconnect
            else R.string.home_action_connect
        )
    ConnectionSphere(
        mode = mode,
        progress = progress,
        actionLabel = action,
        stateLabel = status.title,
        onClick = { onAction(HomeAction.Toggle) },
    ) {
        Icon(
            OpalIcons.ShieldFilled,
            contentDescription = null,
            tint = OpalTheme.colors.connected,
            modifier = Modifier.size(32.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(status.title, style = OpalTheme.type.subhead, color = OpalTheme.colors.onGlass)
            SessionClock(snapshot.connectedSince)
        }
        Icon(
            OpalIcons.PowerSettingsNew,
            contentDescription = null,
            tint = OpalTheme.colors.onGlassMuted,
            modifier = Modifier.size(26.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tunnelState != TunnelState.Connected) {
            Text(
                status.title,
                style = OpalTheme.type.headline,
                color = OpalTheme.colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (status.detail != null) {
            Text(
                status.detail,
                style = OpalTheme.type.callout,
                color = OpalTheme.colors.onBackgroundMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    TransportChip(state, onClick = { onAction(HomeAction.OpenConnection) })
}

@Composable
private fun TransportChip(state: HomeUiState, onClick: () -> Unit) {
    val snapshot = state.snapshot
    val (label, icon) =
        when {
            snapshot.transport != null && snapshot.state.isTunnelActive ->
                stringResource(Labels.transport(snapshot.transport!!)) to
                    transportIcon(snapshot.transport!!)
            snapshot.racing.isNotEmpty() ->
                stringResource(
                    R.string.home_racing,
                    snapshot.racing
                        .map { stringResource(Labels.transport(it)) }
                        .joinToString(" · "),
                ) to OpalIcons.SyncAlt
            else -> modeLabel(state.mode) to OpalIcons.Tune
        }
    GlassChip(label = label, icon = icon, onClick = onClick)
}

@Composable
private fun modeLabel(mode: ConnectionMode): String =
    when (mode) {
        ConnectionMode.Auto -> stringResource(R.string.home_mode_auto)
        ConnectionMode.Custom -> stringResource(R.string.home_mode_custom)
        else -> stringResource(Labels.transport(mode.transport!!))
    }

internal fun transportIcon(kind: TransportKind) =
    when (kind) {
        TransportKind.Snowflake -> OpalIcons.AcUnit
        TransportKind.WebTunnel -> OpalIcons.Public
        TransportKind.Obfs4 -> OpalIcons.Key
        TransportKind.Meek -> OpalIcons.Cloud
        TransportKind.Vanilla -> OpalIcons.Hub
    }

private data class StatusText(val title: String, val detail: String?)

@Composable
private fun statusText(state: HomeUiState): StatusText {
    val s = state.snapshot
    val phase = s.bootstrap?.let { "${stringResource(Labels.phase(it.phase))} · ${it.progress}%" }
    return when (val t = s.state) {
        TunnelState.Off ->
            StatusText(
                stringResource(R.string.home_status_off),
                when (s.warm) {
                    WarmState.Cold -> stringResource(R.string.home_status_off_cold)
                    WarmState.Warming ->
                        listOfNotNull(
                                stringResource(R.string.home_status_off_warming),
                                s.bootstrap?.let { "${it.progress}%" },
                            )
                            .joinToString(" · ")
                    WarmState.Ready -> stringResource(R.string.home_status_off_ready)
                },
            )
        TunnelState.Standby ->
            StatusText(
                stringResource(R.string.home_status_standby),
                stringResource(R.string.home_status_standby_detail),
            )
        is TunnelState.Connecting ->
            StatusText(
                stringResource(R.string.home_status_connecting),
                problemText(s.problem) ?: phase,
            )
        is TunnelState.Reconnecting ->
            StatusText(
                stringResource(R.string.home_status_reconnecting),
                problemText(s.problem) ?: reasonText(t.reason),
            )
        TunnelState.WaitingForNetwork ->
            StatusText(
                stringResource(R.string.home_status_no_network),
                stringResource(R.string.home_status_no_network_detail),
            )
        TunnelState.Connected ->
            StatusText(
                stringResource(R.string.home_status_connected),
                stringResource(R.string.home_status_connected_detail),
            )
        TunnelState.Blocked -> StatusText(stringResource(R.string.home_status_blocked), null)
        TunnelState.Stopping -> StatusText(stringResource(R.string.home_status_stopping), null)
        is TunnelState.Error ->
            StatusText(stringResource(R.string.home_status_error), errorText(t.error))
    }
}

@Composable
private fun reasonText(reason: ReconnectReason): String =
    stringResource(
        when (reason) {
            ReconnectReason.NetworkChanged -> R.string.home_reason_network_changed
            ReconnectReason.Stalled -> R.string.home_reason_stalled
            ReconnectReason.CircuitLost -> R.string.home_reason_circuit_lost
            ReconnectReason.TransportSwitch -> R.string.home_reason_transport_switch
            ReconnectReason.Restart -> R.string.home_reason_restart
        }
    )

@Composable
private fun errorText(error: TunnelError): String =
    stringResource(
        when (error) {
            TunnelError.VpnPermissionMissing -> R.string.home_error_permission
            TunnelError.VpnRevoked -> R.string.home_error_revoked
            TunnelError.VpnEstablishFailed -> R.string.home_error_establish
            TunnelError.TorStartFailed -> R.string.home_error_tor
            TunnelError.NativeLibraryMissing -> R.string.home_error_native
        }
    )

@Composable
private fun problemText(problem: TunnelProblem?): String? = problem?.let {
    stringResource(
        when (it) {
            TunnelProblem.CannotReachBridges -> R.string.home_problem_bridges
            TunnelProblem.ConnectionFrozen -> R.string.home_problem_frozen
            TunnelProblem.SnowflakeUnavailable -> R.string.home_problem_snowflake
            TunnelProblem.ClockSkew -> R.string.home_problem_clock
            TunnelProblem.SettingsApiUnreachable -> R.string.home_problem_settings_api
        }
    )
}

@Composable
private fun ColumnScope.Details(state: HomeUiState, onAction: (HomeAction) -> Unit) {
    val snapshot = state.snapshot
    AnimatedVisibility(
        snapshot.state == TunnelState.Blocked,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Panel(Modifier.fillMaxWidth()) {
                Note(
                    stringResource(R.string.home_blocked_body),
                    icon = OpalIcons.Warning,
                    tint = OpalTheme.colors.warning,
                )
            }
            Spacer(Modifier.height(12.dp))
            GlassButton(onClick = { onAction(HomeAction.OpenCustomBridges) }) {
                Icon(OpalIcons.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.home_blocked_custom_bridges),
                    style = OpalTheme.type.bodyStrong,
                )
            }
        }
    }
    val connected = snapshot.state == TunnelState.Connected
    AnimatedVisibility(
        connected,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            SectionHeader(
                stringResource(R.string.home_section_circuit),
                Modifier.padding(top = 12.dp),
            )
            CircuitPanel(snapshot.circuit)
            SectionHeader(stringResource(R.string.home_section_speed))
            SpeedPanel(state)
            Spacer(Modifier.height(16.dp))
            NewIdentityButton(
                snapshot.newIdentityReadyAt,
                onClick = { onAction(HomeAction.NewIdentity) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    snapshot.network?.let { network ->
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                networkIcon(network),
                contentDescription = null,
                tint = OpalTheme.colors.onBackgroundMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(networkLabel(network)),
                style = OpalTheme.type.caption,
                color = OpalTheme.colors.onBackgroundMuted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

private fun networkIcon(kind: NetworkKind) =
    when (kind) {
        NetworkKind.Wifi -> OpalIcons.Wifi
        NetworkKind.Cellular -> OpalIcons.SignalCellularAlt
        NetworkKind.Ethernet,
        NetworkKind.Other -> OpalIcons.Hub
    }

private fun networkLabel(kind: NetworkKind) =
    when (kind) {
        NetworkKind.Wifi -> R.string.home_network_wifi
        NetworkKind.Cellular -> R.string.home_network_cellular
        NetworkKind.Ethernet -> R.string.home_network_ethernet
        NetworkKind.Other -> R.string.home_network_other
    }

@Composable
private fun CircuitPanel(circuit: CircuitInfo?) {
    val hops = circuit?.hops.orEmpty()
    Panel(Modifier.fillMaxWidth()) {
        if (hops.isEmpty()) {
            Note(stringResource(R.string.home_circuit_pending))
            return@Panel
        }
        hops.forEachIndexed { index, hop ->
            HopRow(hop, last = index == hops.lastIndex)
        }
    }
}

@Composable
private fun HopRow(hop: CircuitHop, last: Boolean) {
    val colors = OpalTheme.colors
    val country = hop.country
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(
            mergeDescendants = true
        ) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (country != null) {
                Text(Countries.flag(country), style = OpalTheme.type.headline)
            } else {
                Icon(
                    OpalIcons.Public,
                    contentDescription = null,
                    tint = colors.onBackgroundMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(hopRole(hop.role)),
                style = OpalTheme.type.caption,
                color = colors.onBackgroundMuted,
            )
            // Without a country (e.g. Snowflake's bridge sits behind a volunteer proxy) the relay
            // nickname is the most useful title.
            val unknown = stringResource(R.string.home_country_unknown)
            val title = country?.let { Countries.name(it) } ?: hop.nickname ?: unknown
            Text(title, style = OpalTheme.type.bodyStrong, color = colors.onBackground)
            val extra =
                listOfNotNull(
                    if (country != null) hop.nickname else unknown.takeIf { hop.nickname != null },
                    hop.address
                        ?.takeIf { last }
                        ?.let { stringResource(R.string.home_exit_address, it) },
                )
            if (extra.isNotEmpty())
                Text(
                    extra.joinToString(" · "),
                    style = OpalTheme.type.caption,
                    color = colors.onBackgroundMuted,
                )
        }
        if (!last)
            Icon(
                OpalIcons.ArrowDownward,
                contentDescription = null,
                tint = colors.onBackgroundMuted,
                modifier = Modifier.size(16.dp),
            )
    }
}

private fun hopRole(role: HopRole) =
    when (role) {
        HopRole.Bridge -> R.string.home_hop_bridge
        HopRole.Guard -> R.string.home_hop_guard
        HopRole.Middle -> R.string.home_hop_middle
        HopRole.Exit -> R.string.home_hop_exit
    }

@Composable
private fun SpeedPanel(state: HomeUiState) {
    val context = LocalContext.current
    val colors = OpalTheme.colors
    val traffic = state.traffic
    val down = Labels.speed(context, traffic?.read ?: 0)
    val up = Labels.speed(context, traffic?.written ?: 0)
    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SpeedFigure(
                stringResource(R.string.home_speed_down),
                down,
                OpalIcons.ArrowDownward,
                colors.download,
            )
            SpeedFigure(
                stringResource(R.string.home_speed_up),
                up,
                OpalIcons.ArrowUpward,
                colors.upload,
            )
        }
        SpeedGraph(
            down = state.down,
            up = state.up,
            description = stringResource(R.string.home_speed_graph, down, up),
            modifier =
                Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        )
        if (traffic != null) {
            Text(
                stringResource(
                    R.string.home_speed_totals,
                    Formatter.formatShortFileSize(context, traffic.totalRead),
                    Formatter.formatShortFileSize(context, traffic.totalWritten),
                ),
                style = OpalTheme.type.caption,
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun SpeedFigure(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = OpalTheme.type.caption,
                color = OpalTheme.colors.onBackgroundMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(value, style = OpalTheme.type.numeric, color = OpalTheme.colors.onBackground)
    }
}

/** Session duration, ticking once per second while the screen is resumed. */
@Composable
private fun SessionClock(since: Long?) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(since, lifecycle) {
        if (since == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now.longValue = System.currentTimeMillis()
                delay(1_000L - now.longValue % 1_000L)
            }
        }
    }
    val seconds = since?.let { ((now.longValue - it) / 1_000L).coerceAtLeast(0) } ?: 0L
    val text = "%d:%02d:%02d".format(seconds / 3_600, seconds / 60 % 60, seconds % 60)
    Text(
        "${stringResource(R.string.home_session)} · $text",
        style = OpalTheme.type.numeric.copy(fontSize = OpalTheme.type.caption.fontSize),
        color = OpalTheme.colors.onGlassMuted,
    )
}

@Composable
private fun NewIdentityButton(readyAt: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentReadyAt by rememberUpdatedState(readyAt)
    LaunchedEffect(readyAt) {
        while ((currentReadyAt ?: 0L) > System.currentTimeMillis()) {
            now.longValue = System.currentTimeMillis()
            delay(250)
        }
        now.longValue = System.currentTimeMillis()
    }
    val wait = readyAt?.let { ((it - now.longValue + 999) / 1_000).toInt() }?.takeIf { it > 0 }
    GlassButton(onClick = onClick, enabled = wait == null, modifier = modifier) {
        Icon(OpalIcons.Autorenew, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            if (wait == null) stringResource(R.string.home_new_identity)
            else stringResource(R.string.home_new_identity_wait, wait),
            style = OpalTheme.type.bodyStrong,
        )
    }
}
