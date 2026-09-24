package app.opal.core.tunnel.bridges

import java.io.DataInputStream
import java.io.IOException
import java.net.Socket

/**
 * Minimal SOCKS5 client (RFC 1928 + RFC 1929) for our own requests:
 * - through a pluggable transport, the username/password carry the PT arguments (pt-spec §3.5),
 *   e.g. `targets=…` for meek_lite;
 * - through Tor, credentials only select an isolated circuit (IsolateSOCKSAuth). The destination is
 *   always sent as a domain name, so nothing is resolved locally.
 */
internal object Socks5 {

    fun connect(socket: Socket, host: String, port: Int, username: String?, password: String?) {
        val out = socket.getOutputStream()
        val input = DataInputStream(socket.getInputStream())
        val withAuth = username != null
        out.write(if (withAuth) byteArrayOf(5, 1, 2) else byteArrayOf(5, 1, 0))
        out.flush()
        val method = ByteArray(2).also { input.readFully(it) }
        if (method[0].toInt() != 5) throw IOException("Not a SOCKS5 server")
        when (method[1].toInt() and 0xff) {
            0 -> Unit
            2 -> {
                val user = requireNotNull(username).toByteArray(Charsets.UTF_8)
                val pass = (password ?: "\u0000").toByteArray(Charsets.UTF_8)
                require(user.size in 1..255 && pass.size in 1..255) { "SOCKS credentials too long" }
                out.write(
                    byteArrayOf(1, user.size.toByte()) +
                        user +
                        byteArrayOf(pass.size.toByte()) +
                        pass
                )
                out.flush()
                val status = ByteArray(2).also { input.readFully(it) }
                if (status[1].toInt() != 0) throw IOException("SOCKS authentication rejected")
            }
            else -> throw IOException("SOCKS server refused our authentication methods")
        }
        val name = host.toByteArray(Charsets.US_ASCII)
        require(name.size in 1..255) { "Host name too long" }
        out.write(
            byteArrayOf(5, 1, 0, 3, name.size.toByte()) +
                name +
                byteArrayOf((port shr 8).toByte(), port.toByte())
        )
        out.flush()
        val head = ByteArray(4).also { input.readFully(it) }
        val reply = head[1].toInt() and 0xff
        if (reply != 0) throw Socks5Exception(reply)
        // Skip the bound address.
        when (head[3].toInt()) {
            1 -> input.skipFully(4)
            3 -> input.skipFully(input.readUnsignedByte())
            4 -> input.skipFully(16)
            else -> throw IOException("Bad SOCKS address type ${head[3]}")
        }
        input.skipFully(2)
    }

    /** Escapes a PT argument value (backslash, '=' and ';'), pt-spec §3.5. */
    fun escapePtArg(value: String): String =
        value.replace("\\", "\\\\").replace("=", "\\=").replace(";", "\\;")

    private fun DataInputStream.skipFully(n: Int) {
        var left = n
        while (left > 0) {
            val skipped = skipBytes(left)
            if (skipped <= 0) {
                if (read() < 0) throw IOException("Unexpected end of SOCKS reply")
                left--
            } else {
                left -= skipped
            }
        }
    }
}

internal class Socks5Exception(val code: Int) : IOException("SOCKS5 connect failed with code $code")
