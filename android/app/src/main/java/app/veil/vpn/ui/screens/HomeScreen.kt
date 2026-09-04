package app.veil.vpn.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.core.formatBytes
import app.veil.vpn.core.formatDuration
import app.veil.vpn.model.PulseState
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
    pulse: PulseState,
    adsBlocked: Boolean,
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

        // The pulse and the ad blocker, as numbers: the round trip and rate
        // of the last beat through the tunnel, and how many advertising names
        // were refused this session. A green screen says the tunnel is up; a
        // round trip measured a few seconds ago says it is alive.
        if (state.isLive) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    label = stringResource(R.string.home_pulse),
                    value = when {
                        !pulse.hasMeasurement -> stringResource(R.string.home_pulse_none)
                        !pulse.ok -> stringResource(R.string.home_pulse_failing, pulse.failures)
                        pulse.kbytesPerSecond > 0 ->
                            stringResource(R.string.home_pulse_value, pulse.rttMillis, pulse.kbytesPerSecond)
                        else -> stringResource(R.string.home_pulse_rtt, pulse.rttMillis)
                    },
                    icon = Icons.Filled.MonitorHeart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.home_ads),
                    value = if (adsBlocked) stats.dnsBlocked.toString() else stringResource(R.string.home_ads_off),
                    icon = Icons.Filled.Block,
                    modifier = Modifier.weight(1f),
                )
            }
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

        BatteryNotice()

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The one thing outside this app that kills a tunnel, put where it will be
 * seen.
 *
 * A few minutes after the screen goes off, Android starts cutting background
 * applications off from the network, and a VPN service is not exempt by
 * default. From the user's side that is a tunnel that was fine, and then
 * after the phone sat in a pocket is dead until it is toggled off and on. The
 * exemption is one system dialogue away; the setting for it already existed
 * on the Settings screen, which is exactly where nobody looks while the
 * tunnel is misbehaving. So it is offered here, once, until it is granted —
 * the diagnostic reports this same condition, and on the phone this was
 * measured against it was on.
 */
@Composable
private fun BatteryNotice() {
    val context = LocalContext.current
    // Re-checked every few seconds so the card leaves on its own once the
    // exemption has been granted in the system dialogue and the user is back.
    val exempt by produceState(initialValue = isExemptFromBatteryOptimisation(context)) {
        while (true) {
            delay(3_000)
            value = isExemptFromBatteryOptimisation(context)
        }
    }
    if (exempt) return
    Spacer(Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.settings_battery),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.settings_battery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                onClick = { requestBatteryExemption(context) },
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(stringResource(R.string.settings_battery_action))
            }
        }
    }
}

private fun isExemptFromBatteryOptimisation(context: Context): Boolean = runCatching {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    power.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(true)

/** The direct dialogue where the device offers it, the system list otherwise. */
private fun requestBatteryExemption(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(fallback) }
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
        is TunnelState.Probing -> stringResource(state.noteRes)
        is TunnelState.Starting -> stringResource(
            R.string.home_step,
            stringResource(state.transport.labelRes),
            state.attempt,
            state.ladderSize,
        )
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
                        text = state.activeTransport?.let { stringResource(it.labelRes) } ?: "—",
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
                    text = stringResource(probe.summaryRes),
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
