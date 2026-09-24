package app.opal.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opal.core.designsystem.component.CheckRow
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelSegment
import app.opal.core.designsystem.component.ScreenTitle
import app.opal.core.designsystem.component.SearchField
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SwitchRow
import app.opal.core.designsystem.glass.GlassButton
import app.opal.core.designsystem.glass.GlassSegmented
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.model.settings.SplitTunnelMode
import kotlinx.collections.immutable.persistentListOf

sealed interface AppsAction {
    data class SetMode(val mode: SplitTunnelMode) : AppsAction

    data class SetQuery(val query: String) : AppsAction

    data class SetShowSystem(val show: Boolean) : AppsAction

    data class SetChecked(val packageName: String, val checked: Boolean) : AppsAction

    data class SetPreset(val applied: Boolean) : AppsAction
}

@Composable
fun AppsRoute(viewModel: AppsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppsScreen(
        state,
        contentPadding,
        onAction = { action ->
            when (action) {
                is AppsAction.SetMode -> viewModel.setMode(action.mode)
                is AppsAction.SetQuery -> viewModel.setQuery(action.query)
                is AppsAction.SetShowSystem -> viewModel.setShowSystem(action.show)
                is AppsAction.SetChecked -> viewModel.setChecked(action.packageName, action.checked)
                is AppsAction.SetPreset -> viewModel.setPresetApplied(action.applied)
            }
        },
    )
}

@Composable
fun AppsScreen(
    state: AppsUiState,
    contentPadding: PaddingValues,
    onAction: (AppsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    val modes = persistentListOf(SplitTunnelMode.AllExcept, SplitTunnelMode.OnlySelected)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val column = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
        item(key = "title") {
            ScreenTitle(stringResource(R.string.apps_title), column.padding(horizontal = 0.dp))
        }
        item(key = "mode") {
            GlassSegmented(
                options =
                    persistentListOf(
                        stringResource(R.string.apps_mode_all_except),
                        stringResource(R.string.apps_mode_only_selected),
                    ),
                selectedIndex = modes.indexOf(state.mode),
                onSelect = { onAction(AppsAction.SetMode(modes[it])) },
                modifier = column.padding(top = 4.dp),
            )
        }
        item(key = "hint") {
            Text(
                stringResource(
                    if (state.mode == SplitTunnelMode.AllExcept) R.string.apps_hint_all_except
                    else R.string.apps_hint_only_selected
                ) + " " + stringResource(R.string.apps_hint_apply),
                style = OpalTheme.type.callout,
                color = colors.onBackgroundMuted,
                modifier = column.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }
        if (state.mode == SplitTunnelMode.AllExcept) {
            item(key = "preset") { PresetPanel(state, onAction, column.padding(top = 16.dp)) }
        }
        item(key = "lockdown") {
            val warn = state.lockdown == true
            Note(
                stringResource(
                    if (warn) R.string.apps_lockdown_warning else R.string.apps_lockdown_note
                ),
                icon = if (warn) OpalIcons.Warning else OpalIcons.Info,
                tint = if (warn) colors.warning else colors.onBackgroundMuted,
                modifier = column,
            )
        }
        if (state.mode == SplitTunnelMode.OnlySelected && state.selectedCount == 0) {
            item(key = "empty-selection") {
                Note(stringResource(R.string.apps_only_selected_empty), modifier = column)
            }
        }
        item(key = "search") {
            SearchField(
                value = state.query,
                onValueChange = { onAction(AppsAction.SetQuery(it)) },
                placeholder = stringResource(R.string.apps_search),
                clearLabel = stringResource(R.string.apps_search_clear),
                modifier = column.padding(top = 4.dp),
            )
        }
        item(key = "system") {
            Panel(column.padding(top = 12.dp)) {
                SwitchRow(
                    title = stringResource(R.string.apps_show_system),
                    checked = state.showSystem,
                    onCheckedChange = { onAction(AppsAction.SetShowSystem(it)) },
                )
            }
        }
        item(key = "count") {
            SectionHeader(
                stringResource(
                    if (state.mode == SplitTunnelMode.AllExcept) R.string.apps_selected_direct
                    else R.string.apps_selected_tor,
                    state.selectedCount,
                ),
                column.padding(horizontal = 0.dp),
            )
        }
        when {
            state.loading ->
                item(key = "loading") {
                    Note(stringResource(R.string.apps_loading), modifier = column)
                }
            state.rows.isEmpty() ->
                item(key = "none") { Note(stringResource(R.string.apps_empty), modifier = column) }
            else ->
                itemsIndexed(state.rows, key = { _, row -> row.packageName }) { index, row ->
                    PanelSegment(
                        first = index == 0,
                        last = index == state.rows.lastIndex,
                        modifier = column.animateItem(),
                    ) {
                        CheckRow(
                            title = row.label,
                            subtitle =
                                if (row.isSystem)
                                    "${row.packageName} · ${stringResource(R.string.apps_system_label)}"
                                else row.packageName,
                            checked = row.checked,
                            onCheckedChange = {
                                onAction(AppsAction.SetChecked(row.packageName, it))
                            },
                            leading = { AppIcon(row.packageName) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun PresetPanel(
    state: AppsUiState,
    onAction: (AppsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpalTheme.colors
    Panel(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.apps_preset_title),
                    style = OpalTheme.type.bodyStrong,
                    color = colors.onBackground,
                )
                Text(
                    if (state.presetInstalled > 0)
                        stringResource(
                            R.string.apps_preset_found,
                            state.presetInstalled,
                            state.presetTotal,
                        )
                    else stringResource(R.string.apps_preset_none),
                    style = OpalTheme.type.callout,
                    color = colors.onBackgroundMuted,
                )
            }
            GlassButton(
                onClick = { onAction(AppsAction.SetPreset(!state.presetApplied)) },
                enabled = state.presetInstalled > 0,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(
                        if (state.presetApplied) R.string.apps_preset_remove
                        else R.string.apps_preset_apply
                    ),
                    style = OpalTheme.type.label,
                )
            }
        }
        Text(
            stringResource(R.string.apps_preset_why),
            style = OpalTheme.type.caption,
            color = colors.onBackgroundMuted,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
}
