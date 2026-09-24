package app.opal.feature.connection

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opal.core.designsystem.component.Countries
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelDivider
import app.opal.core.designsystem.component.RadioRow
import app.opal.core.designsystem.component.ScreenTitle
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SettingRow
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.tunnel.Labels

sealed interface ConnectionAction {
    data class SetMode(val mode: ConnectionMode) : ConnectionAction

    data object EditCustomBridges : ConnectionAction
}

@Composable
fun ConnectionRoute(
    viewModel: ConnectionViewModel,
    onEditCustomBridges: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectionScreen(
        state,
        contentPadding,
        onAction = { action ->
            when (action) {
                is ConnectionAction.SetMode ->
                    if (action.mode == ConnectionMode.Custom && state.customCount == 0)
                        onEditCustomBridges()
                    else viewModel.setMode(action.mode)
                ConnectionAction.EditCustomBridges -> onEditCustomBridges()
            }
        },
    )
}

@Composable
fun ConnectionScreen(
    state: ConnectionUiState,
    contentPadding: PaddingValues,
    onAction: (ConnectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            ScreenTitle(
                stringResource(R.string.connection_title),
                Modifier.padding(horizontal = 0.dp),
            )
            state.activeTransport?.let {
                Text(
                    stringResource(R.string.connection_now, stringResource(Labels.transport(it))),
                    style = OpalTheme.type.callout,
                    color = colors.connected,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            SectionHeader(
                stringResource(R.string.connection_section_method),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                val modes =
                    listOf(
                        Triple(
                            ConnectionMode.Auto,
                            stringResource(R.string.connection_auto),
                            stringResource(R.string.connection_auto_desc),
                        ),
                        Triple(
                            ConnectionMode.Snowflake,
                            stringResource(Labels.transport(TransportKind.Snowflake)),
                            stringResource(R.string.connection_snowflake_desc),
                        ),
                        Triple(
                            ConnectionMode.WebTunnel,
                            stringResource(Labels.transport(TransportKind.WebTunnel)),
                            stringResource(R.string.connection_webtunnel_desc),
                        ),
                        Triple(
                            ConnectionMode.Obfs4,
                            stringResource(Labels.transport(TransportKind.Obfs4)),
                            stringResource(R.string.connection_obfs4_desc),
                        ),
                        Triple(
                            ConnectionMode.Meek,
                            stringResource(Labels.transport(TransportKind.Meek)),
                            stringResource(R.string.connection_meek_desc),
                        ),
                    )
                modes.forEach { (mode, title, desc) ->
                    RadioRow(
                        title = title,
                        subtitle = desc,
                        icon = modeIcon(mode),
                        selected = state.mode == mode,
                        onSelect = { onAction(ConnectionAction.SetMode(mode)) },
                    )
                    PanelDivider()
                }
                RadioRow(
                    title = stringResource(R.string.connection_custom),
                    subtitle =
                        if (state.customCount > 0)
                            pluralStringResource(
                                R.plurals.connection_custom_count,
                                state.customCount,
                                state.customCount,
                            )
                        else stringResource(R.string.connection_custom_none),
                    icon = OpalIcons.Key,
                    selected = state.mode == ConnectionMode.Custom,
                    onSelect = { onAction(ConnectionAction.SetMode(ConnectionMode.Custom)) },
                )
                SettingRow(
                    title = stringResource(R.string.connection_custom_edit),
                    icon = OpalIcons.Tune,
                    onClick = { onAction(ConnectionAction.EditCustomBridges) },
                ) {
                    Icon(
                        OpalIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.onBackgroundMuted,
                    )
                }
            }
            Note(stringResource(R.string.connection_apply_note))

            SectionHeader(
                stringResource(R.string.connection_section_api),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth()) {
                val api = state.settingsApi
                if (api == null) {
                    Note(stringResource(R.string.connection_api_never), icon = OpalIcons.Schedule)
                } else {
                    val whenText =
                        DateUtils.getRelativeTimeSpanString(
                                api.fetchedAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            )
                            .toString()
                    val parts =
                        listOfNotNull(
                            stringResource(R.string.connection_api_fetched, whenText),
                            api.country?.let {
                                stringResource(
                                    R.string.connection_api_country,
                                    "${Countries.flag(it)} ${Countries.name(it)}",
                                )
                            },
                        )
                    SettingRow(
                        title = parts.joinToString(" · "),
                        icon = OpalIcons.Download,
                        subtitle =
                            stringResource(
                                R.string.connection_api_types,
                                api.types.joinToString(", "),
                            ),
                    )
                }
                PanelDivider()
                Note(stringResource(R.string.connection_api_note), icon = OpalIcons.VisibilityOff)
            }

            SectionHeader(
                stringResource(R.string.connection_section_winners),
                Modifier.padding(horizontal = 4.dp),
            )
            Panel(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                if (state.winners.isEmpty()) {
                    Note(stringResource(R.string.connection_winners_none))
                } else {
                    state.winners.entries
                        .sortedBy { it.key.ordinal }
                        .forEach { (network, transport) ->
                            SettingRow(
                                title =
                                    stringResource(
                                        R.string.connection_winner_row,
                                        stringResource(networkLabel(network)),
                                        stringResource(Labels.transport(transport)),
                                    ),
                                icon = networkIcon(network),
                            )
                        }
                }
            }
        }
    }
}

private fun modeIcon(mode: ConnectionMode): ImageVector =
    when (mode) {
        ConnectionMode.Auto -> OpalIcons.Bolt
        ConnectionMode.Snowflake -> OpalIcons.AcUnit
        ConnectionMode.WebTunnel -> OpalIcons.Public
        ConnectionMode.Obfs4 -> OpalIcons.Key
        ConnectionMode.Meek -> OpalIcons.Cloud
        ConnectionMode.Custom -> OpalIcons.Key
    }

private fun networkLabel(kind: NetworkKind) =
    when (kind) {
        NetworkKind.Wifi -> R.string.connection_network_wifi
        NetworkKind.Cellular -> R.string.connection_network_cellular
        NetworkKind.Ethernet -> R.string.connection_network_ethernet
        NetworkKind.Other -> R.string.connection_network_other
    }

private fun networkIcon(kind: NetworkKind) =
    when (kind) {
        NetworkKind.Wifi -> OpalIcons.Wifi
        NetworkKind.Cellular -> OpalIcons.SignalCellularAlt
        NetworkKind.Ethernet,
        NetworkKind.Other -> OpalIcons.Hub
    }
