package app.opal.core.tunnel.tor

import app.opal.core.model.settings.AppSettings
import app.opal.core.tunnel.session.TorConfigFactory
import app.opal.core.tunnel.util.LoopbackPorts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SOCKS port must survive `DisableNetwork` and a start without network (found on Android 17).
 */
class SocksPortTest {

    private fun config(networkUp: Boolean) =
        TorConfigFactory.startup(emptyList(), emptyMap(), AppSettings(), 41000, networkUp)

    @Test
    fun `the SOCKS port is fixed, never auto`() {
        val lines = config(networkUp = true).render().lines()
        assertTrue(lines.contains("SocksPort 127.0.0.1:41000 IPv6Traffic"))
        assertTrue(lines.none { it.contains("auto") })
    }

    @Test
    fun `started without network, Tor has no SOCKS listener yet and the configured port is used`() {
        assertEquals(41000, CTorEngine.socksPortOf("", config(networkUp = false)))
        assertEquals(41000, CTorEngine.socksPortOf(null, config(networkUp = false)))
    }

    @Test
    fun `with network a missing listener is an error`() {
        assertNull(CTorEngine.socksPortOf("", config(networkUp = true)))
    }

    @Test
    fun `the listener Tor reports wins`() {
        assertEquals(41000, CTorEngine.socksPortOf("\"127.0.0.1:41000\"", config(true)))
    }

    @Test
    fun `free loopback ports are real ports`() {
        val port = LoopbackPorts.free()
        assertTrue("$port", port in 1024..65535)
    }
}
