package app.quire.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.quire.R
import app.quire.weather.Place

/**
 * Choosing where the weather is for.
 *
 * Two ways, and the app works with either: name a place, or let the device say. Naming one is the
 * path that needs no permission at all, which is why it is the first thing in the sheet rather
 * than a fallback buried under a refusal.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaceSheet(
    model: WeatherModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.search(it) },
                label = { Text(stringResource(R.string.wx_place_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { model.search(model.query) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(8.dp))

            ListItem(
                onClick = {
                    model.useMyLocation()
                    onDismiss()
                },
                leadingContent = { Icon(Icons.Default.LocationSearching, null) },
                supportingContent = { Text(stringResource(R.string.wx_use_location_hint)) },
            ) {
                Text(stringResource(R.string.wx_use_location))
            }

            HorizontalDivider()

            when {
                model.searching -> {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        LoadingIndicator()
                    }
                }
                model.query.trim().length >= 2 && model.results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.wx_place_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn {
                        items(model.results) { place ->
                            PlaceRow(place) {
                                model.choose(place)
                                onDismiss()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onPick: () -> Unit) {
    ListItem(
        onClick = onPick,
        leadingContent = { Icon(Icons.Default.Place, null) },
        supportingContent = place.describe().takeIf { it.isNotBlank() }?.let { { Text(it) } },
    ) {
        Text(place.name)
    }
}
