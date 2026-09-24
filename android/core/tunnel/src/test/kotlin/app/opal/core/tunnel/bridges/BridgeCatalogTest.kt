package app.opal.core.tunnel.bridges

import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.BridgeSet
import app.opal.core.model.settings.BridgeStat
import app.opal.core.model.settings.CircumventionCache
import app.opal.core.model.settings.TunnelMemory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCatalogTest {

    private val bundled =
        BuiltinBridges.parsePtConfig(File("src/main/assets/pt_config.json").readText())
    private val catalog = BridgeCatalog(bundled)

    @Test
    fun `bundled bridges cover snowflake obfs4 and meek`() {
        val c = catalog.candidates(TunnelMemory())
        assertEquals(2, c[TransportKind.Snowflake]!!.size)
        assertTrue(c[TransportKind.Obfs4]!!.isNotEmpty())
        assertEquals(1, c[TransportKind.Meek]!!.size)
        assertTrue(TransportKind.WebTunnel !in c)
    }

    @Test
    fun `private bridges from the Settings API come first`() {
        val private =
            "obfs4 198.51.100.9:9000 9999999999999999999999999999999999999999 cert=PRIV iat-mode=0"
        val webtunnel =
            "webtunnel [2001:db8::5]:443 5555555555555555555555555555555555555555 url=https://w.example/x ver=0.0.1"
        val memory =
            TunnelMemory(
                circumvention =
                    CircumventionCache(
                        fetchedAt = 1,
                        settings =
                            listOf(
                                BridgeSet("obfs4", "bridgedb", listOf(private)),
                                BridgeSet("webtunnel", "bridgedb", listOf(webtunnel)),
                            ),
                    )
            )
        val c = catalog.candidates(memory)
        assertEquals("198.51.100.9", c[TransportKind.Obfs4]!!.first().host)
        assertEquals(1, c[TransportKind.WebTunnel]!!.size)
    }

    @Test
    fun `statistics reorder bridges of the same transport`() {
        val obfs4 = catalog.candidates(TunnelMemory())[TransportKind.Obfs4]!!
        val last = obfs4.last()
        val memory =
            TunnelMemory(
                bridgeStats =
                    mapOf(
                        last.id to BridgeStat(successes = 5),
                        obfs4.first().id to BridgeStat(failures = 5),
                    )
            )
        val ranked = catalog.candidates(memory)[TransportKind.Obfs4]!!
        assertEquals(last, ranked.first())
        assertEquals(obfs4.first(), ranked.last())
    }

    @Test
    fun `fresh builtin list from the API replaces bundled lines of that transport`() {
        val fresh =
            "obfs4 192.0.2.77:443 7777777777777777777777777777777777777777 cert=NEW iat-mode=0"
        val memory =
            TunnelMemory(
                circumvention =
                    CircumventionCache(fetchedAt = 1, builtin = mapOf("obfs4" to listOf(fresh)))
            )
        val c = catalog.candidates(memory)
        assertEquals(listOf("192.0.2.77"), c[TransportKind.Obfs4]!!.map { it.host })
        assertEquals(2, c[TransportKind.Snowflake]!!.size) // other transports stay bundled
    }
}
