package app.rosa.weather.ui.places

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.designsystem.theme.toColor
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.GlassScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PlacesRoute(viewModel: PlacesViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlacesScreen(
        state,
        onBack = onBack,
        onAdd = onAdd,
        onFollow = { viewModel.setFollowDevice(it) },
        onSelect = { id -> viewModel.select(id); onBack() },
        onRemove = { viewModel.remove(it) },
        onMove = { from, to -> viewModel.move(from, to) },
    )
}

/** The saved places, each a small window onto its own sky; [now] is when the cards show them. */
@Composable
fun PlacesScreen(
    state: PlacesUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onFollow: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    now: Long = System.currentTimeMillis() / 1000,
) {
    val context = LocalContext.current
    val format = remember(state.units) { WeatherFormat(context, state.units) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassScreen(
        title = stringResource(R.string.places_title),
        onBack = onBack,
        actions = { GlassIconButton(RosaIcon.Plus, stringResource(R.string.cd_add), onAdd) },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "follow") {
                GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 26.dp, contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RosaIconView(RosaIcon.Location, Rosa.colors.ink, size = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.places_follow), style = Rosa.type.headline, color = Rosa.colors.ink)
                            Text(stringResource(R.string.places_follow_hint), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
                        }
                        GlassToggle(state.followDevice, onFollow)
                    }
                }
            }
            state.device?.takeIf { state.followDevice }?.let { device ->
                item(key = device.id) {
                    PlaceCard(device, state.forecasts[device.id], format, now, onClick = { onSelect(device.id) }, onDelete = null)
                }
            }
            itemsIndexed(state.places, key = { _, p -> p.id }) { index, place ->
                PlaceCard(
                    place,
                    state.forecasts[place.id],
                    format,
                    now,
                    onClick = { onSelect(place.id) },
                    onDelete = { onRemove(place.id) },
                    onMoveUp = if (index > 0) ({ onMove(index, index - 1) }) else null,
                    onMoveDown = if (index < state.places.lastIndex) ({ onMove(index, index + 1) }) else null,
                )
            }
            if (state.places.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.places_empty), style = Rosa.type.body, color = Rosa.colors.inkSoft, modifier = Modifier.padding(8.dp))
                }
            }
            item(key = "add") {
                GlassButton(onClick = onAdd, modifier = Modifier.fillMaxWidth(), style = GlassStyle.Clear) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RosaIconView(RosaIcon.Plus, Rosa.colors.ink, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.places_add), style = Rosa.type.headline, color = Rosa.colors.ink)
                    }
                }
            }
        }
    }
}

/**
 * A place as a tiny window onto its own sky: the card is tinted with that city's current palette,
 * so a glance down the list shows where it's night, where it's snowing, where the sun is setting.
 * Swipe left to remove.
 */
@Composable
private fun PlaceCard(
    place: Place,
    forecast: Forecast?,
    format: WeatherFormat,
    now: Long,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val moment = remember(forecast, now / 300) { forecast?.momentAt(now) }
    val palette = moment?.let { SkyPalette.of(it.sun.elevation, it.visual, it.moonPhase.illumination) }
    val zoned = remember(format, forecast) { format.withZone(WeatherFormat.zoneOf(forecast?.timezone, forecast?.utcOffsetSeconds ?: 0)) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current
    val name = place.name.ifBlank { format.currentLocation() }
    val deleteLabel = stringResource(R.string.places_delete)

    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .semantics {
                customActions = buildList {
                    if (onDelete != null) add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                    if (onMoveUp != null) add(CustomAccessibilityAction("↑") { onMoveUp(); true })
                    if (onMoveDown != null) add(CustomAccessibilityAction("↓") { onMoveDown(); true })
                }
            }
            .then(
                if (onDelete == null) Modifier else Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -size.width * 0.35f) {
                                    haptics?.confirm()
                                    offset.animateTo(-size.width.toFloat(), RosaMotion.gel())
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, RosaMotion.gel())
                                }
                            }
                        },
                    ) { _, drag -> scope.launch { offset.snapTo((offset.value + drag).coerceAtMost(0f)) } }
                },
            ),
    ) {
        GlassSurface(
            Modifier
                .matchParentSize()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onClick),
            style = GlassStyle.Frosted,
            cornerRadius = 28.dp,
        ) {
            if (palette != null) {
                val top = palette.zenith.toColor().copy(alpha = 0.55f)
                val bottom = palette.horizon.toColor().copy(alpha = 0.4f)
                Canvas(Modifier.matchParentSize()) { drawRect(Brush.linearGradient(listOf(top, bottom))) }
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (place.isCurrentLocation) {
                            RosaIconView(RosaIcon.Location, Rosa.colors.ink, size = 14.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(name, style = Rosa.type.headline, color = Rosa.colors.ink, maxLines = 1)
                    }
                    Text(
                        listOfNotNull(forecast?.let { zoned.time(now) }, place.region ?: place.country).joinToString(" · "),
                        style = Rosa.type.caption,
                        color = Rosa.colors.inkSoft,
                        maxLines = 1,
                    )
                    if (moment != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(zoned.condition(moment.condition, moment.isDay), style = Rosa.type.label, color = Rosa.colors.ink)
                    }
                }
                if (moment != null) {
                    WeatherGlyph(moment.condition, moment.isDay, Modifier.size(46.dp), moonPhase = moment.moonPhase.phase)
                    Spacer(Modifier.width(8.dp))
                    Text(zoned.temperature(moment.temperature), style = Rosa.type.display, color = Rosa.colors.ink)
                }
            }
        }
    }
}
