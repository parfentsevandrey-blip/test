package app.papersky.weather.design

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.PaperGrain
import app.papersky.weather.scene.ScenePalette
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ---- Time --------------------------------------------------------------------------------------

/** One shared animation clock; drawing code reads it so only the draw phase is invalidated. */
@Stable
class SceneClock {
    val seconds = mutableFloatStateOf(0f)
}

val LocalSceneClock = staticCompositionLocalOf { SceneClock() }
val LocalScenePalette = staticCompositionLocalOf<ScenePalette?> { null }

// ---- Shapes ------------------------------------------------------------------------------------

/**
 * Rounded rectangle with continuous ("squircle") corners: the curvature eases in instead of
 * jumping from straight to circular, which is what makes a card read as crafted, not generic.
 */
class SmoothShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        smoothRect(path, 0f, 0f, size.width, size.height, with(density) { radius.toPx() }, smoothing)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is SmoothShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = radius.hashCode() * 31 + smoothing.hashCode()
}

/** Figma-style corner smoothing for a rectangle. */
fun smoothRect(path: Path, left: Float, top: Float, right: Float, bottom: Float, radius: Float, smoothing: Float) {
    val w = right - left
    val h = bottom - top
    val limit = min(w, h) / 2f
    var r = radius.coerceAtMost(limit)
    var xi = smoothing
    var p = (1 + xi) * r
    if (p > limit) {
        xi = (limit / r - 1).coerceIn(0f, smoothing)
        p = (1 + xi) * r
        if (p > limit) { r = limit; xi = 0f; p = r }
    }
    val arcMeasure = 90f * (1 - xi)
    val arcSection = sin(Math.toRadians(arcMeasure / 2.0)).toFloat() * r * sqrt(2f)
    val angleAlpha = (90f - arcMeasure) / 2f
    val p3p4 = r * tan(Math.toRadians(angleAlpha / 2.0)).toFloat()
    val angleBeta = 45f * xi
    val c = p3p4 * cos(Math.toRadians(angleBeta.toDouble())).toFloat()
    val d = c * tan(Math.toRadians(angleBeta.toDouble())).toFloat()
    val b = (p - arcSection - c - d) / 3f
    val a = 2f * b

    path.reset()
    path.moveTo(left + p, top)
    // Top-right.
    path.lineTo(right - p, top)
    path.cubicTo(right - p + a, top, right - p + a + b, top, right - p + a + b + c, top + d)
    path.arcTo(Rect(right - 2 * r, top, right, top + 2 * r), 270f + angleAlpha, arcMeasure, false)
    path.cubicTo(right, top + p - a - b, right, top + p - a, right, top + p)
    // Bottom-right.
    path.lineTo(right, bottom - p)
    path.cubicTo(right, bottom - p + a, right, bottom - p + a + b, right - d, bottom - p + a + b + c)
    path.arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), angleAlpha, arcMeasure, false)
    path.cubicTo(right - p + a + b, bottom, right - p + a, bottom, right - p, bottom)
    // Bottom-left.
    path.lineTo(left + p, bottom)
    path.cubicTo(left + p - a, bottom, left + p - a - b, bottom, left + p - a - b - c, bottom - d)
    path.arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f + angleAlpha, arcMeasure, false)
    path.cubicTo(left, bottom - p + a + b, left, bottom - p + a, left, bottom - p)
    // Top-left.
    path.lineTo(left, top + p)
    path.cubicTo(left, top + p - a, left, top + p - a - b, left + d, top + p - a - b - c)
    path.arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f + angleAlpha, arcMeasure, false)
    path.cubicTo(left + p - a - b, top, left + p - a, top, left + p, top)
    path.close()
}

val CardShape = SmoothShape(28.dp)
val TileShape = SmoothShape(24.dp)
val PillShape = RoundedCornerShape(50)

// ---- Surfaces ----------------------------------------------------------------------------------

/** Paper fibres printed beneath the content (text stays crisp). Plain alpha blending: cheap. */
fun Modifier.paperGrain(alpha: Float = 0.3f): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(PaperGrain.tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawRect(brush, alpha = alpha)
        drawContent()
    }
}

/**
 * A sheet of heavy paper: a wide ambient shadow plus a tight contact shadow, the fill, fibres and
 * a hairline of light along the top edge where the sheet's thickness catches it.
 */
fun Modifier.paperSheet(color: Color, shape: Shape, shadowColor: Color, lift: Dp = 10.dp, night: Boolean = false): Modifier = this
    .dropShadow(shape, Shadow(radius = lift * 2.2f, color = shadowColor, offset = DpOffset(0.dp, lift * 0.7f), alpha = if (night) 0.9f else 0.55f))
    .dropShadow(shape, Shadow(radius = lift * 0.35f, color = shadowColor, offset = DpOffset(0.dp, lift * 0.12f), alpha = if (night) 0.8f else 0.5f))
    .clip(shape)
    .background(color)
    .paperGrain(if (night) 0.18f else 0.28f)
    .drawWithCache {
        val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = if (night) 0.07f else 0.55f), 1f to Color.Transparent, endY = 2.dp.toPx())
        onDrawWithContent {
            drawContent()
            drawRect(edge, size = Size(size.width, 2.dp.toPx()))
        }
    }

/** Press feedback: a springy dip read in the layer phase, so pressing never recomposes. */
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
    val scale = animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = if (isPressed) 0.9f else 0.42f, stiffness = if (isPressed) 1400f else 420f),
        label = "press",
    )
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

/** Coordinates the entrance of a screen: the first items cascade in; later ones just appear. */
@Stable
class Choreography {
    internal val born = System.nanoTime()
    internal val introOver get() = System.nanoTime() - born > 1_600_000_000L
}

val LocalChoreography = staticCompositionLocalOf { Choreography() }

/**
 * Paper being laid down: rises a little, tilts back flat and fades in, [order] × 70 ms after the
 * screen appears. Animated through layer properties only.
 */
@Composable
fun Modifier.reveal(order: Int): Modifier {
    val choreo = LocalChoreography.current
    val progress = remember { Animatable(if (choreo.introOver) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(order * 70L)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 170f))
        }
    }
    val lift = with(LocalDensity.current) { 46.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = (p * 1.6f).coerceIn(0f, 1f)
            translationY = (1f - p) * lift
            rotationX = (1f - p) * 14f
            scaleX = 0.96f + 0.04f * p
            scaleY = scaleX
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

// ---- Cards -------------------------------------------------------------------------------------

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    color: Color = Paper.colors.paper,
    shape: Shape = CardShape,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick, pressed = 0.98f)
            .paperSheet(color, shape, Paper.colors.shadow, night = Paper.colors.isNight)
            .padding(contentPadding),
        content = content,
    )
}

// ---- Text --------------------------------------------------------------------------------------

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Paper.colors.paperInkSoft) {
    BasicText(text.uppercase(), modifier, style = Paper.type.label.copy(color = color))
}

/** Odometer-style text: each changed character rolls up (increase) or down (decrease). */
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
                    val move = spring<IntOffset>(dampingRatio = 0.72f, stiffness = 300f)
                    (slideInVertically(move) { it * dir * 3 / 5 } + fadeIn(tween(220, delayMillis = index * 30)) + scaleIn(initialScale = 0.9f))
                        .togetherWith(slideOutVertically(move) { -it * dir * 3 / 5 } + fadeOut(tween(140)) + scaleOut(targetScale = 0.9f))
                },
                label = "roll$index",
            ) { c -> BasicText(c.toString(), style = style) }
        }
    }
}

/** Text that develops like ink soaking into paper: a soft edge sweeps across it when it changes. */
@Composable
fun InkReveal(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 3, durationMillis: Int = 900) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(text) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis))
    }
    BasicText(
        text,
        modifier
            .semantics { contentDescription = text }
            .graphicsLayer { compositingStrategy = if (progress.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val p = progress.value
                    if (p < 1f) {
                        val fade = size.width * 0.35f
                        val edge = -fade + (size.width + fade * 2) * p
                        drawRect(
                            Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = edge - fade, endX = edge),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }
            },
        style = style,
        maxLines = maxLines,
    )
}

// ---- Glyphs ------------------------------------------------------------------------------------

/** Rendered glyph bitmaps shared across the app: drawing one is a single texture blit. */
private object GlyphCache {
    private val renderer = GlyphRenderer()
    private val cache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(glyph: Glyph, px: Int, colors: GlyphColors, rotation: Float): ImageBitmap {
        val key = "${glyph.ordinal}|$px|${colors.hashCode()}|${rotation.roundToInt()}"
        cache.get(key)?.let { return it }
        val bmp: Bitmap = synchronized(renderer) { GlyphRenderer.bitmap(glyph, px, colors, rotation) }
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    onPaper: Boolean = true,
    animate: Boolean = false,
    rotation: Float = 0f,
    palette: ScenePalette? = LocalScenePalette.current,
) {
    val colors = remember(palette, onPaper) { palette?.let { GlyphColors.from(it, onPaper) } } ?: return
    if (animate) {
        val renderer = remember { GlyphRenderer() }
        val clock = LocalSceneClock.current
        Canvas(modifier.size(size)) {
            drawIntoCanvas { renderer.draw(it.nativeCanvas, glyph, 0f, 0f, this.size.minDimension, colors, clock.seconds.floatValue, rotation) }
        }
    } else {
        val px = with(LocalDensity.current) { size.roundToPx() }
        val image = remember(glyph, px, colors, rotation) { GlyphCache.get(glyph, px, colors, rotation) }
        Canvas(modifier.size(size)) { drawImage(image) }
    }
}

/** Same cache for code that draws glyphs inside its own canvas. */
fun glyphImage(glyph: Glyph, px: Int, colors: GlyphColors, rotation: Float = 0f): ImageBitmap = GlyphCache.get(glyph, px, colors, rotation)

// ---- Controls ----------------------------------------------------------------------------------

@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    primary: Boolean = true,
) {
    val colors = Paper.colors
    val bg = if (primary) colors.accent else colors.paper
    val fg = if (primary) Color(0xFFFFF8EE) else colors.paperInk
    Row(
        modifier
            .pressable(onClick, pressed = 0.95f)
            .paperSheet(bg, PillShape, colors.shadow, lift = if (primary) 8.dp else 5.dp, night = colors.isNight)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, size = 20.dp, onPaper = !primary)
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.bodyStrong.copy(color = fg, textAlign = TextAlign.Center))
    }
}

@Composable
fun PaperIconButton(glyph: Glyph, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = Paper.colors
    Box(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick, pressed = 0.9f)
            .size(size)
            .paperSheet(colors.paper, CircleShape, colors.shadow, lift = 6.dp, night = colors.isNight),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, size = size * 0.52f, onPaper = true)
    }
}

/** A paper tab sliding in a slot. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val offset = animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 520f), label = "switch")
    val track by androidx.compose.animation.animateColorAsState(if (checked) colors.accent else colors.paperInk.copy(alpha = 0.14f), label = "track")
    Box(
        modifier
            .semantics { stateDescription = if (checked) "on" else "off" }
            .size(52.dp, 30.dp)
            .clip(PillShape)
            .background(track)
            .clickable(role = Role.Switch) { h.toggle(!checked); onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToPx(), 0) }
                .size(24.dp)
                .paperSheet(Color(0xFFFFFCF6), CircleShape, colors.shadow, lift = 3.dp),
        )
    }
}

@Composable
fun PaperSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(SmoothShape(16.dp))
            .background(colors.paperInk.copy(alpha = 0.07f))
            .padding(4.dp),
    ) {
        val segW = maxWidth / options.size
        val x = animateDpAsState(segW * selected, spring(dampingRatio = 0.74f, stiffness = 460f), label = "seg")
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToPx(), 0) }
                .width(segW)
                .height(38.dp)
                .paperSheet(colors.paper, SmoothShape(12.dp), colors.shadow, lift = 4.dp, night = colors.isNight),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(SmoothShape(12.dp))
                        .clickable(role = Role.RadioButton) {
                            if (i != selected) { h.tick(); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = Paper.type.caption.copy(
                            color = if (i == selected) colors.paperInk else colors.paperInkSoft,
                            fontWeight = if (i == selected) androidx.compose.ui.text.font.FontWeight(700) else androidx.compose.ui.text.font.FontWeight(500),
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A track with a paper bead. Snaps to [steps] with a tick per step, like a ratchet. */
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
    val shown = animateFloatAsState(fraction, spring(stiffness = 900f), label = "slider")
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
                detectHorizontalDragGestures(onDragStart = { h.dragStart() }, onDragEnd = { h.gestureEnd() }) { change, _ ->
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
        drawLine(colors.paperInk.copy(alpha = 0.12f), Offset(r, cy), Offset(size.width - r, cy), 5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(colors.accent, Offset(r, cy), Offset(x, cy), 5.dp.toPx(), cap = StrokeCap.Round)
        if (steps > 0) {
            for (i in 0..steps + 1) {
                val sx = r + (size.width - 2 * r) * i / (steps + 1)
                drawCircle(colors.paperInk.copy(alpha = 0.22f), 1.5.dp.toPx(), Offset(sx, cy))
            }
        }
        drawCircle(colors.shadow.copy(alpha = 0.18f), r + 2.dp.toPx(), Offset(x, cy + 2.dp.toPx()))
        drawCircle(Color(0xFFFFFCF6), r, Offset(x, cy))
        drawCircle(colors.accent, r * 0.36f, Offset(x, cy))
        drawCircle(colors.paperInk.copy(alpha = 0.1f), r, Offset(x, cy), style = Stroke(1.dp.toPx()))
    }
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
            GlyphIcon(glyph, size = 26.dp)
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

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, swatch: Color? = null) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val bg by androidx.compose.animation.animateColorAsState(if (selected) colors.paperInk else colors.paper, label = "chipBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) colors.paper else colors.paperInk, label = "chipFg")
    Row(
        modifier
            .pressable({ h.tick(); onClick() }, haptic = false, pressed = 0.93f, role = Role.RadioButton)
            .paperSheet(bg, PillShape, colors.shadow, lift = if (selected) 6.dp else 3.dp, night = colors.isNight)
            .widthIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.caption.copy(color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight(600)), maxLines = 1)
    }
}

/** A hairline rule between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val c = Paper.colors.paperInk.copy(alpha = 0.08f)
    Canvas(modifier.fillMaxWidth().height(1.dp)) { drawLine(c, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
}

internal fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())
