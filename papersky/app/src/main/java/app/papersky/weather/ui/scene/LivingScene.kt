package app.papersky.weather.ui.scene

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
        out.value = Offset(prev.x + (x - prev.x) * 0.15f, prev.y + (y - prev.y) * 0.15f)
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

private data class Ripple(val position: Offset, val born: Float, val big: Boolean)

@Composable
fun LivingScene(
    target: SceneState,
    modifier: Modifier = Modifier,
    mode: PaletteMode = PaletteMode.Auto,
    horizon: Float = 0.5f,
    motion: MotionLevel = MotionLevel.Full,
    tilt: Boolean = true,
    detail: Float = 1f,
    controller: SceneController? = null,
    transitionMillis: Int = 1100,
    dim: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(density) { PaperSceneRenderer(density) }
    val clock = LocalSceneClock.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val scene = rememberAnimatedScene(target, transitionMillis)
    val parallax by rememberTiltParallax(tilt && motion == MotionLevel.Full)
    val sky: Any? = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) WatercolorSky() else null }

    val flash = remember { Animatable(0f) }
    var boltSeed by remember { mutableIntStateOf(0) }
    val sunSpin = remember { Animatable(0f) }
    val gust = remember { Animatable(0f) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentScene by rememberUpdatedState(scene.value)

    // Thunderstorms: flashes at random, the rumble arrives a moment later (sound is slower).
    val stormy = target.thunder > 0.5f && motion != MotionLevel.Still
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
                flash.snapTo(0f)
                flash.animateTo(1f, tween(55))
                flash.animateTo(0.25f, tween(90))
                flash.animateTo(0.8f, tween(45))
                flash.animateTo(0f, tween(420))
            }
        }
    }

    // Holding a finger in the rain: little drops tapping on it.
    var holdJob by remember { mutableStateOf<Job?>(null) }

    if (controller != null) {
        controller.onTap = { pos ->
            val s = currentScene
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val body = renderer.celestialAt(w, h, s, PaperSceneRenderer.Options(horizon = horizon))
            if (body != null && hypot(pos.x - body[0], pos.y - body[1]) < body[2] * 2.4f) {
                haptics.whirl()
                scope.launch { sunSpin.animateTo(sunSpin.value + 180f, spring(dampingRatio = 0.45f, stiffness = 30f)) }
            } else {
                ripples += Ripple(pos, clock.seconds.floatValue, big = false)
                when {
                    s.rain + s.drizzle > 0.2f -> haptics.raindrop()
                    s.snow > 0.2f -> haptics.snowflake()
                    else -> haptics.puff()
                }
                scope.launch { gust.animateTo(gust.value + if (pos.x < w / 2) 3f else -3f, spring(stiffness = 60f)); gust.animateTo(0f, spring(stiffness = 8f)) }
            }
            if (ripples.size > 12) ripples.removeAt(0)
        }
        controller.onHold = { pos, down ->
            holdJob?.cancel()
            if (down) {
                holdJob = scope.launch {
                    while (isActive) {
                        val s = currentScene
                        val wet = (s.rain + s.drizzle + s.hail).coerceAtMost(1f)
                        val snowy = s.snow
                        when {
                            wet > 0.1f -> {
                                haptics.raindrop()
                                ripples += Ripple(pos + Offset((Random.nextFloat() - 0.5f) * 90 * density, (Random.nextFloat() - 0.5f) * 60 * density), clock.seconds.floatValue, big = false)
                                delay((380 - wet * 300).toLong().coerceAtLeast(55) + Random.nextLong(0, 160))
                            }
                            snowy > 0.1f -> {
                                haptics.snowflake()
                                delay(Random.nextLong(240, 620))
                            }
                            else -> {
                                // Warmth: the sun slowly turns toward your finger.
                                sunSpin.animateTo(sunSpin.value + 30f, tween(600))
                            }
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

    val motionOn = motion != MotionLevel.Still
    Canvas(
        modifier.onGloballyPositioned {
            size = it.size
            controller?.origin = it.positionInRoot()
        },
    ) {
        val t = clock.seconds.floatValue
        val s = scene.value
        val p = Palettes.resolve(mode, s, context)
        val shader: android.graphics.Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sky is WatercolorSky) {
            sky.update(this.size.width, this.size.height, t, horizon, p.skyTop, p.skyBottom, 0, 0f, 0f)
            sky.shader
        } else null
        val options = PaperSceneRenderer.Options(
            time = t,
            horizon = horizon,
            parallaxX = if (motionOn) parallax.x else 0f,
            parallaxY = if (motionOn) parallax.y else 0f,
            flash = flash.value,
            boltSeed = boltSeed,
            staticBolt = !motionOn && s.thunder > 0.5f,
            sunSpin = sunSpin.value,
            gust = gust.value,
            skyShader = shader,
            detail = detail,
            vignette = 0.75f,
        )
        drawIntoCanvas { renderer.draw(it.nativeCanvas, this.size.width, this.size.height, s, p, options) }

        val ink = Color(ColorMath.withAlpha(p.onSky, 1f))
        for (r in ripples) {
            val age = t - r.born
            if (age < 0f || age > 1.1f) continue
            val k = age / 1.1f
            drawCircle(ink.copy(alpha = (1f - k) * 0.45f), radius = (6f + k * 70f) * density, center = r.position, style = Stroke(width = (2.2f - k * 1.6f) * density))
            drawCircle(ink.copy(alpha = (1f - k) * 0.25f), radius = (3f + k * 38f) * density, center = r.position, style = Stroke(width = 1.2f * density))
        }
        val d = dim()
        if (d > 0.001f) drawRect(Color(ColorMath.darken(p.skyTop, 0.6f)).copy(alpha = d))
    }
}

/** True if a horizontal drag is dominant enough to count as a gust. */
internal fun isHorizontal(dx: Float, dy: Float) = abs(dx) > abs(dy) * 1.4f
