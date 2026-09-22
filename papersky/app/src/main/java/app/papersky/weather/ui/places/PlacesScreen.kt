package app.papersky.weather.ui.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
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
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.Ink
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperSheet
import app.papersky.weather.design.PushPin
import app.papersky.weather.design.Stock
import app.papersky.weather.design.indexRuling
import app.papersky.weather.design.laidDown
import app.papersky.weather.design.material
import app.papersky.weather.design.notebookRuling
import app.papersky.weather.design.pressable
import app.papersky.weather.design.pressed
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.design.tiltFor
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.ui.common.PaperPage
import app.papersky.weather.ui.scene.LivingScene
import app.papersky.weather.ui.scene.SceneThumbnail
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Places: a cork board with polaroids pinned to it (DESIGN_DOCTRINE §12). */
@Composable
fun PlacesScreen(vm: PlacesViewModel, scene: SceneState, units: Units, motion: MotionLevel, village: Boolean, onBack: () -> Unit) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val field = rememberTextFieldState()
    val h = rememberHaptics()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::locate)
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { vm.query.value = it } }

    PaperPage(stringResource(R.string.places_title), scene, motion, onBack, table = Stock.Cork, village = village) {
        item("search") { SearchStrip(field, Modifier.laidDown(0)) }
        when (val s = search) {
            SearchState.Loading, SearchState.Idle -> Unit
            SearchState.Failed -> item("failed") { Scrap(stringResource(R.string.search_failed)) }
            is SearchState.Results -> {
                if (s.places.isEmpty()) {
                    item("empty") { Scrap(stringResource(R.string.search_nothing)) }
                } else {
                    item("results") {
                        Results(s.places, Modifier.animateItem()) { place ->
                            h.pin()
                            field.clearText()
                            vm.add(place)
                            onBack()
                        }
                    }
                }
            }
        }
        if (search !is SearchState.Results) {
            if (rows.none { it.place.id == Place.HERE }) {
                item("locate") {
                    PaperSheet(
                        Modifier.laidDown(1),
                        stock = Stock.Notebook,
                        seed = 12,
                        perforated = true,
                        tape = true,
                        contentPadding = PaddingValues(start = 46.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                        decoration = { notebookRuling(top = 52.dp.toPx(), step = 30.dp.toPx(), margin = 34.dp.toPx()) },
                    ) {
                        BasicText(stringResource(R.string.places_locate_title), style = Paper.type.hand.copy(color = Paper.colors.handInk))
                        Spacer(Modifier.height(14.dp))
                        PaperButton(stringResource(R.string.welcome_locate), { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth(), glyph = Glyph.Pin)
                    }
                }
            }
            item("saved-title") { DymoLabel(stringResource(R.string.places_saved), Modifier.laidDown(2)) }
            val pairs = rows.chunked(2)
            items(pairs, key = { pair -> pair.joinToString("|") { it.place.id } }) { pair ->
                Row(Modifier.animateItem().fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { row ->
                        Box(Modifier.weight(1f)) {
                            val content: @Composable () -> Unit = {
                                Polaroid(row, units, motion, village) {
                                    h.pin()
                                    vm.select(row.place.id)
                                    onBack()
                                }
                            }
                            if (row.place.id == Place.HERE) content() else TearOff(onRemove = { vm.remove(row.place.id) }, content = content)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item("swipe-hint") { Scrap(stringResource(R.string.places_hint)) }
        }
    }
}

/** A strip of cotton paper pressed into the board: inner shadow on top, blue-ink cursor. */
@Composable
private fun SearchStrip(state: TextFieldState, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val light = Paper.light
    val shape = RoundedCornerShape(14.dp)
    val fill = light.lit(Stock.Cotton.base)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind { drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, 1.dp.toPx())) }
            .clip(shape)
            .drawBehind {
                drawRect(fill)
                drawRect(Stock.Cotton.brush, alpha = 0.8f)
                drawRect(Brush.verticalGradient(listOf(Ink.Shadow.copy(alpha = 0.26f), Color.Transparent), endY = 8.dp.toPx()))
                drawRect(Brush.horizontalGradient(listOf(Ink.Shadow.copy(alpha = 0.1f), Color.Transparent), endX = 7.dp.toPx()))
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperIconView(PaperIcon.Search, colors.paperInkSoft, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (state.text.isEmpty()) {
                BasicText(stringResource(R.string.search_hint), style = Paper.type.body.copy(color = colors.paperInkSoft).pressed())
            }
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                textStyle = Paper.type.body.copy(color = colors.paperInk),
                cursorBrush = SolidColor(Ink.Blue),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
        if (state.text.isNotEmpty()) {
            Box(Modifier.pressable({ state.clearText() }).padding(4.dp)) { PaperIconView(PaperIcon.Close, colors.paperInkSoft, size = 18.dp) }
        }
    }
}

/** Search results on an index card: one ruled row per town, a plus to pin it. */
@Composable
private fun Results(places: List<Place>, modifier: Modifier, onPick: (Place) -> Unit) {
    val colors = Paper.colors
    val rowH = with(LocalDensity.current) { 58.dp.toPx() }
    PaperSheet(
        modifier,
        stock = Stock.IndexCard,
        seed = 31,
        pin = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        decoration = { indexRuling(10.dp.toPx(), List(places.size) { i -> 10.dp.toPx() + rowH * (i + 1) }) },
    ) {
        places.forEach { place ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .pressable({ onPick(place) }, pressed = 0.98f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Pin, size = 26.dp, animate = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(place.name, style = Paper.type.bodyStrong.copy(color = colors.paperInk).pressed(), maxLines = 1)
                    place.subtitle?.let { BasicText(it, style = Paper.type.caption.copy(color = colors.paperInkSoft).pressed(), maxLines = 1) }
                }
                PaperIconView(PaperIcon.Plus, colors.accent, size = 22.dp)
            }
        }
    }
}

/** A scrap of paper pinned to the cork with a short note. */
@Composable
private fun Scrap(text: String) {
    PaperSheet(
        Modifier.fillMaxWidth(0.82f).padding(top = 6.dp),
        stock = Stock.Cotton,
        seed = text.length,
        level = 1,
        pin = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BasicText(text, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft).pressed())
    }
}

/** An embossed label-maker strip: glossy dark plastic, letters pushed up from behind. */
@Composable
private fun DymoLabel(text: String, modifier: Modifier = Modifier) {
    val light = Paper.light
    val tape = light.lit(Color(0xFF2E3B3A))
    Box(
        modifier
            .padding(top = 8.dp, start = 4.dp)
            .graphicsLayer { rotationZ = -1.2f }
            .material(Stock.Polaroid, RoundedCornerShape(3.dp), level = 1, color = tape)
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f)))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        BasicText(
            text.uppercase(),
            style = Paper.type.label.copy(
                color = Color(0xFFF4F1EA),
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 1.5f),
            ),
        )
    }
}

/**
 * A polaroid pinned to the board: the place's sky as a snapshot that develops when the board is
 * opened; the chosen place is pinned with a red pin and its snapshot is alive.
 */
@Composable
private fun Polaroid(row: PlaceRow, units: Units, motion: MotionLevel, village: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = Paper.colors
    val f = row.forecast
    val now = System.currentTimeMillis() / 1000
    val moment = f?.momentAt(now)
    val scene = moment?.let { SceneState.from(it, SceneState.seedFor(f.placeId), f) }
    val fmt = remember(f?.timezone, units) { WeatherFormat(context, units, f?.zone ?: java.time.ZoneId.systemDefault()) }
    val seed = row.place.id.hashCode()
    val tilt = tiltFor(seed, 1.6f)
    val develop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { develop.animateTo(1f, tween(1800, delayMillis = 200, easing = FastOutSlowInEasing)) }
    val name = row.place.name.ifBlank { stringResource(R.string.here) }
    Box(
        Modifier
            .semantics(mergeDescendants = true) { contentDescription = name }
            .padding(top = 6.dp)
            .graphicsLayer { rotationZ = tilt; transformOrigin = TransformOrigin(0.5f, 0f) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(onClick, pressed = 0.97f)
                .material(Stock.Polaroid, RoundedCornerShape(3.dp), level = if (row.selected) 3 else 2)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(1.5.dp)),
            ) {
                if (scene != null) {
                    if (row.selected && motion != MotionLevel.Still) {
                        LivingScene(scene, Modifier.matchParentSize(), horizon = 0.5f, motion = motion, tilt = false, detail = 0.55f, village = village)
                    } else {
                        SceneThumbnail(scene, Modifier.matchParentSize(), village = village)
                    }
                } else {
                    Box(Modifier.matchParentSize().drawBehind { drawRect(Color(0xFF3A3530)) })
                }
                // Gloss of the print and the frame's lip over it; then the chemistry developing.
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 0.35f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height)))
                            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 0.08f to Color.Transparent))
                            drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.12f), 0.05f to Color.Transparent))
                            val d = develop.value
                            if (d < 1f) drawRect(Color(0xFF6F6A5E).copy(alpha = (1f - d) * 0.9f))
                            if (d < 1f) drawRect(Color(0xFFF3EEDF).copy(alpha = (1f - d) * (1f - d) * 0.7f))
                        },
                )
                if (moment != null) {
                    GlyphIcon(
                        Glyph.of(moment.condition, moment.isDay),
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                        size = 34.dp,
                        rotation = 8f,
                        animate = row.selected,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.place.isDeviceLocation) {
                    PaperIconView(PaperIcon.Location, colors.accent, size = 14.dp)
                    Spacer(Modifier.width(3.dp))
                }
                BasicText(
                    name, Modifier.weight(1f), style = Paper.type.heading.copy(color = colors.paperInk).pressed(), maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
                )
                if (moment != null) {
                    BasicText(fmt.temp(moment.temperature), style = Paper.type.number.copy(color = colors.paperInk, fontSize = 20.sp, fontWeight = FontWeight(800)).pressed())
                }
            }
            val sub = moment?.let { fmt.condition(it.condition) } ?: row.place.subtitle
            if (sub != null) {
                BasicText(sub, style = Paper.type.caption.copy(color = colors.paperInkSoft).pressed(), maxLines = 1)
            }
        }
        PushPin(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp), color = if (row.selected) Ink.RedPencil else Color(0xFFC9A24A))
    }
}

/** Swipe left to tear a polaroid off its pin: it tilts, rustles at the tear point, flies off. */
@Composable
private fun TearOff(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val density = LocalDensity.current
    val threshold = with(density) { 84.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    val removeLabel = stringResource(R.string.remove)
    Box(
        Modifier
            .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemove(); true }) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = offset.value / threshold * -2.5f
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { h.dragStart() },
                    onDragEnd = {
                        scope.launch {
                            if (armed) {
                                h.rustle()
                                offset.animateTo(-size.width * 2.2f, tween(300))
                                onRemove()
                            } else {
                                offset.animateTo(0f, Motion.release())
                            }
                            armed = false
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    val next = (offset.value + dx).coerceIn(-size.width * 1.2f, 0f)
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
