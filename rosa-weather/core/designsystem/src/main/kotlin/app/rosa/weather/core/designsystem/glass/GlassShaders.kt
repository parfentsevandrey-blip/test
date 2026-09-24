package app.rosa.weather.core.designsystem.glass

import org.intellij.lang.annotations.Language

/**
 * Liquid Glass optics in AGSL, following Apple's published behaviour (WWDC25 "Meet Liquid
 * Glass") and the reverse-engineered parameters of its `glassBackground` filter, lit the way a real
 * slab of glass with a rounded bevel is lit — and lit by the scene itself:
 *
 *  - **Lensing, not scattering.** Across the bevel the surface curves down like a quarter circle;
 *    the backdrop is sampled *outward* along the edge normal by the bevel's depth there, so
 *    content from just outside the shape is pulled into the rim — the glass "samples an area
 *    larger than itself" — while the flat top stays true. The scene lies deeper than the glass:
 *    tilting the phone shifts it a little behind the pane (parallax).
 *  - **A body with a tone of its own**: restrained vibrancy, milky or smoky glass, a little
 *    brighter toward the light, a fine grain that keeps smooth frost from banding.
 *  - **Light from the scene.** The light comes from the sun or moon where it is on screen, in its
 *    colour — gold low in the sky, white at noon, silver at night, soft under cloud — so every pane
 *    catches it on its own side and the highlights slide as it scrolls. From the bevel's 3-D
 *    normal: a Fresnel edge reflecting the sky, a razor catch-light on the rim, a specular streak
 *    along the lit bevel blooming softly, and a weaker twin across the glass. Where the rim faces
 *    the sun a **glint** flares — a hot core, a streak along the edge and a shorter one across —
 *    spilling past the edge. Along the unlit sides the rim all but disappears and the far bevel
 *    falls into shadow: thickness, never an outline.
 *  - **Reflections of the surroundings**, fixed in the world: soft bands of a window's light that
 *    stay put while the pane scrolls past them, and slide across it as the phone tilts.
 *  - **Weather on the glass**: lightning lights the whole pane, its rim most; on a freezing day
 *    frost creeps in from the rim in white veins.
 *  - **Liquid touch**: a finger presses the glass into a lens that swells what is under it and
 *    catches the light.
 *  - **Materialisation**: appearing grows the lensing (never plain alpha) while a sweep of light
 *    crosses the pane as it forms.
 *  - **Chromatic dispersion** at the bezel, strongest at the corners.
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
uniform float grain;
uniform float darkness;
layout(color) uniform half4 lightColor;
uniform float lightPower;
layout(color) uniform half4 skyColor;
uniform float flash;
uniform float frost;
uniform float2 parallax;
uniform float2 root;
uniform float2 sheen;
uniform float px;

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

float hash21(float2 q) {
    q = fract(q * float2(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

// Ridged noise: sharp crests, like the veins of frost crystals.
float ridged(float2 q) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += a * (1.0 - abs(vnoise(q) * 2.0 - 1.0));
        q = q * 2.03 + float2(3.1, 1.7);
        a *= 0.5;
    }
    return v;
}

// The sun catching the rim: a hot core, a streak along the edge, a shorter spike across it.
float glint(float2 p, float2 hs, float r, float2 L, float scale) {
    // March from outside along the light's direction back onto the rim.
    float2 q = L * max(hs.x, hs.y);
    for (int i = 0; i < 4; i++) {
        q -= L * sdRoundRect(q, hs, r);
    }
    float2 dv = p - q;
    float along = dot(dv, float2(-L.y, L.x));
    float across = dot(dv, L);
    float core = exp(-dot(dv, dv) / (2.0 * scale * scale));
    float streak = exp(-abs(across) / (0.3 * scale)) * exp(-abs(along) / (3.4 * scale));
    float spike = exp(-abs(along) / (0.3 * scale)) * exp(-abs(across) / (2.0 * scale));
    return core + 0.55 * streak + 0.3 * spike;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - origin - hs;
    float d = sdRoundRect(p, hs, radius);
    float2 L = float2(cos(lightAngle), sin(lightAngle));
    half3 sun = mix(half3(1.0), lightColor.rgb, 0.65);
    float sparkle = 0.0;
    if (lightPower * materialize > 0.05) {
        sparkle = glint(p, hs, radius, L, 2.4 * px) * lightPower * materialize * highlight * 0.9;
    }
    if (d > 1.0) {
        // Outside the glass only the glint's bloom shows.
        float g = clamp(sparkle, 0.0, 1.0) * (1.0 - smoothstep(0.0, 12.0 * px, d));
        return half4(sun * half(g), half(g));
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
    // Bent at the rim, and shifted a little with the phone's tilt: the scene lies deeper.
    float2 s = coord + n * m + parallax * materialize;

    // A finger presses the glass into a lens: what is under it swells.
    float press = 0.0;
    if (touch.z > 0.0) {
        float2 tv = coord - origin - touch.xy;
        float sig = min(size.x, size.y) * 0.32 + 8.0 * px;
        press = exp(-dot(tv, tv) / (2.0 * sig * sig)) * touch.z;
        s -= tv * press * 0.2;
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

    float toward = dot(n, L);

    // The body: restrained vibrancy, the glass's own tone, a little brighter toward the light.
    col.rgb = vibrance(col.rgb, mix(1.0, saturation, materialize));
    col.rgb = mix(col.rgb, tint.rgb, tint.a * half(materialize));
    float up = clamp(0.5 - dot(p / max(hs, float2(1.0)), L) * 0.5, 0.0, 1.0);
    col.rgb += half(brightness * materialize * (0.55 + 0.9 * (1.0 - up)));

    // Light on the bevel, in the light's own colour.
    float lit = highlight * materialize * (1.0 + touch.z * 0.7) * (0.65 + 0.6 * lightPower);
    half3 white = mix(half3(1.0), clamp(vibrance(col.rgb, 1.6) * 1.35 + 0.08, 0.0, 1.0), 0.18);
    half3 shine = white * sun;
    half3 reflection = mix(white, clamp(skyColor.rgb * 1.2 + 0.1, 0.0, 1.0), 0.35);
    // How strongly each part of the rim sees the light: most at the lit corner, a good part at the
    // opposite one, hardly at all along the sides in between.
    float facing = max(toward, 0.0);
    float opposite = max(-toward, 0.0);
    float rimLight = mix(0.2, 1.0, pow(facing, 1.4)) + 0.5 * pow(opposite, 2.0);
    // Fresnel: the steep outer rim reflects the sky; the flat top barely does.
    float fresnel = pow(1.0 - h, 3.0);
    col.rgb += reflection * half(fresnel * rimLight * lit * mix(0.62, 0.42, darkness));
    // Catch-light on the very edge: a razor line, brightest where the rim meets the light.
    float edge = smoothstep(1.4, 0.1, -d);
    col.rgb += shine * half(edge * (rimLight + 0.6 * pow(facing, 6.0)) * lit * mix(0.6, 0.5, darkness));
    // The specular streak along the bevel just inside the rim, and its weaker twin across.
    float z = (x - 0.8) / 0.14;
    float band = exp(-z * z);
    float streak = band * (pow(facing, 2.2) + 0.45 * pow(opposite, 3.0));
    col.rgb += shine * half(streak * lit * 0.34);
    // Bloom: the brightest light spills softly around the streak.
    float zb = (x - 0.8) / 0.34;
    col.rgb += shine * half(exp(-zb * zb) * pow(facing, 2.0) * lit * 0.07 * (0.5 + lightPower));
    // Thickness: the far side of the bevel falls a little into shadow, and the unlit rim darkens
    // to a hairline that keeps the pane crisp over bright backdrops.
    col.rgb *= half(1.0 - 0.16 * (1.0 - h) * pow(opposite, 0.7) * (1.0 - darkness * 0.5) * materialize);
    col.rgb *= half(1.0 - 0.12 * smoothstep(2.4, 0.4, -d) * (1.0 - rimLight) * (1.0 - darkness) * materialize);

    // Reflections of the surroundings, fixed in the world: they stay put as the pane scrolls past
    // and slide across it as the phone tilts.
    float2 world = (root + (coord - origin) + sheen) / px;
    float phase = fract((world.x * 0.55 + world.y * 0.83) / 560.0);
    float za = (phase - 0.32) / 0.07;
    float zc = (phase - 0.43) / 0.018;
    float mirror = exp(-za * za) + 0.55 * exp(-zc * zc);
    col.rgb += reflection * half(mirror * 0.045 * (1.0 - 0.45 * darkness) * materialize * (0.4 + 0.6 * t));

    // The pressed swell catches the light.
    col.rgb += shine * half(press * 0.1);

    // Lightning: the whole pane lights up, its rim most.
    if (flash > 0.0) {
        col.rgb += half3(0.8, 0.86, 1.0) * half(flash * (0.07 + 0.55 * fresnel + 0.4 * edge * rimLight));
    }

    // Frost creeping in from the rim on a freezing day: white veins, densest at the edge. It
    // reaches in unevenly, never further than a narrow band, so a small pill keeps a clear face.
    if (frost > 0.01) {
        float band = min(18.0 * px, min(hs.x, hs.y) * 0.3);
        float creep = band * (0.5 + 0.5 * vnoise(coord / (26.0 * px)));
        float reach = 1.0 - smoothstep(0.0, creep, -d);
        if (reach > 0.0) {
            float veins = smoothstep(0.62, 0.95, ridged(coord / (7.0 * px)));
            col.rgb = mix(col.rgb, half3(0.92, 0.95, 1.0), half(frost * reach * (0.18 + 0.55 * veins)));
        }
    }

    // Materialising: a sweep of light crosses the pane as it forms.
    if (materialize < 0.999) {
        float mz = clamp(materialize, 0.0, 1.0);
        float across = dot(p / max(hs, float2(1.0)), -L);
        float zs = (across - mix(1.8, -1.8, mz)) / 0.26;
        col.rgb += shine * half(exp(-zs * zs) * sin(3.14159 * mz) * 0.26);
    }

    if (touch.z > 0.0) {
        float reach = max(size.x, size.y) * 0.85;
        float glow = 1.0 - smoothstep(0.0, reach, length(coord - origin - touch.xy));
        col.rgb += half3(0.16 * glow * glow * touch.z + 0.05 * touch.z);
    }

    // The glint where the sun catches the rim.
    col.rgb += sun * half(min(sparkle, 1.4));

    // Fine grain (interleaved gradient noise): frosted glass has a tooth, and smooth frost bands.
    if (grain > 0.0) {
        float ign = fract(52.9829189 * fract(dot(coord, float2(0.06711056, 0.00583715))));
        col.rgb += half((ign - 0.5) * grain * materialize);
    }

    float alpha = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(col.rgb * half(alpha), col.a * half(alpha));
}
"""
