package app.opal.core.tunnel.hev

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * TUN → SOCKS5 bridge (hev-socks5-tunnel) configured for Tor:
 * - MapDNS answers every DNS query with a fake address from [FAKE_NETWORK]; connecting to it sends
 *   the original *name* to Tor, so resolution happens at the exit and DNS never leaves the tunnel.
 * - `udp: reject` (our patch): non-DNS UDP gets ICMP port unreachable, so QUIC etc. fail fast and
 *   apps fall back to TCP.
 *
 * The TUN descriptor is borrowed, not owned: hev can be restarted (e.g. new Tor SOCKS port) while
 * the VPN interface stays up, so nothing leaks in between.
 */
internal class HevTunnel(context: Context) {

    private val configFile = File(context.noBackupFilesDir, "hev/hev.yml")
    private val mutex = Mutex()
    private var runningOn: Pair<Int, Int>? = null // (tun fd, socks port)

    suspend fun start(
        tun: ParcelFileDescriptor,
        socksPort: Int,
        mtu: Int,
        debug: Boolean,
    ): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = tun.fd to socksPort
            if (runningOn == key && HevNative.TProxyIsRunning()) return@withContext true
            if (runningOn != null) HevNative.TProxyStopService()
            configFile.parentFile?.mkdirs()
            configFile.writeText(config(socksPort, mtu, debug))
            val ok = HevNative.TProxyStartService(configFile.absolutePath, tun.fd)
            runningOn = if (ok) key else null
            ok
        }
    }

    suspend fun stop() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (runningOn != null) {
                HevNative.TProxyStopService()
                runningOn = null
            }
        }
    }

    val isRunning: Boolean
        get() = runningOn != null && HevNative.TProxyIsRunning()

    /** Bytes (tx, rx) seen on the TUN side since start, or null when stopped. */
    fun trafficBytes(): Pair<Long, Long>? {
        if (runningOn == null) return null
        val s = HevNative.TProxyGetStats()
        return s[1] to s[3]
    }

    companion object {
        const val TUN_ADDRESS_V4 = "198.18.0.1"
        const val TUN_ADDRESS_V6 = "fdfe:dcba:9876::1"
        const val DNS_ADDRESS = "198.18.0.2"
        const val FAKE_NETWORK = "100.64.0.0"
        const val FAKE_NETMASK = "255.192.0.0"
        const val MTU = 8500

        /**
         * hev waits this long for the SOCKS CONNECT reply, i.e. until Tor reached the destination
         * through an exit. Over Snowflake that often takes longer than hev's 10 s default; Tor
         * itself retries on other circuits for up to SocksTimeout (120 s), so we match it.
         */
        const val CONNECT_TIMEOUT_MS = 120_000

        internal fun config(socksPort: Int, mtu: Int, debug: Boolean): String =
            """
            |tunnel:
            |  mtu: $mtu
            |socks5:
            |  port: $socksPort
            |  address: 127.0.0.1
            |  udp: 'reject'
            |mapdns:
            |  address: $DNS_ADDRESS
            |  port: 53
            |  network: $FAKE_NETWORK
            |  netmask: $FAKE_NETMASK
            |  cache-size: 10000
            |misc:
            |  connect-timeout: $CONNECT_TIMEOUT_MS
            |  log-level: ${if (debug) "warn" else "error"}
            |  log-file: ${if (debug) "stderr" else "null"}
            |"""
                .trimMargin()
    }
}
