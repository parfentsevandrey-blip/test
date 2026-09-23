package app.papersky.weather.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.papersky.weather.scene.MaterialTextures
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Fibres and specks of the paper, laid over its colour with plain alpha (DESIGN_DOCTRINE §3). */
val PaperFibres: ShaderBrush by lazy {
    ShaderBrush(ImageShader(MaterialTextures.paper.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private val frostImage: ImageBitmap by lazy { MaterialTextures.frost.asImageBitmap() }

/**
 * A sheet of the diorama's paper (§3–4): its shadow for [level], the sky-tinted paper colour,
 * the fibres of the sheet, light along the top edge and, in the cold, frost in the corners.
 * Content draws on top.
 */
@Composable
fun Modifier.paperSurface(shape: Shape, level: Int = 2, color: Color? = null): Modifier {
    val colors = Paper.colors
    val light = Paper.light
    val base = color ?: colors.paper
    val e = Elevation.level(level)
    val night = colors.isNight
    val fibres = if (night) 0.5f else 0.75f
    val edgeLight = if (night) 0.1f else 0.45f
    return this
        .shadowOf(shape, e, colors.shadow, if (night) 1.35f else 1f)
        .clip(shape)
        .drawWithCache {
            val shade = Brush.verticalGradient(0f to Color.White.copy(alpha = if (night) 0.035f else 0.06f), 1f to Color.Black.copy(alpha = if (night) 0.08f else 0.035f))
            val edgeH = 1.5.dp.toPx()
            val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = edgeLight), 1f to Color.Transparent, endY = edgeH)
            val frost = light.frost
            val frostSize = min(28.dp.toPx() * (0.6f + 0.4f * frost), min(size.width, size.height) / 2)
            onDrawWithContent {
                drawRect(base)
                drawRect(PaperFibres, alpha = fibres)
                drawRect(shade)
                drawRect(edge, size = Size(size.width, edgeH))
                drawContent()
                if (frost > 0.02f) frostCorners(frostSize, frost)
            }
        }
}

/** Two shadows below a sheet: a wide soft one and a tight contact one, down and a touch right. */
fun Modifier.shadowOf(shape: Shape, e: Elevation, color: Color, boost: Float = 1f): Modifier {
    if (e.h.value <= 0f) return this
    val c = color.copy(alpha = 1f)
    val a = color.alpha
    return this
        .dropShadow(shape, Shadow(radius = e.ambientBlur, color = c, offset = DpOffset(e.h * 0.2f, e.h), alpha = (e.ambientAlpha * a * 2.4f * boost).coerceAtMost(1f)))
        .dropShadow(shape, Shadow(radius = e.contactBlur, color = c, offset = DpOffset(e.h * 0.06f, e.h * 0.3f), alpha = (e.contactAlpha * a * 2.4f * boost).coerceAtMost(1f)))
}

private fun DrawScope.frostCorners(s: Float, amount: Float) {
    val px = s.roundToInt().coerceAtLeast(1)
    val alpha = (amount * 0.8f).coerceIn(0f, 1f)
    val src = IntSize(frostImage.width, frostImage.height)
    // Top-left, then mirrored into the other three corners.
    drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha)
    scale(-1f, 1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha) }
    scale(1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
    scale(-1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
}

/** Rounded rectangle whose edges wobble slightly, like torn (deckled) paper. */
class DeckleShape(
    private val seed: Int = 1,
    private val corner: Dp = 18.dp,
    private val roughness: Dp = 0.9.dp,
) : Shape {
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

    override fun equals(other: Any?) = other is DeckleShape && other.seed == seed && other.corner == corner && other.roughness == roughness
    override fun hashCode() = (seed * 31 + corner.hashCode()) * 31 + roughness.hashCode()

    private fun rnd(i: Int, s: Int): Float {
        var v = i * 374_761_393 + s * 668_265_263
        v = (v xor (v ushr 13)) * 1_274_126_177
        v = v xor (v ushr 16)
        return (v and 0xFFFFFF) / 16_777_216f
    }
}
