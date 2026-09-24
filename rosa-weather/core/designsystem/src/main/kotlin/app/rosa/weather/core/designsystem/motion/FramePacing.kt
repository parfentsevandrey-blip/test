package app.rosa.weather.core.designsystem.motion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * True once this device keeps missing its frame deadlines, measured from real frame times
 * (FrameMetrics), not guessed from a model name. "Auto" effects then step down for the rest of
 * the session and never flip back, so the sky doesn't oscillate between looks. The first seconds
 * (shader compilation, first layout) are ignored.
 */
@Composable
fun rememberFrameStruggle(enabled: Boolean): State<Boolean> {
    val view = LocalView.current
    val struggling = remember { mutableStateOf(false) }
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        if (!enabled || window == null || struggling.value) return@DisposableEffect onDispose { }
        val thread = HandlerThread("rosa-frame-pacing").apply { start() }
        val main = Handler(Looper.getMainLooper())
        val watch = JankWatch(startedAt = SystemClock.uptimeMillis())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val late = metrics.getMetric(FrameMetrics.TOTAL_DURATION) > metrics.getMetric(FrameMetrics.DEADLINE)
            if (watch.record(SystemClock.uptimeMillis(), late)) main.post { struggling.value = true }
        }
        val attached = runCatching { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }.isSuccess
        onDispose {
            if (attached) runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }
    return struggling
}

/**
 * Counts late frames in windows of [window] rendered frames and reports (once) when more than a
 * quarter of a window missed its deadline. Pure logic, so it can be tested without a device.
 */
internal class JankWatch(
    private val startedAt: Long,
    private val warmUpMillis: Long = 5_000,
    private val window: Int = 120,
) {
    private var frames = 0
    private var late = 0
    private var reported = false

    /** @return true exactly once, when the device is judged to be struggling. */
    fun record(nowMillis: Long, frameWasLate: Boolean): Boolean {
        if (reported || nowMillis - startedAt < warmUpMillis) return false
        frames++
        if (frameWasLate) late++
        if (frames < window) return false
        val struggling = late * 4 > frames
        frames = 0
        late = 0
        if (struggling) reported = true
        return struggling
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
