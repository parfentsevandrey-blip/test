package app.opal.core.tunnel.session

/**
 * What to do while the device's network does not reach the internet — Android could not validate it
 * — and Tor makes no progress. In Russia that is typically a mobile network in "whitelist" mode
 * (only approved addresses reachable, often for hours): no bridge can work then, and endless
 * retries only keep the radio awake. Elsewhere: a captive portal or an outage.
 *
 * Pure logic, driven by the session once a second; the session carries out the [Action]s. After
 * [hintAfterMillis] the user is told; after [pauseAfterMillis] Tor's network is paused and tried
 * again for [probeForMillis] every [probeEveryMillis]. Android validating the network, a network
 * change or Tor becoming ready ends it at once (see [reset]).
 */
internal class RestrictedNetwork(
    private val hintAfterMillis: Long = 45_000,
    private val pauseAfterMillis: Long = 3 * 60_000,
    private val probeEveryMillis: Long = 5 * 60_000,
    private val probeForMillis: Long = 90_000,
) {
    enum class Action {
        None,
        /** Tell the user the network does not let Tor through. */
        ShowHint,
        /** Stop Tor's network activity (`DisableNetwork 1`). */
        Pause,
        /** Let Tor try again (`DisableNetwork 0`). */
        Resume,
        /**
         * The network works again: clear the hint and restart Tor's attempts at once — whatever the
         * transport was waiting for began while nothing could get through.
         */
        Recovered,
        /** As [Recovered], with Tor's network paused until now. */
        RecoveredFromPause,
    }

    private enum class State {
        Clear,
        Hinted,
        Paused,
        Probing,
    }

    private var state = State.Clear
    private var until = 0L
    private var probeStartedAt = 0L

    /** The session should leave bridge races and hints to this class. */
    val active: Boolean
        get() = state != State.Clear

    /** Tor's network is off because of this class. */
    val paused: Boolean
        get() = state == State.Paused

    /**
     * One tick. [validated]: Android reaches the internet over the current network;
     * [lastProgressAt]: when Tor last made bootstrap progress.
     */
    fun tick(now: Long, validated: Boolean, lastProgressAt: Long): Action {
        if (validated) {
            val action =
                when (state) {
                    State.Clear -> Action.None
                    State.Paused -> Action.RecoveredFromPause
                    State.Hinted,
                    State.Probing -> Action.Recovered
                }
            state = State.Clear
            return action
        }
        return when (state) {
            State.Clear ->
                if (now - lastProgressAt >= hintAfterMillis) {
                    state = State.Hinted
                    Action.ShowHint
                } else Action.None
            State.Hinted ->
                if (now - lastProgressAt >= pauseAfterMillis) pause(now) else Action.None
            State.Paused ->
                if (now >= until) {
                    state = State.Probing
                    probeStartedAt = now
                    until = now + probeForMillis
                    Action.Resume
                } else Action.None
            State.Probing ->
                when {
                    // Tor got somewhere: whatever Android thinks, stay on and let it continue.
                    lastProgressAt > probeStartedAt -> {
                        state = State.Hinted
                        Action.None
                    }
                    now >= until -> pause(now)
                    else -> Action.None
                }
        }
    }

    /**
     * Tor became ready, or the network changed, was lost or came back (the session sets Tor's
     * network itself then). Returns true if Tor's network was paused by this class.
     */
    fun reset(): Boolean {
        val wasPaused = state == State.Paused
        state = State.Clear
        return wasPaused
    }

    private fun pause(now: Long): Action {
        state = State.Paused
        until = now + probeEveryMillis
        return Action.Pause
    }
}
