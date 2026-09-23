package app.papersky.weather.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.ArchShape
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.LocalLight
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.HearthRenderer
import app.papersky.weather.scene.PaletteMode
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.SceneState
import app.papersky.weather.scene.Shaders
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The room by the fire (DESIGN_DOCTRINE §16), alive: the room and the window frame are cached
 * layers; the weather outside is a whole [LivingScene] seen through the glass; the fire, its sparks
 * and its flickering light are the only things drawn every frame. Poking the fire (a tap on it)
 * makes it flare and throw sparks; taps elsewhere go out of the window, to the weather.
 */
@Composable
fun HearthScene(
    target: SceneState,
    modifier: Modifier = Modifier,
    motion: MotionLevel = MotionLevel.Full,
    tilt: Boolean = true,
    controller: SceneController? = null,
    transitionMillis: Int = 1100,
    village: Boolean = true,
    scroll: () -> Float = { 0f },
    dim: () -> Float = { 0f },
) {
    val density = LocalDensity.current.density
    val renderer = remember(density) { HearthRenderer(density) }
    val clock = LocalSceneClock.current
    val haptics = LocalHaptics.current
    val frost = LocalLight.current.frost
    val scope = rememberCoroutineScope()
    val scene = rememberAnimatedScene(target, transitionMillis)
    val palette = remember { derivedStateOf { Palettes.hearth(scene.value) } }
    val motionOn = motion != MotionLevel.Still
    val shader = remember { Shaders.create(Shaders.FIRE) }
    val flare = remember { Animatable(0f) }
    val outside = remember { SceneController() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val extra = (900 * density).roundToInt()

    fun time() = if (motionOn) clock.seconds.floatValue else 4f

    if (controller != null) {
        controller.onTap = { pos ->
            val g = renderer.geometry(size.width.toFloat(), size.height.toFloat())
            val y = pos.y + scroll() * FOLLOW
            if (g.opening.contains(pos.x, y)) {
                haptics.crackle()
                scope.launch {
                    flare.animateTo(1f, tween(140))
                    flare.animateTo(0f, tween(2200))
                }
            } else {
                outside.tap(controller.origin + pos)
            }
        }
        controller.onHold = { pos, down -> outside.hold(controller.origin + pos, down) }
        controller.onFling = { v -> outside.fling(v) }
    }

    Box(
        modifier
            .clipToBounds()
            .preferredFrameRate(FrameRateCategory.Normal)
            .onGloballyPositioned {
                size = it.size
                controller?.origin = it.positionInRoot()
            },
    ) {
        if (size.width == 0 || size.height == 0) return@Box
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val g = remember(size) { renderer.geometry(w, h) }
        fun shiftY() = -scroll() * FOLLOW

        // The room: redrawn only when the light of the day changes.
        Spacer(
            Modifier
                .placedAt(0, 0, size.width, size.height + extra)
                .graphicsLayer {
                    translationY = shiftY()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind { drawIntoCanvas { renderer.drawRoom(it.nativeCanvas, g, scene.value, palette.value, extra.toFloat()) } },
        )

        // The weather outside, seen through the glass.
        val win = g.window
        Box(
            Modifier
                .placedAt(win.left.roundToInt(), win.top.roundToInt(), win.width().roundToInt(), win.height().roundToInt())
                .graphicsLayer { translationY = shiftY() }
                .clip(ArchShape(0.dp)),
        ) {
            LivingScene(
                target = target,
                modifier = Modifier.fillMaxSize(),
                mode = PaletteMode.Auto,
                horizon = 0.62f,
                motion = motion,
                tilt = tilt,
                detail = 0.85f,
                controller = outside,
                transitionMillis = transitionMillis,
                laneStart = 0.2f,
                laneEnd = 0.8f,
                glass = true,
                village = village,
            )
        }

        // The frame and the sill, over the glass.
        Spacer(
            Modifier
                .placedAt(0, 0, size.width, size.height)
                .graphicsLayer {
                    translationY = shiftY()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind { drawIntoCanvas { renderer.drawWindowFrame(it.nativeCanvas, g, scene.value, palette.value, frost) } },
        )

        // The fire and the light it throws: the only layers drawn every frame.
        Spacer(
            Modifier
                .placedAt(0, 0, size.width, size.height)
                .graphicsLayer { translationY = shiftY() }
                .drawBehind {
                    drawIntoCanvas {
                        val t = time()
                        renderer.drawFire(it.nativeCanvas, g, scene.value, palette.value, t, flare.value, shader)
                        renderer.drawGlow(it.nativeCanvas, g, scene.value, palette.value, t, flare.value, still = !motionOn)
                    }
                },
        )

        // Dusk in the corners of the room, and the dimming as content scrolls over it.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.45f, size.height * 0.5f),
                            radius = maxOf(size.width, size.height) * 0.75f,
                        ),
                    )
                    val d = dim()
                    if (d > 0.001f) drawRect(Color(ColorMath.darken(palette.value.skyTop, 0.6f)).copy(alpha = d))
                },
        )
    }
}

/** How far the room follows content scrolling over it (slower than the page: it recedes). */
private const val FOLLOW = 0.3f

private fun Modifier.placedAt(x: Int, y: Int, width: Int, height: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(1), height.coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
}
