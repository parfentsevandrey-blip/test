package app.veil.vpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.veil.vpn.net.Socks5
import app.veil.vpn.net.SocksProxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * What the tunnel is actually like to use, measured rather than assumed.
 *
 * A bootstrap that reaches 100% says a circuit was built. It says nothing about
 * whether a page will load, how long the first byte takes, whether the link
 * holds still for a minute, or whether name resolution works at all — and those
 * are the things a user means by "it is slow" or "it is unstable". Over
 * Snowflake in particular the gap between connected and usable is the whole
 * problem: the path runs through a volunteer's browser that may vanish, and the
 * symptom is not a disconnection but a pause.
 *
 * So this drives real traffic through the tunnel and times it: how long a
 * connection takes to open, how long until the first byte comes back, how much
 * throughput there is, how far apart repeated samples are, and how many
 * attempts fail outright. Everything is measured through tor's own SOCKS port,
 * which is the same path the device's traffic takes.
 */
object TrafficAnalysis {

    /** One timed request. */
    data class Sample(
        val connectMillis: Long,
        val ttfbMillis: Long,
        val ok: Boolean,
        val note: String = "",
    )

    /** A set of samples, reduced to the numbers worth printing. */
    data class Series(val label: String, val samples: List<Sample>) {
        private val good = samples.filter { it.ok }
        val attempts: Int get() = samples.size
        val failures: Int get() = samples.count { !it.ok }
        val medianTtfb: Long get() = median(good.map { it.ttfbMillis })
        val minTtfb: Long get() = good.minOfOrNull { it.ttfbMillis } ?: 0
        val maxTtfb: Long get() = good.maxOfOrNull { it.ttfbMillis } ?: 0
        val medianConnect: Long get() = median(good.map { it.connectMillis })

        /**
         * Mean absolute deviation from the median, which is what a user
         * perceives as the link being unsteady. A median of 800 ms with a
         * deviation of 80 ms feels slow; the same median with a deviation of
         * 2000 ms feels broken, and the two are worth telling apart.
         */
        val jitter: Long
            get() {
                if (good.size < 2) return 0
                val m = medianTtfb
                return (good.sumOf { abs(it.ttfbMillis - m) } / good.size)
            }

        val firstProblem: String? get() = samples.firstOrNull { !it.ok && it.note.isNotEmpty() }?.note
    }

    /** Facts about the link itself, from the platform rather than the network. */
    data class LinkFacts(
        val mtu: Int,
        val hasIpv4: Boolean,
        val hasIpv6: Boolean,
        val metered: Boolean,
        val downstreamKbps: Int,
        val upstreamKbps: Int,
        val validated: Boolean,
        val captivePortalSuspected: Boolean,
    )

    // --- Before the tunnel ---------------------------------------------------

    /**
     * What the platform already knows about the link.
     *
     * Worth printing before anything is tried, because two of these explain
     * whole classes of failure on their own. An MTU below about 1400 breaks
     * large TLS handshakes in a way that looks exactly like censorship, and a
     * network the system has marked unvalidated is usually a captive portal
     * that will answer every request with its own page.
     */
    fun linkFacts(context: Context): LinkFacts? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network)
        val link = connectivity.getLinkProperties(network)
        val addresses = link?.linkAddresses.orEmpty()
        return LinkFacts(
            mtu = link?.mtu ?: 0,
            hasIpv4 = addresses.any { it.address is java.net.Inet4Address },
            hasIpv6 = addresses.any { it.address is java.net.Inet6Address },
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0,
            upstreamKbps = caps?.linkUpstreamBandwidthKbps ?: 0,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            captivePortalSuspected =
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
        )
    }

    /**
     * How far the device's clock is from a server's, in seconds.
     *
     * Tor refuses to build circuits when the clock is far enough out that
     * consensus documents look expired, and the failure it reports is about
     * certificates rather than time. Worth ruling out in one request.
     *
     * The request is plain HTTP on purpose: it is only after the Date header,
     * and a TLS handshake with a badly wrong clock is exactly what would fail.
     */
    suspend fun clockSkewSeconds(): Long? = withTimeoutOrNull(20_000) {
        // More than one place to ask. The first is plain HTTP, which is the
        // right thing to try first — a badly wrong clock is exactly what makes
        // a TLS handshake fail, so asking over TLS could not tell the two
        // apart. But plain HTTP to a captive-portal probe is also something a
        // filtered network drops outright, and on a network where that happens
        // "could not be checked" is a worse answer than one taken over TLS.
        for (url in CLOCK_URLS) {
            val skew = runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                try {
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 7_000
                    connection.readTimeout = 7_000
                    connection.responseCode
                    val served = connection.date
                    if (served <= 0) null else (served - System.currentTimeMillis()) / 1000
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (skew != null) return@withTimeoutOrNull skew
        }
        null
    }

    // --- Through the tunnel --------------------------------------------------

    /**
     * Opens a connection through tor and reads the first byte of a response.
     *
     * Timed in two parts because they fail for different reasons. The connect
     * is tor building or reusing a circuit and the exit opening a socket; the
     * time to first byte is the round trip through the whole path once it
     * exists. A large connect with a small first byte is a circuit problem; the
     * other way round is a slow path.
     */
    private fun probe(socks: SocksProxy, host: String, secure: Boolean, path: String): Sample {
        val port = if (secure) 443 else 80
        val started = System.currentTimeMillis()
        var connectedAt = started
        var socket: Socket? = null
        return try {
            val raw = Socks5.connect(socks, host, port, CONNECT_TIMEOUT, READ_TIMEOUT)
            socket = raw
            val stream: Socket = if (secure) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(raw, host, port, true)
                    .also { (it as SSLSocket).startHandshake() }
            } else {
                raw
            }
            connectedAt = System.currentTimeMillis()
            stream.getOutputStream().apply {
                write(
                    ("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: $AGENT\r\n" +
                        "Accept: */*\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            val first = stream.getInputStream().read()
            val firstByteAt = System.currentTimeMillis()
            if (first < 0) {
                Sample(connectedAt - started, firstByteAt - started, false, "closed with no answer")
            } else {
                Sample(connectedAt - started, firstByteAt - connectedAt, true)
            }
        } catch (e: Exception) {
            Sample(
                connectedAt - started,
                System.currentTimeMillis() - started,
                false,
                e.message?.take(60) ?: e.javaClass.simpleName,
            )
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * Repeats one probe on a fixed cadence.
     *
     * Spaced rather than back to back, because the question is whether the link
     * stays usable while nothing is happening on it — which is when a Snowflake
     * proxy disappears and is replaced, and when a tunnel that tested fine
     * stops working a few minutes later.
     */
    suspend fun series(
        label: String,
        socks: SocksProxy,
        host: String,
        path: String,
        count: Int,
        spacingMillis: Long,
        onSample: (Int, Sample) -> Unit = { _, _ -> },
    ): Series {
        val samples = mutableListOf<Sample>()
        repeat(count) { index ->
            if (index > 0) delay(spacingMillis)
            val sample = probe(socks, host, secure = true, path = path)
            samples += sample
            onSample(index, sample)
        }
        return Series(label, samples)
    }

    /**
     * Bytes per second, measured on a body of a known size.
     *
     * Reported as a rate and as the time the whole transfer took, because over
     * a slow path the second number is what a person actually waits.
     */
    data class Throughput(val bytes: Long, val millis: Long, val ok: Boolean, val note: String = "") {
        val kbytesPerSecond: Long get() = if (millis <= 0) 0 else (bytes * 1000 / millis) / 1024
    }

    suspend fun throughput(socks: SocksProxy): Throughput = download(socks, SPEED_HOST, SPEED_PATH)

    /**
     * The same measurement three times in a row, on the same endpoint.
     *
     * One sample says how fast the path is; three consecutive ones say whether
     * it stays that fast. A link that is being shaped — a flow allowed to
     * start and then throttled once it has carried enough, which is how video
     * is slowed on Russian networks — shows up as a rate that falls from sample
     * to sample, and no single sample can see it.
     */
    suspend fun sustained(socks: SocksProxy): List<Throughput> = buildList {
        repeat(SUSTAINED_ROUNDS) {
            val run = download(socks, SPEED_HOST, SUSTAINED_PATH)
            add(run)
            if (!run.ok) return@buildList
        }
    }

    /** One request through the tunnel, reduced to its status and how long it took. */
    data class Reach(val status: Int, val millis: Long, val note: String = "") {
        val ok: Boolean get() = status in 200..399
        fun describe(): String = when {
            status == 0 -> "FAILED after $millis ms — $note"
            note.isNotEmpty() -> "HTTP $status in $millis ms $note"
            else -> "HTTP $status in $millis ms"
        }
    }

    /**
     * Asks a site the way a browser would, and reports what it said.
     *
     * Used against Google and YouTube because they are the sites that treat
     * Tor exits differently from everyone else: an exit that has been abused
     * gets a 429, or a redirect to a "sorry" page, instead of a result — and
     * from the phone that reads as "the internet is down" while the tunnel is
     * fine. Telling the two apart is the difference between pressing "new
     * circuit" and giving up on the app.
     */
    fun reach(socks: SocksProxy, host: String, path: String): Reach {
        val started = System.currentTimeMillis()
        var socket: Socket? = null
        return try {
            val raw = Socks5.connect(socks, host, 443, CONNECT_TIMEOUT, READ_TIMEOUT)
            socket = raw
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, 443, true) as SSLSocket
            tls.startHandshake()
            tls.getOutputStream().apply {
                write(
                    ("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: $BROWSER_AGENT\r\n" +
                        "Accept: text/html,*/*\r\nAccept-Language: ru,en\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            val header = readHeader(DataInputStream(tls.getInputStream()))
            val status = header.substringAfter(' ', "").take(3).toIntOrNull() ?: 0
            val location = header.lineSequence()
                .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                ?.substringAfter(':')?.trim().orEmpty()
            Reach(
                status,
                System.currentTimeMillis() - started,
                if (location.isEmpty()) "" else "→ ${location.take(70)}",
            )
        } catch (e: Exception) {
            Reach(0, System.currentTimeMillis() - started, e.message?.take(60) ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket?.close() }
        }
    }

    /**
     * What Google and YouTube say about this exit, and what the sustained
     * rate says about the link — as lines a person can act on.
     */
    fun exitVerdict(
        google: Reach,
        youtube: Reach,
        image: Throughput,
        sustained: List<Throughput>,
    ): List<String> = buildList {
        if (google.status == 429 || google.status == 403 || google.note.contains("/sorry")) {
            add(
                "Google treats this exit as a bot (HTTP ${google.status}): search and YouTube will " +
                    "demand a captcha or refuse. Press «new circuit» for another exit — this is a " +
                    "property of Tor exit addresses, not of this network.",
            )
        } else if (google.status == 0) {
            add("google.com could not be reached through the exit: ${google.note}")
        }
        if (youtube.status == 0) {
            add("youtube.com could not be reached through the exit: ${youtube.note}")
        }
        val first = sustained.firstOrNull()
        val last = sustained.lastOrNull()
        if (first != null && last != null && sustained.size >= 2 && first.ok && last.ok &&
            last.kbytesPerSecond < first.kbytesPerSecond / 2
        ) {
            add(
                "throughput fell from ${first.kbytesPerSecond} to ${last.kbytesPerSecond} KB/s " +
                    "within a megabyte — a flow allowed to start and then slowed, which is what " +
                    "shaping looks like.",
            )
        }
        if (image.ok && image.kbytesPerSecond < VIDEO_KBPS) {
            add(
                "video will buffer: ${image.kbytesPerSecond} KB/s from Google's CDN, and 480p " +
                    "needs about $VIDEO_KBPS.",
            )
        }
    }

    /** Fetches one body through the tunnel and times it. */
    suspend fun download(socks: SocksProxy, host: String, path: String): Throughput = withTimeoutOrNull(90_000) {
        var socket: Socket? = null
        try {
            val started = System.currentTimeMillis()
            val raw = Socks5.connect(socks, host, 443, CONNECT_TIMEOUT, READ_TIMEOUT)
            socket = raw
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, 443, true) as SSLSocket
            tls.startHandshake()
            tls.getOutputStream().apply {
                write(
                    ("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: $AGENT\r\n" +
                        "Accept: */*\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            val input = DataInputStream(tls.getInputStream())
            val header = readHeader(input)
            if (!header.startsWith("HTTP/1.1 200") && !header.startsWith("HTTP/1.0 200")) {
                return@withTimeoutOrNull Throughput(
                    0, 0, false,
                    "server answered ${header.lineSequence().firstOrNull().orEmpty().take(40)}",
                )
            }
            val body = drain(input)
            val millis = System.currentTimeMillis() - started
            Throughput(body, millis, body > 0, if (body > 0) "" else "empty body")
        } catch (e: Exception) {
            Throughput(0, 0, false, e.message?.take(60) ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket?.close() }
        }
    } ?: Throughput(0, 0, false, "timed out")

    /**
     * Asks tor's own DNS port to resolve a name, and times it.
     *
     * Name resolution is where a tunnel most often feels broken while being
     * technically up: the circuit is fine, the resolver at the far end is slow
     * or refusing, and every request in the device waits on it before a single
     * packet of the real traffic is sent.
     */
    fun resolveThroughTor(dnsPort: Int, name: String): Pair<Long, String> {
        val query = dnsQuery(name)
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = 12_000
            val started = System.currentTimeMillis()
            socket.send(
                DatagramPacket(query, query.size, InetAddress.getByName("127.0.0.1"), dnsPort),
            )
            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val millis = System.currentTimeMillis() - started
            val answers = ((buffer[6].toInt() and 0xff) shl 8) or (buffer[7].toInt() and 0xff)
            val rcode = buffer[3].toInt() and 0x0f
            when {
                rcode != 0 -> millis to "refused, rcode $rcode"
                answers == 0 -> millis to "answered with no address"
                else -> millis to "$answers record(s)"
            }
        } catch (e: Exception) {
            -1L to (e.message?.take(50) ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket.close() }
        }
    }

    /**
     * Confirms the traffic really leaves through Tor, and says where.
     *
     * A tunnel that carries traffic but leaks it around Tor is worse than one
     * that does not work, so this is not a formality: it is the one check whose
     * failure means stop using the app.
     */
    fun exitCheck(socks: SocksProxy): String {
        var socket: Socket? = null
        return try {
            val raw = Socks5.connect(socks, CHECK_HOST, 443, CONNECT_TIMEOUT, READ_TIMEOUT)
            socket = raw
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, CHECK_HOST, 443, true) as SSLSocket
            tls.startHandshake()
            tls.getOutputStream().apply {
                write(
                    ("GET $CHECK_PATH HTTP/1.1\r\nHost: $CHECK_HOST\r\nUser-Agent: $AGENT\r\n" +
                        "Accept: */*\r\nConnection: close\r\n\r\n").toByteArray(),
                )
                flush()
            }
            val input = DataInputStream(tls.getInputStream())
            readHeader(input)
            val body = String(input.readBytes().take(400).toByteArray(), Charsets.UTF_8)
            when {
                body.contains("\"IsTor\":true") ->
                    "yes — exit " + (Regex("\"IP\":\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: "?")
                body.contains("\"IsTor\":false") ->
                    "NO — traffic is leaving outside Tor"
                body.isBlank() -> "no answer"
                else -> "unexpected answer"
            }
        } catch (e: Exception) {
            "could not ask: " + (e.message?.take(50) ?: e.javaClass.simpleName)
        } finally {
            runCatching { socket?.close() }
        }
    }

    // --- Plumbing ------------------------------------------------------------

    private fun readHeader(input: InputStream): String {
        val header = StringBuilder()
        var last = 0
        while (header.length < 8192) {
            val b = input.read()
            if (b < 0) break
            header.append(b.toChar())
            if (b == '\n'.code && last == '\n'.code) break
            if (b != '\r'.code) last = b
        }
        return header.toString()
    }

    private fun drain(input: InputStream): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > 4L * 1024 * 1024) break
        }
        return total
    }

    /** A minimal A-record query. Nothing here needs a DNS library. */
    private fun dnsQuery(name: String): ByteArray {
        val labels = name.split('.').filter { it.isNotEmpty() }
        val out = ArrayList<Byte>(32 + name.length)
        val id = (System.nanoTime() and 0xffff).toInt()
        out.add((id shr 8).toByte()); out.add(id.toByte())
        out.add(0x01); out.add(0x00) // recursion desired
        out.add(0x00); out.add(0x01) // one question
        repeat(6) { out.add(0x00) } // no answers, authorities or extras
        labels.forEach { label ->
            out.add(label.length.toByte())
            label.forEach { out.add(it.code.toByte()) }
        }
        out.add(0x00)
        out.add(0x00); out.add(0x01) // A
        out.add(0x00); out.add(0x01) // IN
        return out.toByteArray()
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToLong()
        }
    }

    /** A one-line verdict a person can act on, rather than a table to interpret. */
    fun verdict(steady: Series, throughput: Throughput): String {
        val failed = steady.failures
        val median = steady.medianTtfb
        return when {
            failed >= max(2, steady.attempts / 2) ->
                "unusable: $failed of ${steady.attempts} requests failed while connected"
            failed > 0 ->
                "works but drops requests: $failed of ${steady.attempts} failed, " +
                    "which is what an intermittent proxy looks like"
            steady.jitter > 1_500 ->
                "connected and unsteady: the round trip moves by ${steady.jitter} ms between " +
                    "requests, so pages will stall and resume"
            median > 4_000 ->
                "connected but very slow: ${median} ms to the first byte"
            throughput.ok && throughput.kbytesPerSecond < 20 ->
                "connected, responsive, but only ${throughput.kbytesPerSecond} KB/s — " +
                    "enough for text, not for anything larger"
            median > 1_500 ->
                "usable: ${median} ms to the first byte, steady"
            else ->
                "good: ${median} ms to the first byte, steady"
        }
    }

    private const val AGENT = "Veil/0.1 (Android)"
    private const val CONNECT_TIMEOUT = 30_000
    private const val READ_TIMEOUT = 30_000

    /** Plain HTTP first, then TLS, so a filtered network still gets an answer. */
    private val CLOCK_URLS = listOf(
        "http://detectportal.firefox.com/success.txt",
        "http://cp.cloudflare.com/generate_204",
        "https://www.cloudflare.com/cdn-cgi/trace",
    )

    private const val CHECK_HOST = "check.torproject.org"
    private const val CHECK_PATH = "/api/ip"

    /**
     * A fixed-size body from an endpoint that exists to be measured against.
     * 256 KB is enough to see a rate over a slow path without being a rude
     * amount of traffic to pull through a volunteer's browser.
     */
    private const val SPEED_HOST = "speed.cloudflare.com"
    private const val SPEED_PATH = "/__down?bytes=262144"

    /** Three of these back to back: just over a megabyte, enough to see a trend. */
    private const val SUSTAINED_ROUNDS = 3
    private const val SUSTAINED_PATH = "/__down?bytes=393216"

    /** Roughly what 480p video needs, in KB/s. */
    private const val VIDEO_KBPS = 150

    /**
     * A browser's identity, for the requests that ask how a browser is
     * treated. Google's bot check keys on the client as much as on the
     * address, and a bare library agent is not what the user's browser sends.
     */
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Mobile Safari/537.36"

    /** The user's own test, as they described it: a Google search. */
    const val GOOGLE_HOST = "www.google.com"
    const val GOOGLE_SEARCH_PATH = "/search?q=weather"

    /** YouTube's own connectivity check, used by its app. */
    const val YOUTUBE_HOST = "www.youtube.com"
    const val YOUTUBE_PATH = "/generate_204"

    /** A thumbnail from the CDN video comes from; public, and never blocked by the site. */
    const val YOUTUBE_IMAGE_HOST = "i.ytimg.com"
    const val YOUTUBE_IMAGE_PATH = "/vi/jNQXAC9IVRw/maxresdefault.jpg"

    /** Small, always up, and a name a censor has no reason to poison. */
    const val LATENCY_HOST = "check.torproject.org"
    const val LATENCY_PATH = "/api/ip"
}
