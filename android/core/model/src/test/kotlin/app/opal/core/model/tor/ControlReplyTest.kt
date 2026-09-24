package app.opal.core.model.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlReplyTest {

    private fun assemble(vararg lines: String): List<ControlReply> {
        val a = ReplyAssembler()
        return lines.mapNotNull { a.feed(it) }
    }

    @Test
    fun `single line success`() {
        val replies = assemble("250 OK")
        assertEquals(1, replies.size)
        assertTrue(replies[0].isSuccess)
        assertEquals("OK", replies[0].message)
    }

    @Test
    fun `error reply keeps message`() {
        val r = assemble("552 Unrecognized option: Unknown option 'Foo'.  Failing.").single()
        assertFalse(r.isSuccess)
        assertEquals(552, r.code)
    }

    @Test
    fun `mid lines and data block with dot unescaping`() {
        val replies =
            assemble(
                "250-version=0.4.9.12",
                "250+ns/all=",
                "r relay1 AAAA",
                "..leading dot",
                ".",
                "250 OK",
            )
        val r = replies.single()
        assertEquals(3, r.lines.size)
        assertEquals("r relay1 AAAA\n.leading dot", r.lines[1].data)
        val info = GetInfoParser.parse(r)
        assertEquals("0.4.9.12", info["version"])
        assertEquals("r relay1 AAAA\n.leading dot", info["ns/all"])
    }

    @Test
    fun `interleaved async event and sync reply are separate replies`() {
        val replies = assemble("650 BW 10 20", "250 OK")
        assertEquals(2, replies.size)
        assertTrue(replies[0].isAsync)
        assertFalse(replies[1].isAsync)
    }

    @Test
    fun `incomplete reply yields nothing`() {
        assertTrue(assemble("250-a=b", "250-c=d").isEmpty())
    }

    @Test
    fun `quoted arguments with escapes`() {
        val parsed =
            ControlArgs.parse(
                """NOTICE BOOTSTRAP PROGRESS=10 TAG=conn_done SUMMARY="Connected to a \"relay\"" X=\101"""
            )
        assertEquals(listOf("NOTICE", "BOOTSTRAP"), parsed.positional)
        assertEquals("10", parsed.keywords["PROGRESS"])
        assertEquals("Connected to a \"relay\"", parsed.keywords["SUMMARY"])
        assertEquals("\\101", parsed.keywords["X"])
    }

    @Test
    fun `octal escape inside quotes`() {
        val parsed = ControlArgs.parse("A=\"x\\101y\"")
        assertEquals("xAy", parsed.keywords["A"])
    }

    @Test
    fun `quote only when necessary`() {
        assertEquals("1", ControlArgs.quote("1"))
        assertEquals("\"obfs4 1.2.3.4:443 cert=a\"", ControlArgs.quote("obfs4 1.2.3.4:443 cert=a"))
        assertEquals("\"a\\\"b\\\\c\"", ControlArgs.quote("a\"b\\c"))
        assertEquals("\"\"", ControlArgs.quote(""))
    }

    @Test
    fun `getinfo single value`() {
        val r = assemble("250-net/listeners/socks=\"127.0.0.1:45123\"", "250 OK").single()
        assertEquals("\"127.0.0.1:45123\"", GetInfoParser.parse(r)["net/listeners/socks"])
        assertNull(GetInfoParser.parse(r)["missing"])
    }
}
