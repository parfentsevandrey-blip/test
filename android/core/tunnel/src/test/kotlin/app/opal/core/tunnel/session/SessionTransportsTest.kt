package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTransportsTest {
    // Synthetic lines (documentation addresses, made-up keys and domain): the format only.
    private val snowflake =
        line(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
                "fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://broker.example/"
        )
    private val dnstt =
        line(
            "dnstt 192.0.2.5:1 0123456789ABCDEF0123456789ABCDEF01234567 " +
                "doh=https://doh.example/dns-query pubkey=${"0123456789abcdef".repeat(4)} " +
                "domain=t.example.com"
        )
    private val obfs4 =
        line("obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0")

    private fun line(raw: String) = (BridgeLine.parse(raw) as BridgeLine.ParseResult.Ok).line

    @Test
    fun `snowflake and dnstt carry their own session, tcp transports do not`() {
        assertTrue(listOf(snowflake).isSessionOnly())
        assertTrue(listOf(dnstt, snowflake).isSessionOnly())
        assertFalse(listOf(snowflake, obfs4).isSessionOnly())
        assertFalse(emptyList<BridgeLine>().isSessionOnly())
    }

    /** From the servers: Snowflake's smux keepalive (4 min), dnstt's idleTimeout (2 min). */
    @Test
    fun `a gap ends a session only once every server has dropped it`() {
        assertEquals(4 * 60_000L, listOf(snowflake).sessionExpiryMillis())
        assertEquals(2 * 60_000L, listOf(dnstt).sessionExpiryMillis())
        assertEquals(4 * 60_000L, listOf(dnstt, snowflake).sessionExpiryMillis())
    }
}
