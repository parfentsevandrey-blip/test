package app.veil.vpn.net

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Picks loopback ports for the listeners this app is responsible for.
 *
 * There are two ways to give tor a port. `SocksPort auto` lets tor bind zero
 * and pick one, which reads as the tidier option and is not: while tor's
 * network is disabled it has no SOCKS listener at all, and every time the
 * network is toggled it binds a different port. Anything that read the address
 * once is then pointing at nothing.
 *
 * So the app picks the numbers instead. The kernel is still the one choosing —
 * binding port zero and reading back what it gave us is the only way to learn a
 * free port without a race worth worrying about — but the answer is then
 * written into the configuration and stays true for the session.
 *
 * The window between closing the probe socket and tor binding the same port is
 * real but tiny, and losing it means one failed start rather than a wrong
 * address carried around silently.
 */
object LoopbackPorts {

    /**
     * Returns [count] distinct free loopback ports.
     *
     * All the probe sockets are held open at once and closed together, so the
     * kernel cannot hand out the same number twice.
     */
    fun reserve(count: Int): List<Int> {
        val sockets = ArrayList<ServerSocket>(count)
        return try {
            repeat(count) {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), 0))
                sockets += socket
            }
            sockets.map { it.localPort }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
    }

    /** True once nothing is listening on the port. Binding it is the only test. */
    fun isFree(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), port))
            true
        }
    }.getOrDefault(false)

    private const val LOOPBACK = "127.0.0.1"
}
