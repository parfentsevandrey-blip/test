package app.opal.feature.connection

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomBridgesValidateTest {
    private val obfs4 =
        "obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0"

    @Test
    fun `valid lines are parsed, blank lines ignored, duplicates merged`() {
        val state =
            CustomBridgesViewModel.validate(
                "$obfs4\n\n$obfs4\nBridge webtunnel [2001:db8::1]:443 89ABCDEF0123456789ABCDEF0123456789ABCDEF url=https://example.com/x ver=0.0.1"
            )
        assertEquals(
            listOf(TransportKind.Obfs4, TransportKind.WebTunnel),
            state.valid.map { it.transport },
        )
        assertEquals(0, state.invalid.size)
    }

    @Test
    fun `invalid lines are reported with their line number`() {
        val state =
            CustomBridgesViewModel.validate("$obfs4\nobfs4 192.0.2.300:443\nfoo 192.0.2.1:1")
        assertEquals(1, state.valid.size)
        assertEquals(
            listOf(2 to BridgeLine.Reason.BadAddress, 3 to BridgeLine.Reason.UnknownTransport),
            state.invalid.map { it.number to it.reason },
        )
    }

    @Test
    fun `obfs4 without cert is rejected`() {
        val state =
            CustomBridgesViewModel.validate(
                "obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 iat-mode=0"
            )
        assertEquals(BridgeLine.Reason.MissingRequiredArgument, state.invalid.single().reason)
    }
}
