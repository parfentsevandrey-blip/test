package app.opal.core.tunnel.hev

import org.junit.Assert.assertTrue
import org.junit.Test

class HevConfigTest {
    @Test
    fun `config routes to tor socks with mapdns and udp reject`() {
        val yaml = HevTunnel.config(socksPort = 45678, mtu = 8500, debug = false)
        assertTrue(yaml.contains("port: 45678"))
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("udp: 'reject'"))
        assertTrue(yaml.contains("address: 198.18.0.2"))
        assertTrue(yaml.contains("network: 100.64.0.0"))
        assertTrue(yaml.contains("log-file: null"))
    }

    @Test
    fun `hev outwaits Tor's CONNECT reply`() {
        // The reply falls under hev's read-write timer, which must outlast SocksTimeout (120 s).
        val yaml = HevTunnel.config(socksPort = 45678, mtu = 8500, debug = false)
        assertTrue(yaml, yaml.contains("connect-timeout: 120000"))
        assertTrue(yaml, yaml.contains("tcp-read-write-timeout: 1800000"))
    }

    @Test
    fun `quiet long-lived connections stay open, with a bound on how many`() {
        // hev's defaults: 300000 ms (every connection quiet for 5 minutes closed) and unlimited.
        val misc =
            HevTunnel.config(socksPort = 45678, mtu = 8500, debug = false).substringAfter("misc:")
        assertTrue(misc, misc.contains("\n  tcp-read-write-timeout: 1800000\n"))
        assertTrue(misc, misc.contains("\n  max-session-count: 1024\n"))
    }
}
