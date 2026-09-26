package app.opal.core.designsystem.glass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Where the light on glass comes from. Tilting the phone turns it, like the specular highlights of
 * Liquid Glass: the light stays put in the room while the phone moves. Held upright the designed
 * [DEFAULT_LIGHT_ANGLE] is kept, so screenshots and a still phone look as designed.
 */
@Stable
class GlassLight internal constructor() {
    /** Highlight angle in degrees ([HighlightStyle.Default.angle]). */
    var angle by mutableFloatStateOf(DEFAULT_LIGHT_ANGLE)
        internal set

    private val sweep = Animatable(0f)

    /** [base] lit from [angle]: only the direction changes, color and falloff stay. */
    fun highlight(base: Highlight = Highlight.Default): Highlight {
        val style = base.style as? HighlightStyle.Default ?: return base
        val turn = sweep.value
        return base.copy(
            // During a sweep the rim light runs once around every glass surface and flares up.
            alpha = (base.alpha * (1f + SWEEP_FLARE * sin(PI.toFloat() * turn))).coerceAtMost(1f),
            style = style.copy(angle = angle + 360f * turn),
        )
    }

    /**
     * Runs the light once around all glass: a short, quiet "done" (e.g. connected). Callers skip it
     * with reduced motion.
     */
    suspend fun sweep() {
        try {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SWEEP_MS, easing = FastOutSlowInEasing))
        } finally {
            // Also when cancelled halfway: the light must not stay turned.
            withContext(NonCancellable) { sweep.snapTo(0f) }
        }
    }
}

val LocalGlassLight = staticCompositionLocalOf { GlassLight() }

/**
 * A [GlassLight] that follows the accelerometer while [enabled] and the screen is resumed (no
 * sensor at all otherwise: background, simplified graphics, reduced motion). The direction is
 * smoothed as a vector, so it never jumps across ±180°, and kept as is while the phone lies flat,
 * where "down" within the screen is noise.
 */
@Composable
fun rememberGlassLight(enabled: Boolean): GlassLight {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val light = remember { GlassLight() }
    DisposableEffect(enabled, lifecycle, context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            light.angle = DEFAULT_LIGHT_ANGLE
            return@DisposableEffect onDispose {}
        }
        val filter = TiltFilter()
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (filter.onGravity(event.values[0], event.values[1])) {
                        light.angle = filter.angle
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                Lifecycle.Event.ON_PAUSE -> manager.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return light
}

/**
 * Accelerometer readings (the "up" reaction to gravity, device axes) to the light angle. "Up"
 * within the screen plane is smoothed as a vector, so the angle never jumps across ±180°; readings
 * of a phone lying flat carry no direction and are ignored. Upright "up" is 90°: the light keeps
 * its designed angle there and turns the other way round when the phone turns.
 */
internal class TiltFilter {
    private var upX = 0f
    private var upY = 1f

    var angle = DEFAULT_LIGHT_ANGLE
        private set

    /** Returns true when [angle] moved by at least [STEP_DEGREES]. */
    fun onGravity(x: Float, y: Float): Boolean {
        val inPlane = hypot(x, y)
        if (inPlane < FLAT_THRESHOLD) return false
        upX += (x / inPlane - upX) * SMOOTHING
        upY += (y / inPlane - upY) * SMOOTHING
        val up = Math.toDegrees(atan2(upY, upX).toDouble()).toFloat()
        val target = DEFAULT_LIGHT_ANGLE + (up - 90f)
        if (abs(target - angle) < STEP_DEGREES) return false
        angle = target
        return true
    }
}

/** The angle of [HighlightStyle.Default]; also the angle for a phone held upright. */
const val DEFAULT_LIGHT_ANGLE = 45f
private const val SMOOTHING = 0.15f
private const val SWEEP_MS = 900
private const val SWEEP_FLARE = 0.6f
private const val STEP_DEGREES = 1f

/** m/s² of gravity within the screen plane below which the phone counts as lying flat. */
private const val FLAT_THRESHOLD = 3f
