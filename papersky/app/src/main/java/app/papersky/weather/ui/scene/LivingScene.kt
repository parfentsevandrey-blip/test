package app.papersky.weather.ui.scene

import android.content.Context
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.papersky.weather.core.model.MotionLevel
import app.papersky.weather.design.LocalHaptics
import app.papersky.weather.design.LocalSceneClock
import app.papersky.weather.design.SceneClock
import app.papersky.weather.scene.ColorMath
import app.papersky.weather.scene.PaletteMode
import app.papersky.weather.scene.Palettes
import app.papersky.weather.scene.PaperSceneRenderer
import app.papersky.weather.scene.SceneState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** Advances the shared animation clock only while the screen is resumed. */
@Composable
fun rememberSceneClock(speed: Float): SceneClock {
    val clock = remember { SceneClock() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(speed, lifecycle) {
        if (speed <= 0f) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { now ->
                    clock.seconds.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f) * speed
                    last = now
                }
            }
        }
    }
    return clock
}

/** Glides every numeric field of the scene to [target] — sun, clouds, rain, colours. */
@Composable
fun rememberAnimatedScene(target: SceneState, durationMillis: Int = 1100): State<SceneState> {
    val from = remember { mutableStateOf(target) }
    val to = remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to.value) return@LaunchedEffect
        from.value = from.value.lerp(to.value, progress.value)
        to.value = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return remember { derivedStateOf { from.value.lerp(to.value, progress.value) } }
}

/** Device tilt → gentle parallax of the paper layers (like a real shadow-box diorama). */
@Composable
fun rememberTiltParallax(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    LifecycleResumeEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val listener = if (enabled && sensor != null) TiltListener(state) else null
        if (listener != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            if (listener != null) manager?.unregisterListener(listener)
            state.value = Offset.Zero
        }
    }
    return state
}

private class TiltListener(private val out: MutableState<Offset>) : SensorEventListener {
    private val rot = FloatArray(9)
    private val ori = FloatArray(3)
    private var basePitch = Float.NaN
    private var baseRoll = Float.NaN

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        SensorManager.getOrientation(rot, ori)
        val pitch = ori[1]
        val roll = ori[2]
        if (basePitch.isNaN()) { basePitch = pitch; baseRoll = roll }
        // Slowly re-centre so the diorama settles wherever the phone is held.
        basePitch += (pitch - basePitch) * 0.01f
        baseRoll += (roll - baseRoll) * 0.01f
        val x = ((roll - baseRoll) / 0.35f).coerceIn(-1f, 1f)
        val y = ((pitch - basePitch) / 0.35f).coerceIn(-1f, 1f)
        val prev = out.value
        val next = Offset(prev.x + (x - prev.x) * 0.15f, prev.y + (y - prev.y) * 0.15f)
        // Sub-pixel jitter would only wake the layers up for nothing.
        if (abs(next.x - prev.x) > 0.002f || abs(next.y - prev.y) > 0.002f) out.value = next
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Bridge for gestures that are detected elsewhere (the hero area floats above the scene inside a
 * scrolling list) but must act on the scene in its own coordinates.
 */
@Stable
class SceneController {
    internal var origin = Offset.Zero
    internal var onTap: (Offset) -> Unit = {}
    internal var onHold: (Offset, Boolean) -> Unit = { _, _ -> }
    internal var onFling: (Float) -> Unit = {}

    /** Positions are in root coordinates. */
    fun tap(rootPosition: Offset) = onTap(rootPosition - origin)
    fun hold(rootPosition: Offset, down: Boolean) = onHold(rootPosition - origin, down)
    fun fling(velocityPxPerSec: Float) = onFling(velocityPxPerSec)
}

private class Ripple(val position: Offset, val born: Float)

/** How far each depth follows the content when it scrolls: the sky barely, the meadow most. */
private fun follow(depth: Float) = 0.1f + 0.4f * depth

/** Measures the content at a fixed size and places it at (x, y) in the parent. */
private fun Modifier.placed(x: Int, y: Int, width: Int, height: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(1), height.coerceAtLeast(1)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
}

/**
 * The living paper diorama.
 *
 * Built as a stack of GPU layers. Sky, clouds and the three landscape bands are cached render
 * nodes: while the scene plays, only their *positions* change (drift, parallax, scroll), which
 * costs nothing to redraw. Only rain, snow, stars and other particles are repainted each frame,
 * and they are batched into a handful of draw calls. Nothing here recomposes per frame.
 */
@Composable
fun LivingScene(
    target: SceneState,
    modifier: Modifier = Modifier,
    mode: PaletteMode = PaletteMode.Auto,
    horizon: Float = 0.5f,
    /** Height of the landscape in px; NaN fits it below the horizon. */
    depth: Float = Float.NaN,
    motion: MotionLevel = MotionLevel.Full,
    tilt: Boolean = true,
    detail: Float = 1f,
    controller: SceneController? = null,
    transitionMillis: Int = 1100,
    laneStart: Float = 0.12f,
    laneEnd: Float = 0.88f,
    glass: Boolean = false,
    village: Boolean = true,
    /** Scroll of the content above the scene, px; each layer follows at its own depth. */
    scroll: () -> Float = { 0f },
    dim: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) { PaperSceneRenderer(density) }
    val clock = LocalSceneClock.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val scene = rememberAnimatedScene(target, transitionMillis)
    val palette = remember(mode) { derivedStateOf { Palettes.resolve(mode, scene.value, context) } }
    val motionOn = motion != MotionLevel.Still
    val parallax = rememberTiltParallax(tilt && motion == MotionLevel.Full)

    val flash = remember { Animatable(0f) }
    var boltSeed by remember { mutableIntStateOf(0) }
    val sunSpin = remember { Animatable(0f) }
    val gust = remember { Animatable(0f) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val base = remember(horizon, depth, detail, laneStart, laneEnd, glass, village) {
        PaperSceneRenderer.Options(horizon = horizon, depth = depth, detail = detail, laneStart = laneStart, laneEnd = laneEnd, glass = glass, vignette = 0f, village = village)
    }

    // Thunderstorms: flashes at random, the rumble arrives a moment later (sound is slower).
    val stormy = target.thunder > 0.5f && motionOn
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(stormy) {
        if (!stormy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(Random.nextLong(3_500, 10_500))
                boltSeed = Random.nextInt()
                val distance = Random.nextFloat()
                launch {
                    delay((250 + distance * 1_200).toLong())
                    haptics.thunder(1f - distance * 0.6f)
                }
                // The flash lights up the whole room, paper on the table included (§4.4).
                flash.snapTo(0f)
                flash.animateTo(1f, tween(55)) { clock.flash.floatValue = value }
                flash.animateTo(0.25f, tween(90)) { clock.flash.floatValue = value }
                flash.animateTo(0.8f, tween(45)) { clock.flash.floatValue = value }
                flash.animateTo(0f, tween(420)) { clock.flash.floatValue = value }
            }
        }
    }

    var holdJob by remember { mutableStateOf<Job?>(null) }
    if (controller != null) {
        controller.onTap = { pos ->
            val s = scene.value
            val body = renderer.celestialAt(size.width.toFloat(), size.height.toFloat(), s, base)
            if (body != null && hypot(pos.x - body[0], pos.y - body[1]) < body[2] * 2.4f) {
                haptics.whirl()
                scope.launch { sunSpin.animateTo(sunSpin.value + 180f, spring(dampingRatio = 0.45f, stiffness = 30f)) }
            } else {
                ripples += Ripple(pos, clock.seconds.floatValue)
                when {
                    s.rain + s.drizzle > 0.2f -> haptics.raindrop()
                    s.snow > 0.2f -> haptics.snowflake()
                    else -> haptics.puff()
                }
                scope.launch {
                    gust.animateTo(gust.value + if (pos.x < size.width / 2) 3f else -3f, spring(stiffness = 60f))
                    gust.animateTo(0f, spring(stiffness = 8f))
                }
            }
            if (ripples.size > 12) ripples.removeAt(0)
        }
        controller.onHold = { pos, down ->
            holdJob?.cancel()
            if (down) {
                holdJob = scope.launch {
                    while (isActive) {
                        val s = scene.value
                        val wet = (s.rain + s.drizzle + s.hail).coerceAtMost(1f)
                        when {
                            wet > 0.1f -> {
                                haptics.raindrop()
                                ripples += Ripple(pos + Offset((Random.nextFloat() - 0.5f) * 90 * density, (Random.nextFloat() - 0.5f) * 60 * density), clock.seconds.floatValue)
                                delay((380 - wet * 300).toLong().coerceAtLeast(55) + Random.nextLong(0, 160))
                            }
                            s.snow > 0.1f -> {
                                haptics.snowflake()
                                delay(Random.nextLong(240, 620))
                            }
                            // Warmth: the sun slowly turns toward your finger.
                            else -> sunSpin.animateTo(sunSpin.value + 30f, tween(600))
                        }
                        if (ripples.size > 16) ripples.removeAt(0)
                    }
                }
            }
        }
        controller.onFling = { v ->
            scope.launch {
                gust.animateTo((v / (density * 120f)).coerceIn(-18f, 18f), spring(stiffness = 120f))
                gust.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 6f))
            }
        }
    }

    // Everything time-dependent reads the clock inside draw or layer blocks only.
    fun frame(): PaperSceneRenderer.Options = base.copy(
        time = if (motionOn) clock.seconds.floatValue else 0f,
        flash = flash.value,
        boltSeed = boltSeed,
        staticBolt = !motionOn && target.thunder > 0.5f,
        sunSpin = sunSpin.value,
        gust = gust.value,
        particles = true,
    )

    val dpPx = density
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
        // Lay the diorama out synchronously so the number of cloud and band layers is known.
        val layoutKey = remember(size, target.seed, base) {
            renderer.prepare(w, h, target, base)
            Any()
        }
        val margin = renderer.sceneMargin.roundToInt()
        fun shiftX(depth: Float) = if (motionOn) parallax.value.x * 10 * dpPx * depth else 0f
        fun shiftY(depth: Float) = -scroll() * follow(depth) + if (motionOn) parallax.value.y * 6 * dpPx * depth else 0f

        // Sky, sun and moon: cached, redrawn only when the weather or light changes.
        val skyBottom = remember(layoutKey) { (renderer.bandBottom(0) + 40 * dpPx).roundToInt() }
        Spacer(
            Modifier
                .placed(0, -(24 * dpPx).roundToInt(), size.width, skyBottom + (24 * dpPx).roundToInt())
                .graphicsLayer {
                    translationY = shiftY(0f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    drawIntoCanvas {
                        it.nativeCanvas.translate(0f, 24 * dpPx)
                        renderer.drawSky(it.nativeCanvas, scene.value, palette.value, base)
                    }
                },
        )

        // Stars and sun rays: a few points and one path, per frame.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = shiftY(0f) }
                .drawBehind { drawIntoCanvas { renderer.drawSkyFx(it.nativeCanvas, scene.value, palette.value, frame()) } },
        )

        // Overcast festoon: one cached strip of scallops sliding with the wind.
        val period = remember(layoutKey) { renderer.blanketPeriod }
        Spacer(
            Modifier
                .placed(-(period.roundToInt()), 0, size.width + 2 * period.roundToInt(), (renderer.bandTop(0).coerceAtLeast(size.height * 0.3f)).roundToInt())
                .graphicsLayer {
                    val s = scene.value
                    alpha = renderer.blanketAlpha(s)
                    translationX = renderer.blanketOffset(s, if (motionOn) clock.seconds.floatValue else 0f, gust.value) + shiftX(0.05f)
                    translationY = shiftY(0.05f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    drawIntoCanvas {
                        it.nativeCanvas.translate(period, 0f)
                        renderer.drawBlanket(it.nativeCanvas, scene.value, palette.value, 1f)
                    }
                },
        )

        // Clouds: each one a cached sprite, drifting by translation alone.
        for (i in 0 until renderer.cloudCount) {
            key(layoutKey, i) {
                val box = remember(layoutKey) { RectF().also { renderer.cloudBounds(i, it) } }
                val depth = if (renderer.cloudIsCeiling(i)) 0.05f else 0.12f
                Spacer(
                    Modifier
                        .placed(0, (renderer.cloudTop(i) + box.top).roundToInt(), box.width().roundToInt(), box.height().roundToInt())
                        .graphicsLayer {
                            val s = scene.value
                            alpha = renderer.cloudAlpha(i, s)
                            translationX = renderer.cloudX(i, s, if (motionOn) clock.seconds.floatValue else 0f, gust.value) + box.left + shiftX(depth)
                            translationY = shiftY(depth)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawBehind {
                            drawIntoCanvas {
                                it.nativeCanvas.translate(-box.left, -box.top)
                                renderer.drawCloud(it.nativeCanvas, i, scene.value, palette.value)
                            }
                        },
                )
            }
        }

        // Landscape: three cached bands, each with its own parallax.
        for (b in 0 until renderer.bandCount) {
            key(layoutKey, b) {
                val top = renderer.bandTop(b)
                val bottom = renderer.bandBottom(b)
                val depth = renderer.bandDepth(b)
                Spacer(
                    Modifier
                        .placed(-margin, top.roundToInt(), size.width + 2 * margin, (bottom - top).roundToInt())
                        .graphicsLayer {
                            translationX = shiftX(depth)
                            translationY = shiftY(depth)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawBehind {
                            drawIntoCanvas {
                                it.nativeCanvas.translate(margin.toFloat(), -top)
                                renderer.drawBand(it.nativeCanvas, b, scene.value, palette.value)
                            }
                        },
                )
                if (b == renderer.bandCount - 1) {
                    // Below the nearest ridge: plain paper, drawn as a rectangle, never cached.
                    val bodyTop = bottom.roundToInt() - 1
                    Spacer(
                        Modifier
                            .placed(-margin, bodyTop, size.width + 2 * margin, size.height - bodyTop + (900 * dpPx).roundToInt())
                            .graphicsLayer {
                                translationX = shiftX(depth)
                                translationY = shiftY(depth)
                            }
                            .drawBehind { drawRect(Color(renderer.bandBodyColor(palette.value))) },
                    )
                }
            }
        }

        // The meadow's life — swaying trees, smoke, flickering windows — moves with the meadow.
        val meadowDepth = remember(layoutKey) { renderer.bandDepth(renderer.bandCount - 1) }
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = shiftX(meadowDepth)
                    translationY = shiftY(meadowDepth)
                }
                .drawBehind { drawIntoCanvas { renderer.drawProps(it.nativeCanvas, scene.value, palette.value, frame()) } },
        )

        // Weather in front of everything: the only other layer repainted every frame.
        val nearDepth = remember(layoutKey) { renderer.bandDepth(renderer.bandCount - 1) }
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = shiftY(0.6f) }
                .drawBehind {
                    val o = frame().copy(
                        groundDx = shiftX(nearDepth) - 0f,
                        groundDy = shiftY(nearDepth) - shiftY(0.6f),
                    )
                    drawIntoCanvas { renderer.drawParticles(it.nativeCanvas, scene.value, palette.value, o) }
                    drawRipples(ripples, clock.seconds.floatValue, palette.value.onSky, dpPx, shiftY(0.6f))
                },
        )

        // Vignette and the dimming as content scrolls over the scene.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawIntoCanvas {
                        renderer.drawVignette(it.nativeCanvas, palette.value, 0.7f)
                        if (glass) renderer.drawGlare(it.nativeCanvas)
                    }
                    val d = dim()
                    if (d > 0.001f) drawRect(Color(ColorMath.darken(palette.value.skyTop, 0.6f)).copy(alpha = d))
                },
        )
    }
}

private fun DrawScope.drawRipples(ripples: List<Ripple>, t: Float, ink: Int, dp: Float, dy: Float) {
    if (ripples.isEmpty()) return
    val color = Color(ColorMath.withAlpha(ink, 1f))
    for (r in ripples) {
        val age = t - r.born
        if (age < 0f || age > 1.2f) continue
        val k = age / 1.2f
        val ease = 1f - (1f - k) * (1f - k)
        val c = Offset(r.position.x, r.position.y - dy)
        drawCircle(color.copy(alpha = (1f - k) * 0.5f), radius = (5f + ease * 64f) * dp, center = c, style = Stroke(width = (2f - k * 1.4f) * dp))
        drawCircle(color.copy(alpha = (1f - k) * 0.25f), radius = (2f + ease * 34f) * dp, center = c, style = Stroke(width = 1.1f * dp))
    }
}

/** True if a horizontal drag is dominant enough to count as a gust. */
internal fun isHorizontal(dx: Float, dy: Float) = abs(dx) > abs(dy) * 1.4f

/**
 * A still frame of the diorama for thumbnails (place cards, the widget promo). Recorded once and
 * cached on the GPU; it costs nothing while the list scrolls.
 */
@Composable
fun SceneThumbnail(scene: SceneState, modifier: Modifier = Modifier, mode: PaletteMode = PaletteMode.Auto, time: Float = 8f, village: Boolean = true, horizon: Float = 0.5f) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) { PaperSceneRenderer(density) }
    val palette = remember(scene, mode) { Palettes.resolve(mode, scene, context) }
    Spacer(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawIntoCanvas {
                    renderer.draw(
                        it.nativeCanvas, size.width, size.height, scene, palette,
                        PaperSceneRenderer.Options(time = time, detail = 0.6f, vignette = 0.35f, horizon = horizon, staticBolt = scene.thunder > 0.5f, village = village),
                    )
                }
            },
    )
}

