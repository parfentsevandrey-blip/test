package app.opal.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opal.core.designsystem.component.Countries
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelSegment
import app.opal.core.designsystem.component.RadioRow
import app.opal.core.designsystem.component.SearchField
import app.opal.core.designsystem.component.SectionHeader
import app.opal.core.designsystem.component.SubScreenHeader
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme

/**
 * Exit country (ExitNodes {cc}, StrictNodes 0). Reachable only while connected: before that Tor has
 * no GeoIP loaded and users tend to mistake this for a way around blocking.
 */
@Composable
fun ExitCountryRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = LocalToastState.current
    val anyLabel = stringResource(R.string.exit_any)
    val appliedFormat = stringResource(R.string.exit_applied)
    ExitCountryScreen(
        selected = state.settings.exitCountry,
        connected = state.connected || !state.loaded,
        contentPadding = contentPadding,
        onBack = onBack,
        onSelect = { code ->
            viewModel.setExitCountry(code)
            toast.show(
                appliedFormat.format(
                    code?.let { "${Countries.flag(it)} ${Countries.name(it)}" } ?: anyLabel
                ),
                OpalIcons.TravelExplore,
            )
        },
    )
}

@Composable
fun ExitCountryScreen(
    selected: String?,
    connected: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember { Countries.all.map { it to Countries.name(it) } }
    val filtered =
        remember(query, all) {
            all.filter { (code, name) ->
                query.isBlank() ||
                    name.contains(query.trim(), ignoreCase = true) ||
                    code.equals(query.trim(), ignoreCase = true)
            }
        }
    val popular = remember(all) { Countries.popularExits.map { it to Countries.name(it) } }
    val column = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            Box(column) {
                SubScreenHeader(
                    stringResource(R.string.exit_title),
                    stringResource(R.string.settings_back),
                    onBack,
                )
            }
        }
        item(key = "warning") {
            Panel(column.padding(top = 8.dp)) {
                Note(
                    stringResource(R.string.exit_warning),
                    icon = OpalIcons.Warning,
                    tint = OpalTheme.colors.warning,
                )
            }
        }
        if (!connected) {
            item(key = "offline") {
                Note(stringResource(R.string.exit_not_connected), modifier = column)
            }
            return@LazyColumn
        }
        item(key = "any") {
            Panel(column.padding(top = 12.dp)) {
                RadioRow(
                    title = stringResource(R.string.exit_any),
                    subtitle = stringResource(R.string.exit_any_desc),
                    icon = OpalIcons.Public,
                    selected = selected == null,
                    onSelect = { onSelect(null) },
                )
            }
        }
        item(key = "search") {
            SearchField(
                query,
                { query = it },
                stringResource(R.string.exit_search),
                stringResource(R.string.exit_search_clear),
                column.padding(top = 12.dp),
            )
        }
        if (query.isBlank()) {
            item(key = "popular-header") {
                SectionHeader(
                    stringResource(R.string.exit_popular),
                    column.padding(horizontal = 4.dp),
                )
            }
            countryItems(popular, selected, onSelect, column, keyPrefix = "p")
        }
        item(key = "all-header") {
            SectionHeader(stringResource(R.string.exit_all), column.padding(horizontal = 4.dp))
        }
        countryItems(filtered, selected, onSelect, column, keyPrefix = "a")
        item(key = "end") { Text("", modifier = Modifier.padding(bottom = 16.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.countryItems(
    countries: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
    keyPrefix: String,
) {
    itemsIndexed(countries, key = { _, c -> "$keyPrefix-${c.first}" }) { index, (code, name) ->
        PanelSegment(first = index == 0, last = index == countries.lastIndex, modifier = modifier) {
            RadioRow(
                title = name,
                selected = selected == code,
                onSelect = { onSelect(code) },
                leading = { Text(Countries.flag(code), style = OpalTheme.type.headline) },
            )
        }
    }
}
