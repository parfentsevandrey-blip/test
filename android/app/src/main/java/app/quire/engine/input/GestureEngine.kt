package app.quire.engine.input

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Our own recogniser. It must not use GestureDetector, ScaleGestureDetector or VelocityTracker.
 * Feed it MotionEvents; it reports gestures through a listener. Velocity is estimated from a
 * short ring buffer of timestamped positions with a least-squares fit.
 */
class GestureEngine(density: Float) {

    /** What a host view implements to hear about gestures; every method is optional. */
    interface Listener {

        /** A finger landed, before anything is known about what it will become. */
        fun onDown(x: Float, y: Float) {}

        /** A press that lifted without travelling or being held. */
        fun onTap(x: Float, y: Float) {}

        /** The second of two quick taps in the same place, reported as it lands. */
        fun onDoubleTap(x: Float, y: Float) {}

        /** A press held still past the long-press threshold, reported while the finger is down. */
        fun onLongPress(x: Float, y: Float) {}

        /** A press has travelled past the touch slop and is now a drag. */
        fun onDragStart(x: Float, y: Float) {}

        /** The drag moved; [dx] and [dy] are the step since the previous report. */
        fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}

        /** The drag ended, carrying the release velocity in pixels per second for a fling. */
        fun onDragEnd(vx: Float, vy: Float) {}

        /** Two fingers changed their separation; [scale] is relative to the previous report. */
        fun onPinch(scale: Float, focusX: Float, focusY: Float) {}

        /** The pinch is over, so a host can settle whatever it was scaling. */
        fun onPinchEnd() {}
    }

    /** Where recognised gestures are delivered; null while nothing is listening. */
    var listener: Listener? = null

    private val touchSlop = TOUCH_SLOP_DP * density
    private val doubleTapSlop = DOUBLE_TAP_SLOP_DP * density
    private val minPinchSpan = MIN_PINCH_SPAN_DP * density
    private val maxVelocity = MAX_FLING_DP_PER_SECOND * density

    private var host: View? = null

    private var activeId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var strokeStartMillis = 0L
    private var dragging = false
    private var pinching = false
    private var longPressFired = false
    private var suppressTap = false

    private var pinchIdA = MotionEvent.INVALID_POINTER_ID
    private var pinchIdB = MotionEvent.INVALID_POINTER_ID
    private var previousSpan = 0f

    private var lastTapMillis = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    // Preallocated ring of recent positions; the fit reads it in place, so a fling costs no
    // allocation at the moment the finger leaves the glass.
    private val sampleT = FloatArray(HISTORY)
    private val sampleX = FloatArray(HISTORY)
    private val sampleY = FloatArray(HISTORY)
    private var sampleHead = 0
    private var sampleCount = 0

    private val longPress = Runnable {
        if (!dragging && !pinching && activeId != MotionEvent.INVALID_POINTER_ID) {
            longPressFired = true
            suppressTap = true
            listener?.onLongPress(downX, downY)
        }
    }

    /** Long-press needs a delayed callback; the host View supplies the posting surface. */
    fun attach(view: View) {
        host?.removeCallbacks(longPress)
        host = view
    }

    /** Call from View.onTouchEvent. Returns true when the event was consumed. */
    fun onTouch(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            handleDown(event)
            true
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
            handlePointerDown(event)
            true
        }
        MotionEvent.ACTION_MOVE -> {
            handleMove(event)
            true
        }
        MotionEvent.ACTION_POINTER_UP -> {
            handlePointerUp(event)
            true
        }
        MotionEvent.ACTION_UP -> {
            handleUp(event)
            true
        }
        MotionEvent.ACTION_CANCEL -> {
            cancel()
            true
        }
        else -> false
    }

    /** Abandons the stroke in progress, closing any drag or pinch the host is still holding. */
    fun cancel() {
        host?.removeCallbacks(longPress)
        val target = listener
        if (pinching) {
            pinching = false
            target?.onPinchEnd()
        }
        if (dragging) {
            dragging = false
            target?.onDragEnd(0f, 0f)
        }
        // A stroke taken away from us must not seed a double tap the user never completed.
        lastTapMillis = 0L
        reset()
    }

    private fun handleDown(event: MotionEvent) {
        host?.removeCallbacks(longPress)
        val x = event.x
        val y = event.y
        activeId = event.getPointerId(0)
        downX = x
        downY = y
        lastX = x
        lastY = y
        strokeStartMillis = event.eventTime
        dragging = false
        pinching = false
        longPressFired = false
        suppressTap = false
        sampleHead = 0
        sampleCount = 0
        addSample(event.eventTime, x, y)
        val target = listener
        target?.onDown(x, y)
        val since = event.eventTime - lastTapMillis
        val near = distance(x, y, lastTapX, lastTapY) <= doubleTapSlop
        if (lastTapMillis != 0L && since in 0..DOUBLE_TAP_MILLIS && near) {
            // Reported on the landing rather than the lift: the second tap of a double tap is
            // already unambiguous, and waiting for the lift only adds latency to a zoom.
            lastTapMillis = 0L
            suppressTap = true
            target?.onDoubleTap(x, y)
        } else {
            host?.postDelayed(longPress, LONG_PRESS_MILLIS)
        }
    }

    private fun handlePointerDown(event: MotionEvent) {
        host?.removeCallbacks(longPress)
        if (pinching || event.pointerCount < 2) return
        if (dragging) {
            dragging = false
            listener?.onDragEnd(fitVelocity(sampleX), fitVelocity(sampleY))
        }
        suppressTap = true
        // Pointer indices are always packed from zero, so the first two are two fingers that are
        // down right now, whichever of them is the one that just landed.
        pinchIdA = event.getPointerId(0)
        pinchIdB = event.getPointerId(1)
        val ax = event.getX(0)
        val ay = event.getY(0)
        val bx = event.getX(1)
        val by = event.getY(1)
        previousSpan = distance(ax, ay, bx, by)
        pinching = true
        // An opening report of scale 1 hands the host the focus point before anything moves,
        // which is what it needs to anchor the zoom.
        listener?.onPinch(1f, (ax + bx) * 0.5f, (ay + by) * 0.5f)
    }

    private fun handleMove(event: MotionEvent) {
        if (pinching) {
            updatePinch(event)
            return
        }
        val index = event.findPointerIndex(activeId)
        if (index < 0) return
        // Batched samples are fed to the ring first: they are most of the evidence for velocity
        // on a fast flick, where a frame may carry half a dozen positions in one event.
        var h = 0
        val history = event.historySize
        while (h < history) {
            addSample(
                event.getHistoricalEventTime(h),
                event.getHistoricalX(index, h),
                event.getHistoricalY(index, h),
            )
            h++
        }
        val x = event.getX(index)
        val y = event.getY(index)
        addSample(event.eventTime, x, y)
        if (!dragging) {
            if (distance(x, y, downX, downY) <= touchSlop) return
            dragging = true
            host?.removeCallbacks(longPress)
            // The drag starts from where the finger is now, so the slop is not delivered as a
            // first jump the host has to absorb.
            lastX = x
            lastY = y
            listener?.onDragStart(x, y)
        }
        val dx = x - lastX
        val dy = y - lastY
        lastX = x
        lastY = y
        if (dx != 0f || dy != 0f) listener?.onDrag(x, y, dx, dy)
    }

    private fun updatePinch(event: MotionEvent) {
        val ia = event.findPointerIndex(pinchIdA)
        val ib = event.findPointerIndex(pinchIdB)
        if (ia < 0 || ib < 0) {
            endPinch()
            return
        }
        val ax = event.getX(ia)
        val ay = event.getY(ia)
        val bx = event.getX(ib)
        val by = event.getY(ib)
        val span = distance(ax, ay, bx, by)
        if (span < minPinchSpan || previousSpan < minPinchSpan) {
            // Fingers this close make the ratio explode; keep tracking the span so the scale is
            // continuous again once they separate, but report nothing from the noise.
            previousSpan = span
            return
        }
        val scale = span / previousSpan
        previousSpan = span
        listener?.onPinch(scale, (ax + bx) * 0.5f, (ay + by) * 0.5f)
    }

    private fun handlePointerUp(event: MotionEvent) {
        val goneIndex = event.actionIndex
        val goneId = event.getPointerId(goneIndex)
        if (pinching) {
            if (goneId != pinchIdA && goneId != pinchIdB) return
            if (rebindPinch(event, goneIndex)) return
            endPinch()
            handOver(event, goneIndex)
            return
        }
        if (goneId == activeId) handOver(event, goneIndex)
    }

    private fun handleUp(event: MotionEvent) {
        host?.removeCallbacks(longPress)
        val found = event.findPointerIndex(activeId)
        val index = if (found >= 0) found else 0
        val x = event.getX(index)
        val y = event.getY(index)
        if (pinching) {
            endPinch()
            reset()
            return
        }
        addSample(event.eventTime, x, y)
        if (dragging) {
            dragging = false
            listener?.onDragEnd(fitVelocity(sampleX), fitVelocity(sampleY))
        } else if (!suppressTap && !longPressFired) {
            listener?.onTap(x, y)
            lastTapMillis = event.eventTime
            lastTapX = x
            lastTapY = y
        }
        reset()
    }

    // Three fingers down and one lifts: the pinch carries on between the two that remain rather
    // than ending and restarting under the user's hand.
    private fun rebindPinch(event: MotionEvent, skipIndex: Int): Boolean {
        var first = -1
        var second = -1
        var i = 0
        while (i < event.pointerCount) {
            if (i != skipIndex) {
                if (first < 0) {
                    first = i
                } else {
                    second = i
                    break
                }
            }
            i++
        }
        if (first < 0 || second < 0) return false
        pinchIdA = event.getPointerId(first)
        pinchIdB = event.getPointerId(second)
        previousSpan = distance(
            event.getX(first),
            event.getY(first),
            event.getX(second),
            event.getY(second),
        )
        return true
    }

    // The pointer being tracked left while others are still down: adopt one of them from where
    // it actually is, so a drag continues instead of teleporting to the lifted finger.
    private fun handOver(event: MotionEvent, goneIndex: Int) {
        var index = -1
        var i = 0
        while (i < event.pointerCount) {
            if (i != goneIndex) {
                index = i
                break
            }
            i++
        }
        if (index < 0) {
            activeId = MotionEvent.INVALID_POINTER_ID
            return
        }
        activeId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        downX = x
        downY = y
        lastX = x
        lastY = y
        sampleHead = 0
        sampleCount = 0
        addSample(event.eventTime, x, y)
    }

    private fun endPinch() {
        if (!pinching) return
        pinching = false
        pinchIdA = MotionEvent.INVALID_POINTER_ID
        pinchIdB = MotionEvent.INVALID_POINTER_ID
        previousSpan = 0f
        listener?.onPinchEnd()
    }

    private fun reset() {
        activeId = MotionEvent.INVALID_POINTER_ID
        dragging = false
        pinching = false
        longPressFired = false
        suppressTap = false
        pinchIdA = MotionEvent.INVALID_POINTER_ID
        pinchIdB = MotionEvent.INVALID_POINTER_ID
        previousSpan = 0f
        sampleHead = 0
        sampleCount = 0
    }

    private fun addSample(timeMillis: Long, x: Float, y: Float) {
        sampleT[sampleHead] = (timeMillis - strokeStartMillis) / 1000f
        sampleX[sampleHead] = x
        sampleY[sampleHead] = y
        sampleHead = (sampleHead + 1) % HISTORY
        if (sampleCount < HISTORY) sampleCount++
    }

    /**
     * Least-squares slope over the samples inside the recent window, walked newest first. Fitting
     * a line rather than differencing the last two points keeps one jittery sample from deciding
     * a fling, and the window means a finger that stops before lifting releases at rest.
     */
    private fun fitVelocity(values: FloatArray): Float {
        if (sampleCount < 2) return 0f
        val newestIndex = (sampleHead - 1 + HISTORY) % HISTORY
        val newest = sampleT[newestIndex]
        var n = 0f
        var sumT = 0f
        var sumV = 0f
        var sumTT = 0f
        var sumTV = 0f
        var k = 0
        while (k < sampleCount) {
            val i = (sampleHead - 1 - k + 2 * HISTORY) % HISTORY
            // Times are centred on the newest sample: the slope is unchanged by the shift, and
            // the sums stay small enough for single precision to hold onto them.
            val t = sampleT[i] - newest
            if (t < -VELOCITY_WINDOW_SECONDS) break
            val v = values[i]
            n += 1f
            sumT += t
            sumV += v
            sumTT += t * t
            sumTV += t * v
            k++
        }
        if (n < 2f) return 0f
        val denominator = n * sumTT - sumT * sumT
        if (abs(denominator) < 1e-7f) return 0f
        val slope = (n * sumTV - sumT * sumV) / denominator
        if (!slope.isFinite()) return 0f
        return slope.coerceIn(-maxVelocity, maxVelocity)
    }

    private fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val LONG_PRESS_MILLIS = 420L
        const val DOUBLE_TAP_MILLIS = 280L
        const val TOUCH_SLOP_DP = 8f
        const val DOUBLE_TAP_SLOP_DP = 32f
        const val MIN_PINCH_SPAN_DP = 16f
        const val MAX_FLING_DP_PER_SECOND = 8000f
        const val VELOCITY_WINDOW_SECONDS = 0.12f
        const val HISTORY = 12
    }
}
