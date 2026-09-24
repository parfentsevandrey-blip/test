package app.opal.core.designsystem.aurora

import android.graphics.ColorSpace
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.preferredFrameRate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.opal.core.designsystem.theme.AuroraMood
import app.opal.core.designsystem.theme.LocalReducedMotion
import app.opal.core.designsystem.theme.OpalTheme
import app.opal.core.designsystem.theme.auroraPalette
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Living mesh-gradient background ("aurora") whose colours encode the tunnel state. Glass surfaces
 * refract it, so there is always something to bend.
 *
 * Compose 1.12 has no mesh-gradient painter, so on API 33+ the colour field is an AGSL shader (four
 * drifting colour blobs blended with Gaussian weights + fine grain); below 33 the same composition
 * is drawn with radial gradients. Colours are Display P3 and reach the screen intact when the
 * window runs in wide-colour-gamut mode.
 *
 * Motion is slow (~30 updates/s, periods of a minute) and stops when [animate] is false, when the
 * screen is not resumed, or when the user disabled animations.
 */
@Composable
fun AuroraBackground(mood: AuroraMood, modifier: Modifier = Modifier, animate: Boolean = true) {
    val dark = OpalTheme.colors.isDark
    val palette = auroraPalette(mood, dark)
    val spec = tween<Color>(durationMillis = 1_400)
    val c0 by animateColorAsState(palette.c0, spec, label = "aurora0")
    val c1 by animateColorAsState(palette.c1, spec, label = "aurora1")
    val c2 by animateColorAsState(palette.c2, spec, label = "aurora2")
    val c3 by animateColorAsState(palette.c3, spec, label = "aurora3")

    val time = remember { mutableFloatStateOf(START_TIME) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val moving = animate && !LocalReducedMotion.current
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = System.nanoTime()
            while (true) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AuroraShader() else null
    }
    Box(
        modifier
            // Slow ambient motion: let adaptive-refresh displays drop to a low rate for it.
            .then(if (moving) Modifier.preferredFrameRate(30f) else Modifier)
            .drawBehind {
                val t = time.floatValue
                if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    shader.draw(this, t, c0, c1, c2, c3)
                } else {
                    drawGradientAurora(t, c0, c1, c2, c3)
                }
            }
    )
}

private const val FRAME_MS = 33L
private const val START_TIME = 17f

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AuroraShader {
    private val shader = RuntimeShader(AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float, c0: Color, c1: Color, c2: Color, c3: Color) {
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("time", time)
        shader.setColorUniform("c0", c0.toPlatform())
        shader.setColorUniform("c1", c1.toPlatform())
        shader.setColorUniform("c2", c2.toPlatform())
        shader.setColorUniform("c3", c3.toPlatform())
        scope.drawRect(brush)
    }

    private fun Color.toPlatform(): android.graphics.Color {
        val p3 = convert(ColorSpaces.DisplayP3)
        return android.graphics.Color.valueOf(
            p3.red,
            p3.green,
            p3.blue,
            1f,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3),
        )
    }

    private companion object {
        const val AGSL =
            """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;

float blob(float2 p, float2 c, float s) {
    float2 d = p - c;
    return exp(-dot(d, d) * s);
}

half4 main(float2 fragCoord) {
    float aspect = resolution.x / max(resolution.y, 1.0);
    float2 p = float2(fragCoord.x / resolution.x * aspect, fragCoord.y / resolution.y);
    float t = time;
    float2 b0 = float2((0.22 + 0.16 * sin(t * 0.11)) * aspect, 0.18 + 0.10 * cos(t * 0.09));
    float2 b1 = float2((0.82 + 0.12 * cos(t * 0.08)) * aspect, 0.34 + 0.14 * sin(t * 0.10));
    float2 b2 = float2((0.30 + 0.18 * cos(t * 0.06)) * aspect, 0.78 + 0.09 * sin(t * 0.12));
    float2 b3 = float2((0.72 + 0.14 * sin(t * 0.07)) * aspect, 0.90 + 0.10 * cos(t * 0.05));
    float w0 = blob(p, b0, 3.2);
    float w1 = blob(p, b1, 2.8);
    float w2 = blob(p, b2, 3.0);
    float w3 = blob(p, b3, 2.2) + 0.08;
    half3 col = (c0.rgb * w0 + c1.rgb * w1 + c2.rgb * w2 + c3.rgb * w3) / (w0 + w1 + w2 + w3);
    // Fine grain hides gradient banding on 8-bit panels.
    float n = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += half3((n - 0.5) * 0.012);
    return half4(col, 1.0);
}
"""
    }
}

private fun DrawScope.drawGradientAurora(t: Float, c0: Color, c1: Color, c2: Color, c3: Color) {
    drawRect(c3)
    val w = size.width
    val h = size.height
    val r = size.maxDimension * 0.75f
    fun blob(color: Color, x: Float, y: Float, radius: Float) {
        val center = Offset(x, y)
        drawCircle(
            Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius),
            radius,
            center,
        )
    }
    blob(c0, w * (0.22f + 0.16f * sin(t * 0.11f)), h * (0.18f + 0.10f * cos(t * 0.09f)), r)
    blob(c1, w * (0.82f + 0.12f * cos(t * 0.08f)), h * (0.34f + 0.14f * sin(t * 0.10f)), r * 0.9f)
    blob(c2, w * (0.30f + 0.18f * cos(t * 0.06f)), h * (0.78f + 0.09f * sin(t * 0.12f)), r)
}
