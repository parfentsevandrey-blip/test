package app.veil.vpn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.core.formatBytes
import app.veil.vpn.core.formatDuration
import app.veil.vpn.model.TunnelState
import app.veil.vpn.model.TunnelStats
import app.veil.vpn.net.ProbeReport
import app.veil.vpn.ui.components.ConnectButton
import app.veil.vpn.ui.components.StatTile
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: TunnelState,
    stats: TunnelStats,
    probe: ProbeReport,
    circuit: String?,
    bootstrapPercent: Int,
    onToggle: () -> Unit,
    onNewCircuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        ConnectButton(
            state = state,
            bootstrapPercent = bootstrapPercent,
            onClick = onToggle,
        )

        Spacer(Modifier.height(20.dp))
        StatusLine(state = state)
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = stringResource(R.string.home_traffic_down),
                value = formatBytes(stats.rxBytes),
                icon = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.home_traffic_up),
                value = formatBytes(stats.txBytes),
                icon = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (state is TunnelState.Connected) {
            Spacer(Modifier.height(10.dp))
            UptimeTile(since = state.connectedAtMillis)
        }

        Spacer(Modifier.height(10.dp))
        RouteCard(
            state = state,
            probe = probe,
            circuit = circuit,
            stats = stats,
            onNewCircuit = onNewCircuit,
        )

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The line under the button. During a long connect this is the only place that
 * explains *why* the app is doing what it is doing, so it names the rung and
 * the reason rather than saying "please wait".
 */
@Composable
private fun StatusLine(state: TunnelState) {
    val headline = stringResource(
        when (state) {
            is TunnelState.Idle -> R.string.state_idle
            is TunnelState.Probing -> R.string.state_probing
            is TunnelState.Starting -> R.string.state_starting
            is TunnelState.Bootstrapping -> R.string.state_bootstrapping
            is TunnelState.Connected -> R.string.state_connected
            is TunnelState.Escalating -> R.string.state_reconfiguring
            is TunnelState.Stopping -> R.string.state_stopping
            is TunnelState.Failed -> R.string.state_failed
        },
    )
    val detail = when (state) {
        is TunnelState.Idle -> stringResource(R.string.home_auto_hint)
        is TunnelState.Probing -> state.note
        is TunnelState.Starting ->
            "${state.transport.label}  ·  step ${state.attempt} of ${state.ladderSize}"
        is TunnelState.Bootstrapping -> state.summary
        is TunnelState.Escalating -> state.reason
        is TunnelState.Failed -> state.reason
        else -> null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = headline,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "headline",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
            )
        }
        if (state is TunnelState.Probing) {
            LoadingIndicator(modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun UptimeTile(since: Long) {
    val elapsed by produceState(0L, since) {
        while (true) {
            value = System.currentTimeMillis() - since
            delay(1_000)
        }
    }
    StatTile(
        label = stringResource(R.string.home_uptime),
        value = formatDuration(elapsed),
        icon = Icons.Filled.Shield,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** What the tunnel is made of right now, and what the network looked like. */
@Composable
private fun RouteCard(
    state: TunnelState,
    probe: ProbeReport,
    circuit: String?,
    stats: TunnelStats,
    onNewCircuit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_route),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.activeTransport?.label ?: "—",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (state.isLive) {
                    FilledTonalIconButton(onClick = onNewCircuit) {
                        Icon(
                            Icons.Filled.Autorenew,
                            contentDescription = stringResource(R.string.action_new_circuit),
                        )
                    }
                }
            }

            if (circuit != null) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text(
                    text = circuit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (probe.hasRun) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text(
                    text = probe.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.isLive && stats.blockedUdp > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${stats.blockedUdp} UDP datagrams dropped at the edge of the " +
                        "tunnel. Tor carries TCP only, so this is the leak guard doing its job.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Kept for symmetry with the other screens' empty states. */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
