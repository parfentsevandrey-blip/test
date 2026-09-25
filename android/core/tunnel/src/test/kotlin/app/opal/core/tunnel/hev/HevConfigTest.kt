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
    fun `hev waits as long as Tor for the CONNECT reply`() {
        // hev's 10 s default cut off connections that Tor was still retrying over Snowflake.
        val yaml = HevTunnel.config(socksPort = 45678, mtu = 8500, debug = false)
        assertTrue(yaml, yaml.contains("connect-timeout: 120000"))
    }
}
