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
 *
 * Light is directed like a film's: a low sun swells, turns gold and fills the sky around it with
 * a warm haze, and throws rays through the gaps in the clouds; clouds catch the moon's light as
 * well as the sun's; on a clear night the Milky Way crosses the sky and now and then a meteor
 * falls; lightning lights the clouds from within where it strikes; after a shower, with the sun
 * behind you, a rainbow stands opposite it, its faint twin outside and the darker band between.
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
uniform float bolt;
uniform float boltSeed;
uniform float boltX;
uniform float2 flashAt;
uniform float lift;
uniform float rainbow;
uniform float2 tilt;
// Clear sunshine (motes in the air, the lens's flare) and diamond dust (a frosty clear day), 0..1.
uniform float sunlitAir;
uniform float diamondDust;
$NOISE

// A rainbow's colours across its band, t from 0 (inside: violet) to 1 (outside: red).
half3 spectrum(float t) {
    float hue = mix(0.76, 0.0, clamp(t, 0.0, 1.0));
    half3 k = half3(clamp(abs(fract(hue + float3(1.0, 0.6666667, 0.3333333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0));
    return mix(half3(1.0), k, 0.8);
}

// A glint in the cell of a grid laid over the sky that [g] falls in (0 where the cell holds none:
// only [share] of them do). It wanders about its cell; a soft disc [radius] of a cell wide.
float moteAt(float2 g, float seed, float share, float radius, float t) {
    float2 id = floor(g);
    float h = hash(id + seed);
    if (h > share) {
        return 0.0;
    }
    float2 at = (float2(hash(id + seed + 1.7), hash(id + seed + 3.1)) - 0.5) * 0.6
        + 0.12 * float2(sin(t * (0.23 + h) + h * 40.0), cos(t * (0.19 + h * 0.7) + h * 17.0));
    float2 f = fract(g) - 0.5 - at;
    return exp(-dot(f, f) / (radius * radius));
}

// A lens ghost: a soft disc, a little brighter at its rim.
half3 ghost(float2 q, float2 at, float r, half3 tint) {
    float d = length(q - at);
    float disc = smoothstep(r, r * 0.75, d);
    float z = (d - r * 0.85) / (r * 0.12);
    return tint * half(disc * 0.7 + exp(-z * z) * 0.5);
}

// Horizontal offset of a lightning channel at height y: jagged at every scale, like the real thing.
float channel(float y, float seed) {
    return (noise(float2(y * 7.0, seed)) - 0.5) * 0.1
        + (noise(float2(y * 29.0, seed * 1.7 + 3.0)) - 0.5) * 0.035
        + (noise(float2(y * 110.0, seed * 2.3 + 7.0)) - 0.5) * 0.01;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.x / resolution.y;
    float h = clamp(uv.y, 0.0, 1.0);

    half3 col = mix(zenith.rgb, horizon.rgb, half(pow(h, 1.25)));

    float2 sp = sunPos + tilt * float2(0.02, 0.012);
    float2 dv = (uv - sp) * float2(aspect, 1.0);
    float dist = length(dv);
    float veil = 1.0 - cloudCover * 0.8;
    float sunUp = isSun * step(0.0005, bodySize);
    // Golden hour: the lower the sun, the larger, warmer and wider its light.
    float low = sunUp * (1.0 - smoothstep(0.02, 0.5, lift));
    half3 gold = half3(1.0, 0.62, 0.33);
    half3 warm = mix(sunColor.rgb, gold, half(low * 0.8));

    // Atmospheric glow around the light source and along the horizon.
    col += mix(glow.rgb, gold, half(low * 0.6)) * half(0.26 * exp(-dist * mix(3.2, 1.8, low)) * (0.35 + 0.65 * veil) * (1.0 + low * 0.7) + 0.16 * pow(h, 3.0));

    // Stars: one per cell, twinkling, hidden by clouds later — blue-white and gold, a finer
    // dust of faint ones behind them, and on a clear night the Milky Way across the sky.
    if (stars > 0.01) {
        float2 q = float2(uv.x * aspect, uv.y) + tilt * 0.01;
        // The galaxy's band, rising from the lower left, with dark lanes of dust along it.
        float2 axis = normalize(float2(0.35, -0.94));
        float across = dot(q - float2(0.16, 0.35), float2(-axis.y, axis.x));
        float band = exp(-across * across / 0.03);
        float galaxy = 0.0;
        if (band > 0.02) {
            float along = dot(q, axis);
            float cloud = fbm3(float2(along * 3.1, across * 7.0) + 2.3);
            float lane = smoothstep(0.45, 0.7, fbm3(float2(along * 5.3 + 7.1, across * 16.0))) * exp(-(across - 0.012) * (across - 0.012) / 0.0016);
            galaxy = band * (0.35 + 0.9 * cloud) * (1.0 - 0.75 * lane) * smoothstep(1.05, 0.35, h);
            col += mix(half3(0.5, 0.58, 0.86), half3(0.95, 0.86, 0.74), half(band * cloud * cloud)) * half(stars * galaxy * 0.36);
        }
        float cell = 26.0;
        float2 g = (fragCoord + tilt * 18.0) / cell;
        float2 id = floor(g);
        float r = hash(id);
        if (r > 0.72 - galaxy * 0.18) {
            float2 pos = float2(hash(id + 1.3), hash(id + 7.1));
            float d = length(fract(g) - pos) * cell;
            float tw = 0.55 + 0.45 * sin(time * (1.2 + r * 2.0) + r * 60.0);
            float big = step(0.97, r);
            float kind = hash(id + 5.5);
            half3 tone = kind < 0.2 ? half3(1.0, 0.86, 0.7) : (kind > 0.75 ? half3(0.78, 0.86, 1.0) : half3(1.0));
            col += tone * half(stars * tw * (smoothstep(1.3 + big, 0.0, d) + big * 0.35 * smoothstep(4.0, 0.0, d)) * (1.0 - h * 0.6));
        }
        float2 g2 = (fragCoord + tilt * 9.0) / 11.0;
        float2 id2 = floor(g2);
        float r2 = hash(id2 + 31.7);
        if (r2 > 0.86 - galaxy * 0.3) {
            float d2 = length(fract(g2) - float2(hash(id2 + 2.9), hash(id2 + 4.4))) * 11.0;
            col += half3(stars * (0.22 + 0.2 * galaxy) * smoothstep(1.0, 0.0, d2) * (1.0 - h * 0.7));
        }
        // Now and then a meteor: a streak that flares and burns out in under a second.
        float cycle = 7.0;
        float k = floor(time / cycle);
        float into = time - k * cycle;
        if (stars > 0.25 && into < 0.85 && hash(float2(k, 3.7)) > 0.3) {
            float prog = into / 0.85;
            float2 start = float2((0.15 + 0.7 * hash(float2(k, 1.1))) * aspect, 0.04 + 0.24 * hash(float2(k, 2.3)));
            float2 dir = normalize(float2(hash(float2(k, 5.9)) > 0.5 ? 1.0 : -1.0, 0.35 + 0.45 * hash(float2(k, 7.7))));
            float2 head = start + dir * prog * 0.42;
            float2 rel = q - head;
            float behind = dot(rel, -dir);
            float off = abs(dot(rel, float2(-dir.y, dir.x)));
            float life = sin(3.14159 * prog);
            float trail = step(0.0, behind) * (1.0 - smoothstep(0.0, 0.16 * life + 0.02, behind)) * exp(-off * resolution.y * 0.7);
            float spark = exp(-length(rel) * resolution.y * 0.18);
            col += half3(0.9, 0.95, 1.0) * half((trail * 0.85 + spark * 0.7) * life * stars);
        }
    }

    // Sun or moon (bodySize is 0 once it has set).
    float bodyR = max(bodySize, 0.0001);
    if (bodySize < 0.0005) {
        // Below the horizon: nothing to draw.
    } else if (isSun > 0.5) {
        // A small, hot disc with a tight bloom; clouds veil it strongly. Low in the sky it swells
        // and turns gold, its bloom spreading wide.
        float v2 = veil * veil;
        float sunR = bodyR * (1.0 + 0.45 * low);
        float disc = smoothstep(sunR, sunR * 0.7, dist);
        float bloom = exp(-dist / (sunR * 1.6)) * 0.45 + exp(-dist / (sunR * mix(6.0, 11.0, low))) * mix(0.12, 0.3, low);
        col += warm * half(bloom * v2 * (1.0 - low * 0.35));
        // The disc itself: white-hot at noon, a deep gold low down — laid over, not added, so it
        // keeps its colour instead of burning out to white.
        half3 face = mix(half3(1.0), half3(1.0, 0.82, 0.55), half(low * 0.85));
        // Through broken cloud the disc still shows whole; an overcast sky hides it.
        float through = smoothstep(0.95, 0.5, cloudCover);
        col = mix(col, face, half(disc * 0.97 * mix(v2, 1.0, 0.55) * through));
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
    float moonUp = (1.0 - isSun) * step(0.0005, bodySize);
    float moonLight = 0.5 - 0.5 * cos(6.2831853 * moonPhase);
    if (cloudCover > 0.02) {
        float2 cp = float2(uv.x * aspect, uv.y * 1.6) * 1.8 + float2(drift, time * 0.002) + tilt * 0.04;
        // Domain warp: billows curl and slowly change shape instead of sliding by as a rigid sheet.
        float2 warp = float2(noise(cp * 0.8 + float2(time * 0.011, 1.3)), noise(cp * 0.8 + float2(5.2, time * 0.013))) - 0.5;
        cp += warp * 0.6;
        float2 nc = fbmCoarse(cp);
        float threshold = mix(0.74, 0.22, cloudCover);
        mask = smoothstep(threshold, threshold + 0.26, nc.x);
        if (mask > 0.001) {
            float2 toSun = normalize(sp - uv + 0.0001) * 0.09;
            float lit2 = clamp(0.55 + (nc.y - fbm3(cp + toSun)) * 3.2, 0.0, 1.0);
            half3 cloudCol = mix(cloudShade.rgb, cloudLight.rgb, half(lit2 * (1.0 - cloudDark * 0.55)));
            float lowFade = 1.0 - smoothstep(0.62, 1.05, h) * 0.35;
            col = mix(col, cloudCol, half(mask * (0.5 + cloudCover * 0.5) * lowFade));
            // Silver lining: thin cloud edges near the sun light up with it — gold when it is low —
            // and near the moon, silver, as bright as its phase.
            float thin = mask * (1.0 - mask) * 4.0;
            half3 lining = isSun > 0.5 ? warm * 0.7 + glow.rgb * 0.5 : half3(0.6, 0.66, 0.84);
            float liningPower = sunUp * (1.0 + low * 0.8) + moonUp * 0.6 * moonLight;
            col += lining * half(thin * exp(-dist * 2.6) * 0.45 * liningPower * (1.0 - cloudDark));
        }
        if (cloudCover > 0.05) {
            float2 cp2 = float2(uv.x * aspect, uv.y * 2.4) * 3.2 + float2(drift * 1.8, 0.0) + tilt * 0.08;
            float mask2 = smoothstep(threshold + 0.06, threshold + 0.3, fbm(cp2 + 11.0)) * cloudCover;
            col = mix(col, mix(cloudShade.rgb, cloudLight.rgb, half(0.35 + 0.4 * (1.0 - cloudDark))), half(mask2 * 0.55));
        }
    }

    // Fog: a dense bank rolling low, and thinner wisps drifting faster in front of it.
    if (fog > 0.01) {
        float fb = fbm(float2(uv.x * aspect * 1.3 + time * 0.012, uv.y * 2.8));
        float wisp = fbm3(float2(uv.x * aspect * 2.8 - time * 0.024, uv.y * 6.0 + 3.0));
        float bank = smoothstep(0.18, 0.92, h + fb * 0.38 - 0.08);
        float wisps = smoothstep(0.42, 0.78, wisp) * smoothstep(0.1, 0.7, h);
        float density = clamp(bank * 0.88 + wisps * 0.3, 0.0, 1.0);
        // Fog glows toward the sun: the light scatters in it and warms it on that side.
        half3 fogCol = mix(mix(horizon.rgb, half3(0.93), 0.4), warm, half(exp(-dist * 2.2) * 0.42 * sunUp * veil));
        col = mix(col, fogCol, half(fog * density * 0.86));
    }

    // Crepuscular rays: shafts of light fanning out from the sun through the gaps between clouds —
    // only where there are clouds to cast them, broad and soft, most below the sun, in the haze
    // over the ground, and richest at golden hour.
    if (sunUp > 0.5 && dist > 0.001) {
        float want = smoothstep(0.12, 0.35, cloudCover) * (1.0 - smoothstep(0.78, 0.96, cloudCover)) + fog * 0.5;
        if (want > 0.01) {
            float2 dir = dv / dist;
            float fan = noise(dir * 2.3 + float2(time * 0.015, 1.7)) * 0.7 + noise(dir * 5.1 + float2(4.1, time * 0.025)) * 0.3;
            float shafts = smoothstep(0.5, 0.88, fan);
            float reach = exp(-dist * 1.1) * smoothstep(bodyR * 2.0, bodyR * 7.0, dist);
            float below = 0.35 + 0.65 * smoothstep(-0.05, 0.35, uv.y - sp.y);
            col += warm * half(shafts * reach * below * min(want, 1.0) * (1.0 - mask * 0.85) * veil * (0.16 + 0.12 * low));
        }
    }

    // After a shower, with the sun at your back: the bow opposite it — violet inside, red outside —
    // the sky brighter within it, its faint reversed twin outside and Alexander's dark band between.
    if (rainbow > 0.01) {
        float2 q = float2(uv.x * aspect, uv.y);
        float2 centre = float2((1.0 - sp.x) * aspect, 1.3 + lift * 0.25);
        float rr = length(q - centre);
        float radius = 0.98;
        float w = 0.05;
        float t = (rr - (radius - w)) / (2.0 * w);
        float bow = smoothstep(0.0, 0.18, t) * smoothstep(1.0, 0.82, t);
        float second = (rr - (radius * 1.23 - w * 1.4)) / (2.8 * w);
        float bow2 = smoothstep(0.0, 0.2, second) * smoothstep(1.0, 0.8, second);
        float clearOf = (1.0 - mask * 0.85) * rainbow * smoothstep(0.05, 0.3, 1.0 - abs(q.x - centre.x) / 1.1);
        col += spectrum(t) * half(bow * 0.2 * clearOf);
        col += spectrum(1.0 - second) * half(bow2 * 0.07 * clearOf);
        float inside = smoothstep(radius - w, radius - w - 0.25, rr) * step(rr, radius - w);
        col += half3(0.06) * half(inside * clearOf);
        float dark = smoothstep(radius + w, radius + w + 0.03, rr) * smoothstep(radius * 1.23 - w * 1.4, radius * 1.23 - w * 1.4 - 0.03, rr);
        col *= half(1.0 - 0.06 * dark * clearOf);
    }

    // Light in the air. Clear sunshine lights motes of dust drifting in it — thickest about the
    // sun, a few large and out of focus near the glass; on a frosty clear day, diamond dust: ice
    // crystals hanging in the air, flashing as they turn, now and then in a colour.
    if (sunlitAir > 0.01 || diamondDust > 0.01) {
        float2 q = float2(uv.x * aspect, uv.y) + tilt * float2(0.012, 0.008);
        float nearSun = exp(-dist * 1.7);
        float openSky = 1.0 - mask * 0.9;
        if (sunlitAir > 0.01) {
            // Only in the sun's own glow: out in the open blue, dust is not seen.
            float glowOf = nearSun * nearSun * nearSun;
            float2 up = float2(time * 0.006 * (0.4 + wind), -time * 0.012);
            float2 gf = (q + up) * 22.0;
            float hf = hash(floor(gf) + 11.0);
            float fine = moteAt(gf, 11.0, 0.35, 0.05, time) * (0.5 + 0.5 * sin(time * (0.8 + 2.6 * hf) + hf * 60.0));
            float bokeh = moteAt((q + up * 0.6) * 6.0, 23.0, 0.3, 0.16, time * 0.7);
            half3 dust = mix(half3(1.0, 0.86, 0.62), warm, 0.4);
            col += dust * half((fine * 0.26 + bokeh * 0.07) * glowOf * sunlitAir * openSky);
        }
        if (diamondDust > 0.01) {
            float2 gd = (q + float2(time * 0.004 * (0.3 + wind), time * 0.006)) * 30.0;
            float hd = hash(floor(gd) + 41.0);
            float crystal = moteAt(gd, 41.0, 0.28, 0.04, time * 0.5);
            float turn = pow(0.5 + 0.5 * sin(time * (1.1 + 2.5 * hd) + hd * 80.0), 14.0);
            half3 hue = mix(half3(0.92, 0.96, 1.0), spectrum(fract(hd * 7.3)), 0.35);
            col += hue * half(crystal * turn * (0.25 + 0.9 * nearSun) * diamondDust * openSky);
        }
    }

    // A clear sun flares in the lens: a faint anamorphic streak through it, a thin halo about it,
    // and ghosts along the line from it through the middle of the picture, each tinted by the
    // coatings — sliding across the other way as the phone tilts.
    if (sunlitAir > 0.01 && sunUp > 0.5) {
        float clearSun = sunlitAir * veil * veil;
        float streak = exp(-abs(dv.y) * 110.0) * exp(-abs(dv.x) * 2.2);
        col += mix(warm, half3(0.8, 0.88, 1.0), 0.45) * half(streak * 0.1 * clearSun);
        float ring = (dist - 0.22) / 0.016;
        col += spectrum(clamp(0.5 + ring * 0.25, 0.0, 1.0)) * half(exp(-ring * ring) * 0.042 * clearSun);
        float2 s = float2(sp.x * aspect, sp.y);
        float2 axis = float2(0.5 * aspect, 0.55) - s;
        float2 q = float2(uv.x * aspect, uv.y);
        col += ghost(q, s + axis * 0.5, 0.022, half3(1.0, 0.78, 0.45)) * half(0.08 * clearSun);
        col += ghost(q, s + axis * 0.9, 0.055, half3(0.55, 0.85, 1.0)) * half(0.05 * clearSun);
        col += ghost(q, s + axis * 1.3, 0.03, half3(0.8, 0.6, 1.0)) * half(0.07 * clearSun);
        col += ghost(q, s + axis * 1.75, 0.085, half3(0.6, 1.0, 0.75)) * half(0.035 * clearSun);
    }

    // Lightning lights the clouds from within, brightest around where it strikes; a near strike
    // shows its channel, forked and jagged, glowing through the rain.
    float2 fd = (uv - flashAt) * float2(aspect, 1.0);
    float within = exp(-length(fd) * 2.6);
    col += half3(0.72, 0.76, 1.0) * half(flash * (0.12 + 0.34 * mask + within * (0.2 + 0.45 * mask)));
    if (bolt > 0.01) {
        float2 bp = float2(uv.x * aspect, uv.y);
        float x0 = boltX * aspect;
        float end = 0.55 + 0.3 * fract(boltSeed * 7.31);
        float reach = smoothstep(end, end - 0.12, bp.y);
        float dx = abs(bp.x - x0 - channel(bp.y, boltSeed));
        float core = exp(-dx * resolution.y * 0.8);
        float halo = exp(-dx * 55.0) * 0.4;
        // One fork leaves the channel and wanders off to the side.
        float yb = 0.16 + 0.22 * fract(boltSeed * 3.13);
        float side = fract(boltSeed * 5.71) > 0.5 ? 1.0 : -1.0;
        float fx = x0 + channel(yb, boltSeed) + side * (bp.y - yb) * 0.45 + (channel(bp.y, boltSeed + 9.0)) * 0.5;
        float onFork = step(yb, bp.y) * smoothstep(yb + 0.2, yb + 0.04, bp.y);
        float df = abs(bp.x - fx);
        float fork = (exp(-df * resolution.y * 1.1) + exp(-df * 80.0) * 0.25) * onFork * 0.75;
        col += half3(0.86, 0.9, 1.0) * half(((core + halo) * reach + fork) * bolt);
    }

    // Film grain keeps gradients silky on 8-bit displays.
    col += half((hash(fragCoord + fract(time) * 100.0) - 0.5) * 0.018);
    return half4(col, 1.0);
}
"""

/**
 * Procedural precipitation drawn over the sky, so glass refracts it too. Rain falls in four depths
 * — dense faint drizzle far away, long soft streaks up close, out of focus — slanted by a wind
 * that gusts, in sheets that sweep across; heavy rain veils the distance. Snow falls the same way,
 * from sharp specks far off to large soft bokeh flakes close to the glass, each swaying on its own.
 *
 * A downpour is its own weather, not just more rain: longer streaks, curtains of it marching
 * across on the wind, and spray rising where it hammers the ground. Heavy snow gusts into
 * whiteouts that sweep past. Lightning lights the rain and the snow in mid-air.
 */
@Language("AGSL")
internal const val PRECIPITATION_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float rain;
uniform float snow;
uniform float wind;
uniform float flash;
uniform float2 tilt;
layout(color) uniform half4 tint;
$NOISE

// Rain streaks at one depth (0 far … 1 near), in screen heights; [stretch] lengthens them.
float streaks(float2 uv, float depth, float t, float slant, float density, float stretch) {
    float2 cell = float2(mix(0.0065, 0.03, depth), mix(0.12, 0.42, depth) * stretch);
    float2 p = float2(uv.x - uv.y * slant, uv.y - t * mix(0.85, 2.4, depth));
    float2 g = p / cell;
    float2 id = floor(g);
    if (hash(id + depth * 37.0) > density) {
        return 0.0;
    }
    float2 f = fract(g);
    float len = mix(0.35, 0.8, hash(id + 2.3));
    float along = (f.y - hash(id + 9.1) * (1.0 - len)) / len;
    if (along < 0.0 || along > 1.0) {
        return 0.0;
    }
    float halfWidth = mix(0.00045, 0.0021, depth);
    float dx = abs(f.x - (0.2 + 0.6 * hash(id + 4.7))) * cell.x;
    float thick = smoothstep(halfWidth, halfWidth * mix(0.6, 0.1, depth), dx);
    // Motion blur of a falling drop: brightest where it is now, fading along where it was.
    float shape = smoothstep(0.0, 0.55, along) * smoothstep(1.0, 0.88, along);
    return thick * shape * mix(0.45, 1.0, hash(id + 6.6));
}

// Snowflakes at one depth: sharp specks far away, large soft discs up close. Returns coverage,
// and how much of it is the flake's shaded underside (a clump of snow is lit from above).
float2 flakes(float2 uv, float depth, float t, float density, float drift) {
    float cell = mix(0.028, 0.12, depth);
    float2 p = float2(uv.x - t * drift * mix(0.02, 0.08, depth), uv.y - t * mix(0.03, 0.11, depth));
    float2 g = p / cell;
    float2 id = floor(g);
    float r = hash(id + depth * 17.0);
    if (r > density) {
        return float2(0.0);
    }
    float phase = hash(id + 3.3) * 6.2831;
    float2 c = float2(0.5 + 0.22 * sin(t * (0.6 + r * 0.9) + phase), 0.5 + 0.12 * sin(t * (0.9 + r) + phase * 1.7));
    float2 dv = (fract(g) - c) * cell;
    float d = length(dv);
    float size = mix(0.002, 0.011, depth) * mix(0.6, 1.25, hash(id + 8.8));
    float disc = smoothstep(size, size * mix(0.65, 0.08, depth), d);
    // Out of focus, a flake becomes a disc with a slightly brighter rim.
    float rim = smoothstep(size * 0.95, size * 0.7, d) * (1.0 - smoothstep(size * 0.7, size * 0.4, d));
    float twinkle = 0.85 + 0.15 * sin(t * 3.0 + r * 40.0);
    float shade = smoothstep(-0.2, 0.9, dv.y / size) * (1.0 - depth * 0.6);
    return float2((disc * mix(1.0, 0.7, depth) + rim * 0.2 * depth) * twinkle, shade);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution.y + tilt * 0.015;
    float a = 0.0;
    float veil = 0.0;
    float veilCap = 0.3;
    float downpour = 0.0;
    if (rain > 0.01) {
        // Sheets of heavier rain sweep across; the wind gusts.
        float sheet = noise(float2(uv.x * 1.3 - time * (0.3 + wind * 0.5), uv.y * 0.7 - time * 0.8));
        float slant = 0.1 + wind * 0.32 + (noise(float2(time * 0.25, 4.0)) - 0.5) * 0.12;
        // A downpour marches across in curtains: broad bands of it, driven by the wind.
        float heavy = smoothstep(0.55, 0.95, rain);
        float curtain = smoothstep(0.3, 0.78, noise(float2((uv.x - uv.y * slant) * 1.15 - time * (0.16 + wind * 0.3), time * 0.04 + 2.0)));
        float density = rain * (0.55 + 0.75 * sheet) * (1.0 + heavy * (curtain - 0.35));
        float stretch = 1.0 + heavy * 0.45;
        for (int i = 0; i < 4; i++) {
            float depth = float(i) / 3.0;
            a += streaks(uv, depth, time, slant, density * mix(1.0, 0.75, depth), stretch) * mix(0.2, 0.42, depth) * (1.0 + heavy * 0.55);
        }
        // Rain shafts veil the distance, denser where a sheet passes.
        float shaft = noise(float2((uv.x - uv.y * slant) * 5.0, time * 0.15));
        veil = rain * (0.04 + 0.07 * sheet) * (0.7 + 0.6 * shaft);
        // In a downpour the curtains grey the air, and spray boils up where it hits the ground.
        float spray = smoothstep(0.58, 1.02, uv.y) * (0.55 + 0.45 * fbm3(float2(uv.x * 3.2 - time * (0.2 + wind * 0.3), uv.y * 5.0 - time * 0.45)));
        veil += heavy * (curtain * 0.16 + spray * 0.26 + 0.08);
        veilCap = mix(0.3, 0.55, heavy);
        downpour = heavy;
    }
    // Lightning lights the falling rain and snow in mid-air.
    half3 light = mix(tint.rgb, half3(1.0), 0.5) * half(1.0 + flash * 1.3);
    half3 rgb = light * half(clamp(a, 0.0, 1.0));
    if (snow > 0.01) {
        // Heavy snow gusts: the flakes are driven sideways and whiteouts sweep past.
        float gust = smoothstep(0.5, 0.85, noise(float2(time * 0.13, 9.5))) * smoothstep(0.45, 0.9, snow);
        float drift = 0.4 + wind * 1.6 + (noise(float2(time * 0.2, 9.0)) - 0.5) * 0.8 + gust * 2.2;
        half3 lit = half3(0.98, 0.985, 1.0);
        half3 shaded = mix(tint.rgb, half3(0.62, 0.66, 0.74), 0.6);
        for (int i = 0; i < 4; i++) {
            float depth = float(i) / 3.0;
            float2 f = flakes(uv, depth, time, snow * mix(0.95, 0.7, depth), drift);
            float fa = f.x * mix(0.7, 0.62, depth) * (1.0 - clamp(a, 0.0, 1.0));
            rgb += mix(lit, shaded, half(f.y * 0.55)) * half(fa);
            a += fa;
        }
        veil = max(veil, snow * 0.05);
        float whiteout = fbm3(float2(uv.x * 1.6 - time * (0.25 + drift * 0.08), uv.y * 2.2 + time * 0.05));
        veil += gust * smoothstep(0.35, 0.8, whiteout) * 0.26;
        veilCap = max(veilCap, 0.3 + gust * 0.14);
    }
    a = clamp(a, 0.0, 1.0);
    // The air of a downpour is dark with water; lighter rain only greys the distance.
    half3 haze = mix(mix(tint.rgb, half3(0.9), 0.4), tint.rgb * 0.62, half(downpour * 0.75)) * half(1.0 + flash * 0.6);
    float v = clamp(veil, 0.0, veilCap) * (1.0 - a);
    return half4(rgb + haze * half(v), half(a + v));
}
"""

/**
 * The window pane between you and the weather. Raindrops bead and slide down the glass, leaving
 * clear trails (the drop field after Martijn Steinrucken's "Heartfelt"). The eye is on the glass,
 * so the weather behind it is slightly out of focus, while every bead is a tiny lens: it shows
 * the scene small, sharp and upside down, with a dark refracting rim, a glint where it faces the
 * light and a caustic gathered at its foot. Below zero, frost grows in from the frame as ice
 * grains and feathery needles; humid air fogs the pane with a mist of micro-droplets — which you
 * can wipe clear with a finger. A tap sends a ripple across the glass.
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

// The scene behind the pane, never sampled beyond its edges.
half4 scene(float2 p) {
    return content.eval(clamp(p, float2(0.5), resolution - 0.5));
}

// The scene out of focus: a disc of nine samples.
half4 defocused(float2 p, float r) {
    half4 c = scene(p) * 0.2;
    c += scene(p + float2(r, 0.0)) * 0.1;
    c += scene(p - float2(r, 0.0)) * 0.1;
    c += scene(p + float2(0.0, r)) * 0.1;
    c += scene(p - float2(0.0, r)) * 0.1;
    float d = r * 0.7071;
    c += scene(p + float2(d, d)) * 0.1;
    c += scene(p - float2(d, d)) * 0.1;
    c += scene(p + float2(d, -d)) * 0.1;
    c += scene(p + float2(-d, d)) * 0.1;
    return c;
}

// Ridged noise: sharp crests, like the veins of frost crystals.
float ridged(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        float n = 1.0 - abs(noise(p) * 2.0 - 1.0);
        v += a * n * n;
        p = p * 2.1 + float2(3.7, 1.9);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - 0.5 * resolution) / resolution.y;
    uv.y = -uv.y;
    float t = time * 0.2;
    float px = 1.0 / resolution.y;
    float bead = 0.0;
    float trail = 0.0;
    float2 slope = float2(0.0);

    if (drops > 0.01) {
        float staticDrops = smoothstep(-0.5, 1.0, drops) * 2.0;
        float layer1 = smoothstep(0.25, 0.75, drops);
        float layer2 = smoothstep(0.0, 0.5, drops);
        float2 c = Drops(uv, t, staticDrops, layer1, layer2);
        float cx = Drops(uv + float2(px, 0.0), t, staticDrops, layer1, layer2).x;
        float cy = Drops(uv + float2(0.0, px), t, staticDrops, layer1, layer2).x;
        bead = c.x;
        trail = c.y;
        // Height gradient per screen pixel (uv.y runs up, the screen down).
        slope = float2(cx - c.x, -(cy - c.x));
    }

    // A tap ripple: an expanding ring that bends light as it passes.
    float2 offset = float2(0.0);
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
    float wipeAmount = wipe.eval(fragCoord).a;
    float fogMask = fogged * (1.0 - max(smoothstep(0.1, 0.2, bead), trail)) * (1.0 - wipeAmount);
    half4 col;
    if (drops > 0.01) {
        // The bead's surface, steep at its rim like water on glass.
        float3 N = normalize(float3(-slope * 60.0, 1.0));
        float inside = smoothstep(0.02, 0.2, bead);
        // A bead is a tiny fisheye: it shows a wide view of the scene behind it, sharp and
        // upside down — the bright horizon at its top, the darker sky at its foot.
        half4 through = scene(p - N.xy * resolution.y * 0.2);
        // Around it, the weather is out of focus — except where sliding drops wiped the glass.
        float blur = resolution.y * 0.0055 * drops * (1.0 - trail * 0.85);
        half4 behind = blur > 0.75 ? defocused(p, blur) : scene(p);
        col = mix(behind, through, half(inside));
        if (inside > 0.001) {
            float3 L = normalize(float3(-0.3, -0.85, 0.6));
            float spec = pow(max(dot(N, normalize(L + float3(0.0, 0.0, 1.0))), 0.0), 26.0);
            float rim = smoothstep(0.35, 0.9, 1.0 - N.z);
            float up = max(-N.y, 0.0);
            float down = max(N.y, 0.0);
            // The refracting rim darkens, most at the foot; its top reflects the bright sky;
            // light focused through the bead gathers just inside its foot.
            col.rgb *= half(1.0 - rim * (0.16 + 0.3 * down) * inside);
            col.rgb += half3(rim * up * 0.14 * inside);
            col.rgb += half3((0.03 + 0.22 * down * (1.0 - rim) * smoothstep(0.3, 0.8, bead)) * inside);
            col.rgb += half3(spec * 0.85 * inside);
        }
    } else {
        col = scene(p);
    }

    // Condensation: a milky, out-of-focus pane beaded with a mist of micro-droplets.
    if (fogMask > 0.01) {
        half4 misted = defocused(p, resolution.y * 0.014) + half4(0.075, 0.08, 0.085, 0.0);
        float2 mp = fragCoord / (resolution.y * 0.006);
        float2 mid = floor(mp);
        float mr = hash(mid);
        float md = length(fract(mp) - float2(hash(mid + 1.9), hash(mid + 4.3)) * 0.6 - 0.2);
        float micro = mr > 0.55 ? smoothstep(0.26, 0.05, md) * (0.4 + 0.6 * hash(mid + 8.1)) : 0.0;
        misted.rgb += half3(micro * 0.09);
        col = mix(col, misted, half(fogMask * 0.92));
    }

    // Frost grows in from the frame: crystals branching inward along their veins, scattering the
    // light (the scene turns milky behind them), with the odd glint.
    if (frost > 0.01) {
        float2 q = fragCoord / resolution.y;
        float aspect = resolution.x / resolution.y;
        float edge = min(min(q.x, aspect - q.x), min(q.y, 1.0 - q.y));
        float reach = 0.075 * frost;
        if (edge < reach * 1.8) {
            float2 warp = float2(fbm3(q * 9.0), fbm3(q * 9.0 + 4.0)) * 2.2;
            float veins = ridged(q * 26.0 + warp);
            float n = fbm3(q * 6.0);
            float growth = smoothstep(reach, reach * 0.15, edge + (n - 0.5) * reach * 0.8 - veins * reach * 0.5) * (1.0 - wipeAmount);
            if (growth > 0.001) {
                // Matte ice with a fine grain; the crystal veins only a little brighter than it.
                float crystals = smoothstep(0.64, 0.96, veins);
                float grain = noise(fragCoord * 0.45) - 0.5;
                half4 scattered = defocused(p, resolution.y * 0.009);
                half3 ice = mix(scattered.rgb, half3(0.9, 0.94, 0.99), half(0.16 + 0.34 * growth));
                ice += half3(crystals * (0.12 + 0.16 * growth) + grain * 0.03);
                float2 cell = fragCoord / (resolution.y * 0.012);
                float2 cid = floor(cell);
                float glint = hash(cid) > 0.9 ? smoothstep(0.3, 0.0, length(fract(cell) - float2(hash(cid + 3.1), hash(cid + 5.7)))) : 0.0;
                ice += half3(glint * 0.7 * growth);
                col.rgb = mix(col.rgb, ice, half(growth * 0.9));
            }
        }
    }
    // The pane is opaque: whatever the samples at its borders held.
    return half4(col.rgb, 1.0);
}
"""
