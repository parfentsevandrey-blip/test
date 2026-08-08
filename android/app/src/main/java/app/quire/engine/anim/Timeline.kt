package app.quire.engine.anim

/**
 * Runs several Animatables, optionally with per-item delays, as one unit — a list that enters
 * one row after another is one Timeline the host subscribes once, not one clock hook per row.
 */
class Timeline : Animatable {

    private class Entry(val item: Animatable, val delay: Float)

    private val entries = ArrayList<Entry>(8)
    private var elapsed = 0f
    private var longestDelay = 0f

    /** Adds a member, held back by [delaySeconds] after each [restart]; chainable. */
    fun add(item: Animatable, delaySeconds: Float = 0f): Timeline {
        val delay = if (delaySeconds > 0f) delaySeconds else 0f
        entries.add(Entry(item, delay))
        if (delay > longestDelay) longestDelay = delay
        return this
    }

    /** Rewinds the shared clock so the delays stagger the members again from the start. */
    fun restart() {
        elapsed = 0f
        var i = 0
        while (i < entries.size) {
            // Springs and decays have no start to return to — they resume from wherever the last
            // gesture left them, which is the point of them. Only scripted members rewind.
            when (val item = entries[i].item) {
                is Track -> item.restart()
                is Timeline -> item.restart()
                else -> Unit
            }
            i++
        }
    }

    override val atRest: Boolean
        get() {
            if (elapsed < longestDelay) return false
            var i = 0
            while (i < entries.size) {
                if (!entries[i].item.atRest) return false
                i++
            }
            return true
        }

    override fun advance(dt: Float): Boolean {
        if (entries.isEmpty()) return false
        val step = clamp(dt, 0f, MAX_STEP_SECONDS)
        elapsed += step
        var running = elapsed < longestDelay
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            val local = elapsed - entry.delay
            if (local > 0f) {
                // On the frame a member comes due it gets only the slice of the step that falls
                // after its delay, so a long delay does not quantise to whole frames.
                val slice = if (local < step) local else step
                if (entry.item.advance(slice)) running = true
            }
            i++
        }
        return running
    }
}
