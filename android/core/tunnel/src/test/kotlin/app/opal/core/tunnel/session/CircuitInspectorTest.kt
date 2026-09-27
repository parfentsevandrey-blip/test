package app.opal.core.tunnel.session

import app.opal.core.model.tor.TorEvent
import app.opal.core.model.tor.TorOption
import app.opal.core.model.tor.Torrc
import app.opal.core.model.tunnel.CircuitHop
import app.opal.core.model.tunnel.HopRole
import app.opal.core.tunnel.tor.EngineState
import app.opal.core.tunnel.tor.TorEngine
import app.opal.core.tunnel.tor.TorSignal
import app.opal.core.tunnel.util.LogBuffer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircuitInspectorTest {

    private class FakeEngine(private val info: Map<String, String>) : TorEngine {
        override val state = MutableStateFlow<EngineState>(EngineState.Stopped)
        override val events = MutableSharedFlow<TorEvent>()

        override suspend fun start(config: Torrc) = error("not used")

        override suspend fun reconfigure(config: Torrc, options: Collection<TorOption>) = Unit

        override suspend fun signal(signal: TorSignal) = Unit

        override suspend fun getInfo(vararg keys: String) = keys.associateWith {
            info[it] ?: error("GETINFO $it")
        }

        override suspend fun stop() = Unit
    }

    private fun middle(id: String) = "C".repeat(39) + id

    private fun exit(id: String) = "E".repeat(39) + id

    private fun circuit(id: String, purpose: String, flags: String = "NEED_CAPACITY") =
        "$id BUILT \$${"B".repeat(40)}~bridge,\$${middle(id)}~middle$id,\$${exit(id)}~exit$id " +
            "BUILD_FLAGS=$flags PURPOSE=$purpose TIME_CREATED=2026-09-26T20:00:00.000000"

    private fun engine(vararg circuits: String) =
        FakeEngine(
            buildMap {
                put("circuit-status", circuits.joinToString("\n"))
                for (id in listOf("1", "2", "3", "4")) {
                    put(
                        "ns/id/${middle(id)}",
                        "r middle$id x y 2026-09-26 12:00:00 10.0.0.$id 9001 0",
                    )
                    put("ns/id/${exit(id)}", "r exit$id x y 2026-09-26 12:00:00 192.0.2.$id 443 0")
                }
            }
        )

    @Test
    fun `the linked conflux set carrying the stream is shown`() = runBlocking {
        // Tor 0.4.9 prefers a linked conflux set for general streams: the stream reports a leg.
        val inspector =
            CircuitInspector(
                engine(
                    circuit("1", "GENERAL"),
                    circuit("2", "CONFLUX_UNLINKED"),
                    circuit("3", "CONFLUX_LINKED", "NEED_CAPACITY,NEED_UPTIME"),
                )
            )

        val info = inspector.inspect(emptyList(), preferredCircuitId = "3", geoIpLoaded = false)

        assertEquals(
            listOf(
                CircuitHop(HopRole.Guard, "bridge"),
                CircuitHop(HopRole.Middle, "middle3"),
                CircuitHop(HopRole.Exit, "exit3", address = "192.0.2.3"),
            ),
            info?.hops,
        )
    }

    @Test
    fun `a set still linking and onion service circuits are not the user's traffic`() =
        runBlocking {
            val log = LogBuffer()
            val inspector =
                CircuitInspector(
                    engine(
                        circuit("2", "CONFLUX_UNLINKED"),
                        circuit("4", "HS_CLIENT_HSDIR"),
                    ),
                    log,
                )

            assertNull(
                inspector.inspect(emptyList(), preferredCircuitId = "2", geoIpLoaded = false)
            )
            // The log names purposes only, never relays or addresses.
            assertEquals(
                listOf("No circuit for traffic yet; built: CONFLUX_UNLINKED×1, HS_CLIENT_HSDIR×1"),
                log.snapshot().map { it.message },
            )
        }

    @Test
    fun `without the stream's circuit a circuit that can carry traffic is shown`() = runBlocking {
        val inspector =
            CircuitInspector(
                engine(circuit("2", "CONFLUX_UNLINKED"), circuit("3", "CONFLUX_LINKED"))
            )

        val info = inspector.inspect(emptyList(), preferredCircuitId = null, geoIpLoaded = false)

        assertEquals("exit3", info?.hops?.last()?.nickname)
    }
}
