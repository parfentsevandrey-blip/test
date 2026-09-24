package app.rosa.weather.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import app.rosa.weather.core.designsystem.glass.Backdrop

/**
 * Apple's scroll edge effect: content scrolling up under the floating bar fades into the sky
 * instead of sliding under the glass, so the bar stays legible and its glass keeps showing what is
 * really behind it. The live sky itself is laid over the band — solid down to [solid], gone by
 * [height] — as one small offscreen strip drawn from the sky's own layer.
 */
@Composable
fun SkyScrollEdge(height: Dp, solid: Dp, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current ?: return
    Box(modifier.fillMaxWidth().height(height).then(ScrollEdgeElement(backdrop, solid)))
}

private data class ScrollEdgeElement(val backdrop: Backdrop, val solid: Dp) : ModifierNodeElement<ScrollEdgeNode>() {
    override fun create() = ScrollEdgeNode(backdrop, solid)

    override fun update(node: ScrollEdgeNode) {
        node.backdrop = backdrop
        node.solid = solid
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "skyScrollEdge"
    }
}

private class ScrollEdgeNode(var backdrop: Backdrop, var solid: Dp) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var layer: GraphicsLayer? = null
    private var position = Offset.Zero

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p != position) {
            position = p
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val strip = layer
        if (strip != null && size.height > 0f) {
            val offset = backdrop.positionInRoot - position
            val fadeFrom = (solid.toPx() / size.height).coerceIn(0f, 1f)
            val b = backdrop
            strip.record {
                translate(offset.x, offset.y) { drawLayer(b.layer) }
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, fadeFrom to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            drawLayer(strip)
        }
        drawContent()
    }
}
