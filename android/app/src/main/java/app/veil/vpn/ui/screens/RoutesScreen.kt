package app.veil.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.veil.vpn.R
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.LatencyClass
import app.veil.vpn.model.Transport
import app.veil.vpn.tor.Attempt
import app.veil.vpn.ui.components.Pip
import app.veil.vpn.ui.components.SectionHeader

/**
 * One screen, one decision: which obfuscation to lead with.
 *
 * There used to be an Automatic/Manual pair above this, and it was a false
 * choice — "automatic" meant the ladder, "manual" meant the ladder with one
 * rung pinned to the front, and the app fell back through the rest either way.
 * Two names for one behaviour only made the user responsible for a distinction
 * that did not exist. Now the pinned route is simply first and the ladder
 * behind it is shown, so what will happen is on screen instead of being implied
 * by a mode.
 *
 * The choice is kept in settings, which is DataStore, so it survives being
 * killed and restarted without anything extra here.
 */
@Composable
fun RoutesScreen(
    manualTransport: Transport,
    ladder: List<Attempt>,
    bridges: Map<Transport, List<BridgeLine>>,
    onTransportChange: (Transport) -> Unit,
    onOpenBridges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the routes that survive a censored mobile network are offered. On
    // the network this app was measured against, a plain connection dies in the
    // TLS handshake at 10% and obfs4 reaches its bridge and then never builds a
    // circuit; presenting either as a choice costs the user a failed connect to
    // learn what is already known.
    val offered = Transport.entries.filter { it.isOffered }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(stringResource(R.string.transports_title))
                Text(
                    text = stringResource(R.string.transports_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // A row, not a list. These are alternatives of equal standing rather
        // than a ranking, and a row of cards says that in a way a vertical
        // stack cannot: a stack always reads top to bottom as best to worst.
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            ) {
                items(offered, key = { it.name }) { transport ->
                    TransportCard(
                        transport = transport,
                        bridgeCount = bridges[transport]?.size ?: 0,
                        selected = manualTransport == transport,
                        onClick = { onTransportChange(transport) },
                    )
                }
            }
        }

        // The full description of whatever is currently pinned, below the row.
        // The cards stay short enough to compare at a glance; the paragraph
        // that explains the trade-off belongs to the one being chosen.
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(manualTransport.labelRes),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(describeRes(manualTransport)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = stringResource(R.string.transports_pinned_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // What the app will actually try, in order: the pinned route first and
        // the rest behind it, so pinning reads as a preference and not a cliff.
        if (ladder.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.transports_ladder),
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            items(ladder.size) { index ->
                LadderRow(
                    position = index + 1,
                    attempt = ladder[index],
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onOpenBridges, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bridges_title))
                }
            }
        }
    }
}

/**
 * One obfuscation as a card you can put a thumb on.
 *
 * Fixed width so the row scrolls in even steps and a card is never half-shown
 * in a way that hides its name. The selected one is filled rather than merely
 * outlined: at a glance across a row, colour reads faster than a border.
 */
@Composable
private fun TransportCard(
    transport: Transport,
    bridgeCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(168.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        val onContainer = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val muted = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = iconFor(transport),
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(transport.labelRes),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = onContainer,
                maxLines = 1,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(taglineRes(transport)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(latencyRes(transport.latencyClass)),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
            Text(
                text = if (transport == Transport.CONJURE) {
                    stringResource(R.string.route_no_bridge_needed)
                } else {
                    stringResource(R.string.route_known_bridges, bridgeCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun LadderRow(position: Int, attempt: Attempt, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

private fun iconFor(transport: Transport): ImageVector = when (transport) {
    Transport.SNOWFLAKE -> Icons.Filled.AcUnit
    Transport.MEEK -> Icons.Filled.Cloud
    Transport.WEBTUNNEL -> Icons.Filled.Language
    Transport.CONJURE -> Icons.Filled.Hub
    else -> Icons.Filled.Check
}

/** Six words on the card. The paragraph lives under the row. */
private fun taglineRes(transport: Transport): Int = when (transport) {
    Transport.DIRECT -> R.string.route_direct_tag
    Transport.OBFS4 -> R.string.route_obfs4_tag
    Transport.WEBTUNNEL -> R.string.route_webtunnel_tag
    Transport.MEEK -> R.string.route_meek_tag
    Transport.CONJURE -> R.string.route_conjure_tag
    Transport.SNOWFLAKE -> R.string.route_snowflake_tag
}

/**
 * Plain-language descriptions.
 *
 * A user deciding between these has to understand a trade-off, not a protocol,
 * so each line says what it hides, what it costs, and when it stops working.
 */
private fun describeRes(transport: Transport): Int = when (transport) {
    Transport.DIRECT -> R.string.route_direct_desc
    Transport.OBFS4 -> R.string.route_obfs4_desc
    Transport.WEBTUNNEL -> R.string.route_webtunnel_desc
    Transport.MEEK -> R.string.route_meek_desc
    Transport.CONJURE -> R.string.route_conjure_desc
    Transport.SNOWFLAKE -> R.string.route_snowflake_desc
}

private fun latencyRes(latency: LatencyClass): Int = when (latency) {
    LatencyClass.LOW -> R.string.latency_low
    LatencyClass.MEDIUM -> R.string.latency_medium
    LatencyClass.HIGH -> R.string.latency_high
}
