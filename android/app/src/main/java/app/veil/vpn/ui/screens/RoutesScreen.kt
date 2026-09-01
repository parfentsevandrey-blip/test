package app.veil.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.data.RouteMode
import app.veil.vpn.model.LatencyClass
import app.veil.vpn.model.Transport
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.tor.Attempt
import app.veil.vpn.ui.components.ChoiceCard
import app.veil.vpn.ui.components.Pip
import app.veil.vpn.ui.components.SectionHeader

@Composable
fun RoutesScreen(
    mode: RouteMode,
    manualTransport: Transport,
    ladder: List<Attempt>,
    bridges: Map<Transport, List<BridgeLine>>,
    onModeChange: (RouteMode) -> Unit,
    onTransportChange: (Transport) -> Unit,
    onOpenBridges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // ButtonGroup's content lambda is a plain scope, not a composable
            // one, so anything that reads from the composition happens first.
            val options = listOf(
                RouteMode.AUTO to stringResource(R.string.transports_mode_auto),
                RouteMode.MANUAL to stringResource(R.string.transports_mode_manual),
            )
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                options.forEach { (value, label) ->
                    toggleableItem(
                        checked = mode == value,
                        onCheckedChange = { if (it) onModeChange(value) },
                        label = label,
                        weight = 1f,
                    )
                }
            }
        }

        if (mode == RouteMode.AUTO) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.transports_mode_auto),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.transports_auto_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (ladder.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.transports_ladder)) }
                items(ladder.size) { index ->
                    val attempt = ladder[index]
                    LadderRow(position = index + 1, attempt = attempt)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.transports_title)) }

        items(Transport.entries) { transport ->
            val count = bridges[transport]?.size ?: 0
            ChoiceCard(
                title = transport.label,
                subtitle = describe(transport, count),
                selected = mode == RouteMode.MANUAL && manualTransport == transport,
                onClick = {
                    onModeChange(RouteMode.MANUAL)
                    onTransportChange(transport)
                },
                trailing = {
                    if (mode == RouteMode.MANUAL && manualTransport == transport) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    } else {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = null,
                            tint = when (transport.latencyClass) {
                                LatencyClass.LOW -> MaterialTheme.colorScheme.secondary
                                LatencyClass.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                LatencyClass.HIGH -> MaterialTheme.colorScheme.error
                            },
                        )
                    }
                },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onOpenBridges, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bridges_title))
                }
            }
        }
    }
}

@Composable
private fun LadderRow(position: Int, attempt: Attempt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pip(position.toString())
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = attempt.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = attempt.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (attempt.bridges.isNotEmpty()) {
                Text(
                    text = "${attempt.bridges.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Plain-language descriptions.
 *
 * A user deciding between these has to understand a trade-off, not a protocol,
 * so each line says what it hides, what it costs, and when it stops working.
 */
private fun describe(transport: Transport, bridgeCount: Int): String {
    val base = when (transport) {
        Transport.DIRECT ->
            "Straight to the Tor network. Fastest, and obvious to anyone watching the line."
        Transport.OBFS4 ->
            "Scrambles the stream so it matches no known protocol. Fast, but relies on " +
                "bridge addresses that get discovered and blocked over time."
        Transport.WEBTUNNEL ->
            "Looks like ordinary HTTPS to a site that really exists. Currently the hardest " +
                "to tell apart from normal browsing."
        Transport.MEEK ->
            "Hides inside traffic to a large CDN. Blocking it means blocking the CDN, which " +
                "is expensive — but so is the latency you pay for it."
        Transport.SNOWFLAKE ->
            "Hops through short-lived volunteer proxies found through a broker. No fixed " +
                "address to block; needs UDP and a little patience."
    }
    return if (transport.needsBridges) "$base  ·  $bridgeCount known" else base
}
