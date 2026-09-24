package app.opal.core.tunnel.bridges

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * One HTTPS request through a local SOCKS5 proxy with the platform TLS stack (system CAs only,
 * hostname verified). HTTP/1.1 with `Connection: close`; supports Content-Length and chunked
 * bodies. Deliberately tiny instead of an HTTP library: two call sites, no redirects, no cookies,
 * no cache.
 */
internal class HttpsOverSocks(
    private val proxyPort: Int,
    private val username: String?,
    private val password: String?,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) {

    data class Response(val status: Int, val body: String)

    fun post(host: String, path: String, contentType: String, body: String): Response =
        request("POST", host, path, contentType, body.toByteArray(Charsets.UTF_8))

    fun get(host: String, path: String): Response = request("GET", host, path, null, null)

    private fun request(
        method: String,
        host: String,
        path: String,
        contentType: String?,
        body: ByteArray?,
    ): Response {
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress("127.0.0.1", proxyPort), connectTimeoutMs)
            raw.soTimeout = readTimeoutMs
            Socks5.connect(raw, host, 443, username, password)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val tls = factory.createSocket(raw, host, 443, true) as SSLSocket
            tls.sslParameters =
                tls.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                    serverNames = listOf(SNIHostName(host))
                }
            tls.use { socket ->
                socket.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)) {
                    throw SSLPeerUnverifiedException("Certificate does not match $host")
                }
                val head = buildString {
                    append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                    append("Host: ").append(host).append("\r\n")
                    if (body != null) {
                        append("Content-Type: ").append(contentType).append("\r\n")
                        append("Content-Length: ").append(body.size).append("\r\n")
                    }
                    append("Accept-Encoding: identity\r\n")
                    append("Connection: close\r\n\r\n")
                }
                val out = socket.outputStream
                out.write(head.toByteArray(Charsets.US_ASCII))
                if (body != null) out.write(body)
                out.flush()
                return readResponse(BufferedInputStream(socket.inputStream))
            }
        } finally {
            runCatching { raw.close() }
        }
    }

    private fun readResponse(input: InputStream): Response {
        val statusLine = readLine(input) ?: throw IOException("Empty HTTP response")
        val status =
            statusLine.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw IOException("Bad status line")
        var contentLength = -1
        var chunked = false
        while (true) {
            val line = readLine(input) ?: throw IOException("Truncated HTTP headers")
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (name) {
                "content-length" -> contentLength = value.toIntOrNull() ?: -1
                "transfer-encoding" -> chunked = value.contains("chunked", ignoreCase = true)
            }
        }
        val bytes =
            when {
                chunked -> readChunked(input)
                contentLength >= 0 -> readExactly(input, contentLength)
                else -> input.readBytes()
            }
        if (bytes.size > MAX_BODY) throw IOException("Response too large")
        return Response(status, bytes.toString(Charsets.UTF_8))
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: throw IOException("Truncated chunk")
            val size = sizeLine.substringBefore(';').trim().toInt(16)
            if (size == 0) {
                // Trailers until an empty line.
                var trailer = readLine(input)
                while (!trailer.isNullOrEmpty()) trailer = readLine(input)
                return out.toByteArray()
            }
            out.write(readExactly(input, size))
            readLine(input)
            if (out.size() > MAX_BODY) throw IOException("Response too large")
        }
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        if (n > MAX_BODY) throw IOException("Response too large")
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw IOException("Truncated HTTP body")
            off += r
        }
        return buf
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(c.toChar())
            if (sb.length > 8192) throw IOException("HTTP line too long")
        }
    }

    private companion object {
        const val MAX_BODY = 4 * 1024 * 1024
    }
}
