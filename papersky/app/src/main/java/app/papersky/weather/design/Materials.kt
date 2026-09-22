package app.papersky.weather.design

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
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

/** Paper stocks and other materials of DESIGN_DOCTRINE §3. */
enum class Stock(val base: Color, private val texture: () -> Bitmap, val textureAlpha: Float) {
    Cotton(Color(0xFFFBF6EA), { MaterialTextures.cotton }, 1f),
    Notebook(Color(0xFFFDFBF3), { MaterialTextures.cotton }, 0.9f),
    Graph(Color(0xFFF6F5EC), { MaterialTextures.cotton }, 0.8f),
    IndexCard(Color(0xFFFFFDF6), { MaterialTextures.cotton }, 0.85f),
    Chipboard(Color(0xFFD7CCB6), { MaterialTextures.chipboard }, 1f),
    Kraft(Color(0xFFC9A77C), { MaterialTextures.kraft }, 1f),
    Postcard(Color(0xFFFAF4E4), { MaterialTextures.cotton }, 1f),
    Polaroid(Color(0xFFFBFAF6), { MaterialTextures.cotton }, 0.55f),
    Terracotta(Color(0xFFC9573A), { MaterialTextures.cotton }, 0.9f),
    Linen(Color(0xFFE6DFD2), { MaterialTextures.linen }, 1f),
    Cork(Color(0xFFB8895C), { MaterialTextures.cork }, 1f),
    ;

    /** Is ink on it light or dark? */
    val dark: Boolean get() = this == Terracotta || this == Cork

    val brush: ShaderBrush by lazy { ShaderBrush(ImageShader(texture().asImageBitmap(), TileMode.Repeated, TileMode.Repeated)) }
}

private val frostImage: ImageBitmap by lazy { MaterialTextures.frost.asImageBitmap() }

/**
 * A surface made of [stock]: its two-part shadow for [level] (§4.2), the base colour under the
 * room's light, the material's texture, a soft light-to-shade gradient, a bright top edge and,
 * in the cold, frost in the corners. Content draws on top.
 */
@Composable
fun Modifier.material(stock: Stock, shape: Shape, level: Int = 2, color: Color? = null): Modifier {
    val light = Paper.light
    val base = light.lit(color ?: stock.base)
    val e = Elevation.level(level)
    return this
        .shadowOf(shape, e, light.shadowBoost)
        .clip(shape)
        .drawWithCache {
            val shade = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.07f), 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.06f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val edgeH = 1.5.dp.toPx()
            val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = light.edgeLight), 1f to Color.Transparent, endY = edgeH)
            val frost = light.frost
            val frostSize = min(28.dp.toPx() * (0.6f + 0.4f * frost), min(size.width, size.height) / 2)
            onDrawWithContent {
                drawRect(base)
                drawRect(stock.brush, alpha = stock.textureAlpha)
                drawRect(shade)
                drawContent()
                drawRect(edge, size = Size(size.width, edgeH))
                if (frost > 0.02f) frostCorners(frostSize, frost)
            }
        }
}

/** Two shadows: a wide soft ambient one and a tight contact one, both down-right of the light. */
fun Modifier.shadowOf(shape: Shape, e: Elevation, boost: Float = 1f): Modifier {
    if (e.h.value <= 0f) return this
    return this
        .dropShadow(shape, Shadow(radius = e.ambientBlur, color = Ink.Shadow, offset = DpOffset(e.h * 0.25f, e.h), alpha = (e.ambientAlpha * boost).coerceAtMost(1f)))
        .dropShadow(shape, Shadow(radius = e.contactBlur, color = Ink.Shadow, offset = DpOffset(e.h * 0.08f, e.h * 0.3f), alpha = (e.contactAlpha * boost).coerceAtMost(1f)))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.frostCorners(s: Float, amount: Float) {
    val px = s.roundToInt().coerceAtLeast(1)
    val alpha = (amount * 0.9f).coerceIn(0f, 1f)
    val src = IntSize(frostImage.width, frostImage.height)
    // Top-left, then mirrored into the other three corners.
    drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha)
    scale(-1f, 1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha) }
    scale(1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
    scale(-1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
}

/** Rounded rectangle whose edges wobble slightly, like deckled (torn) paper. */
class DeckleShape(
    private val seed: Int = 1,
    private val corner: Dp = 18.dp,
    private val roughness: Dp = 0.9.dp,
    /** Tear the top edge along a notebook's perforation. */
    private val perforatedTop: Boolean = false,
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
        if (perforatedTop) {
            // Little teeth where the sheet came off the spiral.
            val tooth = with(density) { 6.dp.toPx() }
            val depth = with(density) { 2.2.dp.toPx() }
            p.moveTo(0f, depth)
            var x = 0f
            var k = 0
            while (x < w) {
                val nx = min(x + tooth, w)
                p.lineTo((x + nx) / 2, if (k % 2 == 0) 0f else depth * (0.5f + rnd(k, seed)))
                p.lineTo(nx, depth)
                x = nx
                k++
            }
            p.lineTo(w, h - r)
        } else {
            p.moveTo(r, wob(i++))
            var x = r
            while (x < w - r) { x += step; p.lineTo(min(x, w - r), wob(i++)) }
            p.quadraticTo(w, 0f, w, r)
        }
        var y = r
        while (y < h - r) { y += step; p.lineTo(w + wob(i++), min(y, h - r)) }
        p.quadraticTo(w, h, w - r, h)
        var x = w - r
        while (x > r) { x -= step; p.lineTo(max(x, r), h + wob(i++)) }
        p.quadraticTo(0f, h, 0f, h - r)
        y = h - r
        val top = if (perforatedTop) with(density) { 2.2.dp.toPx() } else r
        while (y > top) { y -= step; p.lineTo(wob(i++), max(y, top)) }
        if (!perforatedTop) p.quadraticTo(0f, 0f, r, 0f)
        p.close()
        return Outline.Generic(p)
    }

    override fun equals(other: Any?) = other is DeckleShape && other.seed == seed && other.corner == corner && other.roughness == roughness && other.perforatedTop == perforatedTop
    override fun hashCode() = ((seed * 31 + corner.hashCode()) * 31 + roughness.hashCode()) * 31 + perforatedTop.hashCode()

    private fun rnd(i: Int, s: Int): Float {
        var v = i * 374_761_393 + s * 668_265_263
        v = (v xor (v ushr 13)) * 1_274_126_177
        v = v xor (v ushr 16)
        return (v and 0xFFFFFF) / 16_777_216f
    }
}

/** Deterministic tilt for an object, within ±[max] degrees (§8: sheets ±0.6°, cards ±1°). */
fun tiltFor(seed: Int, max: Float): Float {
    var v = seed * 668_265_263 + 374_761_393
    v = (v xor (v ushr 13)) * 1_274_126_177
    v = v xor (v ushr 16)
    return ((v and 0xFFFF) / 65_535f * 2f - 1f) * max
}
