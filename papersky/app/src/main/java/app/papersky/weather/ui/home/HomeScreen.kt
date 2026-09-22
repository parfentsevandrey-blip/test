package app.papersky.weather.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.papersky.weather.R
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.WeatherMoment
import app.papersky.weather.core.model.momentAt
import app.papersky.weather.core.text.Narrator
import app.papersky.weather.core.text.NoteText
import app.papersky.weather.core.text.WeatherFormat
import app.papersky.weather.design.DeckleShape
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.HandReveal
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalScenePalette
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PaperTheme
import app.papersky.weather.design.RollingText
import app.papersky.weather.design.paperSheet
import app.papersky.weather.design.pressable
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.ui.scene.LivingScene
import app.papersky.weather.ui.scene.SceneController
import app.papersky.weather.ui.scene.isHorizontal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

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
    val instant = preview ?: nowSec
    val moment = forecast?.momentAt(instant, preferLive = preview == null && forecast.ageMinutes(nowMillis) < 75)
    val scene = moment?.let { SceneState.from(it, SceneState.seedFor(forecast.placeId), forecast) } ?: IdleScene
    val palette = remember(scene) { Palettes.forState(scene) }
    val units = state.settings.resolvedUnits(LocalConfiguration.current.locales[0])
    val fmt = remember(forecast?.timezone, units, context) {
        WeatherFormat(context, units, forecast?.zone ?: java.time.ZoneId.systemDefault())
    }

    PaperTheme(palette) {
        CompositionLocalProvider(LocalScenePalette provides palette) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Paper.colors.sky)) {
                val heroHeight = min(maxHeight.value * 0.6f, 560f).dp
                val listState = rememberLazyListState()
                val density = LocalDensity.current
                val heroPx = with(density) { heroHeight.toPx() }
                val scrolled by remember {
                    androidx.compose.runtime.derivedStateOf {
                        if (listState.firstVisibleItemIndex > 0) heroPx else listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }
                val controller = remember { SceneController() }

                LivingScene(
                    target = scene,
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationY = -scrolled * 0.35f },
                    horizon = 0.5f,
                    motion = state.settings.motion,
                    tilt = state.settings.tiltParallax,
                    controller = controller,
                    transitionMillis = if (preview != null) 420 else 1100,
                    dim = { (scrolled / heroPx).coerceIn(0f, 1f) * 0.35f },
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item("hero") {
                        Hero(
                            state = state, moment = moment, fmt = fmt, nowSec = nowSec, preview = preview,
                            controller = controller, height = heroHeight,
                            onBackToNow = { preview = null }, onRefresh = { vm.refresh(true) },
                        )
                    }
                    if (forecast != null && moment != null) {
                        item("note") { NoteCard(forecast, fmt, instant, Modifier.padding(horizontal = 18.dp)) }
                        item("hourly") { HourlyCard(forecast, fmt, nowSec, preview, { preview = it }, Modifier.padding(horizontal = 14.dp)) }
                        item("daily") { DailyCard(forecast, fmt, nowSec, moment.temperature, { preview = it }, Modifier.padding(horizontal = 14.dp)) }
                        item("details") { DetailsGrid(forecast, moment, fmt, nowSec, Modifier.padding(horizontal = 16.dp)) }
                        item("widgets") { WidgetPromo(scene, onOpenWidgets, Modifier.padding(horizontal = 18.dp)) }
                        item("credits") { Credits() }
                    } else if (state.loaded) {
                        item("welcome") {
                            Welcome(
                                refreshing = state.refreshing,
                                hasPlace = state.place != null,
                                onPermission = vm::onLocationPermissionResult,
                                onFindCity = onOpenPlaces,
                                modifier = Modifier.padding(horizontal = 18.dp),
                            )
                        }
                    }
                }

                TopBar(
                    place = state.place,
                    solid = (scrolled / (heroPx * 0.8f)).coerceIn(0f, 1f),
                    onOpenPlaces = onOpenPlaces,
                    onOpenSettings = onOpenSettings,
                    onOpenWidgets = onOpenWidgets,
                )

                PullCord(
                    refreshing = state.refreshing,
                    onPull = { vm.refresh(true) },
                    label = stringResource(R.string.refresh),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { androidx.compose.ui.unit.IntOffset((-54).dp.roundToPx(), -scrolled.toInt().coerceAtMost(260.dp.roundToPx())) }
                        .graphicsLayer { alpha = 1f - (scrolled / (heroPx * 0.5f)).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

/** Neutral daytime scene shown before any forecast exists. */
private val IdleScene = SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.3f, windX = 1.5f, temperature = 18f, seed = 3)

@Composable
private fun TopBar(place: Place?, solid: Float, onOpenPlaces: () -> Unit, onOpenSettings: () -> Unit, onOpenWidgets: () -> Unit) {
    val colors = Paper.colors
    val ink = if (solid > 0.5f) colors.paperInk else colors.onSky
    Box(Modifier.fillMaxWidth()) {
        if (solid > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = solid }
                    .paperSheet(colors.paper, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp), colors.shadow, lift = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .semantics { contentDescription = place?.name ?: "" }
                    .pressable(onOpenPlaces, pressed = 0.94f)
                    .paperSheet(colors.paper.copy(alpha = 0.92f), DeckleShape(seed = 5, corner = 16.dp), colors.shadow, lift = 6.dp)
                    .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Pin, size = 20.dp, animate = false)
                Spacer(Modifier.width(6.dp))
                AnimatedContent(place?.name?.ifBlank { null } ?: stringResource(R.string.here), label = "place") { name ->
                    BasicText(name, style = Paper.type.bodyStrong.copy(color = colors.paperInk), maxLines = 1)
                }
                Spacer(Modifier.width(4.dp))
                PaperIconView(PaperIcon.Chevron, colors.paperInkSoft, size = 16.dp)
            }
            Spacer(Modifier.weight(1f))
            RoundIcon(PaperIcon.Widgets, stringResource(R.string.widgets_title), ink, onOpenWidgets)
            Spacer(Modifier.width(52.dp)) // room for the pull cord
            RoundIcon(PaperIcon.Settings, stringResource(R.string.settings_title), ink, onOpenSettings)
        }
    }
}

@Composable
private fun RoundIcon(icon: PaperIcon, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = description }
            .pressable(onClick, pressed = 0.88f),
        contentAlignment = Alignment.Center,
    ) {
        PaperIconView(icon, tint, size = 24.dp)
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
    height: androidx.compose.ui.unit.Dp,
    onBackToNow: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = Paper.colors
    val h = rememberHaptics()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
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
                var total = Offset.Zero
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); total = Offset.Zero },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        if (isHorizontal(v.x, v.y) && abs(v.x) > 300f) {
                            h.softTick()
                            controller.fling(v.x)
                        }
                    },
                ) { change, dx ->
                    total += Offset(dx, 0f)
                    tracker.addPosition(change.uptimeMillis, change.position)
                }
            },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        ) {
            if (moment != null) {
                AnimatedVisibility(preview != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    Row(
                        Modifier
                            .pressable({ h.confirm(); onBackToNow() }, pressed = 0.94f)
                            .paperSheet(colors.paper, RoundedCornerShape(50), colors.shadow, lift = 5.dp)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            preview?.let { stringResource(R.string.preview_at, fmt.dayName(it, nowSec), fmt.time(it)) } ?: "",
                            style = Paper.type.caption.copy(color = colors.paperInk),
                        )
                        Spacer(Modifier.width(8.dp))
                        PaperIconView(PaperIcon.Close, colors.paperInkSoft, size = 14.dp)
                    }
                }
                val tempNumber = fmt.tempNumber(moment.temperature)
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                    RollingText(tempNumber, Paper.type.hero.copy(color = colors.onSky), numericValue = moment.temperature)
                    BasicText("°", style = Paper.type.hero.copy(color = colors.onSky, fontSize = 80.sp))
                }
                HandReveal(fmt.condition(moment.condition), Paper.type.handLarge.copy(color = colors.onSky))
                Spacer(Modifier.height(4.dp))
                val day = state.forecast?.dayAt(moment.epochSec)
                val line = buildString {
                    if (day != null) append("↑${fmt.temp(day.tempMax)}  ↓${fmt.temp(day.tempMin)}   ·   ")
                    append(stringResource(R.string.feels_like, fmt.temp(moment.feelsLike)))
                }
                BasicText(line, style = Paper.type.bodyStrong.copy(color = colors.onSkySoft))
                Spacer(Modifier.height(10.dp))
                UpdatedLabel(state, fmt, onRefresh)
            }
        }
    }
}

@Composable
private fun UpdatedLabel(state: HomeUiState, fmt: WeatherFormat, onRefresh: () -> Unit) {
    val colors = Paper.colors
    val f = state.forecast ?: return
    val text = when {
        state.refreshing -> stringResource(R.string.refreshing)
        state.failed -> stringResource(R.string.offline_as_of, fmt.time(f.fetchedAt / 1000))
        else -> stringResource(R.string.updated_at, fmt.time(f.fetchedAt / 1000))
    }
    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "updated") { t ->
        BasicText(
            t,
            Modifier.pressable(onRefresh, pressed = 0.95f),
            style = Paper.type.caption.copy(color = colors.onSkySoft),
        )
    }
}

@Composable
private fun NoteCard(forecast: Forecast, fmt: WeatherFormat, instant: Long, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val notes = remember(forecast, instant / 900, resources) {
        Narrator.notes(forecast, instant).map { NoteText.resolve(it, fmt, resources) }.distinct()
    }
    if (notes.isEmpty()) return
    val h = rememberHaptics()
    var wiggle by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (wiggle) 2.2f else -1.2f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 200f), label = "wiggle")
    PaperCard(
        modifier.graphicsLayer { rotationZ = angle + 1.2f },
        seed = 8,
        tape = true,
        onClick = { h.softTick(); wiggle = !wiggle },
    ) {
        HandReveal(notes.first(), Paper.type.handLarge.copy(color = Paper.colors.paperInk))
        notes.drop(1).take(3).forEach { n ->
            Spacer(Modifier.height(4.dp))
            val dot = Paper.colors.accent
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(6.dp)) { drawCircle(dot) }
                Spacer(Modifier.width(8.dp))
                BasicText(n, style = Paper.type.hand.copy(color = Paper.colors.paperInkSoft, fontSize = 20.sp))
            }
        }
    }
}

@Composable
private fun WidgetPromo(scene: SceneState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val renderer = remember(density) { PaperSceneRenderer(density) }
    val palette = LocalScenePalette.current ?: Palettes.ClearDay
    PaperCard(modifier, seed = 55, tilt = -0.6f, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .size(96.dp, 72.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, scene, palette, PaperSceneRenderer.Options(time = 3f, detail = 0.6f, vignette = 0.4f)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.promo_title), style = Paper.type.heading.copy(color = Paper.colors.paperInk))
                Spacer(Modifier.height(2.dp))
                BasicText(stringResource(R.string.promo_body), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
            }
        }
    }
}

@Composable
private fun Welcome(refreshing: Boolean, hasPlace: Boolean, onPermission: (Boolean) -> Unit, onFindCity: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermission)
    PaperCard(modifier, seed = 13, tape = true) {
        BasicText(stringResource(if (hasPlace) R.string.welcome_loading else R.string.welcome_title), style = Paper.type.handLarge.copy(color = Paper.colors.paperInk))
        Spacer(Modifier.height(6.dp))
        BasicText(stringResource(R.string.welcome_body), style = Paper.type.body.copy(color = Paper.colors.paperInkSoft))
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(!refreshing, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            Column {
                PaperButton(stringResource(R.string.welcome_locate), { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth(), glyph = Glyph.Pin)
                Spacer(Modifier.height(10.dp))
                PaperButton(stringResource(R.string.welcome_search), onFindCity, Modifier.fillMaxWidth(), primary = false)
            }
        }
    }
}

@Composable
private fun Credits() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(stringResource(R.string.credits_data), color = Paper.colors.onSkySoft)
    }
}
