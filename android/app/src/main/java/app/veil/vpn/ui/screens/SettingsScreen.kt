package app.veil.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.veil.vpn.BuildConfig
import app.veil.vpn.R
import app.veil.vpn.data.DnsMode
import app.veil.vpn.data.IsolationMode
import app.veil.vpn.data.VeilSettings
import app.veil.vpn.model.DtlsProfile
import app.veil.vpn.model.TlsProfile
import app.veil.vpn.ui.components.ChoiceCard
import app.veil.vpn.ui.components.SectionHeader
import app.veil.vpn.ui.components.SwitchRow

@Composable
fun SettingsScreen(
    settings: VeilSettings,
    snowflakeServed: Int,
    onBlockUdp: (Boolean) -> Unit,
    onKillSwitch: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onSnowflakeProxy: (Boolean) -> Unit,
    onDnsMode: (DnsMode) -> Unit,
    onIsolation: (IsolationMode) -> Unit,
    onTlsProfile: (TlsProfile) -> Unit,
    onDtlsProfile: (DtlsProfile) -> Unit,
    onBypassLocal: (Boolean) -> Unit,
    onForgetRoutes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.settings_section_privacy)) }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_killswitch),
                subtitle = stringResource(R.string.settings_killswitch_desc),
                checked = settings.killSwitch,
                onCheckedChange = onKillSwitch,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_block_udp),
                subtitle = stringResource(R.string.settings_block_udp_desc),
                checked = settings.blockUdp,
                onCheckedChange = onBlockUdp,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_isolation)) }
        items(count = IsolationMode.entries.size) { index ->
            val mode = IsolationMode.entries[index]
            ChoiceCard(
                title = isolationTitle(mode),
                subtitle = isolationDescription(mode),
                selected = settings.isolation == mode,
                onClick = { onIsolation(mode) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_dns)) }
        items(count = DnsMode.entries.size) { index ->
            val mode = DnsMode.entries[index]
            ChoiceCard(
                title = dnsTitle(mode),
                subtitle = dnsDescription(mode),
                selected = settings.dnsMode == mode,
                onClick = { onDnsMode(mode) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_fingerprint)) }
        item {
            Text(
                text = stringResource(R.string.settings_tls_profile_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        items(count = TlsProfile.entries.size) { index ->
            val profile = TlsProfile.entries[index]
            ChoiceCard(
                title = profile.label,
                subtitle = profile.rationale,
                selected = settings.tlsProfile == profile,
                onClick = { onTlsProfile(profile) },
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_dtls_profile_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
            )
        }
        items(count = DtlsProfile.entries.size) { index ->
            val profile = DtlsProfile.entries[index]
            ChoiceCard(
                title = profile.label,
                subtitle = profile.rationale,
                selected = settings.dtlsProfile == profile,
                onClick = { onDtlsProfile(profile) },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_network)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_autostart),
                subtitle = null,
                checked = settings.autoStartOnBoot,
                onCheckedChange = onAutoStart,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_bypass),
                subtitle = stringResource(R.string.settings_bypass_desc),
                checked = settings.bypassSuffixes.isNotBlank(),
                onCheckedChange = onBypassLocal,
            )
        }
        item {
            OutlinedButton(onClick = onForgetRoutes, modifier = Modifier.fillMaxWidth()) {
                Text("Forget what worked on every network")
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_contribute)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_snowflake_proxy),
                subtitle = stringResource(R.string.settings_snowflake_proxy_desc),
                checked = settings.runSnowflakeProxy,
                onCheckedChange = onSnowflakeProxy,
            )
        }
        if (settings.runSnowflakeProxy) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            text = "$snowflakeServed people helped this session",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Each one is a browser somewhere that could not reach Tor " +
                                "on its own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_about)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        text = "Veil ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Built on the Tor Project's tor daemon, lyrebird and Snowflake, " +
                            "with a gVisor userspace network stack. Every component is free " +
                            "software, and nothing here needs an account or a server you have " +
                            "to find for yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun isolationTitle(mode: IsolationMode) = when (mode) {
    IsolationMode.SHARED -> "One shared circuit"
    IsolationMode.PER_DESTINATION -> "A circuit per site"
    IsolationMode.PER_CONNECTION -> "A circuit per connection"
}

private fun isolationDescription(mode: IsolationMode) = when (mode) {
    IsolationMode.SHARED ->
        "Fastest. Everything you do shares an exit, so it can all be linked together."
    IsolationMode.PER_DESTINATION ->
        "The balance Tor Browser strikes: separate circuits per destination, so two sites " +
            "cannot see each other's traffic as one user."
    IsolationMode.PER_CONNECTION ->
        "A new circuit for every connection. Hardest to correlate, and noticeably slower " +
            "because each one has to be built."
}

private fun dnsTitle(mode: DnsMode) = when (mode) {
    DnsMode.TOR_DNS_PORT -> "Resolve inside Tor"
    DnsMode.TCP_THROUGH_TUNNEL -> "DNS over TCP through the tunnel"
    DnsMode.DOH_THROUGH_TUNNEL -> "DNS over HTTPS through the tunnel"
}

private fun dnsDescription(mode: DnsMode) = when (mode) {
    DnsMode.TOR_DNS_PORT ->
        "Names are resolved at the exit relay. Nothing on your network sees what you look up."
    DnsMode.TCP_THROUGH_TUNNEL ->
        "Queries go to a public resolver as ordinary TCP, carried by the tunnel. Useful when " +
            "a site behaves badly with exit-side resolution."
    DnsMode.DOH_THROUGH_TUNNEL ->
        "Queries go to a public resolver as HTTPS, carried by the tunnel. Same privacy from " +
            "your network, and the resolver sees the tunnel rather than you."
}
