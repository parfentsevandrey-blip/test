package app.quire.engine.anim

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * One frame clock for the whole app, driven by android.view.Choreographer. Nothing here may
 * use the platform animators: they are scaled to zero by the system animation setting, and this
 * app's motion is its interface.
 */
object Clock {

    // Resolved lazily and only ever touched from the main thread: Choreographer.getInstance()
    // needs a Looper, and building either of these at class-init time would tie the clock's
    // existence to whichever thread happened to mention it first.
    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val listeners = ArrayList<(Float) -> Boolean>(8)

    // A listener is free to subscribe or unsubscribe anything from inside its own callback, so
    // the live list is never structurally changed while it is being walked.
    private val pendingAdd = ArrayList<(Float) -> Boolean>(4)
    private val pendingRemove = ArrayList<(Float) -> Boolean>(4)

    private var dispatching = false
    private var running = false
    private var lastFrameNanos = 0L

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        onFrame(frameTimeNanos)
    }

    /** Whether frames are being delivered, so a host can tell a live surface from a still one. */
    val isRunning: Boolean
        get() = running

    /**
     * Adds a per-frame callback, which returns false on the frame it no longer needs the clock.
     */
    fun subscribe(listener: (dtSeconds: Float) -> Boolean) {
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            handler.post { subscribe(listener) }
            return
        }
        if (dispatching) {
            pendingAdd.add(listener)
        } else if (indexOf(listeners, listener) < 0) {
            listeners.add(listener)
        }
        start()
    }

    /** Drops a callback early, for a view that goes away before its motion has settled. */
    fun unsubscribe(listener: (Float) -> Boolean) {
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            handler.post { unsubscribe(listener) }
            return
        }
        if (dispatching) {
            pendingRemove.add(listener)
            return
        }
        val at = indexOf(listeners, listener)
        if (at >= 0) listeners.removeAt(at)
        if (listeners.isEmpty()) stop()
    }

    private fun start() {
        if (running) return
        if (listeners.isEmpty() && pendingAdd.isEmpty()) return
        running = true
        lastFrameNanos = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stop() {
        if (!running) return
        running = false
        lastFrameNanos = 0L
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun onFrame(frameTimeNanos: Long) {
        val raw = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1e9f
        lastFrameNanos = frameTimeNanos
        val dt = clamp(raw, 0f, MAX_STEP_SECONDS)
        dispatching = true
        try {
            var i = 0
            while (i < listeners.size) {
                val listener = listeners[i]
                if (!listener(dt)) pendingRemove.add(listener)
                i++
            }
        } finally {
            dispatching = false
            flush()
        }
        if (listeners.isEmpty()) {
            running = false
            lastFrameNanos = 0L
        } else {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private fun flush() {
        var i = 0
        while (i < pendingRemove.size) {
            val at = indexOf(listeners, pendingRemove[i])
            if (at >= 0) listeners.removeAt(at)
            i++
        }
        pendingRemove.clear()
        i = 0
        while (i < pendingAdd.size) {
            val listener = pendingAdd[i]
            // Removes are applied first, so re-subscribing inside a callback that returned false
            // keeps the listener rather than losing it to its own removal.
            if (indexOf(listeners, listener) < 0) listeners.add(listener)
            i++
        }
        pendingAdd.clear()
    }

    private fun indexOf(list: ArrayList<(Float) -> Boolean>, listener: (Float) -> Boolean): Int {
        var i = 0
        while (i < list.size) {
            if (list[i] == listener) return i
            i++
        }
        return -1
    }
}
