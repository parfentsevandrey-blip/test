package app.papersky.weather.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---- Time --------------------------------------------------------------------------------------

/** One shared animation clock; drawing code reads it so only the draw phase is invalidated. */
@Stable
class SceneClock {
    val seconds = mutableFloatStateOf(0f)
}

val LocalSceneClock = staticCompositionLocalOf { SceneClock() }
val LocalScenePalette = staticCompositionLocalOf<ScenePalette?> { null }

// ---- Shapes & surfaces -------------------------------------------------------------------------

/** Rounded rectangle whose edges wobble slightly, like torn/deckled paper. */
class DeckleShape(private val seed: Int = 1, private val corner: Dp = 18.dp, private val roughness: Dp = 0.9.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(min(size.width, size.height) / 2)
        val j = with(density) { roughness.toPx() }
        val step = with(density) { 9.dp.toPx() }
        fun wob(i: Int) = (rnd(i, seed) - 0.5f) * 2f * j
        val p = Path()
        var i = 0
        val w = size.width
        val h = size.height
        p.moveTo(r, wob(i++))
        var x = r
        while (x < w - r) { x += step; p.lineTo(min(x, w - r), wob(i++)) }
        p.quadraticTo(w, 0f, w, r)
        var y = r
        while (y < h - r) { y += step; p.lineTo(w + wob(i++), min(y, h - r)) }
        p.quadraticTo(w, h, w - r, h)
        x = w - r
        while (x > r) { x -= step; p.lineTo(max(x, r), h + wob(i++)) }
        p.quadraticTo(0f, h, 0f, h - r)
        y = h - r
        while (y > r) { y -= step; p.lineTo(wob(i++), max(y, r)) }
        p.quadraticTo(0f, 0f, r, 0f)
        p.close()
        return Outline.Generic(p)
    }

    private fun rnd(i: Int, s: Int): Float {
        var v = i * 374_761_393 + s * 668_265_263
        v = (v xor (v ushr 13)) * 1_274_126_177
        v = v xor (v ushr 16)
        return (v and 0xFFFFFF) / 16_777_216f
    }
}

/** Paper fibres printed onto the surface below the content (text stays crisp). */
fun Modifier.paperGrain(alpha: Float = 0.35f): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(PaperGrain.tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawRect(brush, alpha = alpha, blendMode = BlendMode.Overlay)
        drawContent()
    }
}

/** A sheet of paper: soft shadow, fill, grain. */
fun Modifier.paperSheet(color: Color, shape: Shape, shadowColor: Color, lift: Dp = 10.dp): Modifier = this
    .dropShadow(shape, Shadow(radius = lift, color = shadowColor, offset = DpOffset(0.dp, lift / 3), alpha = 0.9f))
    .clip(shape)
    .background(color)
    .paperGrain(0.45f)

@Composable
private fun rememberPressScale(interaction: MutableInteractionSource, pressed: Float): Float {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return scale
}

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
    val scale = rememberPressScale(interaction, pressed)
    val h = rememberHaptics()
    val click by rememberUpdatedState(onClick)
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
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

// ---- Cards -------------------------------------------------------------------------------------

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    seed: Int = 1,
    tilt: Float = 0f,
    tape: Boolean = false,
    tapeColor: Color = Paper.colors.tape,
    color: Color = Paper.colors.paper,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(seed) { DeckleShape(seed) }
    Box(modifier.graphicsLayer { rotationZ = tilt }) {
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(onClick, pressed = 0.98f)
                .paperSheet(color, shape, Paper.colors.shadow)
                .padding(contentPadding),
            content = content,
        )
        if (tape) {
            WashiTape(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 22.dp + ((seed % 5) * 9).dp, y = (-7).dp),
                color = tapeColor,
                angle = if (seed % 2 == 0) -8f else 6f,
            )
        }
    }
}

@Composable
fun WashiTape(modifier: Modifier = Modifier, color: Color = Paper.colors.tape, angle: Float = -6f, length: Dp = 58.dp) {
    Canvas(modifier.size(length, 16.dp)) {
        rotate(angle) {
            val th = size.height * 0.8f
            val top = (size.height - th) / 2
            val path = Path()
            val teeth = 5
            path.moveTo(0f, top)
            path.lineTo(size.width, top)
            for (k in 1..teeth) path.lineTo(size.width + if (k % 2 == 1) 2.dp.toPx() else 0f, top + th * k / teeth)
            path.lineTo(0f, top + th)
            for (k in teeth - 1 downTo 0) path.lineTo(if (k % 2 == 1) -2.dp.toPx() else 0f, top + th * k / teeth)
            path.close()
            drawPath(path, color.copy(alpha = 0.85f))
            var x = 5.dp.toPx()
            while (x < size.width - 3.dp.toPx()) {
                drawCircle(Color.White.copy(alpha = 0.38f), 1.3.dp.toPx(), Offset(x, top + th * 0.32f))
                drawCircle(Color.White.copy(alpha = 0.38f), 1.3.dp.toPx(), Offset(x + 3.5.dp.toPx(), top + th * 0.7f))
                x += 7.dp.toPx()
            }
        }
    }
}

// ---- Text ----------------------------------------------------------------------------------------

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
        // Pad from the right so units stay put while digits roll.
        text.forEachIndexed { index, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val dir = if (goingUp) 1 else -1
                    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it * dir } + fadeIn(tween(160)))
                        .togetherWith(slideOutVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { -it * dir } + fadeOut(tween(120)))
                },
                label = "roll$index",
            ) { c -> BasicText(c.toString(), style = style) }
        }
    }
}

/** Handwritten text that "writes itself" from left to right whenever it changes. */
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
            .drawWithCache {
                onDrawWithContent {
                    clipRect(right = size.width * min(1f, progress.value * 1.15f)) {
                        this@onDrawWithContent.drawContent()
                    }
                }
            },
        style = style,
        maxLines = maxLines,
    )
}

// ---- Glyphs --------------------------------------------------------------------------------------

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
    val renderer = remember { GlyphRenderer() }
    val clock = LocalSceneClock.current
    val colors = remember(palette, onPaper) { palette?.let { GlyphColors.from(it, onPaper) } }
    Canvas(modifier.size(size)) {
        val c = colors ?: return@Canvas
        val t = if (animate) clock.seconds.floatValue else 0f
        drawIntoCanvas { renderer.draw(it.nativeCanvas, glyph, 0f, 0f, this.size.minDimension, c, t, rotation) }
    }
}

// ---- Controls ------------------------------------------------------------------------------------

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
    val shape = remember { DeckleShape(seed = text.hashCode(), corner = 22.dp, roughness = 0.6.dp) }
    Row(
        modifier
            .pressable(onClick, pressed = 0.94f)
            .paperSheet(bg, shape, colors.shadow, lift = if (primary) 8.dp else 5.dp)
            .padding(horizontal = 20.dp, vertical = 13.dp),
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
            .paperSheet(colors.paper, CircleShape, colors.shadow, lift = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, size = size * 0.52f, onPaper = true, animate = false)
    }
}

/** A paper tab sliding in a slot. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val offset by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "switch")
    val track by androidx.compose.animation.animateColorAsState(if (checked) colors.accent else colors.paperInk.copy(alpha = 0.16f), label = "track")
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
                .paperSheet(colors.paper, CircleShape, colors.shadow, lift = 3.dp),
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
            .clip(RoundedCornerShape(16.dp))
            .background(colors.paperInk.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.7f, stiffness = 420f), label = "seg")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segW)
                .height(38.dp)
                .paperSheet(colors.paper, RoundedCornerShape(12.dp), colors.shadow, lift = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) {
                            if (i != selected) { h.tick(); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = Paper.type.caption.copy(
                            color = if (i == selected) colors.paperInk else colors.paperInkSoft,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A ribbon with a wooden bead. Snaps to [steps] with a tick per step so it feels like a
 * ratchet under the finger.
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
    val shown by animateFloatAsState(fraction, spring(stiffness = 900f), label = "slider")
    fun quantize(f: Float): Float {
        val raw = range.start + f.coerceIn(0f, 1f) * (range.endInclusive - range.start)
        if (steps <= 0) return raw
        val stepSize = (range.endInclusive - range.start) / (steps + 1)
        return (range.start + ((raw - range.start) / stepSize).roundToInt() * stepSize).coerceIn(range.start, range.endInclusive)
    }
    Box(
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
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val cy = size.height / 2
            val r = 11.dp.toPx()
            val x = r + (size.width - 2 * r) * shown
            drawLine(colors.paperInk.copy(alpha = 0.14f), Offset(r, cy), Offset(size.width - r, cy), 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(colors.accent, Offset(r, cy), Offset(x, cy), 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            if (steps > 0) {
                for (i in 0..steps + 1) {
                    val sx = r + (size.width - 2 * r) * i / (steps + 1)
                    drawCircle(colors.paperInk.copy(alpha = 0.25f), 1.6.dp.toPx(), Offset(sx, cy))
                }
            }
            drawCircle(colors.shadow.copy(alpha = 0.35f), r, Offset(x, cy + 2.dp.toPx()))
            drawCircle(Color(0xFFC98A56), r, Offset(x, cy))
            drawCircle(Color(0xFFE3B283), r * 0.55f, Offset(x - r * 0.2f, cy - r * 0.2f))
            drawCircle(colors.paperInk.copy(alpha = 0.25f), r, Offset(x, cy), style = Stroke(1.dp.toPx()))
        }
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

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, swatch: Color? = null) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val bg by androidx.compose.animation.animateColorAsState(if (selected) colors.paperInk else colors.paper, label = "chipBg")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) colors.paper else colors.paperInk, label = "chipFg")
    Row(
        modifier
            .pressable({ h.tick(); onClick() }, haptic = false, pressed = 0.93f, role = Role.RadioButton)
            .paperSheet(bg, RoundedCornerShape(50), colors.shadow, lift = if (selected) 6.dp else 3.dp)
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

internal fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())
