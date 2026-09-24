package app.opal.core.designsystem.glass

// Adapted from Kyant0/AndroidLiquidGlass catalog (InteractiveHighlight, DragGestureInspector),
// Apache License 2.0, https://github.com/Kyant0/AndroidLiquidGlass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Press response of liquid glass: the surface swells slightly, stretches toward the finger like a
 * jelly (disabled with reduced motion) and a soft highlight follows the touch point. Observation
 * only — it never consumes events, so clicks and scrolling keep working.
 */
internal class InteractiveHighlight(private val scope: CoroutineScope, private val jelly: Boolean) {

    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgress = Animatable(0f, 0.001f)
    private val position =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var start = Offset.Zero

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier = Modifier.drawWithContent {
        drawContent()
        val progress = pressProgress.value
        if (progress <= 0f) return@drawWithContent
        drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
        val s = shader
        if (s != null) {
            s.setFloatUniform("size", size.width, size.height)
            s.setColorUniform("color", Color.White.copy(alpha = 0.15f * progress))
            s.setFloatUniform("radius", size.minDimension * 1.5f)
            s.setFloatUniform(
                "position",
                position.value.x.fastCoerceIn(0f, size.width),
                position.value.y.fastCoerceIn(0f, size.height),
            )
            drawRect(ShaderBrush(s.asComposeShader()), blendMode = BlendMode.Plus)
        }
    }

    val layerBlock: GraphicsLayerScope.() -> Unit = {
        val progress = pressProgress.value
        val scale = lerp(1f, 1f + 4f * density / size.height.coerceAtLeast(1f), progress)
        scaleX = scale
        scaleY = scale
        if (jelly && size.minDimension > 0f) {
            val offset = position.value - start
            val maxOffset = size.minDimension
            val k = 0.05f
            translationX = maxOffset * tanh(k * offset.x / maxOffset)
            translationY = maxOffset * tanh(k * offset.y / maxOffset)
            val maxDrag = 4f * density / size.height
            val angle = atan2(offset.y, offset.x)
            scaleX =
                scale +
                    maxDrag *
                        abs(cos(angle) * offset.x / size.maxDimension) *
                        (size.width / size.height).coerceAtMost(1f)
            scaleY =
                scale +
                    maxDrag *
                        abs(sin(angle) * offset.y / size.maxDimension) *
                        (size.height / size.width).coerceAtMost(1f)
        }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(scope) {
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                start = down.position
                scope.launch {
                    launch { pressProgress.animateTo(1f, pressSpec) }
                    launch { position.snapTo(start) }
                }
                var pointer = down.id
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == pointer } ?: break
                    if (change.changedToUpIgnoreConsumed() || !change.pressed) break
                    pointer = change.id
                    scope.launch { position.snapTo(change.position) }
                }
                scope.launch {
                    launch { pressProgress.animateTo(0f, pressSpec) }
                    launch { position.animateTo(start, positionSpec) }
                }
            }
        }
}
