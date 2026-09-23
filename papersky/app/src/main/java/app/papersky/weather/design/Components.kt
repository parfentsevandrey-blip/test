package app.papersky.weather.design

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.ScenePalette
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ---- Time --------------------------------------------------------------------------------------

/** One shared animation clock; drawing code reads it so only the draw phase is invalidated. */
@Stable
class SceneClock {
    val seconds = mutableFloatStateOf(0f)

    /** A lightning flash lighting up the whole screen, 0..1 (DESIGN_DOCTRINE §4). */
    val flash = mutableFloatStateOf(0f)
}

val LocalSceneClock = staticCompositionLocalOf { SceneClock() }
val LocalScenePalette = staticCompositionLocalOf<ScenePalette?> { null }

val PillShape = RoundedCornerShape(50)

// ---- Touch -------------------------------------------------------------------------------------

/** Pressing debosses a sheet a little (spring `press`); letting go lifts it back (`release`). */
@Composable
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    haptic: Boolean = true,
    pressed: Float = 0.985f,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    if (onClick == null) return this
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (isPressed) pressed else 1f, if (isPressed) Motion.press() else Motion.release(), label = "press")
    val h = rememberHaptics()
    val click by rememberUpdatedState(onClick)
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = role,
            onLongClick = onLongClick?.let { long -> { h.threshold(); long() } },
        ) {
            if (haptic) h.press()
            click()
        }
}

// ---- Choreography ------------------------------------------------------------------------------

/** Coordinates a screen's entrance: the first sheets are laid down one by one; later ones are just there. */
@Stable
class Choreography {
    internal val born = System.nanoTime()
    internal val introOver get() = System.nanoTime() - born > 1_600_000_000L
}

val LocalChoreography = staticCompositionLocalOf { Choreography() }

/**
 * A sheet laid down (§9): it rises 14 dp into place and its ink comes up, on the `settle` spring,
 * [order] × 60 ms after the screen opens. No turning, no scaling.
 */
@Composable
fun Modifier.laidDown(order: Int): Modifier {
    val choreo = LocalChoreography.current
    val progress = remember { Animatable(if (choreo.introOver) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(order * Motion.STAGGER_MS)
            progress.animateTo(1f, Motion.settle())
        }
    }
    val rise = with(LocalDensity.current) { 14.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        if (p != 1f) {
            alpha = (p * 1.6f).coerceIn(0f, 1f)
            translationY = rise * (1f - p)
        }
    }
}

// ---- Paper -------------------------------------------------------------------------------------

/** Radii of §8: controls, sheets, and the big page sheet. */
val ControlShape = RoundedCornerShape(12.dp)
val SheetShape = RoundedCornerShape(16.dp)

/**
 * A sheet of cotton paper (§8): precisely cut, radius 16 dp, laid on the page (level 1) with
 * 20 dp margins. Tappable sheets deboss under the finger.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    level: Int = 1,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick)
            .paperSurface(SheetShape, level, color)
            .padding(contentPadding),
        content = content,
    )
}

/** A hairline rule printed in ink (§8). */
@Composable
fun PaperRule(modifier: Modifier = Modifier) {
    val c = Paper.colors.rule
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(c, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
    }
}

// ---- Text --------------------------------------------------------------------------------------

/** Small capitals over a section, widely tracked (§6 `label`). */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Paper.colors.paperInkSoft) {
    BasicText(text.uppercase(), modifier, style = Paper.type.label.copy(color = color))
}

/** Odometer text: each changed character rolls up (increase) or down (decrease) on the `digit` spring. */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, numericValue: Double? = null) {
    val previous = remember { mutableFloatStateOf(numericValue?.toFloat() ?: 0f) }
    val goingUp = (numericValue?.toFloat() ?: 0f) >= previous.floatValue
    LaunchedEffect(numericValue) { previous.floatValue = numericValue?.toFloat() ?: 0f }
    Row(modifier) {
        text.forEachIndexed { index, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (goingUp) 1 else -1
                    (slideInVertically(Motion.digit()) { it * dir } + fadeIn(tween(160, delayMillis = index * 30)))
                        .togetherWith(slideOutVertically(Motion.digit()) { -it * dir } + fadeOut(tween(120)))
                },
                label = "roll$index",
            ) { c -> BasicText(c.toString(), style = style) }
        }
    }
}

/**
 * The human voice, coming up like ink soaking into paper (§9): a soft-edged wipe from left to
 * right whenever the text changes, 420–1200 ms depending on its length.
 */
@Composable
fun InkReveal(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(text) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = (420 + text.length * 18).coerceAtMost(1200), easing = FastOutSlowInEasing))
    }
    val feather = with(LocalDensity.current) { 24.dp.toPx() }
    BasicText(
        text,
        modifier
            .semantics { contentDescription = text }
            .graphicsLayer { compositingStrategy = if (progress.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
                drawContent()
                val p = progress.value
                if (p < 1f) {
                    val edge = (size.width + feather) * p
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = edge - feather, endX = edge),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        style = style,
        maxLines = maxLines,
    )
}

// ---- Glyphs ------------------------------------------------------------------------------------

/** Weather glyphs baked once and shared across the app: drawing one is a single texture blit. */
private object GlyphCache {
    private val cache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(glyph: Glyph, px: Int, colors: GlyphColors, rotation: Float, base: Boolean): ImageBitmap {
        val key = "${glyph.ordinal}|$px|${colors.hashCode()}|${rotation.roundToInt()}|$base"
        cache.get(key)?.let { return it }
        val pass = if (base) GlyphRenderer.Pass.Base else GlyphRenderer.Pass.All
        val bmp: Bitmap = GlyphRenderer.bitmap(glyph, px, colors, rotation, pass)
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }
}

/** For canvases that lay out many glyphs themselves (the hourly ribbon). */
fun glyphImage(glyph: Glyph, px: Int, colors: GlyphColors, base: Boolean, rotation: Float = 0f): ImageBitmap =
    GlyphCache.get(glyph, px, colors, rotation, base)

/**
 * An engraved weather glyph (§7). Its body is baked once; while [animate], only its moving parts —
 * rays, drops, flakes, the star by the moon, the bolt, mist — are drawn over it per frame.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    onPaper: Boolean = true,
    animate: Boolean = true,
    rotation: Float = 0f,
    palette: ScenePalette? = LocalScenePalette.current,
) {
    val colors = remember(palette, onPaper) { palette?.let { GlyphColors.from(it, onPaper) } } ?: return
    val px = with(LocalDensity.current) { size.roundToPx() }
    val moving = animate && glyph in GlyphRenderer.ANIMATED
    val image = remember(glyph, px, colors, rotation, moving) { GlyphCache.get(glyph, px, colors, rotation, moving) }
    if (!moving) {
        Canvas(modifier.size(size)) { drawImage(image) }
        return
    }
    val renderer = remember { GlyphRenderer() }
    val clock = LocalSceneClock.current
    Canvas(modifier.size(size)) {
        drawImage(image)
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, glyph, 0f, 0f, this.size.minDimension, colors, clock.seconds.floatValue + 0.01f, rotation, GlyphRenderer.Pass.Motion)
        }
    }
}

// ---- Controls ----------------------------------------------------------------------------------

/**
 * A button (§8): the main action is printed in solid ink with the paper's colour for its words;
 * the others are a hairline frame. 50 dp tall, radius 12 dp, Manrope 600 capitals.
 */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    primary: Boolean = true,
) {
    val colors = Paper.colors
    val fg = if (primary) colors.paper else colors.paperInk
    Row(
        modifier
            .pressable(onClick, pressed = 0.97f)
            .heightIn(min = 50.dp)
            .then(
                if (primary) Modifier.shadowOf(ControlShape, Elevation.Raised, colors.shadow, if (colors.isNight) 1.6f else 1f).clip(ControlShape).background(colors.paperInk)
                else Modifier.clip(ControlShape).border(1.dp, colors.paperInk.copy(alpha = 0.28f), ControlShape),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, size = 20.dp, onPaper = !primary, animate = false)
            Spacer(Modifier.width(10.dp))
        }
        BasicText(text.uppercase(), style = Paper.type.label.copy(fontSize = 13.sp, letterSpacing = 0.08.em, color = fg, textAlign = TextAlign.Center))
    }
}

/** A round button of paper with a hairline icon (back, close, the corner handle). */
@Composable
fun PaperDisc(icon: PaperIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = Paper.colors
    Box(
        modifier
            .semantics { contentDescription = description }
            .pressable(onClick, pressed = 0.92f)
            .size(size)
            .paperSurface(CircleShape, level = 2),
        contentAlignment = Alignment.Center,
    ) {
        PaperIconView(icon, colors.paperInk, size = size * 0.45f)
    }
}

/** A paper disc sliding in a debossed slot; the slot fills with ink when on (§8). */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, Motion.snap(), label = "switch")
    val ink by animateColorAsState(if (checked) colors.paperInk else colors.paperInk.copy(alpha = 0f), Motion.snap(), label = "track")
    Box(
        modifier
            .semantics { stateDescription = if (checked) "on" else "off" }
            .size(46.dp, 28.dp)
            .debossed(PillShape)
            .background(ink, PillShape)
            .clickable(role = Role.Switch) { h.toggle(!checked); onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(22.dp)
                .paperSurface(CircleShape, level = 1),
        )
    }
}

/** Options in a debossed slot; the chosen one is a sheet laid into it, sliding on `snap` (§8). */
@Composable
fun PaperSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .debossed(ControlShape)
            .padding(3.dp),
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected, Motion.snap(), label = "seg")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segW)
                .height(38.dp)
                .paperSurface(RoundedCornerShape(10.dp), level = 1),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { if (i != selected) { h.tick(); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = Paper.type.caption.copy(color = if (i == selected) colors.paperInk else colors.paperInkSoft, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A hairline track with a paper disc on it (§8); the travelled part is inked. The disc ticks
 * under the finger on every step and lifts (level 3) while held.
 */
@Composable
fun PaperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val latest by rememberUpdatedState(value)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val shown = animateFloatAsState(fraction, Motion.snap(), label = "slider")
    var dragging by remember { mutableStateOf(false) }
    val lift = animateFloatAsState(if (dragging) 1f else 0f, Motion.lift(), label = "thumb")
    fun quantize(f: Float): Float {
        val raw = range.start + f.coerceIn(0f, 1f) * (range.endInclusive - range.start)
        if (steps <= 0) return raw
        val stepSize = (range.endInclusive - range.start) / (steps + 1)
        return (range.start + ((raw - range.start) / stepSize).roundToInt() * stepSize).coerceIn(range.start, range.endInclusive)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(range, steps) {
                detectTapGestures { o ->
                    val v = quantize(o.x / size.width)
                    if (v != latest) { h.tick(); onValueChange(v) }
                }
            }
            .pointerInput(range, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; h.dragStart() },
                    onDragEnd = { dragging = false; h.gestureEnd() },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val v = quantize(change.position.x / size.width)
                    if (v != latest) {
                        if (steps > 0) h.tick() else if ((v * 20).roundToInt() != (latest * 20).roundToInt()) h.softTick()
                        onValueChange(v)
                    }
                }
            },
    ) {
        val cy = size.height / 2
        val r = 11.dp.toPx()
        val x = r + (size.width - 2 * r) * shown.value
        drawLine(colors.paperInk.copy(alpha = 0.15f), Offset(r, cy), Offset(size.width - r, cy), 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(colors.paperInk, Offset(r, cy), Offset(x, cy), 2.dp.toPx(), cap = StrokeCap.Round)
        if (steps > 0) {
            for (i in 0..steps + 1) {
                val sx = r + (size.width - 2 * r) * i / (steps + 1)
                drawLine(colors.paperInk.copy(alpha = 0.3f), Offset(sx, cy - 4.dp.toPx()), Offset(sx, cy + 4.dp.toPx()), 1f)
            }
        }
        paperThumb(Offset(x, cy - 1.5.dp.toPx() * lift.value), r, lift.value, colors.paper, colors.paperInk, colors.shadow)
    }
}

/** A paper disc with a hairline ring of ink, resting (lift 0) or held up (lift 1). */
fun DrawScope.paperThumb(c: Offset, r: Float, lift: Float, paper: Color, ink: Color, shadow: Color) {
    val drop = (1.5f + 4f * lift) * density
    drawCircle(Brush.radialGradient(listOf(shadow.copy(alpha = 0.22f - 0.06f * lift), shadow.copy(alpha = 0f)), center = c + Offset(0f, drop), radius = r * (1.35f + 0.35f * lift)), r * (1.35f + 0.35f * lift), c + Offset(0f, drop))
    drawCircle(paper, r, c)
    drawCircle(Color.White.copy(alpha = 0.35f), r - 1.dp.toPx(), c, style = Stroke(1.dp.toPx()))
    drawCircle(ink.copy(alpha = 0.55f), r, c, style = Stroke(1.dp.toPx()))
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: Glyph? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) {
            GlyphIcon(glyph, size = 24.dp, animate = false)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Paper.type.bodyStrong.copy(color = Paper.colors.paperInk))
            if (subtitle != null) BasicText(subtitle, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A pill with a hairline frame (§8); the chosen one is printed in solid ink. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, swatch: Color? = null) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val bg by animateColorAsState(if (selected) colors.paperInk else colors.paperInk.copy(alpha = 0f), Motion.snap(), label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.paper else colors.paperInk, Motion.snap(), label = "chipFg")
    Row(
        modifier
            .pressable({ h.tick(); onClick() }, haptic = false, pressed = 0.95f, role = Role.RadioButton)
            .clip(PillShape)
            .background(bg)
            .border(1.dp, colors.paperInk.copy(alpha = if (selected) 0f else 0.22f), PillShape)
            .widthIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.caption.copy(color = fg), maxLines = 1)
    }
}

/** The room around the print (§4.3): dusk gathering in the corners at night, lightning flashes. */
fun Modifier.roomLight(light: () -> Light, flash: () -> Float): Modifier = drawWithContent {
    drawContent()
    val v = light().vignette
    if (v > 0.01f) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xFF0A0710).copy(alpha = v),
                center = Offset(size.width * 0.5f, size.height * 0.45f), radius = maxOf(size.width, size.height) * 0.78f,
            ),
        )
    }
    val f = flash()
    if (f > 0.01f) drawRect(Color(0xFFF4F2FF).copy(alpha = 0.12f * f))
}
