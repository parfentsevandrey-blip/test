package app.veil.vpn.net

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Where to send a request and, when the transport needs them, the
 * per-connection arguments a pluggable transport expects.
 *
 * Tor hands a pluggable transport its parameters through the SOCKS5 username
 * and password fields, splitting the argument string at 255 bytes. Speaking
 * that dialect ourselves is what lets the app reach the Tor Project's bridge
 * API through meek while the API's own domain is blocked.
 */
data class SocksProxy(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    companion object {
        private const val FIELD_LIMIT = 255

        /** Encodes `k=v;k=v` transport arguments into SOCKS credentials. */
        fun withTransportArgs(host: String, port: Int, args: String): SocksProxy {
            if (args.length <= FIELD_LIMIT) {
                // The password field may not be empty, so pad it.
                return SocksProxy(host, port, args, " ")
            }
            return SocksProxy(
                host,
                port,
                args.substring(0, FIELD_LIMIT),
                args.substring(FIELD_LIMIT).take(FIELD_LIMIT),
            )
        }
    }
}

/** A hand-rolled SOCKS5 client, so the credential fields stay under our control. */
object Socks5 {

    private const val VERSION = 0x05
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val ATYP_DOMAIN = 0x03
    private const val ATYP_IPV6 = 0x04

    @Throws(IOException::class)
    fun connect(
        proxy: SocksProxy,
        destinationHost: String,
        destinationPort: Int,
        connectTimeoutMillis: Int = 20_000,
        readTimeoutMillis: Int = 60_000,
    ): Socket {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeoutMillis)
            socket.soTimeout = readTimeoutMillis

            val output = socket.getOutputStream()
            val input = DataInputStream(socket.getInputStream())

            val wantsAuth = proxy.username.isNotEmpty() || proxy.password.isNotEmpty()
            if (wantsAuth) {
                output.write(byteArrayOf(VERSION.toByte(), 2, 0x00, 0x02))
            } else {
                output.write(byteArrayOf(VERSION.toByte(), 1, 0x00))
            }
            output.flush()

            val greeting = ByteArray(2).also(input::readFully)
            if (greeting[0].toInt() != VERSION) throw IOException("SOCKS: bad version ${greeting[0]}")
            when (greeting[1].toInt() and 0xFF) {
                0x00 -> Unit
                0x02 -> authenticate(output, input, proxy)
                0xFF -> throw IOException("SOCKS: proxy rejected every auth method")
                else -> throw IOException("SOCKS: unexpected auth method ${greeting[1]}")
            }

            val hostBytes = destinationHost.toByteArray(Charsets.US_ASCII)
            if (hostBytes.size > 255) throw IOException("SOCKS: host name too long")
            val request = ByteArray(7 + hostBytes.size)
            request[0] = VERSION.toByte()
            request[1] = CMD_CONNECT.toByte()
            request[2] = 0x00
            request[3] = ATYP_DOMAIN.toByte()
            request[4] = hostBytes.size.toByte()
            hostBytes.copyInto(request, 5)
            request[5 + hostBytes.size] = (destinationPort ushr 8).toByte()
            request[6 + hostBytes.size] = destinationPort.toByte()
            output.write(request)
            output.flush()

            val reply = ByteArray(4).also(input::readFully)
            if (reply[1].toInt() != 0) {
                throw IOException("SOCKS: connect refused (${socksError(reply[1].toInt())})")
            }
            when (reply[3].toInt() and 0xFF) {
                ATYP_IPV4 -> input.skipFully(4)
                ATYP_IPV6 -> input.skipFully(16)
                ATYP_DOMAIN -> input.skipFully(input.readUnsignedByte())
                else -> throw IOException("SOCKS: unknown bound address type")
            }
            input.skipFully(2)
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun authenticate(output: OutputStream, input: DataInputStream, proxy: SocksProxy) {
        val user = proxy.username.toByteArray(Charsets.UTF_8)
        val pass = proxy.password.toByteArray(Charsets.UTF_8)
        if (user.size > 255 || pass.size > 255) throw IOException("SOCKS: credentials too long")
        val message = ByteArray(3 + user.size + pass.size)
        message[0] = 0x01
        message[1] = user.size.toByte()
        user.copyInto(message, 2)
        message[2 + user.size] = pass.size.toByte()
        pass.copyInto(message, 3 + user.size)
        output.write(message)
        output.flush()

        val response = ByteArray(2).also(input::readFully)
        if (response[1].toInt() != 0) throw IOException("SOCKS: authentication rejected")
    }

    private fun DataInputStream.skipFully(count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skipBytes(remaining)
            if (skipped <= 0) throw IOException("SOCKS: truncated reply")
            remaining -= skipped
        }
    }

    private fun socksError(code: Int): String = when (code) {
        1 -> "general failure"
        2 -> "not allowed"
        3 -> "network unreachable"
        4 -> "host unreachable"
        5 -> "connection refused"
        6 -> "TTL expired"
        7 -> "command not supported"
        8 -> "address type not supported"
        else -> "code $code"
    }
}
