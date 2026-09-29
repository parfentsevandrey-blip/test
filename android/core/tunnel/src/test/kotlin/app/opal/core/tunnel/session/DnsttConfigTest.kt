package app.opal.core.tunnel.session

import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.BuiltinBridges
import app.opal.core.model.bridge.TransportKind
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsttConfigTest {

    // A synthetic line: documentation address, made-up key, fingerprint and domain.
    private val dnstt =
        requireNotNull(
            BridgeLine.parseOrNull(
                "dnstt 192.0.2.5:1 0123456789ABCDEF0123456789ABCDEF01234567 " +
                    "doh=https://doh.example/dns-query " +
                    "pubkey=${"0123456789abcdef".repeat(4)} domain=t.example.com"
            )
        )
    private val obfs4 =
        BuiltinBridges.parsePtConfig(File("src/main/assets/pt_config.json").readText())[
                TransportKind.Obfs4]
    private val ports = mapOf("dnstt" to 41236, "obfs4" to 41235)

    @Test
    fun `a dnstt session is never torn down`() {
        assertFalse(ModePolicy.of(ConnectionMode.Custom, listOf(dnstt)).watchdogEscalates)
        // With a TCP transport beside it the usual escalation applies.
        assertTrue(ModePolicy.of(ConnectionMode.Custom, listOf(dnstt) + obfs4).watchdogEscalates)
    }

    @Test
    fun `dnstt gets its transport and the slow first hop stream timeout`() {
        val torrc =
            TorConfigFactory.startup(listOf(dnstt), ports, AppSettings(), socksPort = 41000)
                .render()
        assertTrue(torrc, torrc.contains("ClientTransportPlugin dnstt socks5 127.0.0.1:41236"))
        assertTrue(torrc, torrc.contains("Bridge ${dnstt.raw}"))
        assertTrue(torrc, torrc.contains("CircuitStreamTimeout 30"))
        // One bridge: conflux legs would share the tunnel; without it, data rides with BEGIN.
        assertTrue(torrc, torrc.contains("ConfluxEnabled 0"))
    }

    @Test
    fun `dnstt arguments fit Tor's SOCKS5 limit`() {
        // Tor passes bridge arguments to the transport in the SOCKS5 username + password (2 × 255).
        val args = dnstt.args.entries.joinToString(";") { "${it.key}=${it.value}" }
        assertTrue("${args.length} bytes", args.length <= 510)
    }
}
