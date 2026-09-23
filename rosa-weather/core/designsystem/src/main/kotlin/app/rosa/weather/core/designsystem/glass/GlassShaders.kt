package app.rosa.weather.core.designsystem.glass

import org.intellij.lang.annotations.Language

/**
 * Liquid Glass optics in AGSL, following Apple's published behaviour (WWDC25 "Meet Liquid
 * Glass") and the reverse-engineered parameters of its `glassBackground` filter:
 *
 *  - **Lensing, not scattering.** Near the rim the surface bends; we displace the backdrop sample
 *    *outward* along the edge normal with a circular bezel profile, so content from just outside
 *    the shape is pulled into the rim — the glass "samples an area larger than itself".
 *  - **Vibrancy.** Saturation is boosted (~1.5×) so colour glows through instead of greying out.
 *  - **Dual specular rim.** Two highlights on opposite corners follow a light angle driven by the
 *    device's tilt; the rim colour is the saturated backdrop, not flat white.
 *  - **Chromatic dispersion** at the bezel, strongest at the corners.
 *  - **Touch illumination** that starts under the finger and spreads.
 *  - **Materialisation**: appearing and disappearing modulates lensing, never plain alpha.
 */
@Language("AGSL")
internal const val LIQUID_GLASS_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float bezel;
uniform float amount;
uniform float dispersion;
uniform float depth;
uniform float lightAngle;
uniform float highlight;
uniform float saturation;
uniform float brightness;
layout(color) uniform half4 tint;
uniform float materialize;
uniform float3 touch;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 normalRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    float2 g;
    if (q.x > 0.0 || q.y > 0.0) {
        g = normalize(max(q, 0.0) + 0.0001);
    } else {
        g = q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
    }
    return sign(p) * g;
}

half3 vibrance(half3 c, float s) {
    half l = dot(c, half3(0.2126, 0.7152, 0.0722));
    return mix(half3(l), c, half(s));
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - origin - hs;
    float d = sdRoundRect(p, hs, radius);
    if (d > 1.0) {
        return half4(0.0);
    }
    float t = clamp(-d / max(bezel, 1.0), 0.0, 1.0);
    float x = 1.0 - t;
    float m = amount * materialize * (1.0 - sqrt(max(0.0, 1.0 - x * x)));

    // A softened normal (larger virtual radius) avoids a visible seam along the diagonals.
    float gr = min(radius * 1.5, min(hs.x, hs.y));
    float2 n = normalRoundRect(p, hs, gr);
    if (depth > 0.0) {
        n = normalize(n + depth * normalize(p + 0.0001));
    }
    float2 s = coord + n * m;

    half4 col;
    if (dispersion > 0.0) {
        float corner = abs(p.x * p.y) / max(hs.x * hs.y, 1.0);
        float2 dv = n * m * dispersion * (0.12 + 0.35 * corner);
        half4 g = content.eval(s);
        col = half4(content.eval(s + dv).r, g.g, content.eval(s - dv).b, g.a);
    } else {
        col = content.eval(s);
    }

    col.rgb = vibrance(col.rgb, mix(1.0, saturation, materialize)) + half(brightness * materialize);
    col.rgb = mix(col.rgb, tint.rgb, tint.a * half(materialize));

    // Two opposite rim highlights: |dot| lights both the light-facing and the far corner.
    float2 L = float2(cos(lightAngle), sin(lightAngle));
    float facing = pow(abs(dot(n, L)), 1.6);
    float rim = smoothstep(2.4, 0.0, -d);
    half3 rimColor = mix(half3(1.0), clamp(vibrance(col.rgb, 2.0) * 1.45 + 0.05, 0.0, 1.0), 0.5);
    col.rgb += rimColor * half(rim * facing * highlight * materialize);
    // Bezel sheen: a whisper of extra light across the curved band.
    col.rgb += half3(0.05 * x * x * facing * materialize);

    if (touch.z > 0.0) {
        float reach = max(size.x, size.y) * 0.85;
        float glow = 1.0 - smoothstep(0.0, reach, length(coord - origin - touch.xy));
        col.rgb += half3(0.16 * glow * glow * touch.z + 0.05 * touch.z);
    }

    float alpha = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(col.rgb * half(alpha), col.a * half(alpha));
}
"""
