package app.papersky.weather.widget.config

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.papersky.weather.R
import app.papersky.weather.core.model.Place
import app.papersky.weather.design.Chip
import app.papersky.weather.design.Label
import app.papersky.weather.design.Paper
import app.papersky.weather.design.PaperButton
import app.papersky.weather.design.PaperCard
import app.papersky.weather.design.PaperDisc
import app.papersky.weather.design.paperSurface
import app.papersky.weather.design.onSky
import app.papersky.weather.design.PaperSegmented
import app.papersky.weather.design.PaperSlider
import app.papersky.weather.design.PaperSwitch
import app.papersky.weather.design.SettingRow
import app.papersky.weather.design.pressable
import app.papersky.weather.design.rememberHaptics
import app.papersky.weather.scene.PaletteMode
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import app.papersky.weather.widget.TapAction
import app.papersky.weather.widget.WidgetBackground
import app.papersky.weather.widget.WidgetConfig
import app.papersky.weather.widget.WidgetDensity
import app.papersky.weather.widget.WidgetPreset
import kotlin.math.roundToInt

/** Launcher-like grid used for snapping in the editor (dp of an n×m widget). */
object Grid {
    const val CELL_W = 76
    const val CELL_H = 104
    fun size(cols: Int, rows: Int) = DpSize((cols * CELL_W - 12).dp, (rows * CELL_H - 16).dp)
    fun cols(width: Dp) = ((width.value + 12) / CELL_W).roundToInt().coerceAtLeast(1)
    fun rows(height: Dp) = ((height.value + 16) / CELL_H).roundToInt().coerceAtLeast(1)
}

/**
 * Widget workshop: a desk with the live widget on it (drag the corner to any size — it snaps to
 * home-screen cells with a click) above a sheet of every option.
 */
@Composable
fun WidgetEditor(
    initial: WidgetConfig,
    places: List<Place>,
    initialSize: DpSize,
    title: String,
    doneLabel: String,
    onDone: (WidgetConfig) -> Unit,
    onClose: () -> Unit,
) {
    var config by remember { mutableStateOf(initial) }
    var size by remember { mutableStateOf(initialSize) }
    val colors = Paper.colors
    val h = rememberHaptics()
    fun edit(transform: (WidgetConfig) -> WidgetConfig) { config = transform(config) }

    Column(Modifier.fillMaxSize().background(colors.sky)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PaperDisc(PaperIcon.Close, stringResource(R.string.back), onClose)
            Spacer(Modifier.width(12.dp))
            BasicText(
                title, Modifier.weight(1f), style = Paper.type.title.copy(color = colors.onSky).onSky(colors.onSky.red + colors.onSky.green + colors.onSky.blue > 1.5f), maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
            )
            PaperButton(doneLabel, { h.confirm(); onDone(config) })
        }
        Desk(config, size, onResize = { size = it })
        // The options lie on a large sheet of paper, each group on its own card (§12).
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f)
                .paperSurface(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), level = 4),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("presets") {
                Section(stringResource(R.string.editor_presets)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetPreset.entries.forEach { preset ->
                            Chip(presetName(preset), selected = false, onClick = {
                                config = preset.config.copy(placeId = config.placeId)
                                size = Grid.size(preset.cols, preset.rows)
                            })
                        }
                    }
                }
            }
            item("place") {
                Section(stringResource(R.string.editor_place)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.editor_here), config.placeId == Place.HERE, { edit { it.copy(placeId = Place.HERE) } })
                        places.filter { !it.isDeviceLocation }.forEach { p ->
                            Chip(p.name, config.placeId == p.id, { edit { it.copy(placeId = p.id) } })
                        }
                    }
                }
            }
            item("look") {
                Section(stringResource(R.string.editor_look)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaletteMode.entries.forEach { mode ->
                            PaletteSwatch(mode, config.palette == mode) { edit { it.copy(palette = mode) } }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    PaperSegmented(
                        listOf(stringResource(R.string.bg_scene), stringResource(R.string.bg_paper), stringResource(R.string.bg_clear)),
                        config.background.ordinal,
                        { i -> edit { it.copy(background = WidgetBackground.entries[i], opacity = if (WidgetBackground.entries[i] == WidgetBackground.Clear) 0f else it.opacity.coerceAtLeast(0.6f)) } },
                    )
                    Spacer(Modifier.height(12.dp))
                    SliderRow(stringResource(R.string.editor_opacity), "${(config.opacity * 100).roundToInt()}%") {
                        PaperSlider(config.opacity, { v -> edit { it.copy(opacity = v) } }, range = if (config.background == WidgetBackground.Clear) 0f..0.9f else 0.3f..1f, steps = 0)
                    }
                    SliderRow(stringResource(R.string.editor_corners), if (config.corner < 0) stringResource(R.string.editor_corners_system) else "${config.corner} dp") {
                        PaperSlider((config.corner + 1).toFloat(), { v -> edit { it.copy(corner = v.roundToInt() - 1) } }, range = 0f..33f, steps = 32)
                    }
                }
            }
            item("content") {
                Section(stringResource(R.string.editor_content)) {
                    Toggle(stringResource(R.string.show_animate), config.animate) { v -> edit { it.copy(animate = v) } }
                    Toggle(stringResource(R.string.show_location), config.showLocation) { v -> edit { it.copy(showLocation = v) } }
                    Toggle(stringResource(R.string.show_condition), config.showCondition) { v -> edit { it.copy(showCondition = v) } }
                    Toggle(stringResource(R.string.show_hilo), config.showHiLo) { v -> edit { it.copy(showHiLo = v) } }
                    Toggle(stringResource(R.string.show_feels), config.showFeelsLike) { v -> edit { it.copy(showFeelsLike = v) } }
                    Toggle(stringResource(R.string.show_clock), config.showClock) { v -> edit { it.copy(showClock = v) } }
                    Toggle(stringResource(R.string.show_whisper), config.showWhisper) { v -> edit { it.copy(showWhisper = v) } }
                    Toggle(stringResource(R.string.show_hourly), config.showHourly) { v -> edit { it.copy(showHourly = v) } }
                    Toggle(stringResource(R.string.show_daily), config.showDaily) { v -> edit { it.copy(showDaily = v) } }
                    Toggle(stringResource(R.string.show_details), config.showDetails) { v -> edit { it.copy(showDetails = v) } }
                    Toggle(stringResource(R.string.show_refresh), config.showRefresh) { v -> edit { it.copy(showRefresh = v) } }
                    Toggle(stringResource(R.string.show_updated), config.showUpdated) { v -> edit { it.copy(showUpdated = v) } }
                }
            }
            item("text") {
                Section(stringResource(R.string.editor_text)) {
                    SliderRow(stringResource(R.string.editor_text_size), "${(config.textScale * 100).roundToInt()}%") {
                        PaperSlider(config.textScale, { v -> edit { it.copy(textScale = v) } }, range = 0.8f..1.4f, steps = 11)
                    }
                    Spacer(Modifier.height(8.dp))
                    PaperSegmented(
                        listOf(stringResource(R.string.density_compact), stringResource(R.string.density_balanced), stringResource(R.string.density_airy)),
                        config.density.ordinal, { i -> edit { it.copy(density = WidgetDensity.entries[i]) } },
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicText(stringResource(R.string.editor_hour_step), style = Paper.type.caption.copy(color = colors.paperInkSoft))
                    Spacer(Modifier.height(6.dp))
                    PaperSegmented(listOf("1", "2", "3").map { stringResource(R.string.hours_short, it.toInt()) }, config.hourStep - 1, { i -> edit { it.copy(hourStep = i + 1) } })
                    Spacer(Modifier.height(12.dp))
                    BasicText(stringResource(R.string.editor_tap), style = Paper.type.caption.copy(color = colors.paperInkSoft))
                    Spacer(Modifier.height(6.dp))
                    PaperSegmented(
                        listOf(stringResource(R.string.tap_open), stringResource(R.string.tap_refresh)),
                        config.tap.ordinal, { i -> edit { it.copy(tap = TapAction.entries[i]) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun Desk(config: WidgetConfig, widgetSize: DpSize, onResize: (DpSize) -> Unit) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(318.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        val maxW = maxWidth - 28.dp
        val maxH = maxHeight - 34.dp
        val shown = DpSize(widgetSize.width.coerceIn(40.dp, maxW), widgetSize.height.coerceIn(40.dp, maxH))
        var lastCells by remember { mutableIntStateOf(Grid.cols(shown.width) * 100 + Grid.rows(shown.height)) }
        // Home-screen cell dots under the widget.
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF2F4858), Color(0xFF5B6C8F), Color(0xFFB38B91))))) {
            val cw = Grid.CELL_W.dp.toPx()
            val ch = Grid.CELL_H.dp.toPx()
            var x = cw
            while (x < size.width) {
                var y = ch
                while (y < size.height) { drawCircle(Color.White.copy(alpha = 0.25f), 2.dp.toPx(), Offset(x - 6.dp.toPx(), y - 8.dp.toPx())); y += ch }
                x += cw
            }
        }
        WidgetPreview(config, shown, Modifier.offset(x = 0.dp, y = 0.dp).padding(start = 10.dp, top = 10.dp))
        // Resize handle: a folded paper corner.
        val handle = 34.dp
        Box(
            Modifier
                .offset { IntOffset(with(density) { (10.dp + shown.width - handle / 2).roundToPx() }, with(density) { (10.dp + shown.height - handle / 2).roundToPx() }) }
                .size(handle)
                .semantics { contentDescription = "resize" }
                .pointerInput(maxW, maxH) {
                    var start = shown
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = shown; acc = Offset.Zero; h.dragStart() },
                        onDragEnd = { h.gestureEnd() },
                    ) { change, drag ->
                        change.consume()
                        acc += drag
                        var w = (start.width + (acc.x / density.density).dp).coerceIn(40.dp, maxW)
                        var hh = (start.height + (acc.y / density.density).dp).coerceIn(40.dp, maxH)
                        // Magnetic snapping to launcher cells.
                        val snapW = Grid.size(Grid.cols(w), 1).width
                        val snapH = Grid.size(1, Grid.rows(hh)).height
                        if ((w - snapW).value in -9f..9f) w = snapW
                        if ((hh - snapH).value in -9f..9f) hh = snapH
                        val cells = Grid.cols(w) * 100 + Grid.rows(hh)
                        if (cells != lastCells) { lastCells = cells; h.tick() }
                        onResize(DpSize(w, hh))
                    }
                }
                .paperSurface(CircleShape, level = 3),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                val c = colors.paperInk
                drawLine(c, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.8f, size.height * 0.2f), 2.dp.toPx())
                drawLine(c, Offset(size.width * 0.55f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.2f), 2.dp.toPx())
                drawLine(c, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.45f), 2.dp.toPx())
                drawLine(c, Offset(size.width * 0.2f, size.height * 0.55f), Offset(size.width * 0.2f, size.height * 0.8f), 2.dp.toPx())
                drawLine(c, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.45f, size.height * 0.8f), 2.dp.toPx())
            }
        }
        BasicText(
            "${Grid.cols(shown.width)} × ${Grid.rows(shown.height)}  ·  ${shown.width.value.roundToInt()} × ${shown.height.value.roundToInt()} dp",
            Modifier.align(Alignment.BottomEnd).padding(8.dp),
            style = Paper.type.caption.copy(color = Color.White.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PaperCard(seed = title.hashCode()) {
        Label(title)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SliderRow(title: String, value: String, slider: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f), style = Paper.type.body.copy(color = Paper.colors.paperInk))
        BasicText(value, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
    }
    slider()
}

@Composable
private fun Toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title) { PaperSwitch(value, onChange) }
}

@Composable
private fun PaletteSwatch(mode: PaletteMode, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val day = remember(mode) { Palettes.resolve(mode, SceneState(daylight = 1f), context) }
    val night = remember(mode) { Palettes.resolve(mode, SceneState(daylight = 0f, sunProgress = -0.5f), context) }
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(dampingRatio = 0.5f), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable(onClick, pressed = 0.9f)) {
        Canvas(Modifier.size(52.dp)) {
            val r = size.minDimension / 2
            if (ring > 0.dp) drawCircle(Color(day.accent), r)
            val inner = r - ring.toPx() - 2.dp.toPx()
            drawArc(Brush.verticalGradient(listOf(Color(day.skyTop), Color(day.skyBottom))), 90f, 180f, true, Offset(center.x - inner, center.y - inner), androidx.compose.ui.geometry.Size(inner * 2, inner * 2))
            drawArc(Brush.verticalGradient(listOf(Color(night.skyTop), Color(night.skyBottom))), -90f, 180f, true, Offset(center.x - inner, center.y - inner), androidx.compose.ui.geometry.Size(inner * 2, inner * 2))
            drawCircle(Color(day.hillMid), inner * 0.32f, Offset(center.x - inner * 0.35f, center.y + inner * 0.35f))
            drawCircle(Color(night.window), inner * 0.14f, Offset(center.x + inner * 0.35f, center.y + inner * 0.3f))
        }
        Spacer(Modifier.height(4.dp))
        BasicText(paletteName(mode), style = Paper.type.caption.copy(color = Paper.colors.paperInk))
    }
}

@Composable
fun paletteName(mode: PaletteMode): String = stringResource(
    when (mode) {
        PaletteMode.Auto -> R.string.palette_auto
        PaletteMode.Linen -> R.string.palette_linen
        PaletteMode.Ink -> R.string.palette_ink
        PaletteMode.Riso -> R.string.palette_riso
        PaletteMode.Moss -> R.string.palette_moss
        PaletteMode.Wallpaper -> R.string.palette_wallpaper
    },
)

@Composable
fun presetName(preset: WidgetPreset): String = stringResource(
    when (preset) {
        WidgetPreset.LivingWindow -> R.string.preset_living
        WidgetPreset.Pocket -> R.string.preset_pocket
        WidgetPreset.Tower -> R.string.preset_tower
        WidgetPreset.Ribbon -> R.string.preset_ribbon
        WidgetPreset.PaperNote -> R.string.preset_paper
        WidgetPreset.NightLight -> R.string.preset_night
        WidgetPreset.Glass -> R.string.preset_glass
        WidgetPreset.Riso -> R.string.preset_riso
    },
)
