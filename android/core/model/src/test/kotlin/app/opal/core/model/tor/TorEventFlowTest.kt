package app.opal.core.model.tor

import app.cash.turbine.test
import app.opal.core.model.tunnel.BootstrapInfo
import app.opal.core.model.tunnel.BootstrapPhase
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorEventFlowTest {

    private fun events(vararg lines: String) = lines.asList().asFlow().controlReplies().torEvents()

    @Test
    fun `bootstrap events are parsed in order`() = runTest {
        events(
                "650 STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=1 TAG=conn_pt SUMMARY=\"Connecting to pluggable transport\"",
                "250 OK",
                "650 STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=100 TAG=done SUMMARY=\"Done\"",
                "650 STATUS_CLIENT NOTICE CIRCUIT_ESTABLISHED",
            )
            .test {
                val first = awaitItem() as TorEvent.Bootstrap
                assertEquals(1, first.progress)
                assertEquals("conn_pt", first.tag)
                assertEquals("Connecting to pluggable transport", first.summary)
                val done = awaitItem() as TorEvent.Bootstrap
                assertEquals(100, done.progress)
                assertEquals(TorEvent.CircuitEstablished, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `bootstrap problem carries warning and reason`() = runTest {
        events(
                "650 STATUS_CLIENT WARN BOOTSTRAP PROGRESS=5 TAG=conn SUMMARY=\"Connecting to a relay\" " +
                    "WARNING=\"Connection refused\" REASON=CONNECTREFUSED COUNT=3 RECOMMENDATION=ignore " +
                    "HOST=\$2B280B23E1107BB62ABFC40DDCC8824814F80A72 HOSTADDR=\"192.0.2.3:80\""
            )
            .test {
                val e = awaitItem() as TorEvent.Bootstrap
                assertTrue(e.isProblem)
                assertEquals("Connection refused", e.warning)
                assertEquals("CONNECTREFUSED", e.reason)
                assertEquals(3, e.count)
                assertEquals("192.0.2.3:80", e.hostAddress)
                awaitComplete()
            }
    }

    @Test
    fun `bandwidth circuit stream orconn liveness`() = runTest {
        events(
                "650 BW 1536 512",
                "650 CIRC 12 BUILT \$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA~bridge,\$BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB~middle," +
                    "\$CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC~exit BUILD_FLAGS=NEED_CAPACITY PURPOSE=GENERAL " +
                    "TIME_CREATED=2026-09-24T10:00:00.000000",
                "650 CIRC 13 FAILED REASON=TIMEOUT",
                "650 STREAM 44 SUCCEEDED 12 example.com:443",
                "650 STREAM 45 FAILED 0 [scrubbed]:443 REASON=TIMEOUT",
                "650 ORCONN 192.0.2.3:80 LAUNCHED ID=7",
                "650 ORCONN \$2B280B23E1107BB62ABFC40DDCC8824814F80A72~flakey CONNECTED ID=7",
                "650 NETWORK_LIVENESS DOWN",
            )
            .test {
                assertEquals(TorEvent.Bandwidth(1536, 512), awaitItem())
                val circ = awaitItem() as TorEvent.Circuit
                assertEquals(CircuitStatus.BUILT, circ.status)
                assertEquals(3, circ.path.size)
                assertEquals("bridge", circ.path[0].nickname)
                assertEquals("C".repeat(40), circ.path[2].fingerprint)
                assertEquals("GENERAL", circ.purpose)
                assertTrue("NEED_CAPACITY" in circ.buildFlags)
                val failed = awaitItem() as TorEvent.Circuit
                assertEquals(CircuitStatus.FAILED, failed.status)
                assertTrue(failed.path.isEmpty())
                assertEquals("TIMEOUT", failed.reason)
                val ok = awaitItem() as TorEvent.Stream
                assertEquals(StreamStatus.SUCCEEDED, ok.status)
                assertEquals("12", ok.circuitId)
                val bad = awaitItem() as TorEvent.Stream
                assertEquals("TIMEOUT", bad.reason)
                val launched = awaitItem() as TorEvent.OrConn
                assertEquals("192.0.2.3:80", launched.target)
                assertEquals(OrConnStatus.LAUNCHED, launched.status)
                assertEquals("7", launched.connectionId)
                val connected = awaitItem() as TorEvent.OrConn
                assertEquals(OrConnStatus.CONNECTED, connected.status)
                assertEquals(TorEvent.NetworkLiveness(up = false), awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `multi-line log message uses the data block`() = runTest {
        events("650+NOTICE", "line one", "line two", ".", "650 OK").test {
            assertEquals(TorEvent.Log(LogSeverity.NOTICE, "line one\nline two"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `sync replies are not events`() = runTest {
        events("250-version=0.4.9.12", "250 OK", "650 WARN something odd").test {
            assertEquals(TorEvent.Log(LogSeverity.WARN, "something odd"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `bootstrap info collapses tags into phases`() = runTest {
        events(
                "650 STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=10 TAG=conn_done SUMMARY=\"x\"",
                "650 STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=10 TAG=conn_done SUMMARY=\"x\"",
                "650 STATUS_CLIENT NOTICE BOOTSTRAP PROGRESS=45 TAG=requesting_descriptors SUMMARY=\"y\"",
            )
            .bootstrapInfo()
            .test {
                assertEquals(BootstrapInfo(10, BootstrapPhase.ConnectingToBridge), awaitItem())
                assertEquals(BootstrapInfo(45, BootstrapPhase.LoadingRelays), awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `unknown event is preserved`() = runTest {
        events("650 HS_DESC REQUESTED abc").test {
            assertEquals(TorEvent.Unknown("HS_DESC", "REQUESTED abc"), awaitItem())
            awaitComplete()
        }
    }
}
