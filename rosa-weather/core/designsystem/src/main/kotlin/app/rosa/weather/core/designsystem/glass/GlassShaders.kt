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
 *    tilting the phone shifts it a little behind the pane (parallax). Samples never leave the
 *    part of the layer that holds the scene.
 *  - **A body with a tone of its own**: restrained vibrancy, milky or smoky glass, a little
 *    brighter toward the light with a broad sheen from that side, a fine grain that keeps smooth
 *    frost from banding.
 *  - **A rim that reads all the way round.** Apple's glass is lit on two corners: the one facing
 *    the light and, where the light that crossed the glass comes out, the far one. Between them
 *    the rim never goes out: every pane keeps its edge. From the edge inward: a crisp catch-light,
 *    a luminous band reflecting the sky (split by the bevel into a faint rainbow), and light held
 *    in the glass glowing just inside it — thickness — with a specular streak along the lit bevel
 *    and its twin across.
 *  - **Light from the scene.** The light comes from the sun or moon where it is on screen, in its
 *    colour — gold low in the sky, white at noon, silver at night, soft under cloud — so every pane
 *    catches it on its own side and the highlights slide as it scrolls. Where the rim faces the
 *    sun a **glint** flares — a hot core, a streak along the edge and a shorter one across —
 *    spilling past the edge. The far bevel falls a little into shadow.
 *  - **Reflections of the surroundings**, fixed in the world: soft bands of a window's light that
 *    stay put while the pane scrolls past them, and slide across it as the phone tilts.
 *  - **Weather on the glass**: lightning lights the whole pane, its rim most; on a freezing day
 *    frost creeps in from the rim in white veins. While it rains, water beads along the top edge of
 *    every card — little lenses with a dark rim, a glint toward the light and a caustic at their
 *    foot, some grown heavy and about to drip; while it snows, a lumpy cap of snow settles on top.
 *  - **Colour in the rim that moves**: the faint rainbow the bevel splits the light into slides
 *    along the edge as the phone tilts.
 *  - **Liquid touch**: a finger presses the glass into a lens that swells what is under it and
 *    catches the light; the light of the touch runs round the rim from the finger both ways, and
 *    let go, the lens springs back like a gel — past flat into a dimple and back.
 *  - **Living glass** (while [alive]): the light plays in it — sunlight rippling through the lit
 *    bevel, the sky's clouds drifting across it, the prism colours of the rim flowing — the sun's
 *    glint shimmers, stars glint in the rims at night; rain lands on the panes and dries, drops
 *    grow heavy and run down in jerks leaving droplets behind; snowflakes settle and melt; frost
 *    glitters. Moved, the glass is liquid: what it holds lags behind the motion and piles up at
 *    the leading edge, which thickens and brightens, then catches up and settles.
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
uniform float wet;
uniform float snowCap;
uniform float2 parallax;
uniform float2 root;
uniform float2 sheen;
uniform float px;
uniform float4 bounds;
// x: the lens under the finger (signed: it springs past flat on release), y: 0..1 the touch's
// light running round the rim.
uniform float2 gel;
uniform float time;
uniform float alive;
// xy: where the liquid lags behind the pane's motion (px), z: how far, 0..1.
uniform float3 flow;
uniform float stars;
uniform float clouds;
uniform float wind;

// How much of its light the rim keeps along the sides, away from both lit corners.
const float RIM_FLOOR = 0.4;

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

// Arc length along the rim of a rounded rectangle (half size hs, corner radius r), clockwise from
// the middle of its top edge, of the rim point nearest to p (y grows downward).
float rimCoord(float2 p, float2 hs, float r) {
    float a = max(hs.x - r, 0.0);
    float b = max(hs.y - r, 0.0);
    float quarter = a + 1.5707963 * r + b;
    float2 q = abs(p);
    float s;
    if (q.x > a && q.y > b) {
        s = a + r * (1.5707963 - atan(q.y - b, q.x - a));
    } else if (q.x <= a && (q.y > b || hs.y - q.y < hs.x - q.x)) {
        s = q.x;
    } else {
        s = a + 1.5707963 * r + (b - q.y);
    }
    if (p.x >= 0.0 && p.y < 0.0) {
        return s;
    }
    if (p.x >= 0.0) {
        return 2.0 * quarter - s;
    }
    if (p.y >= 0.0) {
        return 2.0 * quarter + s;
    }
    return 4.0 * quarter - s;
}

// A drop of water on the glass at c (radii rad, px): a little lens showing what is behind it upside
// down, darker toward its rim, with a glint toward the light and a caustic at its foot. Returns
// its colour and, in alpha, how much of this pixel it covers.
half4 drop(float2 coord, float2 p, float2 c, float2 rad, float2 L, half3 sun, float2 lo, float2 hi) {
    float2 v = (p - c) / rad;
    float rho = length(v);
    float cover = 1.0 - smoothstep(0.82, 1.0, rho);
    if (cover <= 0.0) {
        return half4(0.0);
    }
    half4 seen = content.eval(clamp(coord - v * rad * 1.7, lo, hi));
    half3 bead = mix(seen.rgb, seen.rgb * 0.5, half(smoothstep(0.45, 1.0, rho) * 0.75));
    float glintB = exp(-dot(v - L * 0.42, v - L * 0.42) / 0.035);
    float causticB = exp(-dot(v + L * 0.5, v + L * 0.5) / 0.06);
    bead += sun * half(glintB * 0.95 + causticB * 0.28);
    return half4(clamp(bead, half3(0.0), half3(1.0)), half(cover));
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
        // Living, the glint shimmers: never quite steady, like the sun on real glass.
        sparkle *= 1.0 + alive * (0.16 * sin(time * 1.9) + 0.09 * sin(time * 4.7 + 1.3));
    }
    if (d > 1.0) {
        // Outside the glass only the glint's bloom shows.
        float g = clamp(sparkle, 0.0, 1.0) * (1.0 - smoothstep(0.0, 12.0 * px, d));
        return half4(sun * half(g), half(g));
    }
    // A softened normal (larger virtual radius) avoids a visible seam along the diagonals.
    float gr = min(radius * 1.5, min(hs.x, hs.y));
    float2 n = normalRoundRect(p, hs, gr);
    if (depth > 0.0) {
        n = normalize(n + depth * normalize(p + 0.0001));
    }
    // Liquid in motion: the glass piles up against the edge leading the way, which thickens.
    float lead = 0.0;
    if (flow.z > 0.0) {
        lead = dot(n, -flow.xy / max(length(flow.xy), 0.001));
    }
    float bevelNow = bezel * (1.0 + 0.5 * flow.z * max(lead, 0.0));
    // x runs 1 at the rim to 0 where the bevel meets the flat top; h is the bevel's height there.
    float t = clamp(-d / max(bevelNow, 1.0), 0.0, 1.0);
    float x = 1.0 - t;
    float h = sqrt(max(0.0, 1.0 - x * x));
    float m = amount * materialize * (1.0 - h);

    // Bent at the rim, and shifted a little with the phone's tilt: the scene lies deeper. Moving,
    // what the glass holds lags behind its motion, most in the bevel.
    float2 s = coord + n * m + parallax * materialize + flow.xy * (0.15 + 0.85 * (1.0 - h)) * materialize;

    // A finger presses the glass into a lens: what is under it swells. Let go, it springs back
    // like a gel: past flat into a slight dimple (a negative lens) and back.
    float press = 0.0;
    if (gel.x != 0.0) {
        float2 tv = coord - origin - touch.xy;
        float sig = min(size.x, size.y) * 0.32 + 8.0 * px;
        press = exp(-dot(tv, tv) / (2.0 * sig * sig)) * gel.x;
        s -= tv * press * 0.2;
    }

    // Samples stay where the layer holds the scene: never the empty layer past its edge (a bar
    // at the top of the screen), which would come out as a bright line along the rim.
    float2 lo = bounds.xy;
    float2 hi = bounds.zw;
    half4 col;
    // Dispersion only where the bezel actually bends light: the flat middle takes one sample.
    if (dispersion > 0.0 && m > 0.3) {
        float corner = abs(p.x * p.y) / max(hs.x * hs.y, 1.0);
        float2 dv = n * m * dispersion * (0.12 + 0.35 * corner);
        half4 g = content.eval(clamp(s, lo, hi));
        col = half4(content.eval(clamp(s + dv, lo, hi)).r, g.g, content.eval(clamp(s - dv, lo, hi)).b, g.a);
    } else {
        col = content.eval(clamp(s, lo, hi));
    }

    float toward = dot(n, L);

    // The body: restrained vibrancy, the glass's own tone, a little brighter toward the light.
    col.rgb = vibrance(col.rgb, mix(1.0, saturation, materialize));
    col.rgb = mix(col.rgb, tint.rgb, tint.a * half(materialize));
    float up = clamp(0.5 - dot(p / max(hs, float2(1.0)), L) * 0.5, 0.0, 1.0);
    col.rgb += half(brightness * materialize * (0.55 + 0.9 * (1.0 - up)));
    // A broad sheen across the glass from the side the light falls on.
    float sheenLit = (1.0 - up) * (1.0 - up);
    col.rgb += mix(half3(1.0), lightColor.rgb, 0.5) * half(sheenLit * highlight * materialize * (0.035 + 0.05 * lightPower) * (1.0 - 0.5 * darkness));

    // Light on the bevel, in the light's own colour.
    float lit = highlight * materialize * (1.0 + touch.z * 0.7) * (0.65 + 0.6 * lightPower);
    half3 white = mix(half3(1.0), clamp(vibrance(col.rgb, 1.6) * 1.35 + 0.08, 0.0, 1.0), 0.18);
    half3 shine = white * sun;
    half3 reflection = mix(white, clamp(skyColor.rgb * 1.2 + 0.1, 0.0, 1.0), 0.35);
    // How strongly each part of the rim sees the light: most at the corner facing it, nearly as
    // much at the far one, where the light that crossed the glass comes out; along the sides in
    // between the rim keeps a good part of it, so every pane keeps its edge.
    float facing = max(toward, 0.0);
    float opposite = max(-toward, 0.0);
    float rimLight = RIM_FLOOR + (1.0 - RIM_FLOOR) * (pow(facing, 1.2) + 0.75 * pow(opposite, 1.5));
    // Fresnel: the steep outer rim reflects the sky in a luminous band; the flat top barely does.
    float fresnel = pow(1.0 - h, 2.4);
    // The bevel splits the light it bends: a faint rainbow runs across the band, from the edge in.
    float u = clamp(t / 0.22, 0.0, 1.0);
    // It slides as the phone tilts, and while the glass lives it flows slowly on its own.
    float slide = (parallax.x - parallax.y) / max(px, 1.0) * 0.06 + time * 0.02 * alive;
    half3 prism = half3(0.5 + 0.5 * cos(6.2832 * (u * 0.8 + slide + float3(0.0, 0.33, 0.67))));
    half3 edgeLight = mix(reflection, reflection * (0.7 + 0.6 * prism), half(0.4 * min(dispersion, 1.0)));
    col.rgb += edgeLight * half(fresnel * rimLight * lit * mix(0.7, 0.5, darkness));
    // Catch-light on the very edge: a fine line all the way round, brightest where the rim meets the light.
    float edge = 1.0 - smoothstep(0.1, max(1.4, 0.55 * px), -d);
    col.rgb += shine * half(edge * (rimLight + 0.6 * pow(facing, 6.0)) * lit * mix(0.62, 0.52, darkness));
    // Light held in the glass glows in a band just inside the rim: thickness, all the way round.
    float zg = (t - 0.1) / 0.09;
    float held = exp(-zg * zg);
    col.rgb += mix(shine, reflection, 0.6) * half(held * lit * (0.07 + 0.22 * pow(facing, 1.3) + 0.12 * pow(opposite, 1.5)));
    // The specular streak along the bevel just inside the rim, and its twin on the far side.
    float z = (x - 0.8) / 0.14;
    float band = exp(-z * z);
    float streak = band * (pow(facing, 1.8) + 0.5 * pow(opposite, 2.2));
    col.rgb += shine * half(streak * lit * 0.36);
    // Bloom: the brightest light spills softly around the streak.
    float zb = (x - 0.8) / 0.34;
    col.rgb += shine * half(exp(-zb * zb) * pow(facing, 2.0) * lit * 0.07 * (0.5 + lightPower));
    // Thickness: the far side of the bevel falls a little into shadow, and the unlit rim darkens
    // to a hairline that keeps the pane crisp over bright backdrops.
    col.rgb *= half(1.0 - 0.16 * (1.0 - h) * pow(opposite, 0.7) * (1.0 - darkness * 0.5) * materialize);
    col.rgb *= half(1.0 - 0.12 * (1.0 - smoothstep(0.4, 2.4, -d)) * (1.0 - rimLight) * (1.0 - darkness) * materialize);

    // Reflections of the surroundings, fixed in the world: they stay put as the pane scrolls past
    // and slide across it as the phone tilts.
    float2 world = (root + (coord - origin) + sheen) / px;
    float phase = fract((world.x * 0.55 + world.y * 0.83) / 560.0);
    float za = (phase - 0.32) / 0.07;
    float zc = (phase - 0.43) / 0.018;
    float mirror = exp(-za * za) + 0.55 * exp(-zc * zc);
    col.rgb += reflection * half(mirror * 0.045 * (1.0 - 0.45 * darkness) * materialize * (0.4 + 0.6 * t));

    // Living glass: light at play in it.
    if (alive > 0.0) {
        // The sky's clouds drifting across the glass, reflected: soft light, slowly passing.
        float2 cq = world * 0.0042 + float2(time * (0.006 + 0.022 * wind), time * 0.0018);
        float cr = vnoise(cq) * 0.62 + vnoise(cq * 2.3 + 5.2) * 0.38;
        col.rgb += reflection * half(smoothstep(0.42, 0.8, cr) * alive * (0.012 + 0.03 * clouds) * (0.35 + 0.65 * max(fresnel, sheenLit)) * (1.0 - 0.45 * darkness) * materialize);
        // Sunlight rippling through the glass as through water: soft patches of light, slowly
        // moving, brightest in the bevel facing the sun and fading across the pane away from it.
        if (lightPower > 0.05) {
            float2 wq = world / 44.0;
            float tt = time * 0.33;
            float w1 = sin(wq.x * 1.25 + tt + 1.6 * sin(wq.y * 0.85 - tt * 0.7));
            float w2 = sin(wq.y * 1.15 - tt * 0.9 + 1.8 * sin(wq.x * 0.95 + tt * 0.5));
            float ripple = pow(clamp(1.0 - abs(w1 + w2) * 0.5, 0.0, 1.0), 3.5);
            float zone = (1.0 - smoothstep(0.0, 1.0, t)) * (0.25 + 0.75 * pow(facing, 0.8)) + 0.6 * pow(1.0 - up, 1.8);
            col.rgb += sun * half(ripple * zone * lightPower * alive * 0.2 * (1.0 - 0.45 * darkness) * materialize);
        }
        // A clear night: stars glinting in the rim, each twinkling in its own time.
        if (stars > 0.02 && -d < 9.0 * px) {
            float sRim = rimCoord(p, hs, radius);
            float cellL = 22.0 * px;
            float k = floor(sRim / cellL);
            float hk = hash21(float2(k, 91.7 + size.x * 0.013 + size.y * 0.007));
            if (hk < 0.24) {
                float sc = (k + 0.5 + (hash21(float2(k, 3.3)) - 0.5) * 0.6) * cellL;
                float inset = mix(1.2, 4.6, hash21(float2(k, 8.1))) * px;
                float2 sv = float2(sRim - sc, -d - inset) / px;
                float tw = 0.5 + 0.5 * sin(time * (0.8 + 4.0 * hk) + hk * 50.0);
                tw = tw * tw;
                float core = exp(-dot(sv, sv) / 0.6);
                float spikes = exp(-abs(sv.x) / 2.2 - abs(sv.y) / 0.22) + exp(-abs(sv.y) / 2.2 - abs(sv.x) / 0.22);
                col.rgb += half3(0.9, 0.93, 1.0) * half((core + 0.6 * spikes) * tw * stars * alive * materialize);
            }
        }
    }
    // Moving: the leading edge brightens as the glass piles up against it; the trailing one dims.
    if (flow.z > 0.0) {
        col.rgb += reflection * half(fresnel * max(lead, 0.0) * flow.z * 0.4);
        col.rgb *= half(1.0 - 0.07 * fresnel * max(-lead, 0.0) * flow.z);
    }

    // The pressed swell catches the light.
    col.rgb += shine * half(max(press, 0.0) * 0.1);

    // Lightning: the whole pane lights up, its rim most.
    if (flash > 0.0) {
        col.rgb += half3(0.8, 0.86, 1.0) * half(flash * (0.07 + 0.55 * fresnel + 0.4 * edge * rimLight));
    }

    // Frost creeping in from the rim on a freezing day: white veins, densest at the edge. It
    // reaches in unevenly, never further than a narrow band, so a small pill keeps a clear face.
    if (frost > 0.01) {
        float band = min(18.0 * px, min(hs.x, hs.y) * 0.3);
        float creep = band * (0.5 + 0.5 * vnoise(coord / (26.0 * px)));
        float reachIn = 1.0 - smoothstep(0.0, creep, -d);
        if (reachIn > 0.0) {
            float veins = smoothstep(0.62, 0.95, ridged(coord / (7.0 * px)));
            col.rgb = mix(col.rgb, half3(0.92, 0.95, 1.0), half(frost * reachIn * (0.18 + 0.55 * veins)));
            // Living: ice crystals in the veins glitter as the light shifts.
            if (alive > 0.0) {
                float2 gq = coord / (2.4 * px);
                float gh = hash21(floor(gq));
                if (gh > 0.9) {
                    float2 gf = fract(gq) - 0.5;
                    float tw = pow(0.5 + 0.5 * sin(time * (1.2 + 2.6 * gh) + gh * 70.0), 16.0);
                    col.rgb += half3(exp(-dot(gf, gf) * 22.0) * tw * veins * reachIn * frost * alive * 0.9);
                }
            }
        }
    }

    // Rain: water beads along the top edge of a card. Each is a little lens: it shows what is
    // behind it upside down, darkens toward its rim, catches a glint toward the light and gathers
    // a caustic at its foot. Some have grown heavy and hang long, about to drip.
    float wide = smoothstep(96.0 * px, 150.0 * px, size.x);
    if (wet > 0.01 && wide > 0.0 && p.y < -hs.y + 16.0 * px) {
        float cellW = 15.0 * px;
        float gx = (p.x + hs.x) / cellW;
        float gi = floor(gx);
        for (int k = -1; k <= 1; k++) {
            float i = gi + float(k);
            float seed = hash21(float2(i, 7.3));
            if (seed > 0.25 + 0.55 * wet) {
                continue;
            }
            float cx = (i + 0.5 + (hash21(float2(i, 3.1)) - 0.5) * 0.6) * cellW - hs.x;
            // Never on the rounded corners: only along the straight top edge.
            if (abs(cx) > hs.x - radius - 3.0 * px) {
                continue;
            }
            float r = mix(1.8, 4.4, hash21(float2(i, 1.7))) * px * (0.75 + 0.45 * wet);
            float hang = 1.0 + 0.8 * step(0.82, hash21(float2(i, 9.9)));
            float2 c = float2(cx, -hs.y + r * hang * 0.95 + 1.2 * px);
            float2 v = (p - c) / float2(r, r * hang);
            float rho = length(v);
            float cover = (1.0 - smoothstep(0.86, 1.0, rho)) * wide;
            if (cover > 0.0) {
                half4 seen = content.eval(clamp(coord - v * r * 1.7, lo, hi));
                half3 bead = mix(seen.rgb, seen.rgb * 0.5, half(smoothstep(0.45, 1.0, rho) * 0.75));
                float glintB = exp(-dot(v - L * 0.42, v - L * 0.42) / 0.035);
                float causticB = exp(-dot(v + L * 0.5, v + L * 0.5) / 0.06);
                bead += sun * half(glintB * 0.95 + causticB * 0.28);
                col.rgb = mix(col.rgb, clamp(bead, half3(0.0), half3(1.0)) * col.a, half(cover * 0.92));
            }
        }
    }

    // Snow settling on the top of a card: a lumpy white cap, lit on top and bluish in its depth,
    // glittering here and there, casting a soft shadow on the glass just under it.
    if (snowCap > 0.01 && p.y < -hs.y + 18.0 * px) {
        float capW = smoothstep(56.0 * px, 96.0 * px, size.x);
        float depthCap = snowCap * capW * px * (3.0 + 4.6 * vnoise(float2(p.x / (12.0 * px), 1.3)) + 1.6 * vnoise(float2(p.x / (3.8 * px), 7.1)));
        float upward = smoothstep(-0.15, -0.65, n.y);
        float below = -d - depthCap;
        if (depthCap > 0.3 * px && upward > 0.0) {
            float inCap = (1.0 - smoothstep(-0.7 * px, 0.7 * px, below)) * upward;
            float shade = (1.0 - smoothstep(0.0, 3.5 * px, below)) * step(0.0, below) * upward;
            col.rgb *= half(1.0 - 0.18 * shade * snowCap);
            if (inCap > 0.0) {
                float into = clamp(-d / max(depthCap, 0.001), 0.0, 1.0);
                half3 snowCol = mix(half3(1.0), half3(0.74, 0.8, 0.92), half(pow(into, 1.4) * 0.85));
                snowCol += half3(0.08) * half(1.0 - smoothstep(0.0, 0.35, into));
                float glitter = step(0.982, hash21(floor(coord / (1.4 * px)))) * 0.4;
                snowCol += half3(glitter);
                col.rgb = mix(col.rgb, clamp(snowCol, half3(0.0), half3(1.0)) * col.a, half(inCap * 0.97));
            }
        }
    }

    // Living rain: drops land all over the pane and dry away; now and then one grows heavy and runs
    // down in jerks, stretched while it moves, leaving a wet trail and droplets that dry after it.
    if (wet > 0.01 && alive > 0.0 && wide > 0.0) {
        float2 lp = coord - origin;
        float cellS = 12.0 * px;
        float2 ci = floor(lp / cellS);
        if (hash21(ci + 17.13) < 0.08 + 0.36 * wet) {
            float period = mix(4.5, 10.0, hash21(ci + 3.31));
            float cyc = time / period + hash21(ci + 9.17);
            float ph = fract(cyc);
            float n0 = floor(cyc);
            float2 jit = float2(hash21(ci + n0 * 1.37 + 0.5), hash21(ci + n0 * 2.71 + 4.0)) - 0.5;
            float2 c = (ci + 0.5 + jit * 0.4) * cellS;
            float grow = smoothstep(0.0, 0.025, ph);
            float dry = 1.0 - smoothstep(0.55, 0.98, ph);
            float hr = hash21(ci + n0 * 0.73 + 7.0);
            float r = mix(1.2, 3.3, hr * hr) * px * grow * (0.35 + 0.65 * dry);
            if (r > 0.3 * px) {
                half4 dd = drop(coord, lp, c, float2(r), L, sun, lo, hi);
                col.rgb = mix(col.rgb, dd.rgb * col.a, dd.a * half(0.9 * alive * min(1.0, dry * 3.0)));
            }
        }
        float laneW = 34.0 * px;
        float li = floor(lp.x / laneW);
        float hl = hash21(float2(li, 51.3));
        if (hl < 0.3 + 0.5 * wet && size.y > 56.0 * px) {
            float speed = mix(26.0, 52.0, hash21(float2(li, 7.7)));
            float travel = size.y / px + 24.0;
            float runT = travel / speed;
            float period = runT + mix(1.5, 4.5, hash21(float2(li, 2.9)));
            float cyc = time / period + hl * 7.0;
            float n0 = floor(cyc);
            float ph = fract(cyc) * period;
            float u = clamp(ph / runT, 0.0, 1.0);
            float jerks = 4.0 + floor(hash21(float2(li, n0)) * 4.0);
            float w = u * jerks;
            float beat = fract(w);
            float stepU = (floor(w) + smoothstep(0.3, 1.0, beat)) / jerks;
            if (u >= 1.0) {
                stepU = 1.0;
            }
            float yNow = -12.0 + stepU * travel;
            float x0 = (li + 0.5) * 34.0 + 9.0 * (hash21(float2(li, n0 + 0.5)) - 0.5);
            float phase = hl * 20.0 + n0;
            float xNow = x0 + 2.5 * sin(yNow * 0.07 + phase) + wind * yNow * 0.2;
            float moving = smoothstep(0.3, 0.55, beat) * (1.0 - smoothstep(0.85, 1.0, beat)) * step(u, 0.999);
            float rd = mix(2.4, 3.4, hash21(float2(li, 4.4)));
            float2 lpd = lp / px;
            // The trail it leaves: a wet streak and droplets every few dp, drying.
            float yh = lpd.y;
            if (yh < yNow - rd * 0.5 && yh > -12.0) {
                float xh = x0 + 2.5 * sin(yh * 0.07 + phase) + wind * yh * 0.2;
                float dx = lpd.x - xh;
                if (abs(dx) < 3.0) {
                    float age = (yNow - yh) / speed + max(ph - runT, 0.0);
                    float wetness = exp(-age / 1.2) * (1.0 - smoothstep(period - 0.8, period, ph));
                    // The wet streak: glass cleared of its mist, a line darker and clearer than the rest.
                    col.rgb = mix(col.rgb, col.rgb * 0.84 + half3(0.05) * col.a, half(exp(-dx * dx / 0.7) * wetness * 0.65 * alive));
                    float kk = floor(yh / 5.0);
                    float hk = hash21(float2(li * 13.0 + kk, n0 + 0.25));
                    if (hk < 0.6) {
                        float rk = mix(0.45, 1.1, hk / 0.6) * sqrt(wetness);
                        if (rk > 0.25) {
                            float yk = (kk + 0.5) * 5.0;
                            float2 ck = float2(x0 + 2.5 * sin(yk * 0.07 + phase) + wind * yk * 0.2 + (hk - 0.3) * 2.0, yk) * px;
                            half4 dk = drop(coord, lp, ck, float2(rk * px), L, sun, lo, hi);
                            col.rgb = mix(col.rgb, dk.rgb * col.a, dk.a * half(0.85 * alive));
                        }
                    }
                }
            }
            if (u < 1.0) {
                float2 cd = float2(xNow, yNow) * px;
                float2 rad = float2(rd * (1.0 - 0.14 * moving), rd * (1.0 + 0.4 * moving)) * px;
                half4 dd = drop(coord, lp, cd, rad, L, sun, lo, hi);
                col.rgb = mix(col.rgb, dd.rgb * col.a, dd.a * half(0.92 * alive));
            }
        }
    }

    // Living snow: flakes land on the pane and glint, then melt into drops that dry away.
    if (snowCap > 0.01 && alive > 0.0 && wide > 0.0) {
        float2 lp = coord - origin;
        float cellS = 18.0 * px;
        float2 ci = floor(lp / cellS);
        if (hash21(ci + 31.7) < 0.1 + 0.36 * snowCap) {
            float period = mix(6.0, 13.0, hash21(ci + 5.3));
            float cyc = time / period + hash21(ci + 1.9);
            float ph = fract(cyc);
            float n0 = floor(cyc);
            float2 jit = float2(hash21(ci + n0 * 1.1 + 3.0), hash21(ci + n0 * 1.7 + 8.0)) - 0.5;
            float2 c = (ci + 0.5 + jit * 0.4) * cellS;
            float2 v = (lp - c) / px;
            float land = smoothstep(0.0, 0.02, ph);
            float melt = smoothstep(0.45, 0.62, ph);
            float sz = mix(2.0, 3.2, hash21(ci + n0 * 0.37 + 2.0));
            float rr = length(v);
            float ang = atan(v.y, v.x) + hash21(ci + n0) * 6.2832;
            float arms = pow(abs(cos(ang * 3.0)), 10.0) * (1.0 - smoothstep(sz * 0.9, sz * 1.5, rr));
            float core = exp(-rr * rr / (0.3 * sz * sz));
            float flake = clamp(core + arms * 0.8, 0.0, 1.0) * land * (1.0 - melt);
            float fresh = exp(-rr * rr / 0.2) * (1.0 - smoothstep(0.02, 0.12, ph)) * land;
            // A faint shade just under the flake, so it reads on milky glass as on dark.
            float2 vs = v - float2(0.35, 0.55);
            float shade = exp(-dot(vs, vs) / (0.5 * sz * sz)) * land * (1.0 - melt);
            col.rgb *= half(1.0 - 0.16 * shade * (1.0 - darkness) * alive);
            col.rgb = mix(col.rgb, half3(0.97, 0.98, 1.0) * col.a, half(flake * 0.9 * alive));
            col.rgb += half3(fresh * 0.6 * alive);
            if (melt > 0.0) {
                float gone = 1.0 - smoothstep(0.8, 1.0, ph);
                float rD = sz * 0.55 * px * melt * gone;
                if (rD > 0.25 * px) {
                    half4 dd = drop(coord, lp, c, float2(rD), L, sun, lo, hi);
                    col.rgb = mix(col.rgb, dd.rgb * col.a, dd.a * half(0.85 * alive));
                }
            }
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
        float glowReach = max(size.x, size.y) * 0.85;
        float glow = 1.0 - smoothstep(0.0, glowReach, length(coord - origin - touch.xy));
        col.rgb += half3(0.16 * glow * glow * touch.z + 0.05 * touch.z);
        // The touch's light runs round the rim from the finger, both ways, fading as it goes.
        if (gel.y > 0.0) {
            float per = 4.0 * (max(hs.x - radius, 0.0) + 1.5707963 * radius + max(hs.y - radius, 0.0));
            float ds = abs(rimCoord(p, hs, radius) - rimCoord(touch.xy - hs, hs, radius));
            ds = min(ds, per - ds);
            float reach = mix(12.0 * px, per * 0.5, gel.y);
            float run = (1.0 - smoothstep(reach * 0.6, reach, ds)) * (1.0 - 0.7 * ds / max(per * 0.5, 1.0));
            // Its front runs ahead, brightest: the light visibly travels.
            float zf = (ds - reach * 0.82) / max(reach * 0.16, 4.0 * px);
            float front = exp(-zf * zf) * (1.0 - gel.y * 0.6);
            // It floods the rim: the edge line, the bevel's band and a soft glow just inside.
            float inner = exp(d / (7.0 * px));
            float rimBand = edge + 0.8 * fresnel + 0.5 * held + 0.45 * inner;
            col.rgb += shine * half((run + 0.9 * front) * rimBand * touch.z * 0.5);
        }
    }

    // The glint where the sun catches the rim.
    col.rgb += sun * half(min(sparkle, 1.4));

    // Fine grain (interleaved gradient noise): frosted glass has a tooth, and smooth frost bands.
    if (grain > 0.0) {
        float ign = fract(52.9829189 * fract(dot(coord, float2(0.06711056, 0.00583715))));
        col.rgb += half((ign - 0.5) * grain * materialize);
    }

    // Light stacks up to white and no further: the result stays a valid premultiplied colour.
    col.rgb = clamp(col.rgb, half3(0.0), half3(col.a));
    float alpha = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(col.rgb * half(alpha), col.a * half(alpha));
}
"""
