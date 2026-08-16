package com.cozyhome.weather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cozyhome.weather.util.Haptics
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Idle "floating in the air" motion: a slow vertical bob plus a tiny tilt,
 * phase-shifted by [index] so cards don't move in lockstep.
 */
@Composable
fun Modifier.floaty(index: Int, amplitude: Dp = 5.dp): Modifier {
    val transition = rememberInfiniteTransition(label = "floaty$index")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 3800 + index * 420, easing = LinearEasing)
        ),
        label = "phase$index",
    )
    return this.graphicsLayer {
        translationY = sin(phase + index * 1.3f) * amplitude.toPx()
        rotationZ = sin(phase * 0.5f + index) * 0.5f
    }
}

/**
 * A translucent floating card. Long-press "picks it up" (with haptics),
 * it can be dragged around, and it springs back home when released.
 */
@Composable
fun FloatingCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var held by remember { mutableStateOf(false) }
    val holdScale by animateFloatAsState(
        targetValue = if (held) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "holdScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .floaty(index)
            .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.roundToInt()) }
            .graphicsLayer {
                scaleX = holdScale
                scaleY = holdScale
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        held = true
                        Haptics.heavy(context)
                    },
                    onDragEnd = {
                        held = false
                        Haptics.click(context)
                        scope.launch {
                            drag.animateTo(Offset.Zero, spring(dampingRatio = 0.45f, stiffness = 320f))
                        }
                    },
                    onDragCancel = {
                        held = false
                        scope.launch {
                            drag.animateTo(Offset.Zero, spring(dampingRatio = 0.45f, stiffness = 320f))
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + dragAmount) }
                }
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}
