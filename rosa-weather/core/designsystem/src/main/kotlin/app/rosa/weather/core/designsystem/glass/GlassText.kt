package app.rosa.weather.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import app.rosa.weather.core.designsystem.R
import app.rosa.weather.core.designsystem.component.LocalBackdrop
import app.rosa.weather.core.designsystem.motion.LocalMotionEnabled
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language

/**
 * Numerals made of liquid glass — the signature of the home screen, after the glass clock of
 * iOS 26. Each value becomes a signed distance field, so every stroke — hairline or stem — gets a
 * real rounded bevel measured from its own edge. An AGSL lens bends the sky through that bevel
 * (splitting it into colour at the very rim), reflects light along the edges, puts a sharp glint
 * where the bevel faces the tilt-driven light, gathers a caustic inside the far edge and casts a
 * soft shadow; a crisp edge line keeps the number legible on any sky. A new value melts out of
 * the old one: their distance fields are blended, so the shapes flow into each other.
 *
 * Fields are built off the main thread, and [prefetch] (say, the next hours' temperatures) is
 * prepared ahead, so scrubbing through time never waits for one.
 */
@Composable
fun GlassText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 600,
    letterSpacingEm: Float = -0.012f,
    tintStrength: Float = 0.2f,
    prefetch: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val backdrop = LocalBackdrop.current
    val environment = LocalGlassEnvironment.current
    val motion = LocalMotionEnabled.current
    val typeface = remember { runCatching { ResourcesCompat.getFont(context, R.font.cormorant) }.getOrNull() ?: Typeface.SERIF }
    val px = with(density) { fontSize.toPx() }
    val font = remember(typeface, px, weight, letterSpacingEm) { GlassFont(typeface, px, weight, letterSpacingEm) }

    // The first value is built right away (the hero is never empty); later ones in the background.
    var current by remember(font) { mutableStateOf(font.masks(text)) }
    LaunchedEffect(font, text) {
        current = font.cached(text) ?: withContext(Dispatchers.Default) { font.masks(text) }
    }
    LaunchedEffect(font, prefetch) {
        withContext(Dispatchers.Default) { prefetch.forEach { font.masks(it) } }
    }

    val melt = remember { Animatable(1f) }
    val morph = remember(font) { MorphState(current, current) }
    LaunchedEffect(current) {
        if (morph.to === current) return@LaunchedEffect
        morph.from = if (motion) morph.to else current
        morph.to = current
        if (motion) {
            melt.snapTo(0f)
            melt.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    val shown = current
    val widthDp = with(density) { shown.width.toDp() }
    val heightDp = with(density) { shown.height.toDp() }
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

/**
 * The distance field of one value, encoded in alpha as `0.5 + d / (2 * range)` (d in px, positive
 * inside). [margin] surrounds the text on every side, for the shadow and the refraction.
 */
internal class GlassMasks(
    val field: Bitmap,
    val width: Int,
    val height: Int,
    val margin: Int,
    val range: Float,
    val sizePx: Float,
)

internal class MorphState(var from: GlassMasks, var to: GlassMasks)

/** Rasterises text into distance fields, with fixed vertical metrics so values don't jump. */
internal class GlassFont(typeface: Typeface, private val sizePx: Float, weight: Int, letterSpacingEm: Float) {
    private val template = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        template.getTextBounds(REFERENCE, 0, REFERENCE.length, bounds)
        top = bounds.top
        bottom = bounds.bottom
    }

    /** Recent and prefetched values: scrubbing the timeline reuses them. */
    private val recent = object : LinkedHashMap<String, GlassMasks>(RECENT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GlassMasks>) = size > RECENT
    }

    @Synchronized
    fun cached(text: String): GlassMasks? = recent[text]

    fun masks(text: String): GlassMasks {
        cached(text)?.let { return it }
        val built = rasterise(text)
        synchronized(this) { recent[text] = built }
        return built
    }

    private fun rasterise(text: String): GlassMasks {
        val paint = Paint(template)
        val width = max(1, ceil(paint.measureText(text)).toInt())
        // Air above and below the digits, like the line spacing of a normal numeral line.
        val pad = (sizePx * 0.1f).toInt()
        val height = max(1, bottom - top + pad * 2)
        val range = sizePx * RANGE
        val margin = ceil(range + sizePx * REFRACTION + 2f).toInt()
        // Measured at full resolution (at half, the transform's pixel steps would ripple through
        // the bevel), then stored at half: a distance field is smooth, and linear filtering reads
        // it back with crisp edges — a quarter of the memory.
        val fw = width + margin * 2
        val fh = height + margin * 2
        val coverage = createBitmap(fw, fh)
        android.graphics.Canvas(coverage).drawText(text, margin.toFloat(), (margin + pad - top).toFloat(), paint)
        val full = IntArray(fw * fh)
        coverage.getPixels(full, 0, fw, 0, 0, fw, fh)
        coverage.recycle()
        for (i in full.indices) full[i] = full[i] ushr 24
        val distance = GlyphDistance.signed(full, fw, fh)
        val w = ceil(fw * FIELD_SCALE).toInt()
        val h = ceil(fh * FIELD_SCALE).toInt()
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // 2 × 2 average: the half-size field, a little smoother still.
                var sum = 0f
                var count = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val sx = x * 2 + dx
                    val sy = y * 2 + dy
                    if (sx < fw && sy < fh) {
                        sum += distance[sy * fw + sx]
                        count++
                    }
                }
                val d = sum / count
                val a = ((0.5f + d / (2f * range)).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[y * w + x] = a shl 24
            }
        }
        val field = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return GlassMasks(field, width, height, margin, range, sizePx)
    }

    internal companion object {
        const val REFERENCE = "0123456789−°"
        const val RECENT = 40
        const val FIELD_SCALE = 0.5f

        /**
         * How far from the edge the field reaches, as a share of the font size. Kept tight: the
         * field has 8 bits, and the narrower its range, the finer its steps (and the smoother the
         * surface normals read from it).
         */
        const val RANGE = 0.055f

        /** The rounded bevel of every stroke; thick stems keep a flat top between their bevels. */
        const val BEVEL = 0.036f

        /** How far the rim reaches out for what lies beyond the glyph. */
        const val REFRACTION = 0.07f
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

    private data class EffectKey(val a: GlassMasks, val b: GlassMasks, val mix: Float, val lightAngle: Float, val tint: Int, val edge: Int)

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
        if (shaders.size > 8) shaders.keys.retainAll(setOf(morph.from.field, morph.to.field))
        return shaders.getOrPut(bitmap) {
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
                setLocalMatrix(Matrix().apply { setScale(1f / GlassFont.FIELD_SCALE, 1f / GlassFont.FIELD_SCALE) })
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val from = morph.from
        val to = morph.to
        val t = progress().coerceIn(0f, 1f)
        val glass = layer
        val back = backdrop
        val margin = to.margin.toFloat()
        if (glass == null || back == null) {
            // No sky to refract (previews): the glyphs in plain ink. The matrix turns the field
            // back into coverage, one pixel of anti-aliasing wide at the edge.
            val k = 2f * to.range
            val threshold = ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, ink.red * 255f,
                    0f, 0f, 0f, 0f, ink.green * 255f,
                    0f, 0f, 0f, 0f, ink.blue * 255f,
                    0f, 0f, 0f, k, 127.5f - 255f * to.range,
                ),
            )
            drawImage(
                to.field.asImageBitmap(),
                dstOffset = IntOffset(-to.margin, -to.margin),
                dstSize = IntSize(to.width + to.margin * 2, to.height + to.margin * 2),
                colorFilter = ColorFilter.colorMatrix(threshold),
            )
            return
        }
        val a = if (t >= 1f) to else from
        val mix = if (a === to) 1f else t
        val tint = ink.copy(alpha = tintStrength).toArgb()
        // Over pale skies a darker line defines the glass; over deep ones a faint light one.
        val edge = if (ink.luminance() < 0.5f) ink.copy(alpha = 0.5f).toArgb() else ink.copy(alpha = 0.22f).toArgb()
        val key = EffectKey(a, to, mix, environment.lightAngle, tint, edge)
        if (key != effectKey) {
            shader.setInputShader("fieldA", shaderOf(a.field))
            shader.setInputShader("fieldB", shaderOf(to.field))
            shader.setFloatUniform("mixT", mix)
            shader.setFloatUniform("range", to.range)
            shader.setFloatUniform("bevel", to.sizePx * GlassFont.BEVEL)
            shader.setFloatUniform("refraction", to.sizePx * GlassFont.REFRACTION)
            shader.setFloatUniform("dispersion", 0.22f)
            shader.setFloatUniform("lightAngle", key.lightAngle)
            shader.setColorUniform("tint", tint)
            shader.setColorUniform("edge", edge)
            shader.setFloatUniform("shadowOffset", 0f, to.sizePx * 0.022f)
            shader.setFloatUniform("shadowAlpha", 0.3f)
            effect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            effectKey = key
        }
        glass.renderEffect = effect

        val w = max(from.width, to.width) + to.margin * 2
        val h = max(from.height, to.height) + to.margin * 2
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
uniform shader fieldA;
uniform shader fieldB;
uniform float mixT;
uniform float range;
uniform float bevel;
uniform float refraction;
uniform float dispersion;
uniform float lightAngle;
layout(color) uniform half4 tint;
layout(color) uniform half4 edge;
uniform float2 shadowOffset;
uniform float shadowAlpha;

// Signed distance to the glyph edge in px (positive inside), blended between the two values.
float sd(float2 p) {
    float a = mix(fieldA.eval(p).a, fieldB.eval(p).a, mixT);
    return (a - 0.5) * 2.0 * range;
}

half3 vibrance(half3 c, float s) {
    half l = dot(c, half3(0.2126, 0.7152, 0.0722));
    return mix(half3(l), c, half(s));
}

half4 main(float2 p) {
    float d = sd(p);
    float shadow = shadowAlpha * smoothstep(-range * 0.85, range * 0.3, sd(p - shadowOffset));
    float cov = smoothstep(-0.75, 0.75, d);
    if (cov < 0.002) {
        return half4(0.0, 0.0, 0.0, half(shadow));
    }

    // The outward normal: the distance falls fastest toward the nearest edge. Left unnormalised
    // (a distance field's slope is 1), it fades to nothing on the crest of a thin stroke, where
    // the two edges meet, instead of flipping into a crease.
    float2 g = float2(sd(p + float2(2.0, 0.0)) - sd(p - float2(2.0, 0.0)), sd(p + float2(0.0, 2.0)) - sd(p - float2(0.0, 2.0))) * 0.25;
    float gl = length(g);
    float2 n = -g / max(gl, 1.0);

    // A round bevel: vertical at the rim, flat where a stroke is thick enough to have a top.
    float x = (1.0 - clamp(d / bevel, 0.0, 1.0)) * min(gl, 1.0);
    float nz = sqrt(max(0.0, 1.0 - x * x));
    float3 N = float3(n * (1.0 - clamp(d / bevel, 0.0, 1.0)), nz);
    N = normalize(N);
    float bend = 1.0 - nz;

    // Refraction: the rim reaches out for the sky beyond the glyph, and splits it into colour.
    float2 disp = n * refraction * bend;
    half3 c = content.eval(p + disp).rgb;
    if (bend > 0.02) {
        c.r = content.eval(p + disp * (1.0 + dispersion)).r;
        c.b = content.eval(p + disp * (1.0 - dispersion)).b;
    }
    c = vibrance(c, 1.35) + 0.03;
    c = mix(c, tint.rgb, tint.a);

    // Light: reflections along the rim, a sharp glint where the bevel faces the light (and a
    // weaker one opposite), a caustic gathered inside the far edge, the far rim in shade.
    float2 L2 = float2(cos(lightAngle), sin(lightAngle));
    float facing = dot(n, L2);
    float3 V = float3(0.0, 0.0, 1.0);
    float spec = pow(max(dot(N, normalize(normalize(float3(L2, 0.8)) + V)), 0.0), 30.0);
    float spec2 = pow(max(dot(N, normalize(normalize(float3(-L2, 0.8)) + V)), 0.0), 30.0);
    float fresnel = x * x * x;
    c += half3(fresnel * (0.2 + 0.5 * max(facing, 0.0)));
    c += half3(spec * 0.95 + spec2 * 0.35);
    float band = smoothstep(0.2, 0.6, x) * (1.0 - smoothstep(0.75, 0.98, x));
    c += half3(0.24 * band * max(-facing, 0.0));
    c *= half(1.0 - 0.22 * smoothstep(0.8, 1.0, x) * max(-facing, 0.0));

    // Where glass meets air: a crisp line that keeps the digits legible over any sky.
    float line = 1.0 - smoothstep(0.0, 1.4, abs(d - 0.35));
    c = mix(c, edge.rgb, half(line * edge.a));

    half4 glass = half4(clamp(c, 0.0, 1.0), 1.0) * half(cov);
    return glass + half4(0.0, 0.0, 0.0, half(shadow)) * half(1.0 - cov);
}
"""
