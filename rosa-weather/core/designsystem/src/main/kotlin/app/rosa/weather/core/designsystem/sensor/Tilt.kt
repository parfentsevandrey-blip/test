package app.rosa.weather.core.designsystem.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Device tilt as a smoothed offset in -1..1 (x: roll, y: pitch), from gravity. Drives the glass
 * specular highlights and a hint of sky parallax. Listens only while the UI is at least STARTED
 * and uses the low-power gravity sensor at UI rate.
 */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) {
            state.value = Offset.Zero
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var smooth = state.value
            listen(context) { x, y ->
                // Low-pass filter: glass light should drift, not jitter.
                smooth = Offset(smooth.x + (x - smooth.x) * 0.12f, smooth.y + (y - smooth.y) * 0.12f)
                // Publish only visible moves: a phone lying still (or a steady hand) redraws nothing.
                if ((smooth - state.value).getDistance() > 0.004f) state.value = smooth
            }
        }
    }
    return state
}

private suspend fun listen(context: Context, onTilt: (Float, Float) -> Unit) {
    val manager = context.getSystemService(SensorManager::class.java) ?: return
    val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
    suspendCancellableCoroutine<Unit> { cont ->
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                onTilt((-event.values[0] / g).coerceIn(-1f, 1f), ((event.values[1] / g) - 0.7f).coerceIn(-1f, 1f))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        cont.invokeOnCancellation { manager.unregisterListener(listener) }
    }
}

/**
 * Light angle for the glass rim. At rest the highlights sit at Apple's 45°/−135° pair; tilting the
 * phone swings them by up to ±35°, as if a window light stayed put while the device moved.
 */
fun Offset.toLightAngle(): Float {
    val base = -2.35f
    val swing = (x * 0.6f - y * 0.3f).coerceIn(-0.6f, 0.6f)
    return base + swing
}
