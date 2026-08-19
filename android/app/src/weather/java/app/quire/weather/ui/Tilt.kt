package app.quire.weather.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * The page as a window: tilt the phone and the sky looks past it.
 *
 * The accelerometer feeds a camera offset, and because the sky is drawn through a real camera,
 * one offset gives every layer its own honest shift — near clouds slide furthest, far ones
 * barely, the stars almost not at all. Nothing here animates by itself: the parallax is driven
 * entirely by the hand holding the phone, which is the one kind of motion this project has
 * never had to apologise for.
 *
 * There is no "neutral grip" to hardcode, because there is no such grip: people read phones
 * flat in bed and upright at a bus stop. So the filter learns the resting pose — a slow average
 * the fast reading is measured against — and any way of holding the phone becomes zero within
 * a couple of seconds. The arithmetic is a plain class with a step function, so a test can
 * feed it a hand's worth of readings and watch it settle.
 */
internal class TiltFilter {

    private var fastX = 0f
    private var fastY = 0f
    private var slowX = 0f
    private var slowY = 0f
    private var primed = false

    /** One accelerometer reading in, one tilt out — each axis in -1..1 around the resting pose. */
    fun step(ax: Float, ay: Float): Offset {
        if (!primed) {
            fastX = ax; fastY = ay; slowX = ax; slowY = ay
            primed = true
            return Offset.Zero
        }
        fastX += (ax - fastX) * FAST
        fastY += (ay - fastY) * FAST
        slowX += (ax - slowX) * SLOW
        slowY += (ay - slowY) * SLOW
        return Offset(
            ((fastX - slowX) / SWING).coerceIn(-1f, 1f),
            ((fastY - slowY) / SWING).coerceIn(-1f, 1f),
        )
    }

    companion object {
        /** How eagerly the reading follows the hand, and how slowly the resting pose does. */
        private const val FAST = 0.30f
        private const val SLOW = 0.02f

        /** How many m/s² of lean count as all the way over. */
        private const val SWING = 2.6f
    }
}

/** The tilt as composable state, alive only while [enabled] and only while composed. */
@Composable
internal fun rememberTilt(enabled: Boolean): State<Offset> {
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (!enabled) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val filter = TiltFilter()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                tilt.value = filter.step(event.values[0], event.values[1])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return tilt
}
