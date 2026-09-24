package app.opal.core.tunnel.bridges

import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class Socks5Test {

    @Test
    fun `sends pt arguments as credentials and a domain target`() {
        ServerSocket(0).use { server ->
            var user = ""
            var pass = ""
            var host = ""
            var port = 0
            val t = thread {
                server.accept().use { c ->
                    val i = DataInputStream(c.getInputStream())
                    val o = c.getOutputStream()
                    val n = ByteArray(2).also { i.readFully(it) }[1].toInt()
                    i.readFully(ByteArray(n))
                    o.write(byteArrayOf(5, 2))
                    i.readUnsignedByte()
                    user = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) })
                    pass = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) })
                    o.write(byteArrayOf(1, 0))
                    i.readFully(ByteArray(3))
                    check(i.readUnsignedByte() == 3)
                    host = String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) })
                    port = i.readUnsignedShort()
                    o.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))
                }
            }
            Socket("127.0.0.1", server.localPort).use {
                Socks5.connect(
                    it,
                    "bridges.torproject.org",
                    443,
                    "targets=" + Socks5.escapePtArg("https://r.example|a+b"),
                    "\u0000",
                )
            }
            t.join()
            assertEquals("targets=https://r.example|a+b", user)
            assertEquals("\u0000", pass)
            assertEquals("bridges.torproject.org", host)
            assertEquals(443, port)
        }
    }

    @Test
    fun `escapes pt argument specials`() {
        val backslash = '\\'
        assertEquals(
            "a${backslash}=b${backslash};c$backslash${backslash}d",
            Socks5.escapePtArg("a=b;c${backslash}d"),
        )
    }
}
