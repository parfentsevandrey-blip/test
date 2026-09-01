package app.veil.vpn.data

import app.veil.vpn.core.VeilLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the app from hammering an endpoint that is already being punished.
 *
 * When Russian DPI decides a TLS connection is a proxy it does not send a
 * reset — it drops the packets and lets the socket hang, for a reported two
 * minutes. Retrying inside that window achieves nothing, and retrying *with a
 * different fingerprint* is reported to extend the penalty to ten minutes for
 * every TLS connection to that host. That turns the obvious reflex — try the
 * next transport against the same bridge immediately — into the worst possible
 * move.
 *
 * So a frozen endpoint is set aside for longer than the penalty is believed to
 * last, and a second freeze sets it aside for longer than the extended penalty.
 * State is per-session and in memory: it is a scheduling hint, not a record of
 * where the user has been.
 */
class EndpointCooldown {

    private data class Entry(val untilMillis: Long, val strikes: Int, val reason: String)

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Marks an endpoint as blackholed. The first freeze costs 150 seconds — a
     * margin over the reported ~120 second penalty — and any further freeze
     * costs 650, over the reported extended one.
     */
    fun markFrozen(host: String, reason: String = "connection blackholed after the handshake") {
        val strikes = (entries[host]?.strikes ?: 0) + 1
        val penalty = if (strikes <= 1) FIRST_FREEZE_MILLIS else EXTENDED_FREEZE_MILLIS
        entries[host] = Entry(System.currentTimeMillis() + penalty, strikes, reason)
        VeilLog.w("cooldown", "$host set aside for ${penalty / 1000}s: $reason")
    }

    /** A plain failure is worth a short pause, not a penalty-sized one. */
    fun markFailed(host: String, reason: String = "did not connect") {
        if (entries[host]?.strikes?.let { it > 0 } == true) return
        entries[host] = Entry(System.currentTimeMillis() + SHORT_MILLIS, 0, reason)
    }

    fun isCoolingDown(host: String): Boolean {
        val entry = entries[host] ?: return false
        if (System.currentTimeMillis() >= entry.untilMillis) {
            entries.remove(host)
            return false
        }
        return true
    }

    /** How many of these endpoints are still on the bench. */
    fun anyCoolingDown(hosts: Collection<String>): Boolean = hosts.any(::isCoolingDown)

    fun remainingSeconds(host: String): Long {
        val entry = entries[host] ?: return 0
        return ((entry.untilMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    fun describe(): List<String> = entries.entries
        .filter { System.currentTimeMillis() < it.value.untilMillis }
        .map { (host, entry) ->
            "$host — ${remainingSeconds(host)}s left, ${entry.reason}"
        }

    fun clear() = entries.clear()

    private companion object {
        const val SHORT_MILLIS = 20_000L
        const val FIRST_FREEZE_MILLIS = 150_000L
        const val EXTENDED_FREEZE_MILLIS = 650_000L
    }
}
