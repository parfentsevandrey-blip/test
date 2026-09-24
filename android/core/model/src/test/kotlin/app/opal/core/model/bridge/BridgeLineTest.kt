package app.opal.core.model.bridge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeLineTest {

    private fun ok(line: String) = (BridgeLine.parse(line) as BridgeLine.ParseResult.Ok).line

    private fun invalid(line: String) =
        (BridgeLine.parse(line) as BridgeLine.ParseResult.Invalid).reason

    @Test
    fun `obfs4 line round-trips`() {
        val raw =
            "obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 " +
                "cert=ssH+9rP8dG2NLDN2XuFw63hIO/9MNNinLmxQDpVa+7kTOa9/m+tGWT1SmSYpQ9uTBGa6Hw iat-mode=0"
        val line = ok("Bridge $raw")
        assertEquals(TransportKind.Obfs4, line.transport)
        assertEquals("209.148.46.65", line.host)
        assertEquals(443, line.port)
        assertEquals("74FAD13168806246602538555B5521A0383A1875", line.fingerprint)
        assertEquals("0", line.args["iat-mode"])
        assertEquals(raw, line.raw)
        assertEquals(line.fingerprint, line.id)
    }

    @Test
    fun `snowflake keeps comma separated values`() {
        val line =
            ok(
                "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 " +
                    "fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ " +
                    "fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478 " +
                    "utls-imitate=hellorandomizedalpn"
            )
        assertEquals(TransportKind.Snowflake, line.transport)
        assertEquals("app.datapacket.com,www.datapacket.com", line.args["fronts"])
    }

    @Test
    fun `meek without fingerprint gets a stable id`() {
        val line =
            ok(
                "meek_lite 192.0.2.20:80 url=https://1603026938.rsc.cdn77.org front=www.phpmyadmin.net utls=HelloRandomizedALPN"
            )
        assertEquals(TransportKind.Meek, line.transport)
        assertNull(line.fingerprint)
        assertEquals("Meek|192.0.2.20:80|https://1603026938.rsc.cdn77.org", line.id)
    }

    @Test
    fun `vanilla and ipv6`() {
        val v = ok("198.51.100.7:9001 0123456789ABCDEF0123456789ABCDEF01234567")
        assertEquals(TransportKind.Vanilla, v.transport)
        assertEquals("198.51.100.7:9001 0123456789ABCDEF0123456789ABCDEF01234567", v.raw)
        val v6 =
            ok(
                "webtunnel [2001:db8::1]:443 0123456789ABCDEF0123456789ABCDEF01234567 url=https://example.org/path ver=0.0.1"
            )
        assertEquals("2001:db8::1", v6.host)
        assertEquals("[2001:db8::1]:443", v6.address)
    }

    @Test
    fun `invalid inputs are reported with a reason`() {
        assertEquals(BridgeLine.Reason.Empty, invalid("   "))
        assertEquals(BridgeLine.Reason.UnknownTransport, invalid("fte 1.2.3.4:1"))
        assertEquals(BridgeLine.Reason.BadAddress, invalid("obfs4 1.2.3:443"))
        assertEquals(BridgeLine.Reason.BadAddress, invalid("obfs4 300.2.3.4:443 cert=a iat-mode=0"))
        assertEquals(BridgeLine.Reason.BadPort, invalid("obfs4 1.2.3.4:70000 cert=a iat-mode=0"))
        assertEquals(
            BridgeLine.Reason.BadFingerprint,
            invalid("obfs4 1.2.3.4:443 XYZ cert=a iat-mode=0"),
        )
        assertEquals(
            BridgeLine.Reason.MissingRequiredArgument,
            invalid("obfs4 1.2.3.4:443 cert=abc"),
        )
        assertEquals(BridgeLine.Reason.MissingRequiredArgument, invalid("webtunnel 1.2.3.4:443"))
    }

    @Test
    fun `extracts lines from a bridges QR payload`() {
        val qr =
            "['obfs4 203.0.113.10:4443 0123456789ABCDEF0123456789ABCDEF01234567 cert=Zm9vYmFyYmF6 iat-mode=0', " +
                "'obfs4 203.0.113.11:4443 1123456789ABCDEF0123456789ABCDEF01234567 cert=YmF6cXV4 iat-mode=1']"
        val lines = BridgeLine.extractAll(qr)
        assertEquals(2, lines.size)
        assertEquals("1", lines[1].args["iat-mode"])
    }

    @Test
    fun `extracts from pasted multi-line text and drops garbage`() {
        val text =
            """
            Here are your bridges:
            Bridge webtunnel [2001:db8::2]:443 2123456789ABCDEF0123456789ABCDEF01234567 url=https://cdn.example.net/abc ver=0.0.2
            not a bridge 1.2.3
            snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://x.example/
            """
                .trimIndent()
        val lines = BridgeLine.extractAll(text)
        assertEquals(
            listOf(TransportKind.WebTunnel, TransportKind.Snowflake),
            lines.map { it.transport },
        )
    }

    @Test
    fun `bundled pt_config parses completely`() {
        val path = System.getProperty("opal.ptConfig") ?: return
        val text = File(path).readText()
        val builtin = BuiltinBridges.parsePtConfig(text)
        assertTrue(builtin[TransportKind.Snowflake].size >= 2)
        assertTrue(builtin[TransportKind.Obfs4].isNotEmpty())
        assertTrue(builtin[TransportKind.Meek].isNotEmpty())
        // Every line in the bundled file must be accepted by our parser.
        val raw =
            Regex("\"((?:obfs4|snowflake|meek_lite|webtunnel) [^\"]+)\"")
                .findAll(text)
                .map { it.groupValues[1] }
                .toList()
        assertEquals(raw.size, builtin.byTransport.values.sumOf { it.size })
    }
}
