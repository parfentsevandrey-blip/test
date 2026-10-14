package app.rosa.weather.core.designsystem.seaglass

import android.graphics.RuntimeShader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The body of a piece of sea glass, drawn over its bounds as a rounded rectangle of its own:
 *
 *  - a pastel body, paler toward the bottom, faintly cloudy as tumbled glass is;
 *  - light caught inside the glass along its rim, strongest low down, and a milky lower half;
 *  - a fine grain of dark and white specks (in dp, so it is the same on every screen), thicker in
 *    places, with a few coarser flecks;
 *  - a 1.5 dp rim lit from within, faint at the top and bright along the bottom;
 *  - an outline that wanders by a fraction of a dp, as worn glass does.
 *
 * [press] (0..1) deepens the glass while a finger holds it.
 */
private const val SEA_GLASS_SHADER = """
uniform float2 size;
uniform float radius;
uniform float px;
uniform float seed;
uniform float opacity;
uniform float press;
layout(color) uniform half4 top;
layout(color) uniform half4 bottom;
layout(color) uniform half4 rimTop;
layout(color) uniform half4 rimBottom;
layout(color) uniform half4 speckDark;
layout(color) uniform half4 speckLight;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21) + seed * 0.0137);
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 c) {
    float2 hs = size * 0.5;
    float2 p = c - hs;
    // Worn glass: the outline wanders a little, slowly along the edge.
    float around = atan(p.y, p.x);
    float wander = (noise(float2(around * 2.5 + seed * 0.1, 3.7)) - 0.5) * 1.1 * px + (noise(c / (px * 16.0)) - 0.5) * 0.7 * px;
    float r = min(radius, min(hs.x, hs.y) - px);
    float d = sdRound(p, hs - px, max(r, 0.0)) + wander;
    float cover = smoothstep(0.7 * px, -0.7 * px, d);
    if (cover <= 0.0) return half4(0.0);
    float inside = -d;
    float2 uv = c / size;

    float3 col = mix(float3(top.rgb), float3(bottom.rgb), smoothstep(0.0, 1.0, uv.y));
    // Pressed, the glass deepens toward its own colour.
    col = mix(col, float3(top.rgb) * 0.9, press * 0.45);
    // Tumbled glass is faintly cloudy.
    float cloud = noise(c / (px * 30.0)) * 0.6 + noise(c / (px * 11.0)) * 0.4;
    col *= 0.972 + 0.056 * cloud;
    // Light caught inside along the rim, most of it low down; a milky lower half.
    float glow = exp(-inside / (px * 10.0));
    col = mix(col, float3(1.0), glow * (0.1 + 0.3 * uv.y) * (1.0 - 0.5 * press));
    col = mix(col, float3(1.0), smoothstep(0.5, 1.05, uv.y) * 0.1);

    // The grain: fine specks, thicker in places, and a few coarser flecks.
    float clump = 0.7 + 0.6 * noise(c / (px * 22.0));
    float n = hash(floor(c / (px * 0.75)));
    if (n > 1.0 - 0.032 * clump) {
        col = mix(col, float3(speckDark.rgb), float(speckDark.a) * 0.75);
    } else if (n < 0.055 * clump) {
        col = mix(col, float3(speckLight.rgb), float(speckLight.a));
    }
    float m = hash(floor(c / (px * 2.1)) + 17.0);
    if (m > 0.993) {
        col = mix(col, float3(speckDark.rgb), float(speckDark.a) * 0.75);
    } else if (m < 0.007) {
        col = mix(col, float3(speckLight.rgb), float(speckLight.a) * 0.8);
    }

    // The rim, lit from within: faint at the top, bright along the bottom.
    float band = 1.5 * px;
    float onRim = smoothstep(band + 0.7 * px, band - 0.5 * px, inside);
    float4 rim = mix(float4(rimTop), float4(rimBottom), smoothstep(0.15, 0.95, uv.y));
    col = mix(col, rim.rgb, rim.a * onRim);

    float a = max(opacity, rim.a * onRim) * cover;
    return half4(half3(col * a), half(a));
}
"""

/**
 * Sea glass of [palette] over this element: a shadow — cool and soft, with a glow of the glass's
 * own colour in it — then the glass ([SEA_GLASS_SHADER]); what the element holds is drawn on top,
 * clipped to the glass. [elevation] lifts it (a press lowers it).
 */
fun Modifier.seaGlass(
    palette: SeaPalette,
    cornerRadius: Dp,
    elevation: Dp = SeaGlass.Dimens.Elevation,
    press: () -> Float = { 0f },
    shadow: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val lifted = if (shadow && elevation > 0.dp) {
        this
            // A cool shadow on the canvas below, and in it the glass's own colour, as light
            // through tinted glass — both cast down, as the references show, not a halo.
            .dropShadow(shape, Shadow(radius = elevation * 1.3f, color = SeaGlass.AmbientShadow, offset = DpOffset(0.dp, elevation * 1.1f), spread = -elevation * 0.15f, alpha = 0.2f))
            .dropShadow(shape, Shadow(radius = elevation * 0.9f, color = palette.spot.copy(alpha = 1f), offset = DpOffset(0.dp, elevation * 0.8f), spread = -elevation * 0.2f, alpha = palette.spot.alpha))
    } else {
        this
    }
    return lifted.then(SeaGlassElement(palette, cornerRadius, press)).clip(shape)
}

private data class SeaGlassElement(
    val palette: SeaPalette,
    val cornerRadius: Dp,
    val press: () -> Float,
) : ModifierNodeElement<SeaGlassNode>() {
    override fun create() = SeaGlassNode(palette, cornerRadius, press)

    override fun update(node: SeaGlassNode) {
        node.palette = palette
        node.cornerRadius = cornerRadius
        node.press = press
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "seaGlass"
    }
}

private class SeaGlassNode(
    var palette: SeaPalette,
    var cornerRadius: Dp,
    var press: () -> Float,
) : Modifier.Node(), DrawModifierNode {
    private val shader = RuntimeShader(SEA_GLASS_SHADER)
    private val brush = ShaderBrush(shader)

    override fun ContentDrawScope.draw() {
        if (size.minDimension > 0f) {
            val p = palette
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", cornerRadius.toPx())
            shader.setFloatUniform("px", density)
            // A different grain for each size of glass, so neighbours don't match speck for speck.
            shader.setFloatUniform("seed", (size.width * 0.37f + size.height * 0.61f) % 97f)
            shader.setFloatUniform("opacity", p.opacity)
            shader.setFloatUniform("press", press().coerceIn(0f, 1f))
            shader.setColorUniform("top", p.top.toArgb())
            shader.setColorUniform("bottom", p.bottom.toArgb())
            shader.setColorUniform("rimTop", p.rimTop.toArgb())
            shader.setColorUniform("rimBottom", p.rimBottom.toArgb())
            shader.setColorUniform("speckDark", p.speckDark.toArgb())
            shader.setColorUniform("speckLight", p.speckLight.toArgb())
            drawRect(brush)
        }
        drawContent()
    }
}
