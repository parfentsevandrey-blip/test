package app.opal.core.model.tor

import app.opal.core.model.bridge.BridgeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TorrcTest {

    private val snowflake =
        BridgeLine.parseOrNull(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=a.example,b.example"
        )!!
    private val obfs4 =
        BridgeLine.parseOrNull(
            "obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 cert=ssH+9rP8dG2NLD iat-mode=0"
        )!!

    @Test
    fun `renders a complete torrc`() {
        val torrc = torrc {
            socksPort(flags = setOf(SocksFlag.IPv6Traffic))
            noExtraListeners()
            clientOnly()
            bridges(listOf(snowflake, obfs4), mapOf("snowflake" to 40001, "obfs4" to 40002))
            exitCountry("DE")
            reducedConnectionPadding(false)
        }
        val expected =
            """
            SocksPort 127.0.0.1:auto IPv6Traffic
            ControlPort 0
            HTTPTunnelPort 0
            ClientOnly 1
            UseBridges 1
            ClientTransportPlugin obfs4 socks5 127.0.0.1:40002
            ClientTransportPlugin snowflake socks5 127.0.0.1:40001
            Bridge ${snowflake.raw}
            Bridge ${obfs4.raw}
            ExitNodes {de}
            StrictNodes 0

            """
                .trimIndent()
        assertEquals(expected, torrc.render())
    }

    @Test
    fun `setconf quotes values and resets missing options`() {
        val torrc = torrc { bridges(listOf(obfs4), mapOf("obfs4" to 5555)) }
        val setconf =
            torrc.toSetConf(
                listOf(
                    TorOption.UseBridges,
                    TorOption.ClientTransportPlugin,
                    TorOption.Bridge,
                    TorOption.ExitNodes,
                )
            )
        assertEquals(
            "UseBridges=1 ClientTransportPlugin=\"obfs4 socks5 127.0.0.1:5555\" Bridge=\"${obfs4.raw}\" ExitNodes",
            setconf,
        )
    }

    @Test
    fun `no bridges disables UseBridges`() {
        val torrc = torrc { bridges(emptyList(), emptyMap()) }
        assertEquals("0", torrc.value(TorOption.UseBridges))
        assertEquals(emptyList<String>(), torrc.values(TorOption.Bridge))
    }

    @Test
    fun `data saver only adds padding option when enabled`() {
        assertEquals(
            null,
            torrc { reducedConnectionPadding(false) }.value(TorOption.ReducedConnectionPadding),
        )
        assertEquals(
            "1",
            torrc { reducedConnectionPadding(true) }.value(TorOption.ReducedConnectionPadding),
        )
    }

    @Test
    fun `validation rejects bad input`() {
        assertThrows(IllegalArgumentException::class.java) { torrc { exitCountry("deu") } }
        assertThrows(IllegalArgumentException::class.java) { torrc { socksPort(port = 0) } }
        assertThrows(IllegalArgumentException::class.java) {
            torrc { bridges(listOf(obfs4), emptyMap()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            torrc { log("notice", "file /x\nExitNodes {ru}") }
        }
    }

    @Test
    fun `single-valued options are replaced, lists accumulate`() {
        val torrc = torrc {
            disableNetwork(true)
            disableNetwork(false)
            log("notice", "stdout")
            log("warn", "stderr")
        }
        assertEquals("0", torrc.value(TorOption.DisableNetwork))
        assertEquals(listOf("notice stdout", "warn stderr"), torrc.values(TorOption.Log))
    }
}
