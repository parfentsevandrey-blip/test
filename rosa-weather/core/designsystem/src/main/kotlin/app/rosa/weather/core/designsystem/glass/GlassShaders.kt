package app.rosa.weather.core.designsystem.glass

import org.intellij.lang.annotations.Language

/**
 * Liquid Glass optics in AGSL, following Apple's published behaviour (WWDC25 "Meet Liquid
 * Glass") and the reverse-engineered parameters of its `glassBackground` filter, lit the way a real
 * slab of glass with a rounded bevel is lit:
 *
 *  - **Lensing, not scattering.** Across the bevel the surface curves down like a quarter circle;
 *    the backdrop is sampled *outward* along the edge normal by the bevel's depth there, so
 *    content from just outside the shape is pulled into the rim — the glass "samples an area
 *    larger than itself" — while the flat top stays true.
 *  - **A body with a tone of its own**: restrained vibrancy (colour glows through without turning
 *    to candy), then milky glass over bright skies or smoky glass over dark ones, a little
 *    brighter toward the light, and a fine grain that keeps smooth frost from banding.
 *  - **Light from a real surface normal.** The bevel's 3-D normal gives a Fresnel edge — the
 *    steep outer rim reflects the sky, in a thin line, not a band — a crisp catch-light on the
 *    very edge, a sheen where the bevel turns toward the light, and a weaker pair on the opposite
 *    corner (light that crossed the glass): Apple's two highlights. Along the sides that face
 *    neither, the rim all but disappears and the far bevel falls slightly into shadow, so the
 *    edge reads as thickness, never as an outline. The light angle follows the device's tilt.
 *  - **Chromatic dispersion** at the bezel, strongest at the corners.
 *  - **Touch illumination** that starts under the finger and spreads; the rim flares while held,
 *    and letting go sends a **wave** of light across the glass that bends the backdrop as it passes.
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
uniform float4 wave;
uniform float grain;
uniform float darkness;

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
    // x runs 1 at the rim to 0 where the bevel meets the flat top; h is the bevel's height there.
    float t = clamp(-d / max(bezel, 1.0), 0.0, 1.0);
    float x = 1.0 - t;
    float h = sqrt(max(0.0, 1.0 - x * x));
    float m = amount * materialize * (1.0 - h);

    // A softened normal (larger virtual radius) avoids a visible seam along the diagonals.
    float gr = min(radius * 1.5, min(hs.x, hs.y));
    float2 n = normalRoundRect(p, hs, gr);
    if (depth > 0.0) {
        n = normalize(n + depth * normalize(p + 0.0001));
    }
    float2 s = coord + n * m;

    // Tap wave: a ring of light that bends what is behind it as it sweeps across.
    float ring = 0.0;
    if (wave.w > 0.0) {
        float2 wv = coord - origin - wave.xy;
        float k = (length(wv) - wave.z) / 16.0;
        ring = exp(-k * k) * wave.w;
        s += normalize(wv + 0.0001) * ring * 8.0;
    }

    half4 col;
    // Dispersion only where the bezel actually bends light: the flat middle takes one sample.
    if (dispersion > 0.0 && m > 0.3) {
        float corner = abs(p.x * p.y) / max(hs.x * hs.y, 1.0);
        float2 dv = n * m * dispersion * (0.12 + 0.35 * corner);
        half4 g = content.eval(s);
        col = half4(content.eval(s + dv).r, g.g, content.eval(s - dv).b, g.a);
    } else {
        col = content.eval(s);
    }

    float2 L = float2(cos(lightAngle), sin(lightAngle));
    float toward = dot(n, L);

    // The body: restrained vibrancy, the glass's own tone, a little brighter toward the light.
    col.rgb = vibrance(col.rgb, mix(1.0, saturation, materialize));
    col.rgb = mix(col.rgb, tint.rgb, tint.a * half(materialize));
    float up = clamp(0.5 - dot(p / max(hs, float2(1.0)), L) * 0.5, 0.0, 1.0);
    col.rgb += half(brightness * materialize * (0.55 + 0.9 * (1.0 - up)));

    // Light on the bevel.
    float lit = highlight * materialize * (1.0 + touch.z * 0.7);
    half3 white = mix(half3(1.0), clamp(vibrance(col.rgb, 1.6) * 1.35 + 0.08, 0.0, 1.0), 0.18);
    // How strongly each part of the rim sees the light: most at the lit corner, a good part at the
    // opposite one, hardly at all along the sides in between.
    float facing = max(toward, 0.0);
    float opposite = max(-toward, 0.0);
    float rimLight = mix(0.2, 1.0, pow(facing, 1.4)) + 0.5 * pow(opposite, 2.0);
    // Fresnel: the steep outer rim reflects the sky; the flat top barely does.
    float fresnel = pow(1.0 - h, 3.0);
    col.rgb += white * half(fresnel * rimLight * lit * mix(0.62, 0.42, darkness));
    // Catch-light on the very edge: a razor line, brightest where the rim meets the light.
    float edge = smoothstep(1.4, 0.1, -d);
    col.rgb += white * half(edge * (rimLight + 0.6 * pow(facing, 6.0)) * lit * mix(0.6, 0.5, darkness));
    // The specular streak: light running along the bevel just inside the rim, bright where the
    // rim faces the light, fading along the sides — and its weaker twin across the glass.
    float z = (x - 0.8) / 0.14;
    float band = exp(-z * z);
    float streak = band * (pow(facing, 2.2) + 0.45 * pow(opposite, 3.0));
    col.rgb += white * half(streak * lit * 0.34);
    // Thickness: the far side of the bevel falls a little into shadow, and the unlit rim darkens
    // to a hairline that keeps the pane crisp over bright backdrops.
    col.rgb *= half(1.0 - 0.16 * (1.0 - h) * pow(opposite, 0.7) * (1.0 - darkness * 0.5) * materialize);
    col.rgb *= half(1.0 - 0.12 * smoothstep(2.4, 0.4, -d) * (1.0 - rimLight) * (1.0 - darkness) * materialize);

    if (touch.z > 0.0) {
        float reach = max(size.x, size.y) * 0.85;
        float glow = 1.0 - smoothstep(0.0, reach, length(coord - origin - touch.xy));
        col.rgb += half3(0.16 * glow * glow * touch.z + 0.05 * touch.z);
    }
    col.rgb += half3(0.22 * ring);

    // Fine grain (interleaved gradient noise): frosted glass has a tooth, and smooth frost bands.
    if (grain > 0.0) {
        float ign = fract(52.9829189 * fract(dot(coord, float2(0.06711056, 0.00583715))));
        col.rgb += half((ign - 0.5) * grain * materialize);
    }

    float alpha = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(col.rgb * half(alpha), col.a * half(alpha));
}
"""
