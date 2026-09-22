package app.papersky.weather.design

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.papersky.weather.scene.Glyph
import app.papersky.weather.scene.GlyphColors
import app.papersky.weather.scene.GlyphRenderer
import app.papersky.weather.scene.MaterialTextures
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

    /** A lightning flash lighting up the whole room, 0..1 (§4.4). */
    val flash = mutableFloatStateOf(0f)
}

val LocalSceneClock = staticCompositionLocalOf { SceneClock() }
val LocalScenePalette = staticCompositionLocalOf<ScenePalette?> { null }

val PillShape = RoundedCornerShape(50)

// ---- Touch -------------------------------------------------------------------------------------

/** Pressing sinks a thing into the table (spring `press`), letting go bounces it back (`release`). */
@Composable
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    haptic: Boolean = true,
    pressed: Float = 0.97f,
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
 * A sheet laid down on the table (§9): it comes in from a little above, slightly turned and a touch
 * larger, and settles onto the table on the `settle` spring. [order] × 70 ms after the screen opens.
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
            scaleX = 1f + 0.035f * q
            scaleY = scaleX
            rotationZ = 2.5f * q
            translationY = -drop * q
        }
    }
}

// ---- Sheets ------------------------------------------------------------------------------------

private fun cornerFor(stock: Stock): Dp = when (stock) {
    Stock.Graph -> 14.dp
    Stock.IndexCard -> 10.dp
    Stock.Chipboard -> 12.dp
    Stock.Postcard, Stock.Polaroid -> 6.dp
    else -> 18.dp
}

/**
 * A sheet of real paper (§8): stock, deckled or cut edge, a slight turn, optionally held by washi
 * tape or a push pin — and then it sways on the tape when tapped.
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    stock: Stock = Stock.Cotton,
    seed: Int = 1,
    tilt: Float = tiltFor(seed, 0.6f),
    deckle: Boolean = stock == Stock.Cotton || stock == Stock.Notebook || stock == Stock.Kraft,
    perforated: Boolean = false,
    tape: Boolean = false,
    pin: Boolean = false,
    level: Int = 2,
    onClick: (() -> Unit)? = null,
    wiggleOnTap: Boolean = onClick == null && (tape || pin),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    decoration: (DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(seed, stock, deckle, perforated) {
        if (deckle || perforated) DeckleShape(seed, corner = cornerFor(stock), perforatedTop = perforated) else RoundedCornerShape(cornerFor(stock))
    }
    val sway = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val anchor = 0.16f + (seed % 5) * 0.04f
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
            transformOrigin = TransformOrigin(anchor, 0f)
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(click, haptic = onClick != null, pressed = 0.985f)
                .material(stock, shape, level)
                .then(if (decoration != null) Modifier.drawBehind(decoration) else Modifier)
                .padding(contentPadding),
            content = content,
        )
        if (tape) {
            WashiTape(
                Modifier.align(Alignment.TopStart).offset(x = 22.dp + ((((seed % 5) + 5) % 5) * 4.5f).dp, y = (-9).dp),
                seed = seed,
            )
        }
        if (pin) PushPin(Modifier.align(Alignment.TopCenter).offset(y = (-7).dp))
    }
}

/** Washi tape: translucent, patterned, torn at both ends; light shines through it. */
@Composable
fun WashiTape(modifier: Modifier = Modifier, seed: Int = 1, length: Dp = 58.dp, angle: Float = if (seed % 2 == 0) -8f else 6.5f) {
    val light = Paper.light
    val color = light.lit(Ink.Tapes[((seed % Ink.Tapes.size) + Ink.Tapes.size) % Ink.Tapes.size])
    val pattern = ((seed / 3) % 3 + 3) % 3
    Canvas(modifier.size(length, 20.dp)) {
        rotate(angle) {
            val th = 16.dp.toPx()
            val top = (size.height - th) / 2
            val tooth = 1.6.dp.toPx()
            val path = Path().apply {
                moveTo(0f, top)
                lineTo(size.width, top)
                for (k in 1..5) lineTo(size.width + if (k % 2 == 1) tooth else 0f, top + th * k / 5)
                lineTo(0f, top + th)
                for (k in 4 downTo 0) lineTo(if (k % 2 == 1) -tooth else 0f, top + th * k / 5)
                close()
            }
            // A whisper of shadow: tape is thin but not weightless.
            drawIntoCanvas { c ->
                c.save(); c.translate(0.6.dp.toPx(), 1.dp.toPx())
                drawPath(path, Ink.Shadow.copy(alpha = 0.12f))
                c.restore()
            }
            drawPath(path, color.copy(alpha = 0.82f))
            clipPath(path) {
                when (pattern) {
                    0 -> {
                        var x = 5.dp.toPx()
                        while (x < size.width) {
                            drawCircle(Color.White.copy(alpha = 0.42f), 1.4.dp.toPx(), Offset(x, top + th * 0.3f))
                            drawCircle(Color.White.copy(alpha = 0.42f), 1.4.dp.toPx(), Offset(x + 3.5.dp.toPx(), top + th * 0.72f))
                            x += 7.dp.toPx()
                        }
                    }
                    1 -> {
                        var x = -th
                        while (x < size.width + th) {
                            drawLine(Color.White.copy(alpha = 0.32f), Offset(x, top + th), Offset(x + th, top), 2.2.dp.toPx())
                            x += 6.dp.toPx()
                        }
                    }
                    else -> {
                        val step = 4.5.dp.toPx()
                        var x = 0f
                        while (x < size.width) { drawLine(Color.Black.copy(alpha = 0.08f), Offset(x, top), Offset(x, top + th), 1f); x += step }
                        var y = top
                        while (y < top + th) { drawLine(Color.Black.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    }
                }
                drawRect(Stock.Cotton.brush, alpha = 0.35f)
                drawRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(0f, top), size = Size(size.width, th * 0.28f))
            }
        }
    }
}

/** A brass push pin: domed head, rim, glint top-left, shadow down-right. */
@Composable
fun PushPin(modifier: Modifier = Modifier, color: Color = Color(0xFFC9A24A)) {
    Canvas(modifier.size(22.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = 7.dp.toPx()
        drawCircle(Ink.Shadow.copy(alpha = 0.18f), r * 1.15f, c + Offset(1.8.dp.toPx(), 3.2.dp.toPx()))
        drawCircle(Ink.Shadow.copy(alpha = 0.22f), r * 0.9f, c + Offset(1.2.dp.toPx(), 2.2.dp.toPx()))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFF1C2), color, Color(0xFF7A5A1C)), center = c + Offset(-r * 0.4f, -r * 0.45f), radius = r * 1.5f),
            r, c,
        )
        drawCircle(Color(0xFF5A3F10).copy(alpha = 0.35f), r, c, style = Stroke(0.8.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, c + Offset(-r * 0.38f, -r * 0.4f))
    }
}

/** Kraft luggage tag: chamfered left end with a brass eyelet. */
class TagShape(private val chamfer: Dp = 11.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { chamfer.toPx() }.coerceAtMost(size.height / 2)
        val r = with(density) { 4.dp.toPx() }
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

/** The eyelet on a tag; draw it at the tag's left end. */
fun DrawScope.eyelet(center: Offset) {
    val r = 4.dp.toPx()
    drawCircle(Ink.Shadow.copy(alpha = 0.25f), r + 1.dp.toPx(), center + Offset(0.5.dp.toPx(), 1.dp.toPx()))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF6DE9A), Color(0xFFB08A34)), center = center - Offset(r * 0.4f, r * 0.4f), radius = r * 1.6f), r, center)
    drawCircle(Color(0xFF3A2A1A).copy(alpha = 0.75f), r * 0.5f, center)
}

/** Tracing paper (vellum, §3): translucent white over whatever it lies on, a faint fibre. */
fun Modifier.vellum(shape: Shape): Modifier = this
    .shadowOf(shape, Elevation.Resting)
    .clip(shape)
    .drawBehind {
        drawRect(Color.White.copy(alpha = 0.5f))
        drawRect(Stock.Cotton.brush, alpha = 0.35f)
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.25f), 0.04f to Color.Transparent))
    }

// ---- Postcards ---------------------------------------------------------------------------------

/** A stamp's edge: a rectangle bitten by perforation holes (§8: teeth 3 dp). */
class StampShape(private val hole: Dp = 3.dp, private val pitch: Dp = 5.5.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { hole.toPx() } / 2
        val step = with(density) { pitch.toPx() }
        val rect = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }
        val holes = Path()
        fun hole(x: Float, y: Float) = holes.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), r))
        val nx = (size.width / step).roundToInt().coerceAtLeast(1)
        val ny = (size.height / step).roundToInt().coerceAtLeast(1)
        for (i in 0..nx) { val x = size.width * i / nx; hole(x, 0f); hole(x, size.height) }
        for (j in 1 until ny) { val y = size.height * j / ny; hole(0f, y); hole(size.width, y) }
        return Outline.Generic(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, rect, holes))
    }
}

/** A postage stamp: perforated white paper with a little picture in it, slightly askew. */
@Composable
fun Stamp(modifier: Modifier = Modifier, seed: Int = 1, picture: @Composable () -> Unit) {
    Box(
        modifier
            .size(56.dp, 64.dp)
            .graphicsLayer { rotationZ = tiltFor(seed * 3 + 1, 4f) }
            .material(Stock.Cotton, StampShape(), level = 1)
            .padding(5.dp)
            .clip(RoundedCornerShape(1.dp)),
    ) { picture() }
}

/** A postmark: a double ring with the post office's name around it, the date in the middle, and wavy cancellation lines. */
@Composable
fun Postmark(text: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val paint = remember(context) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            val base = androidx.core.content.res.ResourcesCompat.getFont(context, app.papersky.weather.R.font.manrope)
            typeface = android.graphics.Typeface.create(base, 800, false)
            textAlign = android.graphics.Paint.Align.CENTER
            letterSpacing = 0.08f
        }
    }
    val date = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy")) }
    Canvas(modifier.size(118.dp, 54.dp).graphicsLayer { rotationZ = -9f }) {
        val ink = Ink.Graphite.copy(alpha = 0.4f)
        val c = Offset(size.width - 27.dp.toPx(), size.height / 2)
        val outer = 24.dp.toPx()
        val inner = 17.5.dp.toPx()
        drawCircle(ink, outer, c, style = Stroke(1.3.dp.toPx()))
        drawCircle(ink, inner, c, style = Stroke(0.9.dp.toPx()))
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            paint.color = ink.toArgb()
            paint.textSize = 6.5.dp.toPx()
            val top = android.graphics.Path().apply { addArc(c.x - inner - 1.dp.toPx(), c.y - inner - 1.dp.toPx(), c.x + inner + 1.dp.toPx(), c.y + inner + 1.dp.toPx(), 180f, 180f) }
            nc.drawTextOnPath("PAPERSKY", top, 0f, 0f, paint)
            val r = outer - 1.5.dp.toPx()
            val bottom = android.graphics.Path().apply { addArc(c.x - r, c.y - r, c.x + r, c.y + r, 180f, -180f) }
            nc.drawTextOnPath(text.uppercase(), bottom, 0f, 0f, paint)
            paint.textSize = 7.5.dp.toPx()
            nc.drawText(date, c.x, c.y + 2.7.dp.toPx(), paint)
        }
        val waveW = size.width - 54.dp.toPx()
        for (k in 0 until 4) {
            val y = size.height / 2 + (k - 1.5f) * 7.dp.toPx()
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x < waveW) {
                    quadraticTo(x + 4.dp.toPx(), y - 2.5.dp.toPx(), x + 8.dp.toPx(), y)
                    quadraticTo(x + 12.dp.toPx(), y + 2.5.dp.toPx(), x + 16.dp.toPx(), y)
                    x += 16.dp.toPx()
                }
            }
            drawPath(path, ink, style = Stroke(1.1.dp.toPx()))
        }
    }
}

/**
 * A postcard (§8): thick card, a message on the left, a stamp with a postmark on the right; the
 * [body] (a picture, a widget) runs across the whole card underneath.
 */
@Composable
fun Postcard(
    modifier: Modifier = Modifier,
    seed: Int = 1,
    onClick: (() -> Unit)? = null,
    postmark: String? = null,
    stamp: (@Composable () -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit,
) {
    PaperSheet(modifier, stock = Stock.Postcard, seed = seed, tilt = tiltFor(seed, 1f), onClick = onClick, contentPadding = PaddingValues(18.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(end = if (stamp != null) 76.dp else 0.dp), content = header)
            if (stamp != null) {
                Stamp(Modifier.align(Alignment.TopEnd), seed, stamp)
                if (postmark != null) Postmark(postmark, Modifier.align(Alignment.TopEnd).offset(x = (-28).dp, y = 40.dp))
            }
        }
        if (body != null) {
            Spacer(Modifier.height(14.dp))
            body()
        }
    }
}

// ---- Text --------------------------------------------------------------------------------------

/** A printed caption in small caps, pressed into its material. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Paper.colors.paperInkSoft, onDark: Boolean = false) {
    BasicText(text.uppercase(), modifier, style = Paper.type.label.copy(color = color).pressed(onDark))
}

/** Odometer text: each changed character rolls up (increase) or down (decrease) on the `digit` spring. */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, numericValue: Double? = null, cell: @Composable (Char) -> Unit = { BasicText(it.toString(), style = style) }) {
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
            ) { c -> cell(c) }
        }
    }
}

/** Card-stock swatches (base colour + cotton fibres) for text that is *cut out of paper*. */
private object Cardstock {
    private val cache = LruCache<Int, ShaderBrush>(8)

    fun brush(color: Color): ShaderBrush {
        val key = color.toArgb()
        cache.get(key)?.let { return it }
        val size = MaterialTextures.SIZE
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(key)
        c.drawBitmap(MaterialTextures.cotton, 0f, 0f, null)
        return ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)).also { cache.put(key, it) }
    }
}

/**
 * Letters cut from card and stood in the diorama (§6 Hero): a fibrous face, a darker 2 dp edge
 * where the card's thickness shows, and a shadow falling down-right onto the sky.
 */
@Composable
fun CutText(text: String, style: TextStyle, face: Color, modifier: Modifier = Modifier) {
    val edge = Color(face.red * 0.72f, face.green * 0.68f, face.blue * 0.62f, 1f)
    val brush = remember(face) { Cardstock.brush(face) }
    val shadow = androidx.compose.ui.graphics.Shadow(Ink.Shadow.copy(alpha = 0.42f), Offset(5f, 12f), 22f)
    Box(modifier) {
        BasicText(text, Modifier.offset(x = 0.6.dp, y = 2.2.dp), style = style.copy(color = edge, shadow = shadow))
        BasicText(text, style = style.copy(brush = brush, shadow = null))
    }
}

/** Handwriting in blue ink that writes itself from left to right whenever it changes (§9). */
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

/** Rendered stickers shared across the app: drawing one is a single texture blit. */
private object GlyphCache {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(glyph: Glyph, px: Int, colors: GlyphColors, rotation: Float, sticker: Boolean, animated: Boolean, border: Int): ImageBitmap {
        val key = "${glyph.ordinal}|$px|${colors.hashCode()}|${rotation.roundToInt()}|$sticker|$animated|$border"
        cache.get(key)?.let { return it }
        val bmp: Bitmap = if (sticker) GlyphRenderer.sticker(glyph, px, colors, rotation, animated, border) else GlyphRenderer.bitmap(glyph, px, colors, rotation)
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }
}

/** For canvases that lay out many stickers themselves (the hourly ribbon). */
fun stickerImage(glyph: Glyph, px: Int, colors: GlyphColors, animated: Boolean, border: Int, rotation: Float = 0f): ImageBitmap =
    GlyphCache.get(glyph, px, colors, rotation, sticker = true, animated = animated, border = border)

/**
 * A weather sticker (§7): die-cut body with a white border and shadow, baked once; its moving
 * parts (rays, drops, flakes, sparkles, bolt, mist) drawn over it every frame while [animate].
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    onPaper: Boolean = true,
    animate: Boolean = true,
    rotation: Float = 0f,
    sticker: Boolean = true,
    palette: ScenePalette? = LocalScenePalette.current,
) {
    val colors = remember(palette, onPaper) { palette?.let { GlyphColors.from(it, onPaper) } } ?: return
    val border = Paper.light.lit(Color(0xFFFFFCF4)).toArgb()
    val px = with(LocalDensity.current) { size.roundToPx() }
    val moving = animate && sticker && glyph in GlyphRenderer.ANIMATED
    val image = remember(glyph, px, colors, rotation, sticker, moving, border) { GlyphCache.get(glyph, px, colors, rotation, sticker, moving, border) }
    if (!moving) {
        Canvas(modifier.size(size)) { drawImage(image) }
        return
    }
    val renderer = remember { GlyphRenderer() }
    val clock = LocalSceneClock.current
    Canvas(modifier.size(size)) {
        drawImage(image)
        val art = this.size.minDimension * GlyphRenderer.STICKER_ART
        val inset = (this.size.minDimension - art) / 2
        drawIntoCanvas {
            renderer.draw(it.nativeCanvas, glyph, inset, inset, art, colors, clock.seconds.floatValue + 0.01f, rotation, GlyphRenderer.Pass.Motion)
        }
    }
}

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
    val stock = if (primary) Stock.Terracotta else Stock.Cotton
    val fg = if (primary) Color(0xFFFFF6E8) else colors.paperInk
    Row(
        modifier
            .pressable(onClick, pressed = 0.96f)
            .material(stock, PillShape, level = 2)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, size = 22.dp, onPaper = !primary, animate = false)
            Spacer(Modifier.width(8.dp))
        }
        BasicText(text, style = Paper.type.bodyStrong.copy(color = fg, textAlign = TextAlign.Center).pressed(onDark = primary))
    }
}

/** A round button: a cotton disc with its icon pressed into it (deboss). */
@Composable
fun DiscButton(icon: PaperIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val colors = Paper.colors
    Box(
        modifier
            .semantics { contentDescription = description }
            .pressable(onClick, pressed = 0.9f)
            .size(size)
            .material(Stock.Cotton, CircleShape, level = 2),
        contentAlignment = Alignment.Center,
    ) {
        Deboss(icon, colors.paperInk, size * 0.5f)
    }
}

/** An icon pressed into paper: a pale rim under a slightly faded ink. */
@Composable
fun Deboss(icon: PaperIcon, ink: Color, size: Dp) {
    Box {
        PaperIconView(icon, Color.White.copy(alpha = 0.75f), Modifier.offset(y = 1.dp), size = size)
        PaperIconView(icon, ink.copy(alpha = 0.82f), size = size)
    }
}

@Composable
fun PaperIconButton(glyph: Glyph, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .pressable(onClick, pressed = 0.9f)
            .size(size)
            .material(Stock.Cotton, CircleShape, level = 2),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, size = size * 0.62f, onPaper = true, animate = false, sticker = false)
    }
}

/** A groove pressed into card: inner shadow along the top, a pale lip along the bottom. */
fun DrawScope.groove(fill: Color, shape: Shape) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { when (outline) {
        is Outline.Generic -> addPath(outline.path)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Rectangle -> addRect(outline.rect)
    } }
    clipPath(path) {
        drawRect(fill)
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), endY = 7.dp.toPx()))
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent), endX = 6.dp.toPx()))
    }
    drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()), alpha = 0.6f)
}

/** iOS-6-style switch: a paper tab sliding in a groove; the groove shows terracotta paper when on. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val light = Paper.light
    val h = rememberHaptics()
    val offset = animateDpAsState(if (checked) 22.dp else 0.dp, Motion.snap(), label = "switch")
    val on = animateFloatAsState(if (checked) 1f else 0f, Motion.snap(), label = "switchOn")
    val offFill = light.lit(Color(0xFFD9CFBE))
    val onFill = light.lit(Stock.Terracotta.base)
    Box(
        modifier
            .semantics { stateDescription = if (checked) "on" else "off" }
            .size(52.dp, 30.dp)
            .drawBehind { groove(androidx.compose.ui.graphics.lerp(offFill, onFill, on.value), PillShape) }
            .clickable(role = Role.Switch) { h.toggle(!checked); onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToPx(), 0) }
                .size(26.dp)
                .material(Stock.Cotton, CircleShape, level = 1),
        )
    }
}

/** Paper tabs in a groove; the chosen one is lifted out and slides on the `snap` spring. */
@Composable
fun PaperSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Paper.colors
    val light = Paper.light
    val h = rememberHaptics()
    val shape = RoundedCornerShape(14.dp)
    val grooveFill = light.lit(Color(0xFFE3DACB))
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .drawBehind { groove(grooveFill, shape) }
            .padding(4.dp),
    ) {
        val segW = maxWidth / options.size
        val x = animateDpAsState(segW * selected, Motion.snap(), label = "seg")
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToPx(), 0) }
                .width(segW)
                .height(38.dp)
                .material(Stock.Cotton, RoundedCornerShape(11.dp), level = 1),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(role = Role.RadioButton) { if (i != selected) { h.tick(); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label,
                        style = Paper.type.caption.copy(
                            color = if (i == selected) colors.paperInk else colors.paperInkSoft,
                            fontWeight = FontWeight(if (i == selected) 800 else 600),
                            textAlign = TextAlign.Center,
                        ).pressed(),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A woven ribbon with a beech bead; snaps to [steps] with a tick per step (§8). */
@Composable
fun PaperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val colors = Paper.colors
    val light = Paper.light
    val h = rememberHaptics()
    val latest by rememberUpdatedState(value)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val shown = animateFloatAsState(fraction, Motion.snap(), label = "slider")
    val ribbon = light.lit(Color(0xFFE2D6C2))
    val filled = light.lit(Stock.Terracotta.base)
    // The bead lifts off the ribbon while it is held (spring `lift`).
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
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
            .height(40.dp)
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
        val rh = 7.dp.toPx()
        // Ribbon, woven, with a groove shadow under it.
        drawRoundRect(Ink.Shadow.copy(alpha = 0.12f), Offset(r, cy - rh / 2 + 1.5.dp.toPx()), Size(size.width - 2 * r, rh), androidx.compose.ui.geometry.CornerRadius(rh / 2))
        drawRoundRect(ribbon, Offset(r, cy - rh / 2), Size(size.width - 2 * r, rh), androidx.compose.ui.geometry.CornerRadius(rh / 2))
        drawRoundRect(filled, Offset(r, cy - rh / 2), Size(x - r, rh), androidx.compose.ui.geometry.CornerRadius(rh / 2))
        drawRoundRect(Stock.Linen.brush, Offset(r, cy - rh / 2), Size(size.width - 2 * r, rh), androidx.compose.ui.geometry.CornerRadius(rh / 2), alpha = 0.9f)
        // Stitches marking the steps.
        if (steps > 0) {
            for (i in 0..steps + 1) {
                val sx = r + (size.width - 2 * r) * i / (steps + 1)
                drawLine(colors.paperInk.copy(alpha = 0.3f), Offset(sx, cy - rh), Offset(sx, cy - rh * 0.7f), 1.2.dp.toPx(), StrokeCap.Round)
            }
        }
        val l = lift.value
        if (l > 0.01f) drawCircle(Ink.Shadow.copy(alpha = 0.16f * l), r * 1.1f, Offset(x + r * 0.35f * l, cy + r * 0.7f * l))
        beechBead(Offset(x, cy - 2.dp.toPx() * l), r * (1f + 0.08f * l))
    }
}

/** A beech bead with growth rings, glint top-left, shadow down-right. */
fun DrawScope.beechBead(c: Offset, r: Float) {
    drawCircle(Ink.Shadow.copy(alpha = 0.28f), r, c + Offset(r * 0.18f, r * 0.3f))
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
            GlyphIcon(glyph, size = 30.dp, animate = false)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Paper.type.bodyStrong.copy(color = Paper.colors.paperInk).pressed())
            if (subtitle != null) BasicText(subtitle, style = Paper.type.caption.copy(color = Paper.colors.paperInkSoft).pressed())
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** A kraft tag to choose from; the chosen one is turned face-up (terracotta) and lifted. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, swatch: Color? = null) {
    val colors = Paper.colors
    val h = rememberHaptics()
    val lift = animateFloatAsState(if (selected) 1f else 0f, Motion.lift(), label = "chipLift")
    Row(
        modifier
            .graphicsLayer { translationY = -lift.value * 2.dp.toPx() }
            .pressable({ h.tick(); onClick() }, haptic = false, pressed = 0.93f, role = Role.RadioButton)
            .material(if (selected) Stock.Terracotta else Stock.Kraft, TagShape(8.dp), level = if (selected) 2 else 1)
            .drawBehind { eyelet(Offset(10.dp.toPx(), size.height / 2)) }
            .widthIn(min = 44.dp)
            .padding(start = 21.dp, end = 13.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).drawBehind { drawCircle(swatch) })
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text,
            style = Paper.type.caption.copy(color = if (selected) Color(0xFFFFF6E8) else Color(0xFF3B2A1A), fontWeight = FontWeight(700)).pressed(onDark = selected),
            maxLines = 1,
        )
    }
}

/** A pencil rule between rows. */
@Composable
fun PencilRule(modifier: Modifier = Modifier) {
    val c = Paper.colors.paperInk.copy(alpha = 0.14f)
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(c, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
    }
}

/** Notebook ruling: blue lines every [step] from [top] and a red margin (§3). */
fun DrawScope.notebookRuling(top: Float, step: Float, margin: Float) {
    var y = top
    while (y < size.height) {
        drawLine(Color(0xFF9DB6D8).copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step
    }
    drawLine(Color(0xFFE07B7B).copy(alpha = 0.8f), Offset(margin, 0f), Offset(margin, size.height), 1.2.dp.toPx())
}

/** Graph paper: fine 5 dp and bold 25 dp grid in green ink (§3). */
fun DrawScope.graphRuling(fine: Float = 5.dp.toPx()) {
    val ink = Color(0xFF6FA58C)
    var i = 0
    var x = 0f
    while (x < size.width) {
        drawLine(ink.copy(alpha = if (i % 5 == 0) 0.2f else 0.1f), Offset(x, 0f), Offset(x, size.height), if (i % 5 == 0) 1.dp.toPx() else 0.6.dp.toPx())
        x += fine; i++
    }
    i = 0
    var y = 0f
    while (y < size.height) {
        drawLine(ink.copy(alpha = if (i % 5 == 0) 0.2f else 0.1f), Offset(0f, y), Offset(size.width, y), if (i % 5 == 0) 1.dp.toPx() else 0.6.dp.toPx())
        y += fine; i++
    }
}

/** Index card: a red heading rule and blue rules under the rows. */
fun DrawScope.indexRuling(headingY: Float, rowTops: List<Float>) {
    drawLine(Color(0xFFD9655B).copy(alpha = 0.75f), Offset(0f, headingY), Offset(size.width, headingY), 1.3.dp.toPx())
    for (y in rowTops) drawLine(Color(0xFF9DB6D8).copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

/** The room's light over everything (§4.3): lamp pool, corners in shade, lightning flashes. */
fun Modifier.roomLight(light: () -> Light, flash: () -> Float): Modifier = drawWithContent {
    drawContent()
    val l = light()
    if (l.lamp > 0.01f) {
        drawRect(
            Brush.radialGradient(
                listOf(Light.LAMP_POOL.copy(alpha = 0.1f * l.lamp), Color.Transparent),
                center = Offset(size.width * 0.35f, size.height * 0.55f), radius = size.width * 0.95f,
            ),
        )
    }
    val v = l.vignette
    if (v > 0.01f) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xFF120A04).copy(alpha = v),
                center = Offset(size.width * 0.5f, size.height * 0.5f), radius = maxOf(size.width, size.height) * 0.75f,
            ),
        )
    }
    val f = flash()
    if (f > 0.01f) drawRect(Color(0xFFF4F2FF).copy(alpha = 0.18f * f))
}
