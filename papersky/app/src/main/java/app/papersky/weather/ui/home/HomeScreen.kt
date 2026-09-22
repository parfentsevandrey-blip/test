package app.papersky.weather.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.papersky.weather.R
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.core.text.Narrator
import app.papersky.weather.core.text.NoteText
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.Choreography
import app.papersky.weather.design.CutText
import app.papersky.weather.design.DeckleShape
import app.papersky.weather.design.DiscButton
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.HandReveal
import app.papersky.weather.design.Ink
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalChoreography
import app.papersky.weather.design.Motion
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperSheet
import app.papersky.weather.design.Postcard
import app.papersky.weather.design.PushPin
import app.papersky.weather.design.RollingText
import app.papersky.weather.design.Stock
import app.papersky.weather.design.TagShape
import app.papersky.weather.design.WashiTape
import app.papersky.weather.design.eyelet
import app.papersky.weather.design.laidDown
import app.papersky.weather.design.material
import app.papersky.weather.design.notebookRuling
import app.papersky.weather.design.onSky
import app.papersky.weather.design.pressable
import app.papersky.weather.design.pressed
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.ui.scene.LivingScene
import app.papersky.weather.ui.scene.SceneController
import app.papersky.weather.ui.scene.SceneThumbnail
import app.papersky.weather.ui.scene.isHorizontal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh(userInitiated = false)
        onPauseOrDispose { }
    }

    var preview by rememberSaveable { mutableStateOf<Long?>(null) }
    val forecast = state.forecast
    val nowSec = nowMillis / 1000
    val live = forecast != null && forecast.ageMinutes(nowMillis) < 75
    val nowMoment = forecast?.momentAt(nowSec, preferLive = live)
    // The sky rehearses the previewed hour; the paper below keeps describing now.
    val moment = if (preview != null) forecast?.momentAt(preview!!, preferLive = false) else nowMoment
    val scene = if (forecast != null && moment != null) SceneState.from(moment, SceneState.seedFor(forecast.placeId), forecast) else IdleScene
    val units = state.settings.resolvedUnits(LocalConfiguration.current.locales[0])
    val fmt = remember(forecast?.timezone, units, context) {
        WeatherFormat(context, units, forecast?.zone ?: java.time.ZoneId.systemDefault())
    }
    val colors = Paper.colors
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.sky)) {
        val screenH = maxHeight
        val heroH = (screenH * 0.6f).coerceIn(440.dp, 620.dp)
        val depth = (screenH * 0.21f).coerceIn(140.dp, 210.dp)
        val horizon = ((heroH - 30.dp - depth * 0.64f) / screenH).coerceIn(0.3f, 0.7f)
        val heroPx = with(density) { heroH.toPx() }
        val depthPx = with(density) { depth.toPx() }
        val listState = rememberLazyListState()
        val scroll = remember(listState, heroPx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) heroPx * 1.4f else listState.firstVisibleItemScrollOffset.toFloat()
            }
        }
        val controller = remember { SceneController() }

        LivingScene(
            target = scene,
            modifier = Modifier.fillMaxSize(),
            horizon = horizon,
            depth = depthPx,
            motion = state.settings.motion,
            tilt = state.settings.tiltParallax,
            controller = controller,
            transitionMillis = if (preview != null) 420 else 1100,
            laneStart = 0.58f,
            laneEnd = 0.94f,
            glass = true,
            village = state.settings.village,
            scroll = { scroll.value },
            dim = { (scroll.value / heroPx).coerceIn(0f, 1f) * 0.22f },
        )

        CompositionLocalProvider(LocalChoreography provides remember(forecast?.placeId) { Choreography() }) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item("hero", contentType = "hero") {
                    Hero(
                        state = state, moment = moment, fmt = fmt, nowSec = nowSec, preview = preview,
                        controller = controller, height = heroH, scroll = scroll, heroPx = heroPx,
                        onBackToNow = { preview = null }, onRefresh = { vm.refresh(true) },
                    )
                }
                if (forecast != null && nowMoment != null) {
                    val side = Modifier.padding(horizontal = 14.dp)
                    item("note", contentType = "note") { NoteSheet(forecast, fmt, nowSec - nowSec % 900, nowMoment, side.laidDown(0)) }
                    item("hourly", contentType = "hourly") { HourlyCard(forecast, fmt, nowSec, preview, { preview = it }, side.laidDown(1)) }
                    item("daily", contentType = "daily") { DailyCard(forecast, fmt, nowSec, nowMoment.temperature, { preview = it }, side.laidDown(2)) }
                    item("details", contentType = "details") { DetailsGrid(forecast, nowMoment, fmt, nowSec, side.laidDown(3)) }
                    item("widgets", contentType = "promo") { WidgetPromo(scene, state.settings.village, onOpenWidgets, Modifier.padding(horizontal = 18.dp).laidDown(4)) }
                    item("credits", contentType = "credits") { Credits() }
                } else if (state.loaded) {
                    item("welcome") {
                        Welcome(
                            refreshing = state.refreshing,
                            hasPlace = state.place != null,
                            onPermission = vm::onLocationPermissionResult,
                            onFindCity = onOpenPlaces,
                            modifier = Modifier.padding(horizontal = 16.dp).laidDown(0),
                        )
                    }
                }
            }
        }

        // Keeps status-bar icons legible over bright skies.
        TopScrim()

        TopBar(
            place = state.place,
            compact = nowMoment?.let { fmt.temp(it.temperature) },
            progress = { (scroll.value / (heroPx * 0.72f)).coerceIn(0f, 1f) },
            onOpenPlaces = onOpenPlaces,
            onOpenSettings = onOpenSettings,
            onOpenWidgets = onOpenWidgets,
        )

        PullCord(
            refreshing = state.refreshing,
            onPull = { vm.refresh(true) },
            label = stringResource(R.string.refresh),
            tag = stringResource(R.string.cord_tag),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset((-54).dp.roundToPx(), -scroll.value.toInt().coerceAtMost(260.dp.roundToPx())) }
                .graphicsLayer { alpha = 1f - (scroll.value / (heroPx * 0.45f)).coerceIn(0f, 1f) },
        )
    }
}

/** Neutral daytime scene shown before any forecast exists. */
private val IdleScene = SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.3f, windX = 1.5f, temperature = 18f, seed = 3)

@Composable
private fun TopScrim() {
    val colors = Paper.colors
    val tint = if (colors.onSky.luminance() > 0.5f) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.16f)
    val h = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .drawWithCache {
                val brush = Brush.verticalGradient(listOf(tint, Color.Transparent))
                onDrawBehind { drawRect(brush) }
            },
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * The top of the table: the place's kraft tag pinned on the left, paper discs on the right. When
 * the paper below scrolls up, a strip of cotton paper with a torn edge slides in with the
 * temperature on it.
 */
@Composable
private fun TopBar(
    place: Place?,
    compact: String?,
    progress: () -> Float,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
) {
    val colors = Paper.colors
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress()
                    alpha = if (p <= 0.01f) 0f else 1f
                    translationY = -(1f - p) * size.height
                }
                .material(Stock.Cotton, DeckleShape(seed = 9, corner = 2.dp, roughness = 1.4.dp), level = 3),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaceTag(place, onOpenPlaces)
            if (compact != null) {
                Spacer(Modifier.width(12.dp))
                BasicText(
                    compact,
                    Modifier.graphicsLayer {
                        val p = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        alpha = p
                        translationY = (1f - p) * 10.dp.toPx()
                    },
                    style = Paper.type.title.copy(color = colors.paperInk, fontWeight = FontWeight(800), fontFeatureSettings = "tnum").pressed(),
                )
            }
            Spacer(Modifier.weight(1f))
            DiscButton(PaperIcon.Widgets, stringResource(R.string.widgets_title), onOpenWidgets)
            Spacer(Modifier.width(52.dp)) // room for the pull cord
            DiscButton(PaperIcon.Settings, stringResource(R.string.settings_title), onOpenSettings)
        }
    }
}

/** A kraft luggage tag pinned through its eyelet; it swings on its pin when the place changes. */
@Composable
private fun PlaceTag(place: Place?, onClick: () -> Unit) {
    val colors = Paper.colors
    val sway = remember { Animatable(0f) }
    val h = rememberHaptics()
    val scope = rememberCoroutineScope()
    LaunchedEffect(place?.id) {
        if (place == null) return@LaunchedEffect
        sway.snapTo(9f)
        sway.animateTo(0f, Motion.string())
    }
    val name = place?.name?.ifBlank { null } ?: stringResource(R.string.here)
    Box(
        Modifier
            .semantics(mergeDescendants = true) { contentDescription = name }
            .graphicsLayer {
                rotationZ = sway.value - 2f
                transformOrigin = TransformOrigin(0.06f, 0.5f)
            },
    ) {
        Row(
            Modifier
                .pressable({
                    scope.launch { sway.animateTo(sway.value + 6f, Motion.press()); sway.animateTo(0f, Motion.string()) }
                    onClick()
                }, pressed = 0.95f)
                .material(Stock.Kraft, TagShape(11.dp), level = 2)
                .drawBehind { eyelet(Offset(11.dp.toPx(), size.height / 2)) }
                .widthIn(max = 220.dp)
                .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                name,
                transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
                label = "place",
            ) { n ->
                BasicText(n, style = Paper.type.bodyStrong.copy(color = Color(0xFF33241A)).pressed(), maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
            PaperIconView(PaperIcon.Chevron, Color(0xFF33241A).copy(alpha = 0.7f), size = 14.dp)
        }
        PushPin(Modifier.align(Alignment.CenterStart).offset(x = 1.dp, y = (-1).dp))
    }
}

/** The ticket's outline: side notches and rounded corners, like a tram ticket. */
private object TicketShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 4.dp.toPx() }
        val notch = with(density) { 7.dp.toPx() }
        val cy = size.height / 2
        val p = Path().apply {
            moveTo(r, 0f)
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            lineTo(size.width, cy - notch)
            arcTo(androidx.compose.ui.geometry.Rect(Offset(size.width, cy), notch), 270f, -180f, false)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, cy + notch)
            arcTo(androidx.compose.ui.geometry.Rect(Offset(0f, cy), notch), 90f, -180f, false)
            lineTo(0f, r)
            quadraticTo(0f, 0f, r, 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

@Composable
private fun Hero(
    state: HomeUiState,
    moment: WeatherMoment?,
    fmt: WeatherFormat,
    nowSec: Long,
    preview: Long?,
    controller: SceneController,
    height: Dp,
    scroll: State<Float>,
    heroPx: Float,
    onBackToNow: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = Paper.colors
    val h = rememberHaptics()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val lightInk = colors.onSky.luminance() > 0.5f
    // The numerals are cut from card: cream card under dark skies, dark card under bright ones.
    val face = if (lightInk) Color(0xFFFBF3E2) else Color(0xFF3A332C)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controller.tap(origin + it) },
                    onPress = { pos ->
                        val job = scope.launch {
                            delay(420)
                            controller.hold(origin + pos, true)
                        }
                        tryAwaitRelease()
                        job.cancel()
                        controller.hold(origin + pos, false)
                    },
                )
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        if (isHorizontal(v.x, v.y) && abs(v.x) > 300f) {
                            h.softTick()
                            controller.fling(v.x)
                        }
                    },
                ) { change, _ -> tracker.addPosition(change.uptimeMillis, change.position) }
            },
    ) {
        if (moment == null) return@Box
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 72.dp)
                .graphicsLayer {
                    // Drifts up slower than the page and sinks back into the diorama.
                    val s = scroll.value
                    val p = (s / heroPx).coerceIn(0f, 1f)
                    translationY = s * 0.42f
                    alpha = (1f - p * 1.7f).coerceIn(0f, 1f)
                    scaleX = 1f - p * 0.1f
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            AnimatedVisibility(
                preview != null,
                enter = fadeIn() + scaleIn(Motion.release(), initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0.5f)),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                Row(
                    Modifier
                        .padding(bottom = 8.dp)
                        .pressable({ h.confirm(); onBackToNow() }, pressed = 0.94f)
                        .material(Stock.Cotton, TagShape(9.dp), level = 2)
                        .drawBehind { eyelet(Offset(10.dp.toPx(), size.height / 2)) }
                        .padding(start = 21.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        preview?.let { stringResource(R.string.preview_at, fmt.dayName(it, nowSec), fmt.time(it)) } ?: "",
                        style = Paper.type.caption.copy(color = colors.paperInk, fontWeight = FontWeight(700)).pressed(),
                    )
                    Spacer(Modifier.width(8.dp))
                    PaperIconView(PaperIcon.Close, colors.paperInkSoft, size = 13.dp)
                }
            }
            val tempNumber = fmt.tempNumber(moment.temperature)
            val hero = Paper.type.hero
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                RollingText(tempNumber, hero, numericValue = moment.temperature) { ch -> CutText(ch.toString(), hero, face) }
                CutText("°", hero.copy(fontSize = 92.sp), face)
            }
            Spacer(Modifier.height(6.dp))
            Ticket(state, moment, fmt)
            Spacer(Modifier.height(10.dp))
            UpdatedLabel(state, fmt, lightInk, onRefresh)
        }
    }
}

/**
 * A kraft ticket taped under the numerals: conditions, high and low, what it feels like.
 * The day's sticker is stuck on its right end.
 */
@Composable
private fun Ticket(state: HomeUiState, moment: WeatherMoment, fmt: WeatherFormat) {
    val ink = Color(0xFF2E2118)
    val soft = Color(0xFF5A4533)
    val day = state.forecast?.dayAt(moment.epochSec)
    Box(Modifier.padding(top = 6.dp)) {
        Row(
            Modifier
                .graphicsLayer { rotationZ = -1.2f }
                .material(Stock.Kraft, TicketShape, level = 2)
                .drawBehind {
                    // Perforation between the stub and the ticket.
                    val x = size.width - 62.dp.toPx()
                    drawLine(ink.copy(alpha = 0.35f), Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                }
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.widthIn(max = 240.dp)) {
                AnimatedContent(fmt.condition(moment.condition), transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "cond") { c ->
                    BasicText(c, style = Paper.type.title.copy(color = ink).pressed(), maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                val line = buildString {
                    if (day != null) append("↑ ${fmt.temp(day.tempMax)}   ↓ ${fmt.temp(day.tempMin)}   ·   ")
                    append(stringResource(R.string.feels_like, fmt.temp(moment.feelsLike)))
                }
                BasicText(line, style = Paper.type.caption.copy(color = soft, fontWeight = FontWeight(700), fontFeatureSettings = "tnum").pressed(), maxLines = 1)
            }
            Spacer(Modifier.width(18.dp))
            GlyphIcon(Glyph.of(moment.condition, moment.isDay), size = 44.dp, rotation = 6f)
        }
        WashiTape(Modifier.align(Alignment.TopStart).offset(x = (-12).dp, y = (-8).dp), seed = 4, length = 46.dp, angle = -28f)
    }
}

@Composable
private fun UpdatedLabel(state: HomeUiState, fmt: WeatherFormat, lightInk: Boolean, onRefresh: () -> Unit) {
    val colors = Paper.colors
    val f = state.forecast ?: return
    val text = when {
        state.refreshing -> stringResource(R.string.refreshing)
        state.failed -> stringResource(R.string.offline_as_of, fmt.time(f.fetchedAt / 1000))
        else -> stringResource(R.string.updated_at, fmt.time(f.fetchedAt / 1000))
    }
    AnimatedContent(text, transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "updated") { t ->
        BasicText(t, Modifier.pressable(onRefresh, pressed = 0.95f), style = Paper.type.caption.copy(color = colors.onSky, fontWeight = FontWeight(700)).onSky(lightInk))
    }
}

/**
 * Notes for the day, handwritten in blue ink on a sheet torn from a notebook and taped down.
 * Tap it and it sways on the tape.
 */
@Composable
private fun NoteSheet(forecast: Forecast, fmt: WeatherFormat, instant: Long, now: WeatherMoment, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val notes = remember(forecast, instant, resources) {
        Narrator.notes(forecast, instant).map { NoteText.resolve(it, fmt, resources) }.distinct()
    }
    if (notes.isEmpty()) return
    val colors = Paper.colors
    val hand = Paper.type.hand.copy(color = colors.handInk, lineHeight = 30.sp)
    PaperSheet(
        modifier,
        stock = Stock.Notebook,
        seed = 8,
        tilt = -0.8f,
        perforated = true,
        tape = true,
        contentPadding = PaddingValues(start = 46.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        decoration = { notebookRuling(top = 50.dp.toPx(), step = 30.dp.toPx(), margin = 34.dp.toPx()) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(26.dp)) {
            Label(stringResource(R.string.note_title))
            Spacer(Modifier.weight(1f))
            GlyphIcon(Glyph.of(now.condition, now.isDay), size = 30.dp, rotation = -8f, animate = false)
        }
        Spacer(Modifier.height(8.dp))
        HandReveal(notes.first(), hand, maxLines = 4)
        notes.drop(1).take(3).forEach { n ->
            Row(verticalAlignment = Alignment.Top) {
                BasicText("—", style = hand.copy(color = Ink.RedPencil))
                Spacer(Modifier.width(6.dp))
                HandReveal(n, hand.copy(fontSize = 21.sp))
            }
        }
    }
}

/** A postcard with a stamp of the current sky: the way into the widget workshop. */
@Composable
private fun WidgetPromo(scene: SceneState, village: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Postcard(
        modifier,
        seed = 55,
        onClick = onOpen,
        postmark = stringResource(R.string.stamp_postmark),
        stamp = { SceneThumbnail(scene, Modifier.fillMaxSize(), village = village) },
    ) {
        BasicText(stringResource(R.string.promo_title), style = Paper.type.heading.copy(color = Paper.colors.paperInk).pressed())
        Spacer(Modifier.height(4.dp))
        BasicText(stringResource(R.string.promo_body), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft).pressed())
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.widgets_title), style = Paper.type.bodyStrong.copy(color = Paper.colors.accent).pressed())
            Spacer(Modifier.width(4.dp))
            PaperIconView(PaperIcon.Chevron, Paper.colors.accent, size = 14.dp, modifier = Modifier.graphicsLayer { rotationZ = -90f })
        }
    }
}

@Composable
private fun Welcome(refreshing: Boolean, hasPlace: Boolean, onPermission: (Boolean) -> Unit, onFindCity: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermission)
    PaperSheet(
        modifier,
        stock = Stock.Notebook,
        seed = 13,
        perforated = true,
        tape = true,
        contentPadding = PaddingValues(start = 46.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        decoration = { notebookRuling(top = 50.dp.toPx(), step = 30.dp.toPx(), margin = 34.dp.toPx()) },
    ) {
        HandReveal(stringResource(if (hasPlace) R.string.welcome_loading else R.string.welcome_title), Paper.type.hand.copy(color = Paper.colors.handInk, fontSize = 28.sp, lineHeight = 30.sp))
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(R.string.welcome_body), style = Paper.type.body.copy(color = Paper.colors.paperInk, lineHeight = 30.sp).pressed())
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(!refreshing, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut()) {
            Column {
                PaperButton(stringResource(R.string.welcome_locate), { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth(), glyph = Glyph.Pin)
                Spacer(Modifier.height(10.dp))
                PaperButton(stringResource(R.string.welcome_search), onFindCity, Modifier.fillMaxWidth(), primary = false)
            }
        }
    }
}

/** A rubber-stamp impression on the meadow: where the weather comes from. */
@Composable
private fun Credits() {
    val ink = Color(0xFFF6EFDF).copy(alpha = 0.72f)
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer { rotationZ = -3f }
                .drawBehind {
                    val r = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    drawRoundRect(ink, cornerRadius = r, style = Stroke(1.6.dp.toPx()))
                    drawRoundRect(ink.copy(alpha = ink.alpha * 0.6f), topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()), cornerRadius = r, style = Stroke(0.8.dp.toPx()))
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Label(stringResource(R.string.credits_data), color = ink, onDark = true)
        }
    }
}
