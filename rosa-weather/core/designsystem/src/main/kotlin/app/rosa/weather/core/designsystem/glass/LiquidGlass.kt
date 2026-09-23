package app.rosa.weather.core.designsystem.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * What the glass sees. [Modifier.backdropSource] records the living sky (or any content) into
 * [layer] every frame; every glass element then draws that same RenderNode through its own lens.
 * Because RenderNodes are shared by reference, the sky is rendered once no matter how many glass
 * surfaces refract it.
 */
@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var positionInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this element's drawing into [backdrop] (and still draws it normally). */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this.then(BackdropSourceElement(backdrop))

/**
 * Physical parameters of a glass material (dp values are converted at draw time). Presets follow
 * the proportions measured from Apple's implementation and the Kyant/kube reimplementations.
 */
@Immutable
data class GlassStyle(
    val blur: Dp,
    val bezel: Dp,
    val refraction: Dp,
    val dispersion: Float,
    val depth: Float,
    val saturation: Float,
    val brightness: Float,
    val highlight: Float,
    val tintAlpha: Float,
) {
    companion object {
        /** Floating controls: capsule buttons, bars, the city switcher. */
        val Regular = GlassStyle(3.dp, 16.dp, 22.dp, 0.6f, 0f, 1.5f, 0.04f, 0.55f, 0.1f)

        /** Permanently more transparent; for controls over rich media, with bold content only. */
        val Clear = GlassStyle(1.dp, 16.dp, 26.dp, 0.8f, 0f, 1.2f, 0f, 0.6f, 0.03f)

        /** Content layer (cards): mostly frosted, gentle lensing — Apple keeps real glass off content. */
        val Frosted = GlassStyle(18.dp, 10.dp, 10.dp, 0.25f, 0f, 1.35f, 0.02f, 0.35f, 0.16f)

        /** Active knobs and indicators while touched: pure lens, strong dispersion, no frost. */
        val Lens = GlassStyle(0.dp, 10.dp, 16.dp, 1f, 0f, 1.1f, 0.02f, 0.7f, 0f)

        /** Large sheets and menus: "thicker" glass — deeper lensing, dome depth, softer frost. */
        val Sheet = GlassStyle(16.dp, 26.dp, 44.dp, 0.4f, 0.2f, 1.5f, 0.03f, 0.5f, 0.2f)
    }
}

/**
 * Shared lighting for every glass element on screen: the adaptive tint (dark smoky glass over
 * night skies, milky glass over bright ones) and the light angle driven by device tilt. Read in
 * the draw phase only, so tilting never recomposes anything.
 */
@Stable
class GlassEnvironment {
    var tint by mutableStateOf(Color.White)
    var lightAngle by mutableFloatStateOf(-2.35f)

    /**
     * 0..1: how much denser the tint must get to keep light type legible over a bright sky.
     * Apple's large glass turns more opaque instead of flipping; this is that behaviour.
     */
    var tintBoost by mutableFloatStateOf(0f)
}

val LocalGlassEnvironment = androidx.compose.runtime.staticCompositionLocalOf { GlassEnvironment() }

/** Per-element state: materialisation progress and the touch glow. */
@Stable
class GlassState {
    var materialize by mutableFloatStateOf(1f)
    var touch by mutableStateOf(Offset.Unspecified)
    var touchStrength by mutableFloatStateOf(0f)
}

@Composable
fun rememberGlassState(): GlassState = remember { GlassState() }

/**
 * Draws [backdrop] through a Liquid Glass lens shaped as a rounded rectangle of [cornerRadius]
 * (clamped to a capsule). Content drawn by the element itself sits on top.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    style: GlassStyle = GlassStyle.Regular,
    cornerRadius: Dp = 28.dp,
    state: GlassState? = null,
    environment: GlassEnvironment? = null,
): Modifier = this.then(LiquidGlassElement(backdrop, style, cornerRadius, state, environment))

private data class LiquidGlassElement(
    val backdrop: Backdrop,
    val style: GlassStyle,
    val cornerRadius: Dp,
    val state: GlassState?,
    val environment: GlassEnvironment?,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(backdrop, style, cornerRadius, state, environment)

    override fun update(node: LiquidGlassNode) {
        node.backdrop = backdrop
        node.style = style
        node.cornerRadius = cornerRadius
        node.state = state
        node.environment = environment
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlass"
        properties["style"] = style
    }
}

private class LiquidGlassNode(
    var backdrop: Backdrop,
    var style: GlassStyle,
    var cornerRadius: Dp,
    var state: GlassState?,
    var environment: GlassEnvironment?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var layer: GraphicsLayer? = null
    private var position = Offset.Zero
    private val shader = RuntimeShader(LIQUID_GLASS_SHADER)

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
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
        val glassLayer = layer
        if (glassLayer == null || size.minDimension <= 0f) {
            drawContent()
            return
        }
        val s = state
        val materialize = s?.materialize ?: 1f
        val refraction = style.refraction.toPx()
        val blur = style.blur.toPx()
        // Sample a larger area than the glass itself (outward lensing + blur spill).
        val margin = ceil(refraction + blur * 2f + 2f)
        val radius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
        val tint = environment?.tint ?: Color.White

        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("origin", margin, margin)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bezel", style.bezel.toPx().coerceAtMost(size.minDimension / 2f))
        shader.setFloatUniform("amount", refraction)
        shader.setFloatUniform("dispersion", style.dispersion)
        shader.setFloatUniform("depth", style.depth)
        shader.setFloatUniform("lightAngle", environment?.lightAngle ?: -2.35f)
        shader.setFloatUniform("highlight", style.highlight)
        shader.setFloatUniform("saturation", style.saturation)
        shader.setFloatUniform("brightness", style.brightness)
        val boost = 1f + 2.4f * (environment?.tintBoost ?: 0f)
        shader.setColorUniform("tint", tint.copy(alpha = (tint.alpha * style.tintAlpha * boost).coerceAtMost(0.62f)).toArgb())
        shader.setFloatUniform("materialize", materialize)
        val touch = s?.touch ?: Offset.Unspecified
        if (touch.isSpecified && (s?.touchStrength ?: 0f) > 0f) {
            shader.setFloatUniform("touch", touch.x, touch.y, s!!.touchStrength)
        } else {
            shader.setFloatUniform("touch", 0f, 0f, 0f)
        }

        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val effectiveBlur = blur * materialize
        glassLayer.renderEffect = if (effectiveBlur > 0.5f) {
            RenderEffect.createChainEffect(lens, RenderEffect.createBlurEffect(effectiveBlur, effectiveBlur, Shader.TileMode.CLAMP))
        } else {
            lens
        }.asComposeRenderEffect()

        val layerSize = IntSize((size.width + margin * 2).toInt(), (size.height + margin * 2).toInt())
        val offset = backdrop.positionInRoot - position + Offset(margin, margin)
        glassLayer.record(layerSize) {
            translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
        }
        translate(-margin, -margin) { drawLayer(glassLayer) }
        drawContent()
    }
}

private data class BackdropSourceElement(val backdrop: Backdrop) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create() = BackdropSourceNode(backdrop)

    override fun InspectorInfo.inspectableProperties() {
        name = "backdropSource"
    }

    override fun update(node: BackdropSourceNode) {
        node.backdrop = backdrop
        node.invalidateDraw()
    }
}

private class BackdropSourceNode(var backdrop: Backdrop) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.positionInRoot = coordinates.positionInRoot()
    }

    override fun ContentDrawScope.draw() {
        val scope = this
        backdrop.layer.record(size.toIntSize()) { scope.drawContent() }
        drawLayer(backdrop.layer)
    }
}
