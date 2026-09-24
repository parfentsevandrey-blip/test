package app.rosa.weather.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.R
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.LiquidPageIndicator
import app.rosa.weather.core.designsystem.component.Odometer
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.component.WeatherGlyph
import app.rosa.weather.core.designsystem.format.WeatherFormat
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.LocalAmbientClock
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.sky.SkyStage
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.ForecastMoment
import app.rosa.weather.core.model.Headline
import app.rosa.weather.core.model.Headlines
import app.rosa.weather.core.model.WeatherCondition
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.ui.common.LocalSky
import app.rosa.weather.ui.common.SkyController
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val userRefreshing by viewModel.userRefreshing.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onVisible() }
    HomeScreen(
        state = state,
        userRefreshing = userRefreshing,
        onRefresh = viewModel::refresh,
        onSelect = viewModel::select,
        onLocationPermission = viewModel::onLocationPermission,
        onOpenPlaces = onOpenPlaces,
        onOpenSettings = onOpenSettings,
        onOpenWidgets = onOpenWidgets,
        onOpenSearch = onOpenSearch,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onLocationPermission: (Boolean) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSearch: () -> Unit,
    userRefreshing: Boolean = false,
    fixedNow: Long? = null,
) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val liveNow by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    val now = fixedNow ?: liveNow
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        onLocationPermission(granted)
    }

    if (state.loaded && state.pages.isEmpty()) {
        LaunchedEffect(Unit) { sky.show(SkyController.placeholder(now), immediate = false) }
        Onboarding(
            waitingForLocation = hasPermission && state.followDevice,
            denied = permissionDenied,
            onAllow = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            onChooseCity = onOpenSearch,
        )
        return
    }
    if (!state.loaded) return

    val pages = state.pages
    val pagerState = rememberPagerState(initialPage = state.selectedIndex) { pages.size }
    val currentPages by rememberUpdatedState(pages)
    val selectedId by rememberUpdatedState(state.selectedId)
    val scrub = remember { mutableStateMapOf<String, Float>() }
    var scrubbing by remember { mutableStateOf(false) }

    // The pager morphs the sky between cities as you swipe; the timeline scrubs it through time.
    LaunchedEffect(pages, now) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
            val a = floor(position).toInt().coerceIn(0, pages.lastIndex)
            val b = (a + 1).coerceAtMost(pages.lastIndex)
            val fraction = position - a
            fun momentOf(i: Int): ForecastMoment {
                val page = pages[i]
                val offset = ((scrub[page.place.id] ?: 0f) * 3600).toLong()
                return page.forecast?.momentAt(now + offset) ?: SkyController.placeholder(now)
            }
            if (fraction < 0.001f || a == b) {
                sky.show(momentOf(a), immediate = pagerState.isScrollInProgress || scrubbing)
            } else {
                sky.blend(momentOf(a), momentOf(b), fraction)
            }
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPages.getOrNull(page)?.let { if (it.place.id != selectedId) onSelect(it.place.id) }
        }
    }
    // Follow selection made elsewhere (Places list, a new city from Search, a widget tap).
    LaunchedEffect(state.selectedId, pages.size) {
        val target = state.selectedIndex
        if (target != pagerState.settledPage && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(target)
    }
    // Every city measures the free sky beside its numerals; the one on screen places the sun.
    val bodyStages = remember { mutableStateMapOf<String, SkyStage>() }
    LaunchedEffect(pagerState) {
        snapshotFlow { currentPages.getOrNull(pagerState.settledPage)?.let { bodyStages[it.place.id] } }
            .collect { stage -> if (stage != null) sky.placeBody(stage) }
    }

    val format = remember(state.units, context) { WeatherFormat(context, state.units) }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pagerState, Modifier.fillMaxSize(), key = { pages[it].place.id }) { index ->
            val page = pages[index]
            PlaceContent(
                page = page,
                now = now,
                format = format,
                refreshing = userRefreshing || page.place.id in state.refreshing,
                online = state.online,
                scrubHours = scrub[page.place.id] ?: 0f,
                onScrub = { hours, dragging ->
                    scrub[page.place.id] = hours
                    scrubbing = dragging
                    // Nudge the sky even when the pager isn't moving.
                    val forecast = page.forecast
                    if (forecast != null && index == pagerState.currentPage) {
                        sky.show(forecast.momentAt(now + (hours * 3600).toLong()), immediate = dragging || hours > 0.01f)
                    }
                },
                onRefresh = onRefresh,
                onBodyStage = { stage -> if (bodyStages[page.place.id] != stage) bodyStages[page.place.id] = stage },
            )
        }
        TopBar(
            title = pages.getOrNull(pagerState.currentPage)?.let { it.place.name.ifBlank { format.currentLocation() } }.orEmpty(),
            isCurrent = pages.getOrNull(pagerState.currentPage)?.place?.isCurrentLocation == true,
            pageCount = pages.size,
            pagePosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onOpenPlaces = onOpenPlaces,
            onOpenSettings = onOpenSettings,
            onOpenWidgets = onOpenWidgets,
        )
    }
}

@Composable
private fun PlaceContent(
    page: PlacePage,
    now: Long,
    format: WeatherFormat,
    refreshing: Boolean,
    online: Boolean,
    scrubHours: Float,
    onScrub: (Float, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBodyStage: (SkyStage) -> Unit,
) {
    val forecast = page.forecast
    val zoneFormat = remember(format, forecast?.timezone) {
        format.withZone(WeatherFormat.zoneOf(forecast?.timezone, forecast?.utcOffsetSeconds ?: 0))
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val isRefreshing by rememberUpdatedState(refreshing)
    val refresh = rememberLiquidRefresh(onRefresh) { isRefreshing }
    val density = LocalDensity.current
    val stageSink by rememberUpdatedState(onBodyStage)
    val probe = remember(density, listState) {
        HeroProbe(
            density = density,
            atRest = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 },
            onStage = { stageSink(it) },
        )
    }

    Box(Modifier.fillMaxSize().onPlaced { probe.page = it }.nestedScroll(refresh.connection)) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 76.dp, bottom = bottom + 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!online) {
                item(key = "offline") { StatusPill(stringResource(R.string.offline)) }
            }
            if (forecast == null) {
                item(key = "loading") {
                    Text(
                        stringResource(R.string.refreshing),
                        style = Rosa.type.headline,
                        color = Rosa.colors.inkSoft,
                        modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                return@LazyColumn
            }
            val moment = forecast.momentAt(now + (scrubHours * 3600).toLong())
            item(key = "hero") {
                Hero(forecast, moment, zoneFormat, scrubHours, probe, Modifier.graphicsLayer {
                    // Gentle parallax: the numerals drift up slower than the cards and fade out.
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1000f
                    translationY = offset * 0.35f
                    alpha = (1f - offset / 700f).coerceIn(0f, 1f)
                })
            }
            item(key = "timeline") {
                HourlyTimeline(forecast, now, forecast.momentAt(now).temperature, zoneFormat, onScrub)
            }
            if (forecast.nowcast.any { it.time > now && it.precipitation > 0.02 }) {
                item(key = "nowcast") { NowcastCard(forecast, now, zoneFormat) }
            }
            item(key = "daily") { DailyForecast(forecast, now, forecast.momentAt(now).temperature, zoneFormat) }
            item(key = "details-title") {
                Text(
                    stringResource(R.string.details_title),
                    style = Rosa.type.label,
                    color = Rosa.colors.inkSoft,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp).semantics { heading() },
                )
            }
            item(key = "details") { DetailsGrid(forecast, forecast.momentAt(now), zoneFormat) }
            item(key = "footer") { Footer(forecast, now, zoneFormat, onRefresh) }
        }
        RefreshDrop(refresh, Modifier.align(Alignment.TopCenter).padding(top = top + 68.dp))
    }
}

@Composable
private fun Hero(
    forecast: Forecast,
    moment: ForecastMoment,
    format: WeatherFormat,
    scrubHours: Float,
    probe: HeroProbe,
    modifier: Modifier,
) {
    val colors = Rosa.colors
    val headline = remember(forecast, moment.epochSeconds / 60) { Headlines.pick(forecast, moment) }
    val shadow = if (colors.isLightSky) null else Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 4f), 28f)
    val today = forecast.dayAt(moment.epochSeconds)
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        AnimatedVisibility(scrubHours > 0.5f, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            Text(
                stringResource(R.string.at_time, format.time(forecast.hourly.lastOrNull { it.time <= moment.epochSeconds }?.time ?: moment.epochSeconds)),
                style = Rosa.type.label,
                color = colors.accent,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.onGloballyPositioned {
                probe.row = it
                probe.report()
            },
        ) {
            Odometer(
                text = format.temperature(moment.temperature),
                style = Rosa.type.hero.copy(shadow = shadow),
                color = colors.ink,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .onGloballyPositioned {
                        probe.numeral = it
                        probe.report()
                    },
            )
            Spacer(Modifier.weight(1f))
            // Under a clear sky the real sun or moon takes this very spot (see HeroProbe), so the
            // icon steps aside for it.
            val bodyUp = (if (moment.sun.elevation > -5) moment.sun.elevation else moment.moon.elevation) > 3
            val clearish = moment.condition == WeatherCondition.Clear || moment.condition == WeatherCondition.MostlyClear
            if (!(clearish && bodyUp)) {
                WeatherGlyph(moment.condition, moment.isDay, Modifier.size(HERO_GLYPH), moonPhase = moment.moonPhase.phase, animated = true)
            }
        }
        Text(
            format.condition(moment.condition, moment.isDay),
            style = Rosa.type.title.copy(shadow = shadow),
            color = colors.ink,
            modifier = Modifier.padding(start = 6.dp),
        )
        if (today != null) {
            Text(
                format.highLow(today.temperatureMax, today.temperatureMin) + " · " +
                    format.headline(Headline.FeelsLike(moment.apparentTemperature), moment.epochSeconds),
                style = Rosa.type.body.copy(shadow = shadow),
                color = colors.inkSoft,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
            )
        }
        if (headline !is Headline.Steady) {
            Spacer(Modifier.height(12.dp))
            GlassSurface(style = GlassStyle.Clear, cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(format.headline(headline, moment.epochSeconds), style = Rosa.type.label, color = colors.ink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val clock = LocalAmbientClock.current
    Canvas(Modifier.size(10.dp)) {
        val pulse = 0.8f + 0.2f * sin(clock.seconds * PI.toFloat() / 1.1f)
        drawCircle(color.copy(alpha = 0.3f * pulse), size.minDimension / 2)
        drawCircle(color, size.minDimension / 4)
    }
}

@Composable
private fun TopBar(
    title: String,
    isCurrent: Boolean,
    pageCount: Int,
    pagePosition: () -> Float,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgets: () -> Unit,
) {
    val colors = Rosa.colors
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton(
                onClick = onOpenPlaces,
                contentDescription = stringResource(R.string.cd_places),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        RosaIconView(RosaIcon.Location, colors.ink, size = 15.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, style = Rosa.type.headline, color = colors.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
                }
            }
            Spacer(Modifier.weight(1f))
            // Related actions share one glass capsule (Apple groups bar items by function).
            GlassSurface(cornerRadius = 24.dp) {
                Row {
                    BarIcon(RosaIcon.Widgets, stringResource(R.string.cd_widgets), onOpenWidgets)
                    BarIcon(RosaIcon.Settings, stringResource(R.string.cd_settings), onOpenSettings)
                }
            }
        }
        if (pageCount > 1) {
            LiquidPageIndicator(pageCount, pagePosition, Modifier.padding(start = 12.dp, top = 8.dp))
        }
    }
}

@Composable
private fun BarIcon(icon: RosaIcon, description: String, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    Box(
        Modifier
            .size(48.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = description) {
                haptics?.press()
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        RosaIconView(icon, Rosa.colors.ink, size = 21.dp)
    }
}

@Composable
private fun StatusPill(text: String) {
    GlassSurface(style = GlassStyle.Clear, cornerRadius = 18.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, style = Rosa.type.caption, color = Rosa.colors.ink)
    }
}

@Composable
private fun NowcastCard(forecast: Forecast, now: Long, format: WeatherFormat) {
    val colors = Rosa.colors
    val slots = forecast.nowcast.filter { it.time > now - 900 }.take(8)
    if (slots.isEmpty()) return
    val moment = forecast.momentAt(now)
    val headline = Headlines.pick(forecast, moment)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, RosaMotion.gel()) }
    GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 30.dp) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.nowcast_title), style = Rosa.type.label, color = colors.inkSoft)
            Text(format.headline(headline, now), style = Rosa.type.headline, color = colors.ink, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            val maxMm = slots.maxOf { it.precipitation }.coerceAtLeast(0.4)
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val w = size.width / slots.size
                slots.forEachIndexed { i, slot ->
                    val x = i * w
                    drawRoundRect(colors.ink.copy(alpha = 0.08f), Offset(x + 3f, 0f), androidx.compose.ui.geometry.Size(w - 6f, size.height), CornerRadius(10f))
                    val v = (slot.precipitation / maxMm).toFloat().coerceIn(0f, 1f) * grow.value
                    if (v > 0.01f) {
                        val h = size.height * v
                        drawRoundRect(colors.rain.copy(alpha = 0.5f + v * 0.5f), Offset(x + 3f, size.height - h), androidx.compose.ui.geometry.Size(w - 6f, h), CornerRadius(10f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(format.now(), style = Rosa.type.caption, color = colors.inkSoft, modifier = Modifier.weight(1f))
                Text(format.time(slots[slots.size / 2].time), style = Rosa.type.caption, color = colors.inkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(format.time(slots.last().time), style = Rosa.type.caption, color = colors.inkSoft, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun Footer(forecast: Forecast, now: Long, format: WeatherFormat, onRefresh: () -> Unit) {
    val colors = Rosa.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassButton(onClick = onRefresh, style = GlassStyle.Clear, contentDescription = stringResource(R.string.cd_refresh)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RosaIconView(RosaIcon.Refresh, colors.ink, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(format.updated(forecast.fetchedAt, now), style = Rosa.type.caption, color = colors.ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.data_source), style = Rosa.type.caption.copy(fontSize = 11.sp), color = colors.inkFaint)
    }
}

private val HERO_GLYPH = 92.dp

/**
 * Finds the free sky beside the big numerals — the slot the weather glyph uses — so the real sun
 * and moon travel there instead of behind the temperature. Measured only at rest (list at the
 * top) and in page coordinates, so scrolling and swiping between cities don't disturb it.
 */
private class HeroProbe(
    private val density: Density,
    private val atRest: () -> Boolean,
    private val onStage: (SkyStage) -> Unit,
) {
    var page: LayoutCoordinates? = null
    var row: LayoutCoordinates? = null
    var numeral: LayoutCoordinates? = null

    fun report() {
        val page = page ?: return
        val row = row ?: return
        val numeral = numeral ?: return
        if (!atRest() || !page.isAttached || !row.isAttached || !numeral.isAttached) return
        val stage = heroBodyStage(
            row = page.localBoundingBoxOf(row, clipBounds = false),
            numeral = page.localBoundingBoxOf(numeral, clipBounds = false),
            page = page.size.toSize(),
            density = density,
        )
        if (stage != null) onStage(stage)
    }
}

/**
 * The box the centre of the sun or moon may travel in: around the glyph slot at the end of the
 * numeral row, kept clear of the numerals (however wide the temperature gets) and of the edge.
 */
internal fun heroBodyStage(row: Rect, numeral: Rect, page: Size, density: Density): SkyStage? {
    if (page.width <= 0f || page.height <= 0f || row.width <= 0f || row.height <= 0f) return null
    return with(density) {
        val radius = page.height * SkyStage.BODY_RADIUS
        val slot = row.right - HERO_GLYPH.toPx() / 2
        val right = min(slot + 40.dp.toPx(), page.width - radius - 12.dp.toPx())
        val left = max(slot - 40.dp.toPx(), numeral.right + radius + 36.dp.toPx()).coerceAtMost(right)
        val top = row.top + radius
        val bottom = max(top, row.bottom - radius)
        SkyStage(left / page.width, top / page.height, right / page.width, bottom / page.height)
    }
}

// region Liquid pull-to-refresh

private class LiquidRefresh(
    private val onRefresh: () -> Unit,
    private val thresholdPx: Float,
    private val haptics: app.rosa.weather.core.designsystem.haptics.RosaHaptics?,
    private val isRefreshing: () -> Boolean,
) {
    val pull = Animatable(0f)
    var armed = false
    var active by mutableStateOf(false)
    lateinit var scope: kotlinx.coroutines.CoroutineScope

    val progress: Float get() = (pull.value / thresholdPx).coerceIn(0f, 1.6f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0 && pull.value > 0f) {
                val consumed = maxOf(available.y, -pull.value)
                scope.launch { pull.snapTo(pull.value + consumed) }
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f) {
                val next = pull.value + available.y * 0.45f
                scope.launch { pull.snapTo(next) }
                if (!armed && next >= thresholdPx) {
                    armed = true
                    haptics?.thresholdReached()
                } else if (armed && next < thresholdPx) {
                    armed = false
                }
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (pull.value <= 0f) return Velocity.Zero
            if (armed) {
                armed = false
                active = true
                haptics?.splash()
                onRefresh()
                // Settle the drop once the refresh is over, whatever its outcome (offline too).
                scope.launch {
                    delay(700)
                    withTimeoutOrNull(20_000) { snapshotFlow { isRefreshing() }.first { !it } }
                    finish()
                }
                pull.animateTo(thresholdPx * 0.7f, RosaMotion.gel())
            } else {
                pull.animateTo(0f, RosaMotion.gel())
            }
            return available
        }
    }

    suspend fun finish() {
        if (active) {
            active = false
            pull.animateTo(0f, RosaMotion.gel())
        }
    }
}

@Composable
private fun rememberLiquidRefresh(onRefresh: () -> Unit, isRefreshing: () -> Boolean): LiquidRefresh {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val refresh by rememberUpdatedState(onRefresh)
    return remember(threshold) { LiquidRefresh({ refresh() }, threshold, haptics, isRefreshing) }.also { it.scope = scope }
}

/** A glass drop that swells as you pull, detaches at the threshold and breathes while refreshing. */
@Composable
private fun RefreshDrop(refresh: LiquidRefresh, modifier: Modifier) {
    val p = refresh.progress
    if (p <= 0.02f && !refresh.active) return
    val clock = LocalAmbientClock.current
    val size = (18 + 30 * min(p, 1f)).dp
    val stretch = if (p > 1f) 1f + (p - 1f) * 0.5f else 1f
    GlassSurface(
        modifier
            .size(size)
            .graphicsLayer {
                // Breathes while refreshing; the clock is only read (and redraws) meanwhile.
                val s = if (refresh.active) 1f + 0.08f * sin(clock.seconds * PI.toFloat() / 0.7f) else 1f
                scaleX = s / stretch
                scaleY = s * stretch
                translationY = refresh.pull.value * 0.35f
            },
        style = GlassStyle.Lens,
        cornerRadius = size / 2,
        contentAlignment = Alignment.Center,
    ) {
        if (p >= 1f || refresh.active) RosaIconView(RosaIcon.Refresh, Rosa.colors.ink, size = size * 0.45f)
    }
}

// endregion

@Composable
private fun Onboarding(waitingForLocation: Boolean, denied: Boolean, onAllow: () -> Unit, onChooseCity: () -> Unit) {
    val colors = Rosa.colors
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Sheet, cornerRadius = 38.dp, contentPadding = PaddingValues(26.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                WeatherGlyph(WeatherCondition.PartlyCloudy, true, Modifier.size(96.dp), animated = true)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (waitingForLocation) R.string.refreshing else R.string.permission_title),
                    style = Rosa.type.title,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                if (!waitingForLocation) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                        style = Rosa.type.body,
                        color = colors.inkSoft,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    GlassButton(onClick = onAllow, modifier = Modifier.fillMaxWidth(), style = GlassStyle.Regular) {
                        Text(stringResource(R.string.permission_allow), style = Rosa.type.headline, color = colors.ink)
                    }
                }
                Spacer(Modifier.height(10.dp))
                GlassButton(onClick = onChooseCity, modifier = Modifier.fillMaxWidth(), style = GlassStyle.Clear) {
                    Text(stringResource(R.string.permission_choose), style = Rosa.type.headline, color = colors.ink)
                }
            }
        }
    }
}
