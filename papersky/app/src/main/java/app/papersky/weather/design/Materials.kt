package app.papersky.weather.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import app.papersky.weather.scene.PaletteMode
import kotlin.math.min
import kotlin.math.roundToInt

/** Fibres of the cotton paper, laid over its colour with plain alpha (DESIGN_DOCTRINE §3). */
val PaperFibres: ShaderBrush by lazy {
    ShaderBrush(ImageShader(MaterialTextures.paper.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private val frostImage: ImageBitmap by lazy { MaterialTextures.frost.asImageBitmap() }

/**
 * A sheet of cotton paper (§3–4): its shadow for [level], the paper colour, the fibres, light
 * falling from above, a hairline cut edge that catches the light along the top and, in the cold,
 * frost in the corners. With [vellum] the sheet is translucent and plain, like tracing paper.
 * Content draws on top.
 */
@Composable
fun Modifier.paperSurface(shape: Shape, level: Int = 1, color: Color? = null, vellum: Boolean = false): Modifier {
    val colors = Paper.colors
    val light = Paper.light
    val base = color ?: if (vellum) colors.paper.copy(alpha = 0.9f) else colors.paper
    val night = colors.isNight
    val fibres = if (vellum) 0f else if (night) 0.22f else 0.4f
    val edgeLight = if (night) 0.06f else 0.5f
    val cut = colors.paperInk.copy(alpha = if (vellum) 0.05f else 0.07f)
    return this
        .shadowOf(shape, Elevation.level(level), colors.shadow, if (night) 1.6f else 1f)
        .clip(shape)
        .drawWithCache {
            val shade = Brush.verticalGradient(0f to Color.White.copy(alpha = if (night) 0.02f else 0.05f), 1f to Color.Black.copy(alpha = if (night) 0.05f else 0.02f))
            val edgeH = 1.dp.toPx()
            val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = edgeLight), 1f to Color.Transparent, endY = edgeH)
            val outline = shape.createOutline(size, layoutDirection, this)
            val hairline = Stroke(2f)
            val frost = light.frost
            val frostSize = min(24.dp.toPx() * (0.6f + 0.4f * frost), min(size.width, size.height) / 2)
            onDrawWithContent {
                drawRect(base)
                if (fibres > 0f) drawRect(PaperFibres, alpha = fibres)
                drawRect(shade)
                drawRect(edge, size = Size(size.width, edgeH))
                drawOutline(outline, cut, style = hairline)
                drawContent()
                if (frost > 0.02f) frostCorners(frostSize, frost)
            }
        }
}

/**
 * Paper pressed into the sheet by a blind die (§3): a faint well of ink, shade along its upper
 * inside edge and light caught by its lower lip. For tracks, fields and slots.
 */
@Composable
fun Modifier.debossed(shape: Shape): Modifier {
    val colors = Paper.colors
    val night = colors.isNight
    val well = colors.paperInk.copy(alpha = if (night) 0.1f else 0.06f)
    return this
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val inner = 1.5.dp.toPx()
            val shade = Brush.verticalGradient(0f to Color.Black.copy(alpha = if (night) 0.22f else 0.1f), 1f to Color.Transparent, endY = inner * 2)
            val lip = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.White.copy(alpha = if (night) 0.05f else 0.35f), startY = size.height - inner, endY = size.height)
            onDrawWithContent {
                drawOutline(outline, well)
                drawRect(shade, size = Size(size.width, inner * 2))
                drawRect(lip)
                drawContent()
            }
        }
}

/** The two shadows of an [Elevation]: a tight contact one and a wide soft one, straight down. */
fun Modifier.shadowOf(shape: Shape, e: Elevation, color: Color, boost: Float = 1f): Modifier {
    if (e.ambientAlpha <= 0f) return this
    val c = color.copy(alpha = 1f)
    return this
        .dropShadow(shape, Shadow(radius = e.ambientBlur, color = c, offset = DpOffset(e.ambientY * 0.1f, e.ambientY), alpha = (e.ambientAlpha * boost).coerceAtMost(1f)))
        .dropShadow(shape, Shadow(radius = e.contactBlur, color = c, offset = DpOffset(0.dp, e.contactY), alpha = (e.contactAlpha * boost).coerceAtMost(1f)))
}

private fun DrawScope.frostCorners(s: Float, amount: Float) {
    val px = s.roundToInt().coerceAtLeast(1)
    val alpha = (amount * 0.5f).coerceIn(0f, 0.5f)
    val src = IntSize(frostImage.width, frostImage.height)
    // Top-left, then mirrored into the other three corners.
    drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha)
    scale(-1f, 1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha) }
    scale(1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
    scale(-1f, -1f) { drawImage(frostImage, IntOffset.Zero, src, IntOffset.Zero, IntSize(px, px), alpha * 0.8f) }
}

/**
 * The arched top of Millais's canvas (DESIGN_DOCTRINE §16): the top edge is half an ellipse
 * spanning the full width; the bottom corners are cut round at [corner].
 */
class ArchShape(private val corner: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }.coerceAtMost(min(size.width, size.height) / 2)
        val rise = min(size.height * 0.34f, size.width * 0.5f)
        val path = Path().apply {
            moveTo(0f, rise)
            arcTo(Rect(0f, 0f, size.width, rise * 2), 180f, 180f, false)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is ArchShape && other.corner == corner
    override fun hashCode() = corner.hashCode()
}

/** The frame of a small print of the sky: arched for Ophelia, a plain cut otherwise. */
fun sceneWindowShape(mode: PaletteMode): Shape = if (mode == PaletteMode.Ophelia) ArchShape() else RoundedCornerShape(10.dp)
