package app.rosa.calendar.widget.studio

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.calendar.R
import app.rosa.calendar.data.PaintingLight
import app.rosa.calendar.ui.PaintingBackdrop
import app.rosa.calendar.ui.PaintingPalette
import app.rosa.calendar.ui.settings.Label
import app.rosa.calendar.ui.settings.Section
import app.rosa.calendar.ui.settings.ToggleLine
import app.rosa.calendar.widget.CalendarEvents
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSlider
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.CalendarOptions
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.WeekStart
import app.rosa.weather.core.model.WidgetAccent
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.widget.render.calendar.SeasonClock
import app.rosa.weather.widget.render.calendar.WeekArt
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import kotlin.math.roundToInt
import app.rosa.weather.widget.R as WidgetR

/**
 * The calendar's studio: opened by the launcher when a calendar is added or changed, or from the
 * app. Every change redraws a live preview with the widget's own renderer, and the preview can be
 * stretched to any grid size right here — so you see exactly how each size will look.
 */
@AndroidEntryPoint
class CalendarStudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Backing out must not leave a half-configured widget behind.
        setResult(Activity.RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val viewModel: CalendarStudioViewModel = hiltViewModel()
            StudioRoot(viewModel, onClose = ::finish, onSaved = {
                setResult(Activity.RESULT_OK, result)
                finish()
            })
        }
    }
}

@Composable
private fun StudioRoot(viewModel: CalendarStudioViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val today = LocalDate.now()
    val art = WeekArt.of(SeasonClock.weekOf(today))
    val night = when (s.settings.light) {
        PaintingLight.Auto -> isSystemInDarkTheme()
        PaintingLight.Day -> false
        PaintingLight.Night -> true
    }
    val palette = remember(art, night) { PaintingPalette.of(art, night) }
    RosaEnvironment(s.settings.asAppSettings(), palette) {
        PaintingBackdrop(art, night, live = s.settings.livePainting) {
            StudioScreen(s, viewModel, onClose, onSaved)
        }
    }
}

@Composable
private fun StudioScreen(state: CalendarStudioState, viewModel: CalendarStudioViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val config = state.config
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(RosaIcon.Close, stringResource(android.R.string.cancel), onClose)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.studio_calendar_title), style = Rosa.type.title, color = Rosa.colors.ink, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1)
            GlassButton(onClick = { haptics?.confirm(); viewModel.save(onSaved) }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RosaIconView(RosaIcon.Check, Rosa.colors.ink, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(WidgetR.string.studio_save), style = Rosa.type.headline, color = Rosa.colors.ink)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResizeStage(state, viewModel.initialSize?.let { it.width to it.height })
            Section(stringResource(WidgetR.string.studio_style)) {
                StylePicker(state) { style ->
                    viewModel.update { it.copy(style = style, opacity = if (style == WidgetStyle.Sky) 1f else it.opacity.coerceAtMost(0.9f)) }
                }
            }
            Section(stringResource(WidgetR.string.studio_theme)) {
                val themeLabels = mapOf(
                    WidgetTheme.Auto to stringResource(WidgetR.string.theme_auto),
                    WidgetTheme.Light to stringResource(WidgetR.string.theme_light),
                    WidgetTheme.Dark to stringResource(WidgetR.string.theme_dark),
                )
                GlassSegmented(WidgetTheme.entries, config.theme, { t -> viewModel.update { it.copy(theme = t) } }, { themeLabels.getValue(it) })
                Label(stringResource(WidgetR.string.studio_accent))
                val accentLabels = mapOf(
                    WidgetAccent.Sky to stringResource(R.string.style_season),
                    WidgetAccent.Dynamic to stringResource(WidgetR.string.accent_dynamic),
                    WidgetAccent.Mono to stringResource(WidgetR.string.accent_mono),
                )
                // A calendar's colours come from its season, the wallpaper, or none: temperature has no say.
                val accents = WidgetAccent.entries - WidgetAccent.Temperature
                GlassSegmented(accents, config.accent.takeIf { it in accents } ?: WidgetAccent.Sky, { a -> viewModel.update { it.copy(accent = a) } }, { accentLabels.getValue(it) })
            }
            Section(stringResource(WidgetR.string.studio_glass)) {
                GlassSlider(config.opacity, { v -> viewModel.update { it.copy(opacity = v) } }, range = 0.15f..1f, stateDescription = "${(config.opacity * 100).roundToInt()}%")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(WidgetR.string.studio_corners), style = Rosa.type.body, color = Rosa.colors.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(WidgetR.string.studio_corners_system), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
                    Spacer(Modifier.width(10.dp))
                    GlassToggle(config.cornerRadiusDp < 0f, { system -> viewModel.update { it.copy(cornerRadiusDp = if (system) -1f else 24f) } })
                }
                if (config.cornerRadiusDp >= 0f) {
                    GlassSlider(config.cornerRadiusDp, { v -> viewModel.update { it.copy(cornerRadiusDp = v) } }, range = 0f..44f, steps = 10, stateDescription = "${config.cornerRadiusDp.roundToInt()} dp")
                }
            }
            Section(stringResource(WidgetR.string.studio_text)) {
                GlassSlider(config.textScale, { v -> viewModel.update { it.copy(textScale = v) } }, range = 0.85f..1.3f, steps = 8, stateDescription = "${(config.textScale * 100).roundToInt()}%")
            }
            Section(stringResource(R.string.studio_calendar)) { CalendarOptionsEditor(config, viewModel) }
            if (state.places.isNotEmpty() && config.calendar.forecastInDays) {
                Section(stringResource(WidgetR.string.studio_place)) { PlacePicker(state, viewModel) }
            }
            if (config.style != WidgetStyle.Paper) {
                Section(stringResource(WidgetR.string.studio_extras)) {
                    if (config.style == WidgetStyle.Sky || config.style == WidgetStyle.Glass) {
                        ToggleLine(stringResource(R.string.toggle_live_season), config.liveWeather) { v -> viewModel.update { it.copy(liveWeather = v) } }
                    }
                    ToggleLine(stringResource(WidgetR.string.toggle_glass_rim), config.glassRim) { v -> viewModel.update { it.copy(glassRim = v) } }
                }
            }
        }
    }
}

/**
 * A slice of home screen with a launcher-like grid. The preview follows your finger at *any*
 * size while dragging (the layout re-flows live), then settles onto the nearest cells.
 */
@Composable
private fun ResizeStage(state: CalendarStudioState, initialDp: Pair<Float, Float>?) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = 5
        val maxRows = 5
        val cellW = maxWidth / columns
        val cellH = cellW * 1.12f
        var cols by remember { mutableIntStateOf(initialDp?.let { ((it.first + 8) / cellW.value).roundToInt().coerceIn(1, columns) } ?: 4) }
        var rows by remember { mutableIntStateOf(initialDp?.let { ((it.second + 8) / cellH.value).roundToInt().coerceIn(1, maxRows) } ?: 4) }
        var dragging by remember { mutableStateOf(false) }
        var dragW by remember { mutableFloatStateOf(0f) }
        var dragH by remember { mutableFloatStateOf(0f) }
        val gap = 8.dp
        val snappedW = cellW * cols - gap
        val snappedH = cellH * rows - gap
        val animW by animateDpAsState(snappedW, RosaMotion.gel(), label = "w")
        val animH by animateDpAsState(snappedH, RosaMotion.gel(), label = "h")
        val w: Dp = if (dragging) with(density) { dragW.toDp() } else animW
        val h: Dp = if (dragging) with(density) { dragH.toDp() } else animH
        val ink = Rosa.colors.inkFaint

        Column {
            Box(Modifier.fillMaxWidth().height(cellH * maxRows)) {
                Canvas(Modifier.fillMaxSize()) {
                    for (c in 0..columns) for (r in 0..maxRows) {
                        drawCircle(ink, 1.6.dp.toPx(), Offset(c * cellW.toPx(), r * cellH.toPx()))
                    }
                }
                CalendarPreview(
                    state.config,
                    state.content,
                    Modifier.offset(gap / 2, gap / 2).size(w, h),
                    cornerRadiusDp = if (state.config.cornerRadiusDp >= 0) state.config.cornerRadiusDp else 24f,
                    resizing = dragging || animW != snappedW || animH != snappedH,
                    live = true,
                )
                // The resize handle: a small glass lens at the widget's corner.
                GlassSurface(
                    Modifier
                        .offset(gap / 2 + w - 22.dp, gap / 2 + h - 22.dp)
                        .size(44.dp)
                        .pointerInput(cellW, cellH) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragW = snappedW.toPx()
                                    dragH = snappedH.toPx()
                                    haptics?.press()
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, amount ->
                                change.consume()
                                dragW = (dragW + amount.x).coerceIn(28.dp.toPx(), (cellW * columns - gap).toPx())
                                dragH = (dragH + amount.y).coerceIn(28.dp.toPx(), (cellH * maxRows - gap).toPx())
                                val c = ((dragW + gap.toPx()) / cellW.toPx()).roundToInt().coerceIn(1, columns)
                                val r = ((dragH + gap.toPx()) / cellH.toPx()).roundToInt().coerceIn(1, maxRows)
                                if (c != cols || r != rows) {
                                    cols = c
                                    rows = r
                                    haptics?.snap()
                                }
                            }
                        },
                    style = GlassStyle.Lens,
                    cornerRadius = 22.dp,
                    contentAlignment = Alignment.Center,
                ) {
                    RosaIconView(RosaIcon.Drag, Rosa.colors.ink, size = 16.dp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(WidgetR.string.studio_cells, cols, rows), style = Rosa.type.headline, color = Rosa.colors.ink)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(WidgetR.string.studio_resize_hint), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
            }
        }
    }
}

/**
 * What a calendar shows besides its month: where weeks begin, their numbers, the weather in the
 * days, and the phone's events (asking for calendar access the first time they are turned on).
 */
@Composable
private fun CalendarOptionsEditor(config: WidgetConfig, viewModel: CalendarStudioViewModel) {
    val context = LocalContext.current
    val options = config.calendar
    fun set(transform: (CalendarOptions) -> CalendarOptions) = viewModel.update { it.copy(calendar = transform(it.calendar)) }
    Label(stringResource(R.string.studio_week_start))
    val weekLabels = mapOf(
        WeekStart.Auto to stringResource(R.string.week_start_auto),
        WeekStart.Monday to stringResource(R.string.week_start_monday),
        WeekStart.Sunday to stringResource(R.string.week_start_sunday),
        WeekStart.Saturday to stringResource(R.string.week_start_saturday),
    )
    GlassSegmented(WeekStart.entries, options.weekStart, { v -> set { it.copy(weekStart = v) } }, { weekLabels.getValue(it) })
    ToggleLine(stringResource(R.string.toggle_week_numbers), options.weekNumbers) { v -> set { it.copy(weekNumbers = v) } }
    ToggleLine(stringResource(R.string.toggle_forecast_days), options.forecastInDays) { v -> set { it.copy(forecastInDays = v) } }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) set { it.copy(events = true) }
    }
    ToggleLine(stringResource(R.string.toggle_events), options.events) { on ->
        when {
            !on -> set { it.copy(events = false) }
            CalendarEvents.granted(context) -> set { it.copy(events = true) }
            else -> permission.launch(Manifest.permission.READ_CALENDAR)
        }
    }
    if (denied) Text(stringResource(R.string.events_permission_hint), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
}

@Composable
private fun StylePicker(state: CalendarStudioState, onPick: (WidgetStyle) -> Unit) {
    val haptics = LocalHaptics.current
    val labels = mapOf(
        WidgetStyle.Glass to stringResource(WidgetR.string.style_glass),
        WidgetStyle.Sky to stringResource(R.string.style_season),
        WidgetStyle.Clear to stringResource(WidgetR.string.style_clear),
        WidgetStyle.Tonal to stringResource(WidgetR.string.style_tonal),
        WidgetStyle.Paper to stringResource(WidgetR.string.style_paper),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WidgetStyle.entries.forEach { style ->
            val selected = style == state.config.style
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(96.dp)
                        .border(BorderStroke(if (selected) 2.dp else 0.dp, if (selected) Rosa.colors.accent else Rosa.colors.ink.copy(alpha = 0f)), RoundedCornerShape(26.dp))
                        .padding(4.dp)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.RadioButton) {
                            haptics?.tick()
                            onPick(style)
                        },
                ) {
                    CalendarPreview(
                        state.config.copy(style = style, opacity = if (style == WidgetStyle.Sky) 1f else state.config.opacity),
                        state.content,
                        Modifier.fillMaxSize(),
                        cornerRadiusDp = 22f,
                    )
                }
                Text(labels.getValue(style), style = Rosa.type.caption, color = if (selected) Rosa.colors.ink else Rosa.colors.inkSoft, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PlacePicker(state: CalendarStudioState, viewModel: CalendarStudioViewModel) {
    val currentLocation = stringResource(app.rosa.weather.core.designsystem.R.string.current_location)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceChip(
                label = stringResource(R.string.studio_place_weather_app),
                icon = RosaIcon.Sparkle,
                selected = state.config.placeId == Place.FOLLOW_APP_ID,
            ) { viewModel.update { it.copy(placeId = Place.FOLLOW_APP_ID) } }
            state.places.forEach { place ->
                PlaceChip(
                    label = place.name.ifBlank { currentLocation },
                    icon = if (place.isCurrentLocation) RosaIcon.Location else null,
                    selected = place.id == state.config.placeId,
                ) { viewModel.update { it.copy(placeId = place.id) } }
            }
        }
        if (state.config.placeId == Place.FOLLOW_APP_ID) {
            val city = state.appPlace?.let { it.name.ifBlank { currentLocation } }
            if (city != null) Label(stringResource(R.string.studio_place_weather_app_hint, city))
        }
    }
}

@Composable
private fun PlaceChip(label: String, icon: RosaIcon?, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    GlassButton(
        onClick = {
            haptics?.tick()
            onClick()
        },
        style = if (selected) GlassStyle.Regular else GlassStyle.Clear,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                RosaIconView(icon, Rosa.colors.ink, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = if (selected) Rosa.type.headline else Rosa.type.body, color = Rosa.colors.ink)
        }
    }
}
