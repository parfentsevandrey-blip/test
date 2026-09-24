package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.tor.OrConnStatus
import app.opal.core.model.tor.TorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeUsageTrackerTest {

    private val snowflake =
        BridgeLine.parseOrNull(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://b.example/"
        )!!
    private val meek =
        BridgeLine.parseOrNull("meek_lite 192.0.2.20:80 url=https://m.example front=f.example")!!
    private val bridges = listOf(snowflake, meek)

    @Test
    fun `guard fingerprint matches a bridge line with fingerprint`() {
        val t = BridgeUsageTracker()
        assertEquals(snowflake, t.bridgeFor("2B280B23E1107BB62ABFC40DDCC8824814F80A72", bridges))
    }

    @Test
    fun `fingerprint learned through connection id maps to a line without fingerprint`() {
        val t = BridgeUsageTracker()
        t.onOrConn(TorEvent.OrConn("192.0.2.20:80", OrConnStatus.LAUNCHED, null, "9"))
        t.onOrConn(
            TorEvent.OrConn(
                "\$ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD~meekbridge",
                OrConnStatus.CONNECTED,
                null,
                "9",
            )
        )
        assertEquals(meek, t.bridgeFor("ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD", bridges))
    }

    @Test
    fun `falls back to the first connected bridge`() {
        val t = BridgeUsageTracker()
        t.onOrConn(TorEvent.OrConn("192.0.2.3:80", OrConnStatus.CONNECTED, null, "1"))
        assertEquals(snowflake, t.bridgeFor(null, bridges))
        assertNull(BridgeUsageTracker().bridgeFor("FFFF", bridges))
    }

    @Test
    fun `failed connections are attributed to their bridge`() {
        val t = BridgeUsageTracker()
        t.onOrConn(TorEvent.OrConn("192.0.2.20:80", OrConnStatus.LAUNCHED, null, "4"))
        t.onOrConn(TorEvent.OrConn("192.0.2.20:80", OrConnStatus.FAILED, "TIMEOUT", "4"))
        assertEquals(listOf(meek), t.failedBridges(bridges))
    }
}
