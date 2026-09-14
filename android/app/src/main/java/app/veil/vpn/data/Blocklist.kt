package app.veil.vpn.data

import android.content.Context
import app.veil.tun.veiltun.Veiltun
import app.veil.vpn.core.VeilLog
import app.veil.vpn.net.Socks5
import app.veil.vpn.net.SocksProxy
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * The ad blocker's list of names.
 *
 * It ships inside the APK, because nothing here may depend on a server and a
 * list fetched at runtime on a censored network is a list that is usually not
 * there: a snapshot of Steven Black's unified hosts list (MIT), reduced to one
 * name per line and compressed. It is unpacked into the app's files on first
 * use, and again whenever the APK ships a newer snapshot; the stamp file
 * records the date of whatever is unpacked.
 *
 * A snapshot ages. Advertising names come and go by the thousand a month, so
 * the list can also be refreshed on request — through the tunnel, from the
 * same repository, which is somebody else's server and reachable through Tor
 * where it is not reachable directly. A refreshed list carries the day it
 * was fetched as its stamp, and the shipped snapshot never overwrites a
 * newer one.
 */
object Blocklist {

    private const val ASSET = "blocklist/hosts.xz"
    private const val STAMP_ASSET = "blocklist/STAMP"

    /** Where a refresh comes from. Not ours; the list's own home. */
    private const val SOURCE_HOST = "raw.githubusercontent.com"
    private const val SOURCE_PATH = "/StevenBlack/hosts/master/hosts"

    /** A list with fewer names than this is not the list; something else came back. */
    private const val PLAUSIBLE_MINIMUM = 20_000

    fun file(context: Context): File = File(context.filesDir, "blocklist/hosts.txt")

    private fun stampFile(context: Context): File = File(context.filesDir, "blocklist/STAMP")

    /** Whether the list is unpacked and current, and how many names it holds. */
    fun describe(context: Context): String {
        val target = file(context)
        if (!target.exists() || target.length() == 0L) return "not planted"
        val stamp = stampFile(context).takeIf { it.exists() }?.readText()?.trim() ?: "?"
        return "snapshot $stamp, ${target.length() / 1024} KB"
    }

    /** The date of the list in use, or null when none is planted. */
    fun stamp(context: Context): String? =
        stampFile(context).takeIf { it.exists() && file(context).length() > 0 }?.readText()?.trim()

    /**
     * Makes sure a list is unpacked, and that it is no older than the shipped
     * one. Cheap when it already is: a stat and a tiny read. A list fetched
     * more recently than the APK's snapshot is left exactly as it is.
     */
    fun plant(context: Context): File? = runCatching {
        val target = file(context)
        val shipped = context.assets.open(STAMP_ASSET).bufferedReader().use { it.readText().trim() }
        val planted = stampFile(context).takeIf { it.exists() }?.readText()?.trim()
        // Stamps are ISO dates, so the string order is the date order.
        if (target.exists() && target.length() > 0 && planted != null && planted >= shipped) return target

        target.parentFile?.mkdirs()
        val packed = File(context.cacheDir, "hosts.xz")
        context.assets.open(ASSET).use { input ->
            packed.outputStream().use { output -> input.copyTo(output) }
        }
        Veiltun.extractXz(packed.absolutePath, target.absolutePath)
        packed.delete()
        stampFile(context).writeText(shipped)
        VeilLog.i("adblock", "planted the blocklist: snapshot $shipped, ${target.length() / 1024} KB")
        target
    }.onFailure { VeilLog.w("adblock", "could not plant the blocklist: $it") }.getOrNull()

    /**
     * Fetches the current list through the tunnel and swaps it in.
     *
     * Streamed rather than read into memory: the raw file is several
     * megabytes of hosts lines, and what is kept is one name per line, the
     * same shape the shipped snapshot has. The swap is a rename, so a
     * download that dies halfway leaves the old list untouched. Returns the
     * number of names, or the reason it did not happen.
     */
    fun refresh(context: Context, socks: SocksProxy): Result<Int> = runCatching {
        val target = file(context)
        target.parentFile?.mkdirs()
        val incoming = File(target.parentFile, "hosts.incoming")

        val names = Socks5.connect(socks, SOURCE_HOST, 443, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS).use { raw ->
            val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, SOURCE_HOST, 443, true) as SSLSocket
            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            socket.startHandshake()
            socket.getOutputStream().apply {
                write(
                    ("GET $SOURCE_PATH HTTP/1.1\r\nHost: $SOURCE_HOST\r\n" +
                        "User-Agent: Veil/0.1 (Android)\r\nAccept: text/plain\r\n" +
                        "Accept-Encoding: identity\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            val input = socket.getInputStream().buffered()
            val (status, chunked) = readHead(input)
            if (status != 200) throw IOException("HTTP $status from $SOURCE_HOST")
            val body: InputStream = if (chunked) DechunkingStream(input) else input
            reduce(BufferedReader(InputStreamReader(body, Charsets.UTF_8)), incoming)
        }

        if (names < PLAUSIBLE_MINIMUM) {
            incoming.delete()
            throw IOException("only $names names came back; that is not the list")
        }
        if (!incoming.renameTo(target)) {
            incoming.copyTo(target, overwrite = true)
            incoming.delete()
        }
        val today = LocalDate.now().toString()
        stampFile(context).writeText(today)
        VeilLog.i("adblock", "refreshed the blocklist through the tunnel: $names names, stamped $today")
        names
    }.onFailure { VeilLog.w("adblock", "blocklist refresh failed: $it") }

    /** Status line and the one header that changes how the body is framed. */
    private fun readHead(input: InputStream): Pair<Int, Boolean> {
        fun line(): String {
            val out = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) out.append(b.toChar())
            }
            return out.toString()
        }
        val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("no status line")
        var chunked = false
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            if (header.startsWith("transfer-encoding:", ignoreCase = true) &&
                header.contains("chunked", ignoreCase = true)
            ) {
                chunked = true
            }
        }
        return status to chunked
    }

    /**
     * Hosts lines in, names out. "0.0.0.0 name" and "127.0.0.1 name" both
     * count; comments, blank lines and the loopback names every hosts file
     * starts with do not.
     */
    private fun reduce(reader: BufferedReader, into: File): Int {
        var count = 0
        into.bufferedWriter().use { writer ->
            while (true) {
                val line = reader.readLine() ?: break
                val text = line.substringBefore('#').trim()
                if (text.isEmpty()) continue
                val parts = text.split(' ', '\t').filter { it.isNotEmpty() }
                if (parts.size < 2) continue
                if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") continue
                val name = parts[1].lowercase()
                if (name in LOCAL_NAMES || !name.contains('.')) continue
                writer.write(name)
                writer.newLine()
                count++
            }
        }
        return count
    }

    private val LOCAL_NAMES = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0")

    private const val CONNECT_TIMEOUT_MILLIS = 30_000
    private const val READ_TIMEOUT_MILLIS = 60_000

    /** Strips HTTP chunk framing from a body stream, and nothing more. */
    private class DechunkingStream(private val input: InputStream) : InputStream() {
        private var remaining = 0
        private var done = false

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (done) return -1
            if (remaining == 0) {
                val size = readLine().substringBefore(';').trim().toIntOrNull(16) ?: throw IOException("bad chunk")
                if (size == 0) {
                    done = true
                    return -1
                }
                remaining = size
            }
            val n = input.read(b, off, minOf(len, remaining))
            if (n < 0) throw IOException("chunk cut short")
            remaining -= n
            if (remaining == 0) readLine() // the CRLF after the chunk
            return n
        }

        private fun readLine(): String {
            val out = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) out.append(c.toChar())
            }
            return out.toString()
        }
    }
}
