package app.veil.vpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.veil.vpn.R
import app.veil.vpn.data.AppRoutingMode
import app.veil.vpn.data.InstalledApp
import app.veil.vpn.ui.components.SectionHeader

@Composable
fun AppsScreen(
    apps: List<InstalledApp>,
    mode: AppRoutingMode,
    selected: Set<String>,
    onLoad: () -> Unit,
    onModeChange: (AppRoutingMode) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }
    var query by remember { mutableStateOf("") }

    val visible = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            // ButtonGroup's content lambda is a plain scope, not a composable
            // one, so anything that reads from the composition happens first.
            val options = listOf(
                AppRoutingMode.ALL to stringResource(R.string.apps_mode_all),
                AppRoutingMode.ONLY_SELECTED to stringResource(R.string.apps_mode_allow),
                AppRoutingMode.EXCEPT_SELECTED to stringResource(R.string.apps_mode_deny),
            )
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text(stringResource(R.string.apps_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            if (mode == AppRoutingMode.ALL) {
                SectionHeader(stringResource(R.string.apps_mode_all))
                Text(
                    text = stringResource(R.string.apps_mode_all_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
        }

        if (apps.isEmpty()) {
            LoadingIndicator(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .align(Alignment.CenterHorizontally),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = visible.size, key = { visible[it].packageName }) { index ->
                val app = visible[index]
                AppRow(
                    app = app,
                    checked = app.packageName in selected,
                    enabled = mode != AppRoutingMode.ALL,
                    onToggle = { onToggle(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val icon = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(width = 96, height = 96) }.getOrNull()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (enabled) onToggle() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    }
}
