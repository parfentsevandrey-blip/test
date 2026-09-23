package app.papersky.weather.scene

import android.graphics.RuntimeShader
import android.os.Build

/**
 * GPU shaders for the effects that paper alone cannot fake (DESIGN_DOCTRINE §10): fire, drifting
 * mist and light playing on water. They only run on hardware canvases (Android 13+); software
 * canvases — widget bitmaps, thumbnails, tests — and older phones get the Canvas versions drawn by
 * the renderers. A shader that fails to compile is simply never used.
 */
object Shaders {
    private const val NOISE = """
        float hash(float2 p) {
            p = fract(p * float2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }
        float noise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + float2(1.0, 0.0));
            float c = hash(i + float2(0.0, 1.0));
            float d = hash(i + float2(1.0, 1.0));
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int k = 0; k < 5; k++) {
                v += a * noise(p);
                p = p * 2.03 + float2(1.7, 9.2);
                a *= 0.5;
            }
            return v;
        }
    """

    /**
     * Flames rising from a bed of logs: a row of tongues of wandering heights along the grate, torn
     * at the edges by rising, self-warped turbulence; a heat ramp from deep red through orange to a
     * pale yellow core. Premultiplied; draw it additively over the firebox.
     */
    const val FIRE = NOISE + """
        uniform float2 size;
        uniform float time;
        uniform float intensity;
        uniform float lean;
        uniform float flare;

        half4 main(float2 frag) {
            float2 uv = frag / size;
            float y = 1.0 - uv.y;
            float x = uv.x * 2.0 - 1.0;
            float power = clamp(intensity + flare * 0.6, 0.2, 1.7);
            x -= lean * y * y * 0.6;
            // The logs span most of the grate; the fire thins out towards their ends.
            float bed = 1.0 - smoothstep(0.55, 1.0, abs(x));
            // Where the tongues stand and how tall each one is, wandering slowly.
            float w0 = noise(float2(x * 1.3 + 5.0, y * 0.9 - time * 0.9));
            float comb = 0.5 + 0.5 * cos(x * 12.5 + w0 * 6.0 + sin(time * 0.7) * 0.8);
            float h1 = noise(float2(x * 3.2 + 7.0, time * 0.8));
            float h2 = noise(float2(x * 6.5 - 2.0, time * 1.9 + 5.0));
            float reach = (0.26 + 0.55 * power) * (0.45 + 0.65 * h1 + 0.25 * h2) * (0.3 + 0.7 * comb) * (0.5 + 0.5 * bed);
            float body = 1.0 - y / max(reach, 0.02);
            // Rising turbulence licks at the edges and tears wisps off the tips.
            float2 q = float2(x * 4.6, y * 2.3 - time * 2.8);
            float warp = fbm(q * 0.6 + float2(3.1, -time * 0.3));
            float n = fbm(q + float2(warp * 1.6, warp * 1.3));
            float heat = clamp((body + (n - 0.47) * 1.8 * (0.2 + y * 1.3)) * bed, 0.0, 1.2);
            float t1 = smoothstep(0.02, 0.25, heat);
            float t2 = smoothstep(0.25, 0.6, heat);
            float t3 = smoothstep(0.8, 1.15, heat);
            half3 col = mix(mix(mix(half3(0.40, 0.04, 0.02), half3(0.88, 0.24, 0.03), t1), half3(1.0, 0.58, 0.10), t2), half3(1.0, 0.9, 0.6), t3);
            // The flames rise from between the logs, not from a line along the bottom.
            float a = smoothstep(0.0, 0.3, heat) * smoothstep(0.0, 0.07, y);
            return half4(col * a, a);
        }
    """

    /**
     * Mist drifting along the ground in slow rolls: two layers of noise sliding at different
     * speeds, thickest near the horizon and thinning upwards and downwards.
     */
    const val FOG = NOISE + """
        uniform float2 size;
        uniform float time;
        uniform float density;
        uniform float horizon;
        uniform float depth;
        uniform half4 tint;

        half4 main(float2 frag) {
            float2 p = frag / size.y;
            float d = (frag.y - horizon) / max(depth, 1.0);
            float band = exp(-d * d * 1.6);
            float n1 = fbm(float2(p.x * 2.2 + time * 0.035, p.y * 5.0));
            float n2 = fbm(float2(p.x * 4.5 - time * 0.06, p.y * 9.0 + 3.7));
            float m = smoothstep(0.32, 0.85, n1 * 0.65 + n2 * 0.45);
            float a = clamp(density * (0.25 + 0.95 * band) * (0.35 + 0.9 * m), 0.0, 0.92);
            return tint * a;
        }
    """

    /**
     * Light playing on still water: slow elongated ripples catch the sky as thin highlights and
     * darken between them, flattened by perspective towards the far bank.
     */
    const val WATER = NOISE + """
        uniform float2 size;
        uniform float time;
        uniform float strength;
        uniform half4 light;
        uniform half4 shade;

        half4 main(float2 frag) {
            float2 uv = frag / size;
            float persp = mix(0.35, 1.0, uv.y);
            float2 q = float2(uv.x * 7.0 / persp, uv.y * 38.0 / persp);
            float n = fbm(q + float2(time * 0.12, time * 0.05));
            float ripple = sin(q.y * 1.3 + n * 5.0 - time * 0.9);
            float glint = smoothstep(0.82, 0.98, ripple) * (0.5 + 0.5 * n);
            float trough = smoothstep(-0.6, -0.95, ripple) * 0.6;
            float a = strength * (glint * light.a + trough * shade.a);
            half3 c = (glint * light.rgb * light.a + trough * shade.rgb * shade.a) * strength;
            return half4(c, clamp(a, 0.0, 1.0));
        }
    """

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** A new shader for [source], or null where shaders don't run or it doesn't compile. */
    fun create(source: String): RuntimeShader? =
        if (!supported) null else runCatching { RuntimeShader(source) }.getOrNull()
}
