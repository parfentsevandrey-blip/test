package app.veil.vpn.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
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
                title = stringResource(profile.labelRes),
                subtitle = stringResource(profile.rationaleRes),
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
                title = stringResource(profile.labelRes),
                subtitle = stringResource(profile.rationaleRes),
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
        item { BatteryExemptionRow() }
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
                Text(stringResource(R.string.settings_forget_routes))
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
                            text = stringResource(R.string.settings_snowflake_served_desc),
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
                        text = stringResource(R.string.settings_about),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Offers to take the app out of Android's battery optimisation.
 *
 * This is not a nicety. Once the screen has been off for some minutes Android
 * starts deferring background work and restricting network access, and a tunnel
 * is background work by every definition the system uses. The transport's
 * connection to its bridge goes quiet, the far end times it out, and the next
 * time the phone is picked up everything waits for the whole path to be built
 * again — which over a volunteer-proxied transport is tens of seconds. The
 * symptom is a VPN that "works and then stops working after a while", and it is
 * the single most common reason for it.
 *
 * The row disappears once the exemption is granted, and it is rechecked on
 * every return to the screen, because the answer is changed outside the app.
 */
@Composable
private fun BatteryExemptionRow() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isExemptFromBatteryOptimisation(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = isExemptFromBatteryOptimisation(context)
        onPauseOrDispose { }
    }
    if (exempt) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.settings_battery),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.settings_battery_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { requestBatteryExemption(context) }) {
                Text(stringResource(R.string.settings_battery_action))
            }
        }
    }
}

private fun isExemptFromBatteryOptimisation(context: Context): Boolean = runCatching {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    power.isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(true)

/**
 * Asks for the exemption directly, and falls back to the system list if this
 * device does not offer the direct dialogue.
 */
private fun requestBatteryExemption(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { context.startActivity(direct) }
        .recoverCatching { context.startActivity(fallback) }
}

@Composable
private fun isolationTitle(mode: IsolationMode) = when (mode) {
    IsolationMode.SHARED -> stringResource(R.string.isolation_shared)
    IsolationMode.PER_DESTINATION -> stringResource(R.string.isolation_per_host)
    IsolationMode.PER_CONNECTION -> stringResource(R.string.isolation_per_connection)
}

@Composable
private fun isolationDescription(mode: IsolationMode) = when (mode) {
    IsolationMode.SHARED -> stringResource(R.string.isolation_shared_desc)
    IsolationMode.PER_DESTINATION -> stringResource(R.string.isolation_per_host_desc)
    IsolationMode.PER_CONNECTION -> stringResource(R.string.isolation_per_connection_desc)
}

@Composable
private fun dnsTitle(mode: DnsMode) = when (mode) {
    DnsMode.TOR_DNS_PORT -> stringResource(R.string.dns_tor)
    DnsMode.TCP_THROUGH_TUNNEL -> stringResource(R.string.dns_tcp)
    DnsMode.DOH_THROUGH_TUNNEL -> stringResource(R.string.dns_doh)
}

@Composable
private fun dnsDescription(mode: DnsMode) = when (mode) {
    DnsMode.TOR_DNS_PORT -> stringResource(R.string.dns_tor_desc)
    DnsMode.TCP_THROUGH_TUNNEL -> stringResource(R.string.dns_tcp_desc)
    DnsMode.DOH_THROUGH_TUNNEL -> stringResource(R.string.dns_doh_desc)
}
