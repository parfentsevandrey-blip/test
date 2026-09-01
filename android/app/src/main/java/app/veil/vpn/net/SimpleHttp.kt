package app.veil.vpn.net

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A response body plus enough metadata to decide what to do with it. */
data class HttpResponse(
    val code: Int,
    val body: ByteArray,
    val contentType: String?,
) {
    val isSuccess: Boolean get() = code in 200..299
    fun text(): String = String(body, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean =
        other is HttpResponse && code == other.code && body.contentEquals(other.body)

    override fun hashCode(): Int = 31 * code + body.contentHashCode()
}

/**
 * The smallest HTTP client that can talk to the Tor Project's bridge API.
 *
 * Direct requests go through the platform stack. Requests that have to be
 * carried by a pluggable transport are written onto a socket we opened through
 * that transport's SOCKS port ourselves, because the platform client gives no
 * way to set the SOCKS credentials a transport needs.
 */
object SimpleHttp {

    private const val USER_AGENT = "Veil/0.1 (Android)"

    @Throws(IOException::class)
    fun post(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
        proxy: SocksProxy? = null,
        timeoutMillis: Int = 45_000,
    ): HttpResponse = request("POST", url, body, headers, proxy, timeoutMillis)

    @Throws(IOException::class)
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        proxy: SocksProxy? = null,
        timeoutMillis: Int = 30_000,
    ): HttpResponse = request("GET", url, null, headers, proxy, timeoutMillis)

    @Throws(IOException::class)
    private fun request(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        proxy: SocksProxy?,
        timeoutMillis: Int,
    ): HttpResponse =
        if (proxy == null) {
            direct(method, url, body, headers, timeoutMillis)
        } else {
            throughSocks(method, url, body, headers, proxy, timeoutMillis)
        }

    private fun direct(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        timeoutMillis: Int,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytesLimited() } ?: ByteArray(0)
            return HttpResponse(code, bytes, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }

    private fun throughSocks(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        proxy: SocksProxy,
        timeoutMillis: Int,
    ): HttpResponse {
        val parsed = URL(url)
        val secure = parsed.protocol.equals("https", ignoreCase = true)
        val port = if (parsed.port != -1) parsed.port else if (secure) 443 else 80
        val path = (parsed.path.ifEmpty { "/" }) + (parsed.query?.let { "?$it" } ?: "")

        Socks5.connect(proxy, parsed.host, port, timeoutMillis, timeoutMillis).use { raw ->
            val socket = if (secure) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, parsed.host, port, true) as SSLSocket
            } else {
                raw
            }
            if (socket is SSLSocket) {
                // Without this the handshake would accept any valid certificate
                // rather than one issued for the host we asked for.
                socket.sslParameters = socket.sslParameters.apply {
                    endpointIdentificationAlgorithm = "HTTPS"
                }
                socket.startHandshake()
            }

            val request = StringBuilder()
            request.append("$method $path HTTP/1.1\r\n")
            request.append("Host: ${parsed.host}\r\n")
            request.append("User-Agent: $USER_AGENT\r\n")
            request.append("Connection: close\r\n")
            request.append("Accept-Encoding: identity\r\n")
            headers.forEach { (key, value) -> request.append("$key: $value\r\n") }
            if (body != null) request.append("Content-Length: ${body.size}\r\n")
            request.append("\r\n")

            val output = socket.getOutputStream()
            output.write(request.toString().toByteArray(Charsets.US_ASCII))
            if (body != null) output.write(body)
            output.flush()

            return readResponse(DataInputStream(socket.getInputStream().buffered()))
        }
    }

    private fun readResponse(input: DataInputStream): HttpResponse {
        val statusLine = input.readLine() ?: throw IOException("HTTP: empty response")
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("HTTP: unparseable status line '$statusLine'")

        var contentLength = -1
        var chunked = false
        var contentType: String? = null
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (name) {
                "content-length" -> contentLength = value.toIntOrNull() ?: -1
                "transfer-encoding" -> chunked = value.contains("chunked", ignoreCase = true)
                "content-type" -> contentType = value
            }
        }

        val body = when {
            chunked -> readChunked(input)
            contentLength >= 0 -> ByteArray(contentLength).also(input::readFully)
            else -> input.readBytesLimited()
        }
        return HttpResponse(code, body, contentType)
    }

    private fun readChunked(input: DataInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val header = input.readLine() ?: break
            val size = header.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size == 0) break
            if (out.size() + size > MAX_BODY) throw IOException("HTTP: response too large")
            val chunk = ByteArray(size).also(input::readFully)
            out.write(chunk)
            input.readLine() // trailing CRLF
        }
        return out.toByteArray()
    }

    private const val MAX_BODY = 4 * 1024 * 1024

    private fun InputStream.readBytesLimited(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > MAX_BODY) throw IOException("HTTP: response too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
