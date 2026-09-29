package app.opal.core.tunnel.session

/**
 * Whether a transport session outlived a deep sleep of the phone. While the CPU sleeps not a frame
 * leaves the phone; once that lasts longer than the servers keep a silent session (Snowflake 4 min,
 * dnstt 2), the session is gone — and the watchdog would notice only after a minute of silence with
 * apps waiting, right after the user picks the phone up. So the session is renewed as soon as the
 * screen is on; with the screen off nobody needs the network yet.
 *
 * Pure logic, driven by the session's watchdog tick.
 */
internal class SleepWatch {
    /** The phone slept longer than the servers keep the session. */
    var stale = false
        private set

    /**
     * One tick: [sleptMillis] of deep sleep since the last one; [watching]: a session transport is
     * ready. Returns true when the session has just become stale.
     */
    fun onTick(sleptMillis: Long, expiryMillis: Long, watching: Boolean): Boolean {
        if (stale || !watching || sleptMillis < expiryMillis) return false
        stale = true
        return true
    }

    /** Whether to renew the session now: stale, and someone is at the screen. */
    fun renewNow(interactive: () -> Boolean): Boolean {
        if (!stale || !interactive()) return false
        stale = false
        return true
    }

    /** The bridge answered after all, or the session is being renewed anyway. */
    fun clear() {
        stale = false
    }
}
