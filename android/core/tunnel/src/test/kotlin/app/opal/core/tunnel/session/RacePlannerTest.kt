package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import app.opal.core.model.settings.TunnelMemory
import app.opal.core.model.tunnel.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacePlannerTest {

    private val snowflake =
        line(
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://b.example/"
        )
    private val obfs4a =
        line("obfs4 203.0.113.1:443 1111111111111111111111111111111111111111 cert=AAAA iat-mode=0")
    private val obfs4b =
        line("obfs4 203.0.113.2:443 2222222222222222222222222222222222222222 cert=BBBB iat-mode=0")
    private val meek = line("meek_lite 192.0.2.20:80 url=https://m.example front=f.example")
    private val candidates =
        mapOf(
            TransportKind.Snowflake to listOf(snowflake),
            TransportKind.Obfs4 to listOf(obfs4a, obfs4b),
            TransportKind.Meek to listOf(meek),
        )

    private fun line(raw: String) = BridgeLine.parseOrNull(raw)!!

    @Test
    fun `first run races everything at once`() {
        val plan =
            RacePlanner.plan(
                AppSettings(),
                TunnelMemory(),
                NetworkKind.Wifi,
                candidates,
                emptyList(),
            )
        assertEquals(
            setOf(TransportKind.Snowflake, TransportKind.Obfs4, TransportKind.Meek),
            plan.initialKinds,
        )
        assertTrue(plan.expansion.isEmpty())
        assertTrue(plan.settingsApiAllowed)
    }

    @Test
    fun `known winner for this network starts alone and expands to the rest`() {
        val memory =
            TunnelMemory(
                winners =
                    mapOf(
                        NetworkKind.Cellular to TransportKind.Obfs4,
                        NetworkKind.Wifi to TransportKind.Snowflake,
                    )
            )
        val plan =
            RacePlanner.plan(AppSettings(), memory, NetworkKind.Cellular, candidates, emptyList())
        assertEquals(setOf(TransportKind.Obfs4), plan.initialKinds)
        assertEquals(
            setOf(TransportKind.Snowflake, TransportKind.Meek),
            plan.expansion.map { it.transport }.toSet(),
        )
    }

    @Test
    fun `last working bridge goes first`() {
        val memory =
            TunnelMemory(
                winners = mapOf(NetworkKind.Wifi to TransportKind.Obfs4),
                lastWorkingBridge = mapOf(NetworkKind.Wifi to obfs4b.id),
            )
        val plan =
            RacePlanner.plan(AppSettings(), memory, NetworkKind.Wifi, candidates, emptyList())
        assertEquals(obfs4b, plan.initial.first())
    }

    @Test
    fun `explicit transport never expands`() {
        val plan =
            RacePlanner.plan(
                AppSettings(connectionMode = ConnectionMode.Snowflake),
                TunnelMemory(),
                null,
                candidates,
                emptyList(),
            )
        assertEquals(listOf(snowflake), plan.initial)
        assertTrue(plan.expansion.isEmpty())
    }

    @Test
    fun `custom mode uses only the user's bridges and never asks the API`() {
        val plan =
            RacePlanner.plan(
                AppSettings(connectionMode = ConnectionMode.Custom),
                TunnelMemory(),
                null,
                candidates,
                listOf(obfs4a),
            )
        assertEquals(listOf(obfs4a), plan.initial)
        assertFalse(plan.settingsApiAllowed)
    }

    @Test
    fun `stale winner that is no longer available falls back to a full race`() {
        val memory = TunnelMemory(winners = mapOf(NetworkKind.Wifi to TransportKind.WebTunnel))
        val plan =
            RacePlanner.plan(AppSettings(), memory, NetworkKind.Wifi, candidates, emptyList())
        assertEquals(3, plan.initialKinds.size)
    }
}
