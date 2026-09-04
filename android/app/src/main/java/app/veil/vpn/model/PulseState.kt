package app.veil.vpn.model

/**
 * The last thing the tunnel's pulse measured.
 *
 * The pulse is a small request sent through the tunnel every few seconds
 * while it is up — see the supervisor in the VPN service — and this is what it
 * found: the round trip of the last one, the rate of the last speed sample,
 * when, and how many in a row have gone unanswered. It is shown on the home
 * screen so that "is the tunnel actually alive right now" has an answer that
 * is a measurement rather than a colour.
 */
data class PulseState(
    val rttMillis: Long = 0,
    val kbytesPerSecond: Long = 0,
    val measuredAtMillis: Long = 0,
    /** Consecutive pulses that went unanswered. Zero while the path is fine. */
    val failures: Int = 0,
    val ok: Boolean = false,
) {
    val hasMeasurement: Boolean get() = measuredAtMillis > 0
}
