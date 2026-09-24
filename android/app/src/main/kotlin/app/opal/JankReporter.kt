package app.opal

import android.util.Log
import android.view.Window
import androidx.metrics.performance.JankStats

/**
 * Debug builds only: share of janky frames, logged to logcat every [WINDOW] frames while the
 * activity is resumed. Nothing is collected or sent anywhere — release builds do not create it.
 */
internal class JankReporter(window: Window) {
    private var frames = 0
    private var janky = 0
    private val stats =
        JankStats.createAndTrack(window) { frame ->
            frames++
            if (frame.isJank) janky++
            if (frames >= WINDOW) {
                Log.d(TAG, "janky frames: ${"%.1f".format(janky * 100f / frames)}% of $frames")
                frames = 0
                janky = 0
            }
        }

    fun resume() {
        stats.isTrackingEnabled = true
    }

    fun pause() {
        stats.isTrackingEnabled = false
    }

    private companion object {
        const val TAG = "OpalJank"
        const val WINDOW = 600
    }
}
