package app.opal.core.model.tunnel

import app.opal.core.model.tunnel.ConnectionEvent.AllTransportsFailed
import app.opal.core.model.tunnel.ConnectionEvent.Connect
import app.opal.core.model.tunnel.ConnectionEvent.Disconnect
import app.opal.core.model.tunnel.ConnectionEvent.Fatal
import app.opal.core.model.tunnel.ConnectionEvent.NetworkAvailable
import app.opal.core.model.tunnel.ConnectionEvent.NetworkLost
import app.opal.core.model.tunnel.ConnectionEvent.Reconnect
import app.opal.core.model.tunnel.ConnectionEvent.Retry
import app.opal.core.model.tunnel.ConnectionEvent.Revoked
import app.opal.core.model.tunnel.ConnectionEvent.Stalled
import app.opal.core.model.tunnel.ConnectionEvent.Stopped
import app.opal.core.model.tunnel.ConnectionEvent.TorNotReady
import app.opal.core.model.tunnel.ConnectionEvent.TorReady
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionMachineTest {

    private fun run(
        vararg events: ConnectionEvent,
        from: MachineState = MachineState(),
    ): MachineState = events.fold(from, ConnectionMachine::reduce)

    @Test
    fun `cold connect goes through connecting to connected`() {
        val s1 = run(Connect)
        assertEquals(TunnelState.Connecting(1), s1.state)
        val s2 = ConnectionMachine.reduce(s1, TorReady)
        assertEquals(TunnelState.Connected, s2.state)
        assertTrue(s2.sessionConnected)
    }

    @Test
    fun `warm tor connects instantly`() {
        // Prewarm / hot standby: Tor became ready while the VPN was off.
        val warm = run(TorReady)
        assertEquals(TunnelState.Off, warm.state)
        assertTrue(warm.torReady)
        assertEquals(TunnelState.Connected, ConnectionMachine.reduce(warm, Connect).state)
    }

    @Test
    fun `disconnect with hot standby keeps tor ready`() {
        val s = run(Connect, TorReady, Disconnect(keepTor = true))
        assertEquals(TunnelState.Standby, s.state)
        assertTrue(s.torReady)
        assertEquals(TunnelState.Connected, ConnectionMachine.reduce(s, Connect).state)
    }

    @Test
    fun `disconnect without standby stops then goes off`() {
        val stopping = run(Connect, TorReady, Disconnect(keepTor = false))
        assertEquals(TunnelState.Stopping, stopping.state)
        val off = ConnectionMachine.reduce(stopping, Stopped)
        assertEquals(MachineState(), off)
    }

    @Test
    fun `network loss and recovery after being connected is a reconnect`() {
        val lost = run(Connect, TorReady, NetworkLost)
        assertEquals(TunnelState.WaitingForNetwork, lost.state)
        val back = ConnectionMachine.reduce(lost, NetworkAvailable)
        assertEquals(TunnelState.Reconnecting(ReconnectReason.NetworkChanged), back.state)
        assertEquals(TunnelState.Connected, ConnectionMachine.reduce(back, TorReady).state)
    }

    @Test
    fun `network recovery before first connection keeps connecting`() {
        val s = run(Connect, NetworkLost, NetworkAvailable)
        assertEquals(TunnelState.Connecting(2), s.state)
    }

    @Test
    fun `stall and circuit loss reconnect only from connected`() {
        assertEquals(
            TunnelState.Reconnecting(ReconnectReason.Stalled),
            run(Connect, TorReady, Stalled).state,
        )
        assertEquals(TunnelState.Connecting(1), run(Connect, Stalled).state)
        val lost = run(Connect, TorReady, TorNotReady(ReconnectReason.CircuitLost))
        assertEquals(TunnelState.Reconnecting(ReconnectReason.CircuitLost), lost.state)
        assertFalse(lost.torReady)
    }

    @Test
    fun `all transports failed is blocked and retry resumes`() {
        val blocked = run(Connect, AllTransportsFailed)
        assertEquals(TunnelState.Blocked, blocked.state)
        assertTrue(blocked.state.isTunnelActive) // still no leaks while blocked
        assertEquals(TunnelState.Connecting(2), ConnectionMachine.reduce(blocked, Retry).state)
        assertEquals(TunnelState.Connected, ConnectionMachine.reduce(blocked, TorReady).state)
    }

    @Test
    fun `revocation and fatal errors win from any state`() {
        assertEquals(
            TunnelState.Error(TunnelError.VpnRevoked),
            run(Connect, TorReady, Revoked).state,
        )
        val fatal = run(Connect, Fatal(TunnelError.TorStartFailed))
        assertEquals(TunnelState.Error(TunnelError.TorStartFailed), fatal.state)
        // The error stays visible after teardown, until the user connects again.
        assertEquals(fatal.state, ConnectionMachine.reduce(fatal, Stopped).state)
        assertEquals(TunnelState.Connecting(1), ConnectionMachine.reduce(fatal, Connect).state)
    }

    @Test
    fun `connect is idempotent while active`() {
        val s = run(Connect, TorReady)
        assertEquals(s, ConnectionMachine.reduce(s, Connect))
        val connecting = run(Connect)
        assertEquals(connecting, ConnectionMachine.reduce(connecting, Connect))
    }

    @Test
    fun `events that do not apply are ignored`() {
        val off = MachineState()
        assertEquals(off, ConnectionMachine.reduce(off, NetworkLost))
        assertEquals(off, ConnectionMachine.reduce(off, Stalled))
        assertEquals(off, ConnectionMachine.reduce(off, Disconnect(keepTor = false)))
        assertEquals(off, ConnectionMachine.reduce(off, Retry))
    }

    @Test
    fun `reconnect on request starts a fresh attempt and ends connected`() {
        val reconnecting = run(Connect, TorReady, Reconnect)
        assertEquals(TunnelState.Reconnecting(ReconnectReason.User), reconnecting.state)
        assertFalse(reconnecting.torReady)
        assertEquals(TunnelState.Connected, ConnectionMachine.reduce(reconnecting, TorReady).state)
        // Still connecting: the next attempt.
        assertEquals(TunnelState.Connecting(2), run(Connect, Reconnect).state)
        // Blocked on a first connection: back to connecting.
        assertEquals(
            TunnelState.Connecting(2),
            run(Connect, AllTransportsFailed, Reconnect).state,
        )
    }

    @Test
    fun `reconnect is ignored without a network or with the vpn off`() {
        val waiting = run(Connect, TorReady, NetworkLost)
        assertEquals(waiting, ConnectionMachine.reduce(waiting, Reconnect))
        assertEquals(MachineState(), run(Reconnect))
        val standby = run(Connect, TorReady, Disconnect(keepTor = true))
        assertEquals(standby, ConnectionMachine.reduce(standby, Reconnect))
    }

    @Test
    fun `snapshot json round trip`() {
        val snapshot =
            TunnelSnapshot(
                state = TunnelState.Reconnecting(ReconnectReason.Stalled),
                bootstrap = BootstrapInfo(45, BootstrapPhase.LoadingRelays),
                circuit =
                    CircuitInfo(
                        listOf(
                            CircuitHop(HopRole.Bridge),
                            CircuitHop(HopRole.Exit, "exit1", "de", "198.51.100.1"),
                        )
                    ),
                warm = WarmState.Ready,
            )
        assertEquals(snapshot, TunnelSnapshot.fromJson(snapshot.toJson()))
    }
}
