package app.rosa.weather.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import app.rosa.weather.core.designsystem.R
import app.rosa.weather.core.designsystem.component.LocalBackdrop
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import org.intellij.lang.annotations.Language
import kotlin.math.ceil
import kotlin.math.max

/**
 * Numerals made of liquid glass — the signature of the home screen, after the glass clock of
 * iOS 26. The text becomes two masks: its exact shape, and a soft height field (the same shape,
 * blurred) whose slope gives every point of every glyph a surface normal. An AGSL lens then
 * refracts the sky through the digits (most at their rounded edges), lights the rims from the
 * tilt-driven light, shades the far side, tints the body with the ink colour so the number reads
 * over any sky, and casts a soft shadow. A new value melts out of the old one instead of cutting.
 */
@Composable
fun GlassText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 500,
    letterSpacingEm: Float = -0.012f,
    tintStrength: Float = 0.34f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backdrop = LocalBackdrop.current
    val environment = LocalGlassEnvironment.current
    val motion = LocalMotionEnabled.current
    val typeface = remember { runCatching { ResourcesCompat.getFont(context, R.font.cormorant) }.getOrNull() ?: Typeface.SERIF }
    val px = with(density) { fontSize.toPx() }
    val font = remember(typeface, px, weight, letterSpacingEm) { GlassFont(typeface, px, weight, letterSpacingEm) }

    val current = remember(font, text) { font.masks(text) }
    val melt = remember { Animatable(1f) }
    val morph = remember { MorphState(current, current) }
    LaunchedEffect(current) {
        if (morph.to === current) return@LaunchedEffect
        morph.from = if (motion) morph.to else current
        morph.to = current
        if (motion) {
            melt.snapTo(0f)
            melt.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    val widthDp = with(density) { current.width.toDp() }
    val heightDp = with(density) { current.height.toDp() }
    Box(
        modifier
            .size(widthDp, heightDp)
            .semantics { contentDescription = text }
            .then(
                GlassTextElement(
                    morph = morph,
                    progress = { melt.value },
                    backdrop = backdrop,
                    environment = environment,
                    ink = color,
                    tintStrength = tintStrength,
                ),
            ),
    )
}

/** The glyph masks of one value. [margin] surrounds the text on every side (shadow, refraction). */
internal class GlassMasks(
    val sharp: Bitmap,
    val soft: Bitmap,
    val width: Int,
    val height: Int,
    val margin: Int,
    val softRadius: Float,
)

internal class MorphState(var from: GlassMasks, var to: GlassMasks)

/** Rasterises text into the two masks, with fixed vertical metrics so values don't jump. */
internal class GlassFont(typeface: Typeface, private val sizePx: Float, weight: Int, letterSpacingEm: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePx
        color = android.graphics.Color.WHITE
        runCatching { fontVariationSettings = "'wght' $weight" }
        fontFeatureSettings = "lnum"
        letterSpacing = letterSpacingEm
    }
    private val top: Int
    private val bottom: Int

    init {
        val bounds = Rect()
        paint.getTextBounds(REFERENCE, 0, REFERENCE.length, bounds)
        top = bounds.top
        bottom = bounds.bottom
    }

    /** Recent values: scrubbing the timeline back and forth reuses them instead of re-rasterising. */
    private val recent = object : LinkedHashMap<String, GlassMasks>(RECENT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GlassMasks>) = size > RECENT
    }

    fun masks(text: String): GlassMasks = recent.getOrPut(text) { rasterise(text) }

    private fun rasterise(text: String): GlassMasks {
        val width = max(1, ceil(paint.measureText(text)).toInt())
        // Air above and below the digits, like the line spacing of a normal numeral line.
        val pad = (sizePx * 0.1f).toInt()
        val height = max(1, bottom - top + pad * 2)
        val softRadius = sizePx * 0.034f
        val margin = ceil(sizePx * 0.13f).toInt()
        val baseline = (margin + pad - top).toFloat()
        fun render(blur: Float): Bitmap {
            val bitmap = createBitmap(width + margin * 2, height + margin * 2)
            paint.maskFilter = if (blur > 0f) BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) else null
            android.graphics.Canvas(bitmap).drawText(text, margin.toFloat(), baseline, paint)
            paint.maskFilter = null
            return bitmap
        }
        return GlassMasks(render(0f), render(softRadius), width, height, margin, softRadius)
    }

    private companion object {
        const val REFERENCE = "0123456789−°"
        const val RECENT = 8
    }
}

private data class GlassTextElement(
    val morph: MorphState,
    val progress: () -> Float,
    val backdrop: Backdrop?,
    val environment: GlassEnvironment,
    val ink: Color,
    val tintStrength: Float,
) : ModifierNodeElement<GlassTextNode>() {
    override fun create() = GlassTextNode(morph, progress, backdrop, environment, ink, tintStrength)

    override fun update(node: GlassTextNode) {
        node.morph = morph
        node.progress = progress
        node.backdrop = backdrop
        node.environment = environment
        node.ink = ink
        node.tintStrength = tintStrength
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassText"
    }
}

private class GlassTextNode(
    var morph: MorphState,
    var progress: () -> Float,
    var backdrop: Backdrop?,
    var environment: GlassEnvironment,
    var ink: Color,
    var tintStrength: Float,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var layer: GraphicsLayer? = null
    private var position = Offset.Zero
    private val shader = RuntimeShader(GLASS_TEXT_SHADER)
    private val shaders = HashMap<Bitmap, BitmapShader>()

    /** The effect is rebuilt only when what it looks like changes, not on every scroll frame. */
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null
    private var effectKey: EffectKey? = null

    private data class EffectKey(val a: GlassMasks, val b: GlassMasks, val mix: Float, val lightAngle: Float, val tint: Int)

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        shaders.clear()
        effect = null
        effectKey = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInRoot()
        if (p != position) {
            position = p
            invalidateDraw()
        }
    }

    private fun shaderOf(bitmap: Bitmap): BitmapShader {
        if (shaders.size > 8) shaders.keys.retainAll(setOf(morph.from.sharp, morph.from.soft, morph.to.sharp, morph.to.soft))
        return shaders.getOrPut(bitmap) { BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    override fun ContentDrawScope.draw() {
        val from = morph.from
        val to = morph.to
        val t = progress().coerceIn(0f, 1f)
        val glass = layer
        val back = backdrop
        val margin = to.margin.toFloat()
        if (glass == null || back == null) {
            // No sky to refract (previews): plain ink numerals.
            drawImage(to.sharp.asImageBitmap(), topLeft = Offset(-margin, -margin), colorFilter = ColorFilter.tint(ink))
            return
        }
        val a = if (t >= 1f) to else from
        val mix = if (a === to) 1f else t
        val tint = ink.copy(alpha = tintStrength).toArgb()
        val key = EffectKey(a, to, mix, environment.lightAngle, tint)
        if (key != effectKey) {
            shader.setInputShader("sharpA", shaderOf(a.sharp))
            shader.setInputShader("softA", shaderOf(a.soft))
            shader.setInputShader("sharpB", shaderOf(to.sharp))
            shader.setInputShader("softB", shaderOf(to.soft))
            shader.setFloatUniform("mixT", mix)
            shader.setFloatUniform("refraction", to.softRadius * 1.6f)
            shader.setFloatUniform("lightAngle", key.lightAngle)
            shader.setColorUniform("tint", tint)
            shader.setFloatUniform("shadowOffset", 0f, to.softRadius * 0.55f)
            shader.setFloatUniform("shadowAlpha", 0.3f)
            effect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            effectKey = key
        }
        glass.renderEffect = effect

        val w = max(from.sharp.width, to.sharp.width)
        val h = max(from.sharp.height, to.sharp.height)
        val offset = back.positionInRoot - position + Offset(margin, margin)
        glass.record(IntSize(w, h)) {
            translate(offset.x, offset.y) { drawLayer(back.layer) }
        }
        translate(-margin, -margin) { drawLayer(glass) }
    }
}

@Language("AGSL")
private const val GLASS_TEXT_SHADER = """
uniform shader content;
uniform shader sharpA;
uniform shader softA;
uniform shader sharpB;
uniform shader softB;
uniform float mixT;
uniform float refraction;
uniform float lightAngle;
layout(color) uniform half4 tint;
uniform float2 shadowOffset;
uniform float shadowAlpha;

float field(float2 p) {
    return mix(softA.eval(p).a, softB.eval(p).a, mixT);
}

half4 main(float2 p) {
    // Coverage: the exact glyphs at rest; mid-morph the soft fields melt into each other.
    float sharp = mix(sharpA.eval(p).a, sharpB.eval(p).a, mixT);
    float goo = smoothstep(0.42, 0.58, field(p));
    float melt = 4.0 * mixT * (1.0 - mixT);
    float cov = mix(sharp, max(sharp, goo), melt);

    // Soft shadow beneath the glass.
    float sh = smoothstep(0.04, 0.7, field(p - shadowOffset));
    half4 shadow = half4(0.0, 0.0, 0.0, half(shadowAlpha * sh));
    if (cov < 0.003) {
        return shadow;
    }

    // Surface normal from the slope of the height field.
    float h = field(p);
    float e = 1.5;
    float2 g = float2(field(p + float2(e, 0.0)) - field(p - float2(e, 0.0)), field(p + float2(0.0, e)) - field(p - float2(0.0, e)));
    float gl = length(g);
    float2 n = gl > 0.0001 ? -g / gl : float2(0.0);
    float edge = clamp((1.0 - h) * 1.7, 0.0, 1.0);

    // Refraction: the rounded rims bend the sky outward.
    half4 c = content.eval(p + n * refraction * edge * edge);
    half l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
    c.rgb = mix(half3(l), c.rgb, 1.4) + 0.04;
    c.rgb = mix(c.rgb, tint.rgb, tint.a);

    // Light: bright rims facing the light, a crest glint, shade where the surface turns away.
    float2 L = float2(cos(lightAngle), sin(lightAngle));
    float facing = dot(n, L);
    float rim = pow(edge, 2.2);
    c.rgb += half3(0.5 * rim * max(facing, 0.0) + 0.16 * rim);
    c.rgb *= half(1.0 - 0.3 * rim * max(-facing, 0.0));
    c.rgb += half3(0.3 * pow(max(facing, 0.0), 8.0) * edge);

    half4 glass = half4(clamp(c.rgb, 0.0, 1.0), 1.0) * half(cov);
    return glass + shadow * half(1.0 - cov);
}
"""
