package app.veil.vpn.net

import app.veil.vpn.core.VeilLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom

/**
 * Which STUN servers answer from here, and how fast.
 *
 * This is the measurement behind the single largest avoidable delay in a
 * Snowflake connection. Before it will even ask the broker for a proxy, the
 * client gathers ICE candidates from every STUN server on its list and waits
 * for gathering to *complete* — and pion's gathering completes only when every
 * server has answered or its five-second timeout has run out. One unreachable
 * server on the list is therefore five seconds of nothing, paid on every peer
 * the client collects, standbys included. On the mobile networks this app is
 * for, several of the published servers do not answer; that is what "Snowflake
 * takes twenty seconds" is mostly made of.
 *
 * The published list is not wrong, and an earlier attempt to shorten it by
 * guessing was: it kept the three servers furthest from a user in Russia and
 * discarded the ones most likely to answer. The fix is to ask, not to guess.
 * Every server is asked at once on one socket; the ones that reply are handed
 * to Snowflake in the order they replied, and the ones that did not are left
 * out. The same replies say what the NAT does, which the client needs before
 * its first offer if the broker is to match it well.
 *
 * Results are remembered per network for a few hours. A network's STUN
 * reachability does not change minute to minute, and a repeat connect should
 * not pay for the question again.
 */
object StunSurvey {

    /** One server that answered, with the round trip it took. */
    data class Answer(val host: String, val port: Int, val mapped: String, val millis: Long) {
        val url: String get() = "stun:$host:$port"
    }

    data class Result(
        val answers: List<Answer>,
        val natBehaviour: NatBehaviour,
        val elapsedMillis: Long,
        val takenAtMillis: Long = System.currentTimeMillis(),
    ) {
        /** The ICE list to hand Snowflake: responsive servers, fastest first. */
        val iceServers: List<String> get() = answers.map { it.url }
        val isFresh: Boolean get() = System.currentTimeMillis() - takenAtMillis < FRESH_MILLIS
    }

    private val cache = mutableMapOf<String, Result>()

    /** The last answer for this network, if it is recent enough to trust. */
    fun cached(networkFingerprint: String): Result? =
        cache[networkFingerprint]?.takeIf { it.isFresh }

    /**
     * Asks every server at once and collects what answers within the window.
     *
     * Unlike the probe's version of this, which stops at two replies because
     * two are what the NAT comparison needs, this keeps listening for the whole
     * window: the point is to learn about every server, since every server
     * left on the list costs time if it is dead.
     */
    suspend fun run(
        networkFingerprint: String,
        protector: SocketProtector? = null,
        servers: List<Pair<String, Int>> = PUBLISHED_SERVERS,
        windowMillis: Int = WINDOW_MILLIS,
    ): Result = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val answers = mutableListOf<Answer>()

        runCatching {
            DatagramSocket().use { socket ->
                protector?.protect(socket)
                val pending = mutableMapOf<String, Pair<String, Int>>()
                for ((host, port) in servers) {
                    val transaction = runCatching { ask(socket, host, port) }.getOrNull()
                    if (transaction != null) pending[transaction] = host to port
                }
                val deadline = started + windowMillis
                while (pending.isNotEmpty() && System.currentTimeMillis() < deadline) {
                    val remaining = (deadline - System.currentTimeMillis()).toInt()
                    if (remaining <= 0) break
                    socket.soTimeout = remaining
                    val reply = DatagramPacket(ByteArray(512), 512)
                    runCatching { socket.receive(reply) }.getOrElse { break }
                    val key = transactionKey(reply.data, reply.length) ?: continue
                    val (host, port) = pending.remove(key) ?: continue
                    val mapped = parseXorMappedAddress(reply.data, reply.length, keyBytes(key))
                        ?: continue
                    answers += Answer(host, port, mapped, System.currentTimeMillis() - started)
                }
            }
        }.onFailure { VeilLog.w("stun", "survey failed: $it") }

        val nat = when {
            answers.isEmpty() -> NatBehaviour.NO_UDP
            answers.size == 1 -> NatBehaviour.UNKNOWN
            answers.map { it.mapped }.distinct().size == 1 -> NatBehaviour.ENDPOINT_INDEPENDENT
            else -> NatBehaviour.SYMMETRIC
        }
        val result = Result(answers.sortedBy { it.millis }, nat, System.currentTimeMillis() - started)
        cache[networkFingerprint] = result
        VeilLog.i(
            "stun",
            "${answers.size}/${servers.size} answered in ${result.elapsedMillis}ms, nat $nat: " +
                answers.joinToString { "${it.host} ${it.millis}ms" }.ifEmpty { "none" },
        )
        result
    }

    // --- STUN on the wire, the two dozen lines of it this needs -------------

    /** Sends one binding request; returns its transaction id as hex. */
    internal fun ask(socket: DatagramSocket, host: String, port: Int): String {
        val transactionId = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val request = ByteArray(20)
        request[0] = 0x00; request[1] = 0x01 // Binding request
        request[2] = 0x00; request[3] = 0x00 // Length 0
        request[4] = 0x21; request[5] = 0x12 // Magic cookie 0x2112A442
        request[6] = 0xA4.toByte(); request[7] = 0x42
        transactionId.copyInto(request, 8)
        socket.send(DatagramPacket(request, request.size, InetAddress.getByName(host), port))
        return transactionId.joinToString("") { "%02x".format(it) }
    }

    /** The transaction id of a binding success response, or null. */
    internal fun transactionKey(data: ByteArray, length: Int): String? {
        if (length < 20) return null
        if (data[0].toInt() != 0x01 || data[1].toInt() != 0x01) return null
        return (8 until 20).joinToString("") { "%02x".format(data[it]) }
    }

    internal fun keyBytes(key: String): ByteArray =
        ByteArray(12) { key.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    internal fun parseXorMappedAddress(data: ByteArray, length: Int, transactionId: ByteArray): String? {
        if (length < 20) return null
        if (data[0].toInt() != 0x01 || data[1].toInt() != 0x01) return null
        for (i in 0 until 12) if (data[8 + i] != transactionId[i]) return null

        var offset = 20
        while (offset + 4 <= length) {
            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            val size = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            val value = offset + 4
            if (value + size > length) return null
            if (type == XOR_MAPPED_ADDRESS && size >= 8 && data[value + 1].toInt() == 0x01) {
                val port = (
                    ((data[value + 2].toInt() and 0xFF) shl 8) or (data[value + 3].toInt() and 0xFF)
                    ) xor MAGIC_COOKIE_HIGH
                val octets = IntArray(4) { i -> (data[value + 4 + i].toInt() and 0xFF) xor MAGIC_COOKIE[i] }
                return octets.joinToString(".") + ":" + port
            }
            offset = value + size + ((4 - size % 4) % 4)
        }
        return null
    }

    /**
     * How long to listen. Long enough for a slow-but-real server on a mobile
     * network — the survey on one user's Wi-Fi saw answers at six seconds — and
     * bounded, because every second here is a second before the connect.
     * Cached afterwards, so it is paid once per network, not once per connect.
     */
    const val WINDOW_MILLIS = 2_500

    /**
     * The Tor Project's published STUN list, in its order. This is the set the
     * survey asks; Snowflake is handed whichever of them answered.
     */
    val PUBLISHED_SERVERS: List<Pair<String, Int>> = listOf(
        "stun.antisip.com" to 3478,
        "stun.epygi.com" to 3478,
        "stun.uls.co.za" to 3478,
        "stun.voipgate.com" to 3478,
        "stun.telnyx.com" to 3478,
        "stun.hot-chilli.net" to 3478,
        "stun.fitauto.ru" to 3478,
        "stun.m-online.net" to 3478,
    )
    private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L
    private const val XOR_MAPPED_ADDRESS = 0x0020
    private val MAGIC_COOKIE = intArrayOf(0x21, 0x12, 0xA4, 0x42)
    private const val MAGIC_COOKIE_HIGH = 0x2112
}
