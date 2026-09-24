package app.rosa.weather.core.designsystem.sky

import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val NOISE = """
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// fbm that also returns its coarse part (first 3 octaves) — for cheap gradients.
float2 fbmCoarse(float2 p) {
    float v = 0.0;
    float coarse = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        if (i == 2) {
            coarse = v;
        }
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return float2(v, coarse);
}

float fbm3(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}
"""

/**
 * The living sky: gradient from the shared [app.rosa.weather.core.model.SkyPalette], the real
 * sun or a phase-correct moon at its true position, twinkling stars, two drifting fbm cloud decks
 * lit from the sun's side, fog banks and lightning. Rendered at reduced resolution and upscaled —
 * the sky is soft by nature, so this costs nothing visually.
 */
@Language("AGSL")
internal const val SKY_SHADER = """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 zenith;
layout(color) uniform half4 horizon;
layout(color) uniform half4 glow;
layout(color) uniform half4 sunColor;
layout(color) uniform half4 cloudLight;
layout(color) uniform half4 cloudShade;
uniform float2 sunPos;
uniform float isSun;
uniform float bodySize;
uniform float moonPhase;
uniform float cloudCover;
uniform float cloudDark;
uniform float fog;
uniform float wind;
uniform float stars;
uniform float flash;
uniform float2 tilt;
$NOISE

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.x / resolution.y;
    float h = clamp(uv.y, 0.0, 1.0);

    half3 col = mix(zenith.rgb, horizon.rgb, half(pow(h, 1.25)));

    float2 sp = sunPos + tilt * float2(0.02, 0.012);
    float2 dv = (uv - sp) * float2(aspect, 1.0);
    float dist = length(dv);
    float veil = 1.0 - cloudCover * 0.8;

    // Atmospheric glow around the light source and along the horizon.
    col += glow.rgb * half(0.26 * exp(-dist * 3.2) * (0.35 + 0.65 * veil) + 0.16 * pow(h, 3.0));

    // Stars: one per cell, twinkling, hidden by clouds later.
    if (stars > 0.01) {
        float cell = 26.0;
        float2 g = (fragCoord + tilt * 18.0) / cell;
        float2 id = floor(g);
        float r = hash(id);
        if (r > 0.72) {
            float2 pos = float2(hash(id + 1.3), hash(id + 7.1));
            float d = length(fract(g) - pos) * cell;
            float tw = 0.55 + 0.45 * sin(time * (1.2 + r * 2.0) + r * 60.0);
            float big = step(0.97, r);
            col += half3(stars * tw * (smoothstep(1.3 + big, 0.0, d) + big * 0.35 * smoothstep(4.0, 0.0, d)) * (1.0 - h * 0.6));
        }
    }

    // Sun or moon (bodySize is 0 once it has set).
    float bodyR = max(bodySize, 0.0001);
    if (bodySize < 0.0005) {
        // Below the horizon: nothing to draw.
    } else if (isSun > 0.5) {
        // A small, hot disc with a tight bloom; clouds veil it strongly.
        float v2 = veil * veil;
        float disc = smoothstep(bodyR, bodyR * 0.7, dist);
        float bloom = exp(-dist / (bodyR * 1.6)) * 0.45 + exp(-dist / (bodyR * 6.0)) * 0.12;
        col += sunColor.rgb * half((bloom + disc * 0.9) * v2);
    } else {
        float2 m = dv / bodyR;
        float inside = smoothstep(1.0, 0.94, length(m));
        float k = cos(6.2831853 * moonPhase);
        float term = k * sqrt(max(0.0, 1.0 - m.y * m.y));
        float lit = moonPhase < 0.5 ? smoothstep(term - 0.06, term + 0.06, m.x) : smoothstep(-term + 0.06, -term - 0.06, m.x);
        float crater = 0.9 + 0.1 * noise(m * 3.0 + 4.0);
        half3 moonCol = half3(0.96, 0.95, 1.0) * half(crater);
        col = mix(col, mix(col + half3(0.03, 0.035, 0.06), moonCol, half(lit)), half(inside * veil));
        col += half3(0.55, 0.58, 0.75) * half(exp(-dist / (bodyR * 2.5)) * 0.18 * veil * (0.3 + moonPhase * (1.0 - moonPhase) * 2.8));
    }

    // Two cloud decks with parallax; lit on the side facing the sun. Noise is evaluated only
    // where clouds can show: a clear sky skips it, the lit side is sampled only inside a cloud,
    // and it compares coarse octaves (the fine ones would only add grain to the shading).
    float drift = time * (0.006 + wind * 0.02);
    float mask = 0.0;
    if (cloudCover > 0.02) {
        float2 cp = float2(uv.x * aspect, uv.y * 1.6) * 1.8 + float2(drift, time * 0.002) + tilt * 0.04;
        float2 nc = fbmCoarse(cp);
        float threshold = mix(0.74, 0.22, cloudCover);
        mask = smoothstep(threshold, threshold + 0.26, nc.x);
        if (mask > 0.001) {
            float2 toSun = normalize(sp - uv + 0.0001) * 0.09;
            float lit2 = clamp(0.55 + (nc.y - fbm3(cp + toSun)) * 3.2, 0.0, 1.0);
            half3 cloudCol = mix(cloudShade.rgb, cloudLight.rgb, half(lit2 * (1.0 - cloudDark * 0.55)));
            float lowFade = 1.0 - smoothstep(0.62, 1.05, h) * 0.35;
            col = mix(col, cloudCol, half(mask * (0.5 + cloudCover * 0.5) * lowFade));
        }
        if (cloudCover > 0.05) {
            float2 cp2 = float2(uv.x * aspect, uv.y * 2.4) * 3.2 + float2(drift * 1.8, 0.0) + tilt * 0.08;
            float mask2 = smoothstep(threshold + 0.06, threshold + 0.3, fbm(cp2 + 11.0)) * cloudCover;
            col = mix(col, mix(cloudShade.rgb, cloudLight.rgb, half(0.35 + 0.4 * (1.0 - cloudDark))), half(mask2 * 0.55));
        }
    }

    // Fog banks rolling low.
    if (fog > 0.01) {
        float fb = fbm(float2(uv.x * aspect * 1.4 + time * 0.01, uv.y * 3.0));
        float band = smoothstep(0.25, 0.9, h + fb * 0.35);
        col = mix(col, mix(horizon.rgb, half3(0.92), 0.35), half(fog * band * 0.85));
    }

    // Lightning lights the clouds from within.
    col += half3(0.7, 0.72, 1.0) * half(flash * (0.25 + 0.75 * mask));

    // Film grain keeps gradients silky on 8-bit displays.
    col += half((hash(fragCoord + fract(time) * 100.0) - 0.5) * 0.018);
    return half4(col, 1.0);
}
"""

/**
 * Procedural precipitation drawn over the sky (so glass refracts it too): three parallax layers
 * of rain streaks slanted by wind, or soft swaying snowflakes.
 */
@Language("AGSL")
internal const val PRECIPITATION_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float rain;
uniform float snow;
uniform float wind;
uniform float2 tilt;
$NOISE

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution.y;
    float a = 0.0;
    if (rain > 0.01) {
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float scale = 1.0 + fi * 0.6;
            float2 p = uv * scale + tilt * 0.02 * (fi + 1.0);
            p.x += p.y * (0.12 + wind * 0.35);
            float2 grid = float2(70.0, 2.2) * scale;
            float2 q = p * grid;
            float col = floor(q.x);
            float rnd = hash(float2(col, fi));
            if (rnd > rain * 0.9 + 0.08) {
                continue;
            }
            float y = fract(q.y * 0.25 - time * (1.3 + fi * 0.45 + rnd) + rnd * 10.0);
            float streak = smoothstep(0.0, 0.06, y) * smoothstep(0.42, 0.06, y);
            float x = abs(fract(q.x) - 0.5);
            float thin = smoothstep(0.1 + fi * 0.04, 0.0, x);
            a += streak * thin * (0.22 + 0.16 * fi) * (0.5 + rain * 0.5);
        }
    }
    if (snow > 0.01) {
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float cell = 0.07 - fi * 0.015;
            float2 p = uv + tilt * 0.015 * (fi + 1.0);
            p.y -= time * (0.05 + fi * 0.035);
            p.x += sin(p.y * 7.0 + fi * 2.0 + time * 0.6) * 0.012 + time * wind * 0.04;
            float2 g = p / cell;
            float2 id = floor(g);
            float rnd = hash(id + fi * 17.0);
            if (rnd > snow * 0.85 + 0.1) {
                continue;
            }
            float2 pos = float2(hash(id + 3.1), hash(id + 9.7)) * 0.7 + 0.15;
            float d = length(fract(g) - pos);
            float r = 0.045 + 0.04 * fi + rnd * 0.035;
            a += smoothstep(r, r * 0.3, d) * (0.45 + 0.25 * fi);
        }
    }
    a = clamp(a, 0.0, 1.0);
    return half4(half3(a), half(a));
}
"""

/**
 * The window pane between you and the weather. Raindrops bead and slide down the glass (after
 * Martijn Steinrucken's "Heartfelt"), frost creeps in from the edges below zero, and humid air
 * fogs the pane — which you can wipe clear with a finger. A tap sends a ripple across the glass.
 */
@Language("AGSL")
internal const val WINDOW_SHADER = """
uniform shader content;
uniform shader wipe;
uniform float2 resolution;
uniform float time;
uniform float drops;
uniform float frost;
uniform float fogged;
uniform float4 ripple;
$NOISE

float3 N13(float p) {
    float3 p3 = fract(float3(p) * float3(0.1031, 0.11369, 0.13787));
    p3 += dot(p3, p3.yzx + 19.19);
    return fract(float3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));
}

float N(float t) {
    return fract(sin(t * 12345.564) * 7658.76);
}

float Saw(float b, float t) {
    return smoothstep(0.0, b, t) * smoothstep(1.0, b, t);
}

float2 DropLayer(float2 uv, float t) {
    float2 UV = uv;
    uv.y += t * 0.75;
    float2 a = float2(6.0, 1.0);
    float2 grid = a * 2.0;
    float2 id = floor(uv * grid);
    uv.y += N(id.x);
    id = floor(uv * grid);
    float3 n = N13(id.x * 35.2 + id.y * 2376.1);
    float2 st = fract(uv * grid) - float2(0.5, 0.0);
    float x = n.x - 0.5;
    float y = UV.y * 20.0;
    float wiggle = sin(y + sin(y));
    x += wiggle * (0.5 - abs(x)) * (n.z - 0.5);
    x *= 0.7;
    float ti = fract(t + n.z);
    y = (Saw(0.85, ti) - 0.5) * 0.9 + 0.5;
    float2 p = float2(x, y);
    float d = length((st - p) * a.yx);
    float mainDrop = smoothstep(0.4, 0.0, d);
    float r = sqrt(smoothstep(1.0, y, st.y));
    float cd = abs(st.x - x);
    float trail = smoothstep(0.23 * r, 0.15 * r * r, cd);
    float trailFront = smoothstep(-0.02, 0.02, st.y - y);
    trail *= trailFront * r * r;
    y = UV.y;
    y = fract(y * 10.0) + (st.y - 0.5);
    float dd = length(st - float2(x, y));
    float droplets = smoothstep(0.3, 0.0, dd);
    float m = mainDrop + droplets * r * trailFront;
    return float2(m, trail);
}

float StaticDrops(float2 uv, float t) {
    uv *= 40.0;
    float2 id = floor(uv);
    uv = fract(uv) - 0.5;
    float3 n = N13(id.x * 107.45 + id.y * 3543.654);
    float2 p = (n.xy - 0.5) * 0.7;
    float d = length(uv - p);
    float fade = Saw(0.025, fract(t + n.z));
    return smoothstep(0.3, 0.0, d) * fract(n.z * 10.0) * fade;
}

float2 Drops(float2 uv, float t, float l0, float l1, float l2) {
    float s = StaticDrops(uv, t) * l0;
    float2 m1 = DropLayer(uv, t) * l1;
    float2 m2 = DropLayer(uv * 1.85, t) * l2;
    float c = s + m1.x + m2.x;
    c = smoothstep(0.3, 1.0, c);
    return float2(c, max(m1.y * l0, m2.y * l1));
}

half4 soft(float2 p, float r) {
    half4 c = content.eval(p) * 0.28;
    c += content.eval(p + float2(r, 0.0)) * 0.18;
    c += content.eval(p - float2(r, 0.0)) * 0.18;
    c += content.eval(p + float2(0.0, r)) * 0.18;
    c += content.eval(p - float2(0.0, r)) * 0.18;
    return c;
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * resolution) / resolution.y;
    uv.y = -uv.y;
    float t = time * 0.2;
    float2 offset = float2(0.0);
    float cleared = 0.0;

    if (drops > 0.01) {
        float staticDrops = smoothstep(-0.5, 1.0, drops) * 2.0;
        float layer1 = smoothstep(0.25, 0.75, drops);
        float layer2 = smoothstep(0.0, 0.5, drops);
        float2 c = Drops(uv, t, staticDrops, layer1, layer2);
        float2 e = float2(0.0015, 0.0);
        float cx = Drops(uv + e, t, staticDrops, layer1, layer2).x;
        float cy = Drops(uv + e.yx, t, staticDrops, layer1, layer2).x;
        offset = float2(cx - c.x, -(cy - c.x)) * resolution.y * 0.35;
        cleared = max(smoothstep(0.1, 0.2, c.x), c.y);
    }

    // A tap ripple: an expanding ring that bends light as it passes.
    if (ripple.w > 0.0) {
        float age = time - ripple.z;
        float2 dv = fragCoord - ripple.xy;
        float dist = length(dv);
        float front = age * resolution.y * 0.55;
        float k = (dist - front) / (resolution.y * 0.03);
        float ring = exp(-k * k) * exp(-age * 2.2);
        offset += normalize(dv + 0.0001) * ring * resolution.y * 0.02 * ripple.w;
    }

    float2 p = fragCoord + offset;
    half4 sharp = content.eval(p);
    float wipeAmount = wipe.eval(fragCoord).a;

    // Condensation: a milky, blurred pane that drops and fingers clear.
    float fogMask = fogged * (1.0 - cleared) * (1.0 - wipeAmount);
    half4 col = sharp;
    if (fogMask > 0.01) {
        half4 blurred = soft(p, resolution.y * 0.012);
        col = mix(sharp, blurred + half4(0.08, 0.085, 0.09, 0.0), half(fogMask * 0.9));
    }

    // Frost grows in from the frame; ice crystals catch the light.
    if (frost > 0.01) {
        float2 q = fragCoord / resolution;
        float edge = min(min(q.x, 1.0 - q.x) * resolution.x / resolution.y, min(q.y, 1.0 - q.y));
        float f = fbm(fragCoord / resolution.y * 9.0);
        float band = 0.09 * frost;
        float growth = smoothstep(band, 0.0, edge - (f - 0.5) * band) * (1.0 - wipeAmount);
        // Sparse glints instead of a noise grid: one possible sparkle per small cell.
        float2 cell = fragCoord / (resolution.y * 0.012);
        float2 cid = floor(cell);
        float rnd = hash(cid);
        float glint = rnd > 0.86 ? smoothstep(0.35, 0.0, length(fract(cell) - float2(hash(cid + 3.1), hash(cid + 5.7)))) : 0.0;
        half3 ice = mix(col.rgb, half3(0.9, 0.95, 1.0), half(0.6 * growth)) + half3(glint * growth * 0.7);
        col = half4(ice, col.a);
    }
    return col;
}
"""
