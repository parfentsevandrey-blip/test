package app.rosa.weather.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.Place
import app.rosa.weather.ui.common.GlassScreen

@Composable
fun SearchRoute(viewModel: SearchViewModel, onBack: () -> Unit, onAdded: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val haptics = LocalHaptics.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    GlassScreen(title = stringResource(R.string.places_add), onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            GlassSurface(Modifier.fillMaxWidth().padding(top = 8.dp), style = GlassStyle.Regular, cornerRadius = 26.dp, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RosaIconView(RosaIcon.Search, Rosa.colors.inkSoft, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQuery,
                        singleLine = true,
                        textStyle = Rosa.type.headline.copy(color = Rosa.colors.ink),
                        cursorBrush = SolidColor(Rosa.colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = Rosa.type.headline, color = Rosa.colors.inkFaint)
                            inner()
                        },
                    )
                }
            }
            AnimatedContent(results, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "results") { r ->
                when (r) {
                    is SearchResults.Found -> LazyColumn(
                        contentPadding = PaddingValues(top = 14.dp, bottom = ime + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(r.places, key = { it.id }) { place ->
                            ResultRow(place) {
                                haptics?.confirm()
                                viewModel.add(place, onAdded)
                            }
                        }
                    }
                    SearchResults.Empty -> Message(stringResource(R.string.search_empty))
                    SearchResults.Error -> Message(stringResource(R.string.search_error))
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ResultRow(place: Place, onClick: () -> Unit) {
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick),
        style = GlassStyle.Frosted,
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column {
            Text(place.name, style = Rosa.type.headline, color = Rosa.colors.ink)
            Text(listOfNotNull(place.region, place.country).joinToString(", "), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = Rosa.type.body, color = Rosa.colors.inkSoft, modifier = Modifier.padding(20.dp))
}
