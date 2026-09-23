package app.papersky.weather.ui.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.papersky.weather.R
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.Units
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.ControlShape
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.debossed
import app.papersky.weather.design.laidDown
import app.papersky.weather.design.pressable
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.Hint
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.ui.common.PaperPage
import app.papersky.weather.ui.common.SectionTitle
import app.papersky.weather.ui.scene.SceneThumbnail
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PlacesScreen(vm: PlacesViewModel, scene: SceneState, units: Units, motion: MotionLevel, village: Boolean, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val field = rememberTextFieldState()
    val h = rememberHaptics()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::locate)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { vm.query.value = it } }

    PaperPage(stringResource(R.string.places_title), scene, motion, onBack, village = village) {
        item("search") { SearchField(field, Modifier.laidDown(0)) }
        when (val s = search) {
            SearchState.Loading, SearchState.Idle -> Unit
            SearchState.Failed -> item("failed") { Hint(stringResource(R.string.search_failed)) }
            is SearchState.Results -> {
                if (s.places.isEmpty()) item("empty") { Hint(stringResource(R.string.search_nothing)) }
                items(s.places, key = { "r-" + it.id }) { place ->
                    SearchResult(place, Modifier.animateItem()) {
                        h.pin()
                        field.clearText()
                        vm.add(place)
                        onBack()
                    }
                }
            }
        }
        if (search !is SearchState.Results) {
            if (rows.none { it.place.id == Place.HERE }) {
                item("locate") {
                    PaperCard(Modifier.laidDown(1)) {
                        BasicText(stringResource(R.string.places_locate_title), style = Paper.type.note.copy(color = Paper.colors.paperInk, fontSize = 23.sp))
                        Spacer(Modifier.height(14.dp))
                        PaperButton(stringResource(R.string.welcome_locate), { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth(), glyph = Glyph.Pin)
                    }
                }
            }
            item("saved-title") { SectionTitle(stringResource(R.string.places_saved)) }
            items(rows, key = { it.place.id }) { row ->
                val content: @Composable () -> Unit = {
                    PlaceCard(row, units, village) {
                        h.pin()
                        vm.select(row.place.id)
                        onBack()
                    }
                }
                Box(Modifier.animateItem()) {
                    if (row.place.id == Place.HERE) content() else SwipeToTear(onRemove = { vm.remove(row.place.id) }, content = content)
                }
            }
            item("swipe-hint") { Hint(stringResource(R.string.places_hint)) }
        }
    }
}

/** A field pressed into the sheet (§3 deboss) to set a town's name in. */
@Composable
private fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    Row(
        modifier
            .fillMaxWidth()
            .debossed(ControlShape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperIconView(PaperIcon.Search, colors.paperInkSoft, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) {
                BasicText(stringResource(R.string.search_hint), style = Paper.type.body.copy(color = colors.paperInkSoft))
            }
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                textStyle = Paper.type.body.copy(color = colors.paperInk),
                cursorBrush = SolidColor(colors.accent),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        if (state.text.isNotEmpty()) {
            Box(Modifier.pressable({ state.clearText() }).padding(4.dp)) { PaperIconView(PaperIcon.Close, colors.paperInkSoft, size = 18.dp) }
        }
    }
}

@Composable
private fun SearchResult(place: Place, modifier: Modifier, onPick: () -> Unit) {
    val colors = Paper.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onPick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Pin, size = 24.dp, animate = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(place.name, style = Paper.type.heading.copy(color = colors.paperInk))
            place.subtitle?.let { BasicText(it, style = Paper.type.caption.copy(color = colors.paperInkSoft)) }
        }
        PaperIconView(PaperIcon.Plus, colors.accent, size = 20.dp)
    }
}

/** A place on its own sheet: a small print of its sky, its name, its weather in the italic. */
@Composable
private fun PlaceCard(row: PlaceRow, units: Units, village: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = Paper.colors
    val f = row.forecast
    val now = System.currentTimeMillis() / 1000
    val moment = f?.momentAt(now)
    val scene = moment?.let { SceneState.from(it, SceneState.seedFor(f.placeId), f) }
    val fmt = remember(f?.timezone, units) { WeatherFormat(context, units, f?.zone ?: java.time.ZoneId.systemDefault()) }
    PaperCard(level = if (row.selected) 2 else 1, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 80.dp).clip(RoundedCornerShape(10.dp))) {
                if (scene != null) SceneThumbnail(scene, Modifier.matchParentSize(), village = village)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.place.isDeviceLocation) {
                        PaperIconView(PaperIcon.Location, colors.accent, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    BasicText(
                        row.place.name.ifBlank { stringResource(R.string.here) },
                        style = Paper.type.heading.copy(color = colors.paperInk),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 21.sp, stepSize = 0.5.sp),
                    )
                }
                row.place.subtitle?.let { BasicText(it, style = Paper.type.caption.copy(color = colors.paperInkSoft), maxLines = 1) }
                if (moment != null) {
                    BasicText(fmt.condition(moment.condition), style = Paper.type.note.copy(color = colors.paperInkSoft, fontSize = 17.sp), maxLines = 1)
                }
            }
            if (moment != null) {
                BasicText(fmt.temp(moment.temperature), style = Paper.type.display.copy(color = colors.paperInk, fontSize = 34.sp, fontWeight = FontWeight(300)))
            }
            if (row.selected) {
                Spacer(Modifier.width(4.dp))
                PaperIconView(PaperIcon.Check, colors.accent, size = 20.dp)
            }
        }
    }
}

/** Swipe left to take a place away: the sheet slides off with a rustle past the threshold. */
@Composable
private fun SwipeToTear(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.remove)
    Box(Modifier.semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true }) }) {
        Row(
            Modifier.matchParentSize().padding(end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            PaperIconView(PaperIcon.Trash, if (armed) Paper.colors.accent else Paper.colors.paperInkSoft, size = 22.dp)
            Spacer(Modifier.width(6.dp))
            BasicText(removeLabel.uppercase(), style = Paper.type.label.copy(color = if (armed) Paper.colors.accent else Paper.colors.paperInkSoft))
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { h.dragStart() },
                        onDragEnd = {
                            scope.launch {
                                if (armed) {
                                    h.rustle()
                                    offset.animateTo(-size.width * 1.3f, tween(260))
                                    onRemove()
                                } else {
                                    offset.animateTo(0f, Motion.release())
                                }
                                armed = false
                            }
                        },
                    ) { change, dx ->
                        change.consume()
                        val next = (offset.value + dx).coerceIn(-size.width.toFloat(), 0f)
                        scope.launch { offset.snapTo(next) }
                        val nowArmed = -next > threshold
                        if (nowArmed != armed) {
                            if (nowArmed) h.threshold() else h.softTick()
                            armed = nowArmed
                        }
                    }
                },
        ) { content() }
    }
}
