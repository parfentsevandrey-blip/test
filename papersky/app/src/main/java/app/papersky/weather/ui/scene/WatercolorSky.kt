package app.papersky.weather.ui.scene

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AGSL watercolour wash for the app's sky (Android 13+): the flat gradient gets slow-moving
 * pigment blooms and paler washes, like wet gouache drying on cold-press paper.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class WatercolorSky {
    val shader = RuntimeShader(SOURCE)

    fun update(width: Float, height: Float, time: Float, horizon: Float, top: Int, bottom: Int, glow: Int, sunX: Float, sunY: Float) {
        shader.setFloatUniform("res", width, height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("horizon", horizon)
        shader.setFloatUniform("sun", sunX, sunY)
        shader.setColorUniform("top", top)
        shader.setColorUniform("bottom", bottom)
        shader.setColorUniform("glow", glow)
    }

    private companion object {
        const val SOURCE = """
            uniform float2 res;
            uniform float time;
            uniform float horizon;
            uniform float2 sun;
            layout(color) uniform half4 top;
            layout(color) uniform half4 bottom;
            layout(color) uniform half4 glow;

            float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

            float noise(float2 p) {
                float2 i = floor(p);
                float2 f = fract(p);
                float2 u = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                           mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
            }

            float fbm(float2 p) {
                float v = 0.0;
                float a = 0.5;
                for (int i = 0; i < 5; i++) {
                    v += a * noise(p);
                    p = p * 2.03 + float2(3.1, 1.7);
                    a *= 0.5;
                }
                return v;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / res;
                float y = uv.y / max(horizon, 0.001);
                float2 q = float2(uv.x * res.x / res.y, uv.y) * 2.4;
                float n = fbm(q + float2(time * 0.010, time * 0.003));
                float n2 = fbm(q * 2.6 - float2(time * 0.007, 0.0) + 7.3);
                float g = clamp(y + (n - 0.5) * 0.24, 0.0, 1.0);
                half4 c = mix(top, bottom, half(smoothstep(0.0, 1.0, g)));
                float pool = smoothstep(0.55, 0.8, n2);
                c.rgb *= half(1.0 - pool * 0.075);
                c.rgb = mix(c.rgb, half3(1.0), half(smoothstep(0.34, 0.06, n2) * 0.06));
                float d = distance(fragCoord, sun) / res.y;
                c.rgb = mix(c.rgb, glow.rgb, glow.a * half(exp(-d * 5.0)));
                c.a = 1.0;
                return c;
            }
        """
    }
}
