package app.rosa.weather.widget.studio

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rosa.weather.core.designsystem.component.GlassButton
import app.rosa.weather.core.designsystem.component.GlassIconButton
import app.rosa.weather.core.designsystem.component.GlassSegmented
import app.rosa.weather.core.designsystem.component.GlassSlider
import app.rosa.weather.core.designsystem.component.GlassSurface
import app.rosa.weather.core.designsystem.component.GlassToggle
import app.rosa.weather.core.designsystem.component.RosaEnvironment
import app.rosa.weather.core.designsystem.component.RosaIcon
import app.rosa.weather.core.designsystem.component.RosaIconView
import app.rosa.weather.core.designsystem.component.SkyBackdrop
import app.rosa.weather.core.designsystem.glass.GlassStyle
import app.rosa.weather.core.designsystem.haptics.LocalHaptics
import app.rosa.weather.core.designsystem.motion.RosaMotion
import app.rosa.weather.core.designsystem.sky.SkyParams
import app.rosa.weather.core.designsystem.theme.Rosa
import app.rosa.weather.core.model.SkyPalette
import app.rosa.weather.core.model.WidgetAccent
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.core.model.WidgetDensity
import app.rosa.weather.core.model.WidgetModule
import app.rosa.weather.core.model.WidgetStyle
import app.rosa.weather.core.model.WidgetTapAction
import app.rosa.weather.core.model.WidgetTheme
import app.rosa.weather.core.model.momentAt
import app.rosa.weather.widget.R
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

/**
 * The widget studio: opened by the launcher when a widget is added or reconfigured, or from the
 * app. Every change re-renders a live preview with the real engine, and the preview can be
 * stretched to any grid size right here — so you see exactly how each size will look.
 */
@AndroidEntryPoint
class WidgetStudioActivity : ComponentActivity() {
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
            val viewModel: WidgetStudioViewModel = hiltViewModel()
            StudioRoot(viewModel, onClose = ::finish, onSaved = {
                setResult(Activity.RESULT_OK, result)
                finish()
            })
        }
    }
}

@Composable
private fun StudioRoot(viewModel: WidgetStudioViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val forecast = s.content.forecast ?: return
    val moment = remember(forecast, s.content.nowEpochSeconds / 60) { forecast.momentAt(s.content.nowEpochSeconds) }
    val palette = remember(moment) { SkyPalette.of(moment.sun.elevation, moment.visual, moment.moonPhase.illumination) }
    RosaEnvironment(s.settings, palette) {
        SkyBackdrop(SkyParams.from(moment, palette), s.settings.effects, interactive = false) {
            StudioScreen(s, viewModel, onClose, onSaved)
        }
    }
}

@Composable
private fun StudioScreen(state: StudioState, viewModel: WidgetStudioViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val config = state.config
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = LocalHaptics.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(RosaIcon.Close, stringResource(android.R.string.cancel), onClose)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.studio_title), style = Rosa.type.title, color = Rosa.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            GlassButton(onClick = { haptics?.confirm(); viewModel.save(onSaved) }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RosaIconView(RosaIcon.Check, Rosa.colors.ink, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.studio_save), style = Rosa.type.headline, color = Rosa.colors.ink)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResizeStage(state, viewModel.initialSize?.let { it.width to it.height })
            Section(stringResource(R.string.studio_style)) {
                StylePicker(state) { style ->
                    viewModel.update { it.copy(style = style, opacity = if (style == WidgetStyle.Sky) 1f else it.opacity.coerceAtMost(0.9f)) }
                }
            }
            Section(stringResource(R.string.studio_theme)) {
                val themeLabels = mapOf(
                    WidgetTheme.Auto to stringResource(R.string.theme_auto),
                    WidgetTheme.Light to stringResource(R.string.theme_light),
                    WidgetTheme.Dark to stringResource(R.string.theme_dark),
                )
                GlassSegmented(WidgetTheme.entries, config.theme, { t -> viewModel.update { it.copy(theme = t) } }, { themeLabels.getValue(it) })
                Label(stringResource(R.string.studio_accent))
                val accentLabels = mapOf(
                    WidgetAccent.Sky to stringResource(R.string.accent_sky),
                    WidgetAccent.Temperature to stringResource(R.string.accent_temperature),
                    WidgetAccent.Dynamic to stringResource(R.string.accent_dynamic),
                    WidgetAccent.Mono to stringResource(R.string.accent_mono),
                )
                GlassSegmented(WidgetAccent.entries, config.accent, { a -> viewModel.update { it.copy(accent = a) } }, { accentLabels.getValue(it) })
            }
            Section(stringResource(R.string.studio_glass)) {
                GlassSlider(config.opacity, { v -> viewModel.update { it.copy(opacity = v) } }, range = 0.15f..1f, stateDescription = "${(config.opacity * 100).roundToInt()}%")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.studio_corners), style = Rosa.type.body, color = Rosa.colors.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.studio_corners_system), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
                    Spacer(Modifier.width(10.dp))
                    GlassToggle(config.cornerRadiusDp < 0f, { system -> viewModel.update { it.copy(cornerRadiusDp = if (system) -1f else 24f) } })
                }
                if (config.cornerRadiusDp >= 0f) {
                    GlassSlider(config.cornerRadiusDp, { v -> viewModel.update { it.copy(cornerRadiusDp = v) } }, range = 0f..44f, steps = 10, stateDescription = "${config.cornerRadiusDp.roundToInt()} dp")
                }
            }
            Section(stringResource(R.string.studio_text)) {
                GlassSlider(config.textScale, { v -> viewModel.update { it.copy(textScale = v) } }, range = 0.85f..1.3f, steps = 8, stateDescription = "${(config.textScale * 100).roundToInt()}%")
                Label(stringResource(R.string.studio_density))
                val densityLabels = mapOf(
                    WidgetDensity.Compact to stringResource(R.string.density_compact),
                    WidgetDensity.Balanced to stringResource(R.string.density_balanced),
                    WidgetDensity.Airy to stringResource(R.string.density_airy),
                )
                GlassSegmented(WidgetDensity.entries, config.density, { d -> viewModel.update { it.copy(density = d) } }, { densityLabels.getValue(it) })
            }
            Section(stringResource(R.string.studio_content)) { ModulesEditor(config, viewModel) }
            if (state.places.isNotEmpty()) {
                Section(stringResource(R.string.studio_place)) { PlacePicker(state, viewModel) }
            }
            Section(stringResource(R.string.studio_extras)) {
                ToggleLine(stringResource(R.string.toggle_location), config.showLocation) { v -> viewModel.update { it.copy(showLocation = v) } }
                ToggleLine(stringResource(R.string.toggle_feels), config.showFeelsLike) { v -> viewModel.update { it.copy(showFeelsLike = v) } }
                ToggleLine(stringResource(R.string.toggle_art), config.showWeatherArt) { v -> viewModel.update { it.copy(showWeatherArt = v) } }
                Label(stringResource(R.string.studio_tap))
                val tapLabels = mapOf(WidgetTapAction.OpenApp to stringResource(R.string.tap_app), WidgetTapAction.Refresh to stringResource(R.string.tap_refresh))
                GlassSegmented(WidgetTapAction.entries, config.tapAction, { t -> viewModel.update { it.copy(tapAction = t) } }, { tapLabels.getValue(it) })
            }
        }
    }
}

/**
 * A slice of home screen with a launcher-like grid. The preview follows your finger at *any*
 * size while dragging (the layout engine re-flows live), then settles onto the nearest cells.
 */
@Composable
private fun ResizeStage(state: StudioState, initialDp: Pair<Float, Float>?) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = 5
        val maxRows = 5
        val cellW = maxWidth / columns
        val cellH = cellW * 1.12f
        var cols by remember { mutableIntStateOf(initialDp?.let { ((it.first + 8) / cellW.value).roundToInt().coerceIn(1, columns) } ?: 4) }
        var rows by remember { mutableIntStateOf(initialDp?.let { ((it.second + 8) / cellH.value).roundToInt().coerceIn(1, maxRows) } ?: 2) }
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
                WidgetPreview(
                    state.config,
                    state.content,
                    Modifier.offset(gap / 2, gap / 2).size(w, h),
                    cornerRadiusDp = if (state.config.cornerRadiusDp >= 0) state.config.cornerRadiusDp else 24f,
                )
                // Resize handle: a small glass lens at the widget's corner.
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
                Text(stringResource(R.string.studio_cells, cols, rows), style = Rosa.type.headline, color = Rosa.colors.ink)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.studio_resize_hint), style = Rosa.type.caption, color = Rosa.colors.inkSoft)
            }
        }
    }
}

@Composable
private fun StylePicker(state: StudioState, onPick: (WidgetStyle) -> Unit) {
    val haptics = LocalHaptics.current
    val labels = mapOf(
        WidgetStyle.Glass to stringResource(R.string.style_glass),
        WidgetStyle.Sky to stringResource(R.string.style_sky),
        WidgetStyle.Clear to stringResource(R.string.style_clear),
        WidgetStyle.Tonal to stringResource(R.string.style_tonal),
        WidgetStyle.Paper to stringResource(R.string.style_paper),
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
                    WidgetPreview(
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
private fun ModulesEditor(config: WidgetConfig, viewModel: WidgetStudioViewModel) {
    val names = mapOf(
        WidgetModule.Headline to stringResource(R.string.module_headline),
        WidgetModule.Hourly to stringResource(R.string.module_hourly),
        WidgetModule.Daily to stringResource(R.string.module_daily),
        WidgetModule.Details to stringResource(R.string.module_details),
        WidgetModule.Nowcast to stringResource(R.string.module_nowcast),
        WidgetModule.SunPath to stringResource(R.string.module_sunpath),
    )
    val ordered = config.modules + WidgetModule.entries.filter { it !in config.modules }
    ordered.forEach { module ->
        val enabled = module in config.modules
        val index = config.modules.indexOf(module)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(names.getValue(module), style = Rosa.type.body, color = if (enabled) Rosa.colors.ink else Rosa.colors.inkSoft, modifier = Modifier.weight(1f))
            if (enabled && index > 0) {
                ArrowButton(RosaIcon.Back, stringResource(R.string.studio_move_up), rotated = true) {
                    viewModel.update { c -> c.copy(modules = c.modules.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassToggle(enabled, { on ->
                viewModel.update { c -> c.copy(modules = if (on) c.modules + module else c.modules - module) }
            })
        }
    }
}

@Composable
private fun ArrowButton(icon: RosaIcon, description: String, rotated: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RosaIconView(icon, Rosa.colors.inkSoft, modifier = if (rotated) Modifier.rotate(90f) else Modifier, size = 18.dp)
    }
}

@Composable
private fun PlacePicker(state: StudioState, viewModel: WidgetStudioViewModel) {
    val haptics = LocalHaptics.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.places.forEach { place ->
            val selected = place.id == state.config.placeId
            GlassButton(
                onClick = {
                    haptics?.tick()
                    viewModel.update { it.copy(placeId = place.id) }
                },
                style = if (selected) GlassStyle.Regular else GlassStyle.Clear,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (place.isCurrentLocation) {
                        RosaIconView(RosaIcon.Location, Rosa.colors.ink, size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        place.name.ifBlank { stringResource(app.rosa.weather.core.designsystem.R.string.current_location) },
                        style = if (selected) Rosa.type.headline else Rosa.type.body,
                        color = Rosa.colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), style = GlassStyle.Frosted, cornerRadius = 28.dp, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = Rosa.type.headline, color = Rosa.colors.ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Rosa.type.caption, color = Rosa.colors.inkSoft, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Rosa.type.body, color = Rosa.colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        GlassToggle(checked, onChange)
    }
}
