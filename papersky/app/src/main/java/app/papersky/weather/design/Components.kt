package app.papersky.weather.design

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.ScenePalette
import app.papersky.weather.ui.common.PaperIcon
import app.papersky.weather.ui.common.PaperIconView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

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

/** Pressing sinks a sheet a little (spring `press`); letting go bounces it back (`release`). */
@Composable
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    haptic: Boolean = true,
    pressed: Float = 0.965f,
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
 * A sheet laid down onto the diorama (§9): it drifts in from a little above, slightly turned,
 * and settles on the `settle` spring, [order] × 70 ms after the screen opens.
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
    val drop = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        if (p != 1f) {
            val q = 1f - p
            alpha = (p * 3.5f).coerceIn(0f, 1f)
            scaleX = 1f + 0.03f * q
            scaleY = scaleX
            rotationZ = 2f * q
            translationY = -drop * q
        }
    }
}

// ---- Paper -------------------------------------------------------------------------------------

/**
 * A card of the diorama's paper (§8): torn edge, fibres, a soft shadow, sometimes a strip of
 * washi tape holding it — and then it sways on the tape when tapped.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    seed: Int = 1,
    tilt: Float = 0f,
    tape: Boolean = false,
    tapeColor: Color = Paper.colors.tape,
    color: Color? = null,
    level: Int = 2,
    onClick: (() -> Unit)? = null,
    wiggleOnTap: Boolean = onClick == null && tape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(seed) { DeckleShape(seed) }
    val sway = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val tapeX = 22.dp + ((((seed % 5) + 5) % 5) * 9).dp
    val click: (() -> Unit)? = when {
        onClick != null -> onClick
        wiggleOnTap -> {
            {
                h.softTick()
                scope.launch {
                    sway.snapTo(if (sway.value > 0f) -2.2f else 2.2f)
                    sway.animateTo(0f, Motion.wiggle())
                }
            }
        }
        else -> null
    }
    Box(
        modifier.graphicsLayer {
            rotationZ = tilt + sway.value
            // A taped sheet swings around its tape.
            if (tape && size.width > 0f) transformOrigin = TransformOrigin(((tapeX.toPx() + 29.dp.toPx()) / size.width).coerceIn(0f, 1f), 0f)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(click, haptic = onClick != null, pressed = 0.985f)
                .paperSurface(shape, level, color)
                .padding(contentPadding),
            content = content,
        )
        if (tape) {
            WashiTape(
                Modifier.align(Alignment.TopStart).offset(x = tapeX, y = (-7).dp),
                color = tapeColor,
                angle = if (seed % 2 == 0) -8f else 6f,
            )
        }
    }
}

/** Washi tape: translucent, dotted, torn at both ends; the paper shows through it. */
@Composable
fun WashiTape(modifier: Modifier = Modifier, color: Color = Paper.colors.tape, angle: Float = -6f, length: Dp = 58.dp) {
    Canvas(modifier.size(length, 16.dp)) {
        rotate(angle) {
            val th = size.height * 0.8f
            val top = (size.height - th) / 2
            val tooth = 2.dp.toPx()
            val path = Path().apply {
                moveTo(0f, top)
                lineTo(size.width, top)
                for (k in 1..5) lineTo(size.width + if (k % 2 == 1) tooth else 0f, top + th * k / 5)
                lineTo(0f, top + th)
                for (k in 4 downTo 0) lineTo(if (k % 2 == 1) -tooth else 0f, top + th * k / 5)
                close()
            }
            translate(0.5.dp.toPx(), 1.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.1f)) }
            drawPath(path, color.copy(alpha = 0.85f))
            clipPath(path) {
                var x = 5.dp.toPx()
                while (x < size.width - 3.dp.toPx()) {
                    drawCircle(Color.White.copy(alpha = 0.38f), 1.3.dp.toPx(), Offset(x, top + th * 0.32f))
                    drawCircle(Color.White.copy(alpha = 0.38f), 1.3.dp.toPx(), Offset(x + 3.5.dp.toPx(), top + th * 0.7f))
                    x += 7.dp.toPx()
                }
                drawRect(PaperFibres, alpha = 0.35f)
            }
        }
    }
}

/** A faint pencil line between rows on a card. */
@Composable
fun PaperRule(modifier: Modifier = Modifier) {
    val c = Paper.colors.paperInk.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(c, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    }
}

// ---- Text --------------------------------------------------------------------------------------

/** Small caps over a card. */
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

/** Handwriting that writes itself from left to right whenever it changes (§9). */
@Composable
fun HandReveal(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(text) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = (380 + text.length * 22).coerceAtMost(1400)))
    }
    BasicText(
        text,
        modifier
            .semantics { contentDescription = text }
            .drawWithContent {
                val p = progress.value
                if (p >= 1f) drawContent() else clipRect(right = size.width * min(1f, p * 1.15f)) { this@drawWithContent.drawContent() }
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
 * A paper-cut weather glyph (§7). Its body (with its shadow) is baked once; while [animate], only
 * its moving parts — rays, drops, flakes, sparkles, the bolt, mist — are drawn over it per frame.
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

/** A paper button with a torn edge: the accent colour for the main action, plain paper otherwise. */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    primary: Boolean = true,
) {
    val colors = Paper.colors
    val fg = if (primary) Color(0xFFFFF8EE) else colors.paperInk
    val shape = remember(text) { DeckleShape(seed = text.hashCode(), corner = 22.dp, roughness = 0.6.dp) }
    Row(
        modifier
            .pressable(onClick, pressed = 0.94f)
            .paperSurface(shape, level = if (primary) 3 else 2, color = if (primary) colors.accent else null)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, size = 20.dp, onPaper = !primary, animate = false)
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.bodyStrong.copy(color = fg, textAlign = TextAlign.Center))
    }
}

/** A round paper button with an inked icon (back, close, the corner handle). */
@Composable
fun PaperDisc(icon: PaperIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = Paper.colors
    Box(
        modifier
            .semantics { contentDescription = description }
            .pressable(onClick, pressed = 0.88f)
            .size(size)
            .paperSurface(CircleShape, level = 2),
        contentAlignment = Alignment.Center,
    ) {
        PaperIconView(icon, colors.paperInk, size = size * 0.5f)
    }
}

/** A paper tab sliding in a slot; the slot shows the accent when on. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val offset by animateDpAsState(if (checked) 22.dp else 0.dp, Motion.snap(), label = "switch")
    val track by animateColorAsState(if (checked) colors.accent else colors.paperInk.copy(alpha = 0.16f), label = "track")
    Box(
        modifier
            .semantics { stateDescription = if (checked) "on" else "off" }
            .size(52.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .clickable(role = Role.Switch) { h.toggle(!checked); onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .size(24.dp)
                .paperSurface(CircleShape, level = 1),
        )
    }
}

/** Paper tabs in a shallow slot; the chosen one is a lifted sheet that slides on the `snap` spring. */
@Composable
fun PaperSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paperInk.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected, Motion.snap(), label = "seg")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segW)
                .height(38.dp)
                .paperSurface(RoundedCornerShape(12.dp), level = 1),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
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

/** A ribbon with a wooden bead; it ticks under the finger on every step and lifts while held. */
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
    val lift = animateFloatAsState(if (dragging) 1f else 0f, Motion.lift(), label = "bead")
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
        drawLine(colors.paperInk.copy(alpha = 0.14f), Offset(r, cy), Offset(size.width - r, cy), 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(colors.accent, Offset(r, cy), Offset(x, cy), 6.dp.toPx(), cap = StrokeCap.Round)
        if (steps > 0) {
            for (i in 0..steps + 1) {
                val sx = r + (size.width - 2 * r) * i / (steps + 1)
                drawCircle(colors.paperInk.copy(alpha = 0.25f), 1.6.dp.toPx(), Offset(sx, cy))
            }
        }
        val l = lift.value
        if (l > 0.01f) drawCircle(Color.Black.copy(alpha = 0.16f * l), r * 1.1f, Offset(x + r * 0.3f * l, cy + r * 0.7f * l))
        beechBead(Offset(x, cy - 2.dp.toPx() * l), r * (1f + 0.08f * l))
    }
}

/** A beech bead: growth rings, a glint top-left, a shadow down-right. */
fun DrawScope.beechBead(c: Offset, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.25f), r, c + Offset(r * 0.15f, r * 0.28f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE9BE8C), Color(0xFFC98A56), Color(0xFF8C5A30)), center = c - Offset(r * 0.35f, r * 0.4f), radius = r * 1.6f), r, c)
    for (k in 1..3) drawCircle(Color(0xFF7A4B2A).copy(alpha = 0.12f), r * (0.35f + k * 0.2f), c + Offset(r * 0.25f, r * 0.2f), style = Stroke(0.8.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.26f, c - Offset(r * 0.38f, r * 0.42f))
    drawCircle(Color(0xFF5A3418).copy(alpha = 0.35f), r, c, style = Stroke(0.8.dp.toPx()))
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
            GlyphIcon(glyph, size = 26.dp, animate = false)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Paper.type.bodyStrong.copy(color = Paper.colors.paperInk))
            if (subtitle != null) BasicText(subtitle, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft))
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A paper pill to choose from; the chosen one is turned over (ink-coloured) and lifted. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, swatch: Color? = null) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val bg by animateColorAsState(if (selected) colors.paperInk else colors.paper, label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.paper else colors.paperInk, label = "chipFg")
    Row(
        modifier
            .pressable({ h.tick(); onClick() }, haptic = false, pressed = 0.93f, role = Role.RadioButton)
            .paperSurface(PillShape, level = if (selected) 3 else 1, color = bg)
            .widthIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.caption.copy(color = fg), maxLines = 1)
    }
}

/** The room around the diorama (§4): dusk gathering in the corners at night, lightning flashes. */
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
    if (f > 0.01f) drawRect(Color(0xFFF4F2FF).copy(alpha = 0.16f * f))
}
