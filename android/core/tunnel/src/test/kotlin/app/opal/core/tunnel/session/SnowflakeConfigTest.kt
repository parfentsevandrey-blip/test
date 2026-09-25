package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.settings.TunnelMemory
import app.opal.core.tunnel.bridges.BridgeCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The default connection must be exactly Tor Browser's Snowflake setup, nothing else. */
class SnowflakeConfigTest {
    private val ptConfig = File("src/main/assets/pt_config.json").readText()
    private val bundled = BuiltinBridges.parsePtConfig(ptConfig)

    @Test
    fun `default torrc has only the two built-in Snowflake bridges`() {
        val plan =
            RacePlanner.plan(
                AppSettings(),
                TunnelMemory(),
                null,
                BridgeCatalog(bundled).candidates(TunnelMemory()),
                emptyList(),
            )
        val torrc =
            TorConfigFactory.startup(plan.initial, mapOf("snowflake" to 41234), AppSettings())
                .render()
        val lines = torrc.lines()
        assertTrue(lines.contains("UseBridges 1"))
        assertEquals(
            listOf("ClientTransportPlugin snowflake socks5 127.0.0.1:41234"),
            lines.filter { it.startsWith("ClientTransportPlugin") },
        )
        val bridges = lines.filter { it.startsWith("Bridge ") }
        assertEquals(2, bridges.size)
        assertTrue(bridges.all { it.startsWith("Bridge snowflake ") })
        // Verbatim from Tor Browser's pt_config.json: same fronts, broker, ICE and uTLS settings.
        for (bridge in bridges) assertTrue(
            bridge,
            ptConfig.contains(bridge.removePrefix("Bridge ")),
        )
        assertFalse(
            torrc.contains("obfs4") || torrc.contains("meek") || torrc.contains("webtunnel")
        )
    }

    @Test
    fun `Snowflake mode never tears connections down on its own`() {
        val policy = ModePolicy.of(ConnectionMode.Snowflake)
        assertFalse(policy.race)
        assertFalse(policy.settingsApiBeforeConnect)
        assertFalse(policy.watchdogMayReconnect)
        assertEquals(ConnectionMode.Snowflake, AppSettings().connectionMode)
    }

    @Test
    fun `only Auto races transports`() {
        assertEquals(
            listOf(ConnectionMode.Auto),
            ConnectionMode.entries.filter { ModePolicy.of(it).race },
        )
    }

    @Test
    fun `Snowflake arguments fit Tor's SOCKS5 limit`() {
        // Tor passes bridge arguments to the transport in the SOCKS5 username + password (2 × 255).
        for (line in bundled[TransportKind.Snowflake]) {
            val args = line.args.entries.joinToString(";") { "${it.key}=${it.value}" }
            assertTrue("${args.length} bytes", args.length <= 510)
        }
    }
}
