package app.quire.engine.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * How the phone is being held, as two numbers in −1..1.
 *
 * Gravity is used rather than the accelerometer because it is already free of
 * the shake of a hand. The rest posture assumed is a phone tilted about thirty
 * degrees towards its owner, which is where a screen is actually read; from
 * there, leaning it left or right runs the numbers to the ends of their range.
 *
 * Nothing is reported unless it moved: a phone lying still on a table sends
 * events forever, and each one that reached the view would cost a frame.
 */
class Tilt(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Resting pitch: a screen held at roughly thirty degrees. */
    private val restY = -4.9f
    private val span = 5.0f
    private val smoothing = 0.16f

    private var currentX = 0f
    private var currentY = 0f
    private var running = false

    var onChanged: ((Float, Float) -> Unit)? = null

    val available: Boolean get() = sensor != null

    fun start() {
        val s = sensor ?: return
        if (running) return
        running = manager?.registerListener(this, s, SensorManager.SENSOR_DELAY_UI) == true
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(this)
        currentX = 0f
        currentY = 0f
        onChanged?.invoke(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values.getOrNull(0) ?: return
        val gy = event.values.getOrNull(1) ?: return
        val targetX = (-gx / span).coerceIn(-1f, 1f)
        val targetY = ((gy - restY) / span).coerceIn(-1f, 1f)
        val nextX = currentX + (targetX - currentX) * smoothing
        val nextY = currentY + (targetY - currentY) * smoothing
        // A still phone still reports; only a real change is worth a frame.
        if (abs(nextX - currentX) < 0.002f && abs(nextY - currentY) < 0.002f) return
        currentX = nextX
        currentY = nextY
        onChanged?.invoke(currentX, currentY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
