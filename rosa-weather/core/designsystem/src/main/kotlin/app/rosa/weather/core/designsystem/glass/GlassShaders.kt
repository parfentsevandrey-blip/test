package app.rosa.weather.core.designsystem.glass

import org.intellij.lang.annotations.Language

/**
 * Liquid Glass optics in AGSL, following Apple's published behaviour (WWDC25 "Meet Liquid
 * Glass") and the reverse-engineered parameters of its `glassBackground` filter, traced the way
 * light goes through a real slab of glass with a rounded bevel — and lit by the scene itself:
 *
 *  - **A real bevel.** Its profile is a squircle, z = (1 − (1 − t)⁴)^¼: vertical at the rim,
 *    rounding over smoothly into a flat top. Every point of it has a 3-D normal from its slope,
 *    and all the light below is worked out from that normal.
 *  - **Refraction by Snell's law.** The view ray, straight down, bends where it enters the tilted
 *    surface (index 1.5) and runs on through the glass beneath; how far it travels sideways is how
 *    far the rim reaches for what it shows — outward, as Liquid Glass does, so the scene just past
 *    the edge is gathered and squeezed into the rim, like the edge of a thick lens, while the flat
 *    top stays true. The scene lies deeper than the glass: tilting the phone shifts it a little
 *    behind the pane (parallax).
 *  - **Dispersion** from each colour's own index: red bends a little less, blue a little more,
 *    so bright edges seen through the rim fringe finely — sampled only where the bevel bends
 *    light; the flat middle takes one sample.
 *  - **Fresnel reflection** (Schlick): the flat top reflects a trace of the sky; the steep rim
 *    becomes a mirror of the surroundings — the scene just beside the pane, reflected across its
 *    edge, fading into sky where the reflected ray runs too far to see anything nearby.
 *  - **A body with a tone of its own**: restrained vibrancy, milky or smoky glass, a little
 *    brighter toward the light, a fine grain that keeps smooth frost from banding.
 *  - **Light from the scene.** The light comes from the sun or moon where it is on screen, in its
 *    colour — gold low in the sky, white at noon, silver at night, soft under cloud — so every pane
 *    catches it on its own side and the highlights slide as it scrolls. Lit from above the glass,
 *    the bevel's normal throws a crisp specular highlight along the lit rim (its soft skirt a
 *    bloom) and a weaker one on the opposite rim: light that crossed the glass and reflected inside
 *    the far bevel. Light trapped in the slab escapes where the surface steepens, so the rim glows
 *    softly. Where the rim faces the sun a **glint** flares — a hot core, a streak along the edge
 *    and a shorter one across — spilling past the edge. The far bevel falls into shadow, and past
 *    it the pane focuses the light it gathered into a faint **caustic** on the scene.
 *  - **Reflections of the surroundings**, fixed in the world: soft bands of a window's light that
 *    stay put while the pane scrolls past them, and slide across it as the phone tilts.
 *  - **Weather on the glass**: lightning lights the whole pane, its rim most; on a freezing day
 *    frost creeps in from the rim in white veins.
 *  - **Liquid touch**: a finger presses the glass into a lens that swells what is under it and
 *    catches the light.
 *  - **Materialisation**: appearing grows the lensing (never plain alpha) while a sweep of light
 *    crosses the pane as it forms.
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
uniform float reach;
uniform float4 bounds;

// Crown glass. Red bends a little less than green, blue a little more: this much apart per unit
// of the style's dispersion.
const float IOR = 1.5;
const float IOR_SPREAD = 0.08;
// The bevel stands 1.2 times as tall as it is wide, on a slab one bevel width thick.
const float STEEP = 1.2;
const float BASE = 1.0;
// The farthest a view ray travels sideways through that glass (just inside the rim), in bevel
// widths: scaled by it, the rim reaches exactly `amount`.
const float TRAVEL = 1.488;
// The light stands about 20° above the glass.
const float LIGHT_RISE = 0.34;
const float LIGHT_SPREAD = 0.94;

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

// Snell's law for a view ray straight down onto a surface tilted by (cos c, sin s): the lean of
// the ray refracted into glass of index `ior` — how far it travels sideways per unit of depth.
float bend(float c, float s, float ior) {
    float eta = 1.0 / ior;
    float k = eta * c - sqrt(1.0 - eta * eta * s * s);
    return -k * s / (eta - k * c);
}

// A GGX lobe scaled to 1 at its peak: a crisp highlight with a soft skirt of bloom.
float lobe(float nh, float a2) {
    float q = nh * nh * (a2 - 1.0) + 1.0;
    return a2 * a2 / (q * q);
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - origin - hs;
    float d = sdRoundRect(p, hs, radius);
    float2 L = float2(cos(lightAngle), sin(lightAngle));
    half3 sun = mix(half3(1.0), lightColor.rgb, 0.65);
    // A softened normal (larger virtual radius) avoids a visible seam along the diagonals.
    float gr = min(radius * 1.5, min(hs.x, hs.y));
    float2 n = normalRoundRect(p, hs, gr);
    if (depth > 0.0) {
        n = normalize(n + depth * normalize(p + 0.0001));
    }
    float toward = dot(n, L);
    float sparkle = 0.0;
    if (lightPower * materialize > 0.05) {
        sparkle = glint(p, hs, radius, L, 2.4 * px) * lightPower * materialize * highlight * 0.9;
    }
    if (d > 1.0) {
        // Outside the glass: the glint's bloom, and the caustic — the light the pane gathered,
        // focused into a soft crescent on the scene just past its far side.
        float g = clamp(sparkle, 0.0, 1.0) * (1.0 - smoothstep(0.0, 12.0 * px, d));
        float focus = 0.0;
        if (toward < 0.0 && lightPower * materialize > 0.02) {
            float zr = (d - 4.5 * px) / (3.0 * px);
            focus = smoothstep(0.0, 2.0 * px, d) * exp(-zr * zr) * pow(-toward, 3.0) * (1.0 - smoothstep(0.5 * reach, reach, d))
                * lightPower * materialize * highlight * 0.22;
        }
        half3 gold = mix(half3(1.0), lightColor.rgb, 0.85);
        half cover = half(min(g + focus, 1.0));
        return half4(min(sun * half(g) + gold * half(focus), half3(cover)), cover);
    }

    // The bevel: t runs from 0 at the rim to 1 where the flat top begins; z is the squircle's
    // height there, and its slope tilts the surface — vertical at the rim, flat on the top.
    float e = -d;
    float t = clamp(e / max(bezel, 1.0), 0.0, 1.0);
    float u = 1.0 - t;
    float u2 = u * u;
    float w = max(1.0 - u2 * u2, 0.0001);
    float z = sqrt(sqrt(w));
    float slope = STEEP * u2 * u * z / w;
    float c = inversesqrt(1.0 + slope * slope);
    float sn = slope * c;
    float3 N = float3(n * sn, c);

    // Refraction: the view ray bends where it enters the tilted surface and runs on through the
    // glass beneath it, sideways by the bend times the glass's thickness there.
    float thick = BASE + STEEP * z;
    float travel = amount * materialize * thick / TRAVEL;
    float dg = travel * bend(c, sn, IOR);
    // Shifted a little with the phone's tilt: the scene lies deeper.
    float2 s = coord + parallax * materialize;

    // A finger presses the glass into a lens: what is under it swells.
    float press = 0.0;
    if (touch.z > 0.0) {
        float2 tv = coord - origin - touch.xy;
        float sig = min(size.x, size.y) * 0.32 + 8.0 * px;
        press = exp(-dot(tv, tv) / (2.0 * sig * sig)) * touch.z;
        s -= tv * press * 0.2;
    }

    // Samples stay where the layer holds the scene: up to `reach` past the shape, less where the
    // scene itself ends (a bar at the top of the screen).
    float2 lo = bounds.xy;
    float2 hi = bounds.zw;
    half4 col;
    // Dispersion only where the bevel actually bends light: the flat middle takes one sample.
    // Each sample carries a band of the spectrum, not a bare primary — neighbours overlap, as the
    // eye's own responses do — so a thin line squeezed into the rim fringes in a soft rainbow
    // rather than splitting into neon threads.
    float spread = IOR_SPREAD * dispersion;
    if (dg * spread > 0.2) {
        half4 g = content.eval(clamp(s + n * dg, lo, hi));
        half3 r = content.eval(clamp(s + n * (travel * bend(c, sn, IOR - spread)), lo, hi)).rgb;
        half3 b = content.eval(clamp(s + n * (travel * bend(c, sn, IOR + spread)), lo, hi)).rgb;
        col = half4(
            0.7 * r.r + 0.3 * g.r,
            0.2 * r.g + 0.6 * g.g + 0.2 * b.g,
            0.3 * g.b + 0.7 * b.b,
            g.a);
    } else {
        col = content.eval(clamp(s + n * dg, lo, hi));
    }

    // The body: restrained vibrancy, the glass's own tone, a little brighter toward the light.
    col.rgb = vibrance(col.rgb, mix(1.0, saturation, materialize));
    col.rgb = mix(col.rgb, tint.rgb, tint.a * half(materialize));
    float up = clamp(0.5 - dot(p / max(hs, float2(1.0)), L) * 0.5, 0.0, 1.0);
    col.rgb += half(brightness * materialize * (0.55 + 0.9 * (1.0 - up)));

    // Light on the bevel, in the light's own colour.
    float lit = highlight * materialize * (1.0 + touch.z * 0.7) * (0.65 + 0.6 * lightPower);
    half3 white = mix(half3(1.0), clamp(vibrance(col.rgb, 1.6) * 1.35 + 0.08, 0.0, 1.0), 0.18);
    half3 shine = white * sun;
    half3 sky = clamp(skyColor.rgb * 1.15 + 0.06, 0.0, 1.0);
    half3 reflection = mix(white, sky, 0.35);
    // How strongly each part of the rim sees the light: most at the lit corner, a good part at the
    // opposite one, hardly at all along the sides in between.
    float facing = max(toward, 0.0);
    float opposite = max(-toward, 0.0);
    float rimLight = mix(0.2, 1.0, pow(facing, 1.4)) + 0.5 * opposite * opposite;

    // Fresnel (Schlick, F0 = 0.04): the flat top reflects a trace of sky, the steep rim becomes a
    // mirror. Its reflected ray runs down and outward and lands on the scene past the edge — the
    // flatter the rim, the farther; beyond what the layer holds, it sees only sky — low sky, paler
    // than the zenith (less so at night).
    float f1 = 1.0 - c;
    float f2 = f1 * f1;
    float fresnel = 0.04 + 0.96 * f2 * f2 * f1;
    half3 mirror = mix(sky, half3(1.0), half(mix(0.55, 0.25, darkness)));
    if (fresnel > 0.07) {
        float rise = 2.0 * c * c - 1.0;
        float run = rise < -0.02 ? thick * bezel * 2.0 * sn * c / -rise : 1.0e4;
        float past = max(run - e, e);
        float far = smoothstep(0.45 * reach, reach, past);
        if (far < 1.0) {
            half3 beside = content.eval(clamp(coord + parallax * materialize + n * (e + past), lo, hi)).rgb;
            mirror = mix(mix(beside, sky, 0.3), mirror, half(far));
        }
    }
    col.rgb = mix(col.rgb, mirror, half(fresnel * highlight * materialize * mix(0.85, 0.45, darkness)));

    // Thickness: the far side of the bevel falls a little into shadow, and the unlit rim darkens
    // to a hairline that keeps the pane crisp over bright backdrops.
    float steep = 1.0 - c;
    float edge = 1.0 - smoothstep(0.1, 1.4, e);
    col.rgb *= half(1.0 - 0.16 * steep * pow(opposite, 0.7) * (1.0 - darkness * 0.5) * materialize);
    col.rgb *= half(1.0 - 0.12 * (1.0 - smoothstep(0.4, 2.4, e)) * (1.0 - rimLight) * (1.0 - darkness) * materialize);

    // The light stands above the glass: a crisp highlight where the bevel's normal halves the
    // angle between it and the eye, and a weaker one on the opposite rim — light that crossed the
    // glass and reflected inside the far bevel, tinted by the body it went through.
    float3 H = normalize(float3(L * LIGHT_SPREAD, 1.0 + LIGHT_RISE));
    float3 Hi = normalize(float3(-L * LIGHT_SPREAD, 1.0 + LIGHT_RISE));
    float spec = lobe(max(dot(N, H), 0.0), 0.04);
    float inner = lobe(max(dot(N, Hi), 0.0), 0.06);
    col.rgb += shine * half(spec * lit * 0.6);
    col.rgb += mix(shine, col.rgb, 0.4) * half(inner * lit * 0.24);
    // Light trapped in the glass: what enters the top face is held by total internal reflection
    // and escapes where the surface steepens, so the rim glows softly — most toward the light, and
    // on the far side, where the light that crossed the pane comes out.
    float trapped = steep * steep * steep;
    col.rgb += shine * half(trapped * lit * (0.08 + 0.3 * pow(facing, 1.5) + 0.16 * opposite * opposite) * mix(1.0, 0.6, darkness));
    // Catch-light on the very edge: a razor line, brightest where the rim meets the light.
    float facing3 = facing * facing * facing;
    col.rgb += shine * half(edge * (0.8 * rimLight + 0.6 * facing3 * facing3) * lit * mix(0.55, 0.45, darkness));

    // Reflections of the surroundings, fixed in the world: they stay put as the pane scrolls past
    // and slide across it as the phone tilts.
    float2 world = (root + (coord - origin) + sheen) / px;
    float phase = fract((world.x * 0.55 + world.y * 0.83) / 560.0);
    float za = (phase - 0.32) / 0.07;
    float zc = (phase - 0.43) / 0.018;
    float bands = exp(-za * za) + 0.55 * exp(-zc * zc);
    col.rgb += reflection * half(bands * 0.045 * (1.0 - 0.45 * darkness) * materialize * (0.4 + 0.6 * t));

    // The pressed swell catches the light.
    col.rgb += shine * half(press * 0.1);

    // Lightning: the whole pane lights up, its rim most.
    if (flash > 0.0) {
        col.rgb += half3(0.8, 0.86, 1.0) * half(flash * (0.07 + 0.55 * steep * steep + 0.4 * edge * rimLight));
    }

    // Frost creeping in from the rim on a freezing day: white veins, densest at the edge. It
    // reaches in unevenly, never further than a narrow band, so a small pill keeps a clear face.
    if (frost > 0.01) {
        float band = min(18.0 * px, min(hs.x, hs.y) * 0.3);
        float creep = band * (0.5 + 0.5 * vnoise(coord / (26.0 * px)));
        float reachIn = 1.0 - smoothstep(0.0, creep, e);
        if (reachIn > 0.0) {
            float veins = smoothstep(0.62, 0.95, ridged(coord / (7.0 * px)));
            col.rgb = mix(col.rgb, half3(0.92, 0.95, 1.0), half(frost * reachIn * (0.18 + 0.55 * veins)));
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
