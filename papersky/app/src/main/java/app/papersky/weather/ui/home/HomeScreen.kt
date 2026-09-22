package app.papersky.weather.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.unit.Dp
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
import app.papersky.weather.design.GlyphIcon
import app.papersky.weather.design.InkReveal
import app.papersky.weather.design.Label
import app.papersky.weather.design.LocalChoreography
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PillShape
import app.papersky.weather.design.RollingText
import app.papersky.weather.design.paperSheet
import app.papersky.weather.design.pressable
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.design.reveal
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
    // The sky rehearses the previewed hour; the cards below keep describing now.
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
        val heroH = (screenH * 0.6f).coerceIn(430.dp, 620.dp)
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
            scroll = { scroll.value },
            dim = { (scroll.value / heroPx).coerceIn(0f, 1f) * 0.28f },
        )

        CompositionLocalProvider(LocalChoreography provides remember(forecast?.placeId) { Choreography() }) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    item("note", contentType = "note") { NoteCard(forecast, fmt, nowSec - nowSec % 900, nowMoment, side.reveal(0)) }
                    item("hourly", contentType = "hourly") { HourlyCard(forecast, fmt, nowSec, preview, { preview = it }, side.reveal(1)) }
                    item("daily", contentType = "daily") { DailyCard(forecast, fmt, nowSec, nowMoment.temperature, { preview = it }, side.reveal(2)) }
                    item("details", contentType = "details") { DetailsGrid(forecast, nowMoment, fmt, nowSec, side.reveal(3)) }
                    item("widgets", contentType = "promo") { WidgetPromo(scene, onOpenWidgets, side.reveal(4)) }
                    item("credits", contentType = "credits") { Credits() }
                } else if (state.loaded) {
                    item("welcome") {
                        Welcome(
                            refreshing = state.refreshing,
                            hasPlace = state.place != null,
                            onPermission = vm::onLocationPermissionResult,
                            onFindCity = onOpenPlaces,
                            modifier = Modifier.padding(horizontal = 16.dp).reveal(0),
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
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { androidx.compose.ui.unit.IntOffset((-54).dp.roundToPx(), -scroll.value.toInt().coerceAtMost(260.dp.roundToPx())) }
                .graphicsLayer { alpha = 1f - (scroll.value / (heroPx * 0.45f)).coerceIn(0f, 1f) },
        )
    }
}

/** Neutral daytime scene shown before any forecast exists. */
private val IdleScene = SceneState(daylight = 1f, sunProgress = 0.35f, cloudCover = 0.3f, windX = 1.5f, temperature = 18f, seed = 3)

@Composable
private fun TopScrim() {
    val colors = Paper.colors
    val tint = if (colors.onSky.luminance() > 0.5f) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.18f)
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
    val solid by remember { derivedStateOf { progress() > 0.55f } }
    val ink = if (solid) colors.paperInk else colors.onSky
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val p = progress()
                    alpha = p
                    translationY = -(1f - p) * 12.dp.toPx()
                }
                .paperSheet(colors.paper, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), colors.shadow, lift = 8.dp, night = colors.isNight),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .semantics { contentDescription = place?.name ?: "" }
                    .pressable(onOpenPlaces, pressed = 0.94f)
                    .paperSheet(colors.paper.copy(alpha = 0.94f), PillShape, colors.shadow, lift = 6.dp, night = colors.isNight)
                    .padding(start = 11.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Pin, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                AnimatedContent(
                    place?.name?.ifBlank { null } ?: stringResource(R.string.here),
                    transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
                    label = "place",
                ) { name ->
                    BasicText(name, style = Paper.type.bodyStrong.copy(color = colors.paperInk), maxLines = 1)
                }
                Spacer(Modifier.width(4.dp))
                PaperIconView(PaperIcon.Chevron, colors.paperInkSoft, size = 14.dp)
            }
            if (compact != null) {
                Spacer(Modifier.width(12.dp))
                BasicText(
                    compact,
                    Modifier.graphicsLayer {
                        val p = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        alpha = p
                        translationY = (1f - p) * 10.dp.toPx()
                    },
                    style = Paper.type.title.copy(color = colors.paperInk, fontWeight = androidx.compose.ui.text.font.FontWeight(300), fontSize = 24.sp),
                )
            }
            Spacer(Modifier.weight(1f))
            RoundIcon(PaperIcon.Widgets, stringResource(R.string.widgets_title), ink, onOpenWidgets)
            Spacer(Modifier.width(52.dp)) // room for the pull cord
            RoundIcon(PaperIcon.Settings, stringResource(R.string.settings_title), ink, onOpenSettings)
        }
    }
}

@Composable
private fun RoundIcon(icon: PaperIcon, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(PillShape)
            .semantics { contentDescription = description }
            .pressable(onClick, pressed = 0.86f),
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
    val heroShadow = if (lightInk) Shadow(Color(0x40000000), Offset(0f, 3f), 22f) else Shadow(Color(0x33FFFFFF), Offset(0f, 2f), 18f)
    val scrim = if (lightInk) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.26f)
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
                .padding(start = 26.dp, end = 26.dp, top = 70.dp)
                .graphicsLayer {
                    // Drifts up slower than the page and sinks back into the sky.
                    val s = scroll.value
                    val p = (s / heroPx).coerceIn(0f, 1f)
                    translationY = s * 0.42f
                    alpha = (1f - p * 1.7f).coerceIn(0f, 1f)
                    scaleX = 1f - p * 0.1f
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .drawWithCache {
                    val r = size.width * 0.75f
                    val brush = Brush.radialGradient(listOf(scrim, scrim.copy(alpha = scrim.alpha * 0.4f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.45f), radius = r)
                    onDrawBehind { drawRect(brush, topLeft = Offset(-r * 0.4f, -r * 0.3f), size = androidx.compose.ui.geometry.Size(size.width + r * 0.8f, size.height + r * 0.6f)) }
                },
        ) {
            AnimatedVisibility(preview != null, enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(0f, 0.5f)), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                Row(
                    Modifier
                        .padding(bottom = 6.dp)
                        .pressable({ h.confirm(); onBackToNow() }, pressed = 0.94f)
                        .paperSheet(colors.paper, PillShape, colors.shadow, lift = 5.dp, night = colors.isNight)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        preview?.let { stringResource(R.string.preview_at, fmt.dayName(it, nowSec), fmt.time(it)) } ?: "",
                        style = Paper.type.caption.copy(color = colors.paperInk, fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
                    )
                    Spacer(Modifier.width(8.dp))
                    PaperIconView(PaperIcon.Close, colors.paperInkSoft, size = 13.dp)
                }
            }
            val tempNumber = fmt.tempNumber(moment.temperature)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                RollingText(tempNumber, Paper.type.hero.copy(color = colors.onSky, shadow = heroShadow), numericValue = moment.temperature)
                BasicText("°", style = Paper.type.hero.copy(color = colors.onSky, shadow = heroShadow, fontSize = 92.sp))
            }
            Spacer(Modifier.height(2.dp))
            InkReveal(fmt.condition(moment.condition), Paper.type.lead.copy(color = colors.onSky, shadow = heroShadow, fontSize = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(500)), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            val day = state.forecast?.dayAt(moment.epochSec)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (day != null) {
                    BasicText("↑ ${fmt.temp(day.tempMax)}   ↓ ${fmt.temp(day.tempMin)}", style = Paper.type.bodyStrong.copy(color = colors.onSky, shadow = heroShadow))
                    Dot(colors.onSkySoft)
                }
                BasicText(stringResource(R.string.feels_like, fmt.temp(moment.feelsLike)), style = Paper.type.body.copy(color = colors.onSkySoft, shadow = heroShadow))
            }
            Spacer(Modifier.height(10.dp))
            UpdatedLabel(state, fmt, onRefresh)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Canvas(Modifier.padding(horizontal = 10.dp).size(4.dp)) { drawCircle(color) }
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
    AnimatedContent(text, transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "updated") { t ->
        BasicText(t, Modifier.pressable(onRefresh, pressed = 0.95f), style = Paper.type.caption.copy(color = colors.onSkySoft))
    }
}

@Composable
private fun NoteCard(forecast: Forecast, fmt: WeatherFormat, instant: Long, now: WeatherMoment, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val notes = remember(forecast, instant, resources) {
        Narrator.notes(forecast, instant).map { NoteText.resolve(it, fmt, resources) }.distinct()
    }
    if (notes.isEmpty()) return
    val colors = Paper.colors
    PaperCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.of(now.condition, now.isDay), size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Label(fmt.dayName(instant, instant))
        }
        Spacer(Modifier.height(10.dp))
        InkReveal(notes.first(), Paper.type.lead.copy(color = colors.paperInk), durationMillis = 1100)
        notes.drop(1).take(3).forEach { n ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Canvas(Modifier.padding(top = 9.dp).size(5.dp)) { drawCircle(colors.accent) }
                Spacer(Modifier.width(10.dp))
                BasicText(n, style = Paper.type.quote.copy(color = colors.paperInkSoft))
            }
        }
    }
}

@Composable
private fun WidgetPromo(scene: SceneState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    PaperCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SceneThumbnail(scene, Modifier.size(104.dp, 76.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(R.string.promo_title), style = Paper.type.heading.copy(color = Paper.colors.paperInk))
                Spacer(Modifier.height(3.dp))
                BasicText(stringResource(R.string.promo_body), style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
            }
            Spacer(Modifier.width(8.dp))
            PaperIconView(PaperIcon.Chevron, Paper.colors.paperInkSoft, size = 16.dp, modifier = Modifier.graphicsLayer { rotationZ = -90f })
        }
    }
}

@Composable
private fun Welcome(refreshing: Boolean, hasPlace: Boolean, onPermission: (Boolean) -> Unit, onFindCity: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onPermission)
    PaperCard(modifier) {
        InkReveal(stringResource(if (hasPlace) R.string.welcome_loading else R.string.welcome_title), Paper.type.title.copy(color = Paper.colors.paperInk))
        Spacer(Modifier.height(6.dp))
        BasicText(stringResource(R.string.welcome_body), style = Paper.type.body.copy(color = Paper.colors.paperInkSoft))
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

@Composable
private fun Credits() {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(stringResource(R.string.credits_data), color = Paper.colors.onSkySoft)
    }
}
