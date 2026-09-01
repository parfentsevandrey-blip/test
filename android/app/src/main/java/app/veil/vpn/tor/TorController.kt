package app.veil.vpn.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.veil.vpn.core.VeilLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/** Where tor thinks it is in the process of becoming usable. */
data class Bootstrap(
    val percent: Int = 0,
    val tag: String = "",
    val summary: String = "",
    val lastWarning: String? = null,
) {
    val isDone: Boolean get() = percent >= 100
}

/**
 * Drives the embedded tor daemon.
 *
 * tor runs inside a bound Service from the Guardian Project's tor-android
 * build; this class owns the parts that matter to us: writing the torrc for the
 * attempt, watching bootstrap progress on the control port closely enough to
 * tell "slow" from "blocked", and shutting everything down cleanly enough that
 * the next attempt can bind the same ports.
 */
class TorController(private val context: Context) {

    private val _bootstrap = MutableStateFlow(Bootstrap())
    val bootstrap: StateFlow<Bootstrap> = _bootstrap.asStateFlow()

    private var connection: TorControlConnection? = null
    private var binding: ServiceConnection? = null
    private var eventListener: RawEventListener? = null

    var socksPort: Int = 0
        private set
    var dnsPort: Int = 0
        private set

    val isRunning: Boolean get() = binding != null

    /**
     * Writes the torrc and brings tor up. Returns once the control port is
     * answering; bootstrap progress then arrives on [bootstrap].
     */
    suspend fun start(torrc: String): Boolean = withContext(Dispatchers.IO) {
        check(binding == null) { "tor is already running" }
        _bootstrap.value = Bootstrap()

        TorService.getTorrc(context).writeText(torrc)
        VeilLog.d("tor", "torrc written (${torrc.lines().size} lines)")

        val bound = CompletableDeferred<TorService?>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? TorService.LocalBinder
                bound.complete(binder?.service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                VeilLog.w("tor", "service disconnected")
                connection = null
            }
        }

        val intent = Intent(context, TorService::class.java)
        if (!context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            VeilLog.e("tor", "bindService refused")
            return@withContext false
        }
        binding = serviceConnection

        val service = withTimeoutOrNull(20_000) { bound.await() }
        if (service == null) {
            VeilLog.e("tor", "tor service did not bind in time")
            stop()
            return@withContext false
        }

        // The control connection is opened on tor's own schedule, a moment
        // after the service binds.
        val control = withTimeoutOrNull(45_000) {
            var attempt = service.torControlConnection
            while (attempt == null) {
                delay(150)
                attempt = service.torControlConnection
            }
            attempt
        }
        if (control == null) {
            VeilLog.e("tor", "control port never answered")
            stop()
            return@withContext false
        }
        connection = control
        attachEvents(control)

        socksPort = readPort(control, "net/listeners/socks")
        dnsPort = readPort(control, "net/listeners/dns")
        VeilLog.i("tor", "control port up; socks=$socksPort dns=$dnsPort")
        true
    }

    private fun attachEvents(control: TorControlConnection) {
        val listener = RawEventListener { keyword, data ->
            when (keyword) {
                TorControlCommands.EVENT_STATUS_CLIENT -> onStatusClient(data.orEmpty())
                TorControlCommands.EVENT_WARN_MSG -> {
                    VeilLog.w("tor", data.orEmpty())
                    _bootstrap.value = _bootstrap.value.copy(lastWarning = data)
                }
                TorControlCommands.EVENT_ERR_MSG -> VeilLog.e("tor", data.orEmpty())
                TorControlCommands.EVENT_NOTICE_MSG -> VeilLog.d("tor", data.orEmpty())
            }
        }
        eventListener = listener
        control.addRawEventListener(listener)
        // TorService itself subscribes to STATUS_CLIENT; the list has to keep
        // it or the service never learns that tor came up.
        runCatching {
            control.setEvents(
                listOf(
                    TorControlCommands.EVENT_STATUS_CLIENT,
                    TorControlCommands.EVENT_NOTICE_MSG,
                    TorControlCommands.EVENT_WARN_MSG,
                    TorControlCommands.EVENT_ERR_MSG,
                ),
            )
        }.onFailure { VeilLog.w("tor", "could not subscribe to events: $it") }
    }

    /**
     * Parses lines like
     * `NOTICE BOOTSTRAP PROGRESS=25 TAG=requesting_status SUMMARY="..."`.
     */
    private fun onStatusClient(data: String) {
        if (!data.contains("BOOTSTRAP")) return
        val percent = field(data, "PROGRESS")?.toIntOrNull() ?: return
        val tag = field(data, "TAG").orEmpty()
        val summary = QUOTED_SUMMARY.find(data)?.groupValues?.getOrNull(1).orEmpty()
        val previous = _bootstrap.value
        if (percent >= previous.percent) {
            _bootstrap.value = previous.copy(percent = percent, tag = tag, summary = summary)
            VeilLog.i("tor", "bootstrap $percent% $summary")
        }
    }

    private fun readPort(control: TorControlConnection, key: String): Int = runCatching {
        // GETINFO returns the listener as a quoted "address:port".
        val raw = control.getInfo(key).orEmpty().trim().trim('"')
        raw.substringAfterLast(':').trim('"').toInt()
    }.getOrElse {
        VeilLog.w("tor", "could not read $key: $it")
        0
    }

    /** Asks tor for fresh circuits without restarting anything. */
    fun requestNewIdentity(): Boolean = runCatching {
        connection?.signal(TorControlCommands.SIGNAL_NEWNYM)
        VeilLog.i("tor", "requested a new circuit")
        true
    }.getOrElse {
        VeilLog.w("tor", "NEWNYM failed: $it")
        false
    }

    /** The circuit currently carrying traffic, for the home screen. */
    fun describeCircuit(): String? = runCatching {
        val circuits = connection?.getInfo("circuit-status").orEmpty()
        circuits.lineSequence()
            .firstOrNull { it.contains("BUILT") }
            ?.let { line ->
                Regex("\\$[0-9A-Fa-f]{40}~(\\w+)").findAll(line)
                    .map { it.groupValues[1] }
                    .joinToString("  ->  ")
                    .ifEmpty { null }
            }
    }.getOrNull()

    suspend fun stop() = withContext(Dispatchers.IO) {
        val control = connection
        eventListener?.let { runCatching { control?.removeRawEventListener(it) } }
        eventListener = null
        connection = null

        runCatching { control?.shutdownTor(TorControlCommands.SIGNAL_HALT) }

        binding?.let { runCatching { context.unbindService(it) } }
        binding = null
        runCatching { context.stopService(Intent(context, TorService::class.java)) }

        // tor releases its listeners a moment after the service goes away.
        // Starting the next attempt before that produces a port clash that
        // looks exactly like censorship, so wait for the port to come free.
        val port = socksPort
        if (port > 0) {
            withTimeoutOrNull(8_000) {
                while (!isPortFree(port)) delay(200)
            }
        } else {
            delay(1_000)
        }
        socksPort = 0
        dnsPort = 0
        _bootstrap.value = Bootstrap()
        VeilLog.i("tor", "stopped")
    }

    /**
     * True once nothing is listening on the port any more. Binding it is the
     * only reliable way to know: tor releases its listeners a moment after the
     * service goes away, and starting the next attempt too early produces a
     * port clash that looks exactly like censorship.
     */
    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
            true
        }
    }.getOrDefault(false)

    private companion object {
        val QUOTED_SUMMARY = Regex("SUMMARY=\"([^\"]*)\"")

        /** Pulls `KEY=value` out of a control-port status line. */
        fun field(data: String, key: String): String? =
            Regex("\\b$key=([^\\s]+)").find(data)?.groupValues?.getOrNull(1)
    }
}
