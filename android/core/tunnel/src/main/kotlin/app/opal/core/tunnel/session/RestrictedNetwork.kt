package app.opal.core.tunnel.session

/**
 * Whether to tell the user that the network does not reach the internet: Android could not validate
 * it and Tor makes no progress. In Russia that is typically a mobile network in "whitelist" mode
 * (only approved addresses reachable, often for hours): no bridge works then. Elsewhere: a captive
 * portal or an outage.
 *
 * Only a hint — Tor and the transport keep trying exactly as before. 1.0.5 also paused Tor on such
 * networks and restarted its connections when Android validated the network again; on real phones
 * connections got worse: Android's check is a guess about *its* servers, and every restart threw a
 * Snowflake session away (CLAUDE.md, ADR 50).
 *
 * Pure logic, driven by the session once a second.
 */
internal class RestrictedNetwork(private val hintAfterMillis: Long = 60_000) {
    enum class Change {
        Show,
        Hide,
    }

    var shown = false
        private set

    /**
     * One tick. [validated]: Android reaches the internet over the current network;
     * [lastProgressAt]: when Tor last made progress. Returns what changed, if anything.
     */
    fun tick(now: Long, validated: Boolean, lastProgressAt: Long): Change? {
        val restricted = !validated && now - lastProgressAt >= hintAfterMillis
        if (restricted == shown) return null
        shown = restricted
        return if (restricted) Change.Show else Change.Hide
    }

    /**
     * Tor became ready or restarted, the network changed, or the bridges did. Returns true if the
     * hint was shown (the caller takes it down).
     */
    fun reset(): Boolean {
        val wasShown = shown
        shown = false
        return wasShown
    }
}
