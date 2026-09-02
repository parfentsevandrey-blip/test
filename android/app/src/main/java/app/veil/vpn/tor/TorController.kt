package app.veil.vpn.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.veil.vpn.core.VeilLog
import app.veil.vpn.model.BridgeLine
import app.veil.vpn.model.Transport
import app.veil.vpn.net.LoopbackPorts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.freehaven.tor.control.RawEventListener
import net.freehaven.tor.control.TorControlCommands
import net.freehaven.tor.control.TorControlConnection
import org.torproject.jni.TorService

/**
 * Where tor is listening for us.
 *
 * Modelled as a network plus an address rather than a port so that moving to a
 * unix socket — which no other app on the device could reach — stays a
 * configuration change rather than a refactor.
 */
data class SocksEndpoint(val network: String, val address: String) {
    val isLoopbackTcp: Boolean get() = network == "tcp"
    val port: Int get() = address.substringAfterLast(':').toIntOrNull() ?: 0
    override fun toString(): String = "$network://$address"
}

/**
 * Where tor thinks it is in the process of becoming usable.
 *
 * [problems] is the part worth explaining. Tor does not only report progress;
 * when a bootstrap step keeps failing it reports the failure too, with a count
 * of how many times it has happened and its own opinion of how bad that is. A
 * route that completes the handshake and is then cut — the signature of a
 * fronted connection being killed once the censor has decided what it is —
 * looks like steady progress followed by silence, and waiting out a stall timer
 * on it wastes most of a minute. The count says immediately that it is dead.
 */
data class Bootstrap(
    val percent: Int = 0,
    val tag: String = "",
    val summary: String = "",
    val lastWarning: String? = null,
    /** How many times tor has failed at this step. */
    val problems: Int = 0,
    /** Tor's own verdict: "ignore", "warn" or "err". */
    val recommendation: String = "",
) {
    val isDone: Boolean get() = percent >= 100

    /** Tor has said, more than once, that this route is not working. */
    /**
     * Tor has said, repeatedly and in its own words, that this route is failing.
     *
     * [problems] counts only the failures reported since this route was
     * selected. Tor's own counter runs for the life of the process, so reading
     * it raw meant that from the second route onwards the app inherited every
     * failure of the first and gave up within one poll — routes that had not
     * been tried for two seconds were being written off. The baseline is taken
     * when the route starts, which is what makes this number mean what it says.
     *
     * Tor's own recommendation has to agree. It says "ignore" until a failure
     * has repeated enough to be worth mentioning, and a slow transport failing
     * its first attempts while it looks for a proxy is completely normal.
     */
    val isHopeless: Boolean
        get() = problems >= HOPELESS_PROBLEM_COUNT && recommendation == "warn"

    private companion object {
        /**
         * Failures on this route, after tor has already decided the situation
         * is worth warning about.
         */
        const val HOPELESS_PROBLEM_COUNT = 3
    }
}

/**
 * Drives the embedded tor daemon for the whole session.
 *
 * tor is started once and then reconfigured over its control port. That is the
 * difference between switching routes in about a second and switching them in
 * about a minute — and, more importantly, the difference between switching them
 * reliably and not at all: the tor service serialises startup on a static lock
 * and tor locks its data directory, so stopping and starting it for every route
 * regularly left the second attempt unable to start, for reasons that from the
 * outside looked exactly like censorship.
 */
class TorController(private val context: Context) {

    private val _bootstrap = MutableStateFlow(Bootstrap())
    val bootstrap: StateFlow<Bootstrap> = _bootstrap.asStateFlow()

    /**
     * The last thing tor complained about.
     *
     * tor refuses to start at all if it dislikes one line of its
     * configuration, and without this the app could only report a timeout —
     * indistinguishable, from the outside, from a blocked network.
     */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var connection: TorControlConnection? = null
    private var binding: ServiceConnection? = null
    private var eventListener: RawEventListener? = null
    private var errorReceiver: BroadcastReceiver? = null

    /**
     * Lets the first bootstrap report of a new route through whatever it says.
     *
     * Progress is monotonic within one route — a smaller number is a stale
     * event arriving late — but across routes it has to be allowed to go
     * backwards, and tor keeps its own counter across a route change rather
     * than re-announcing from zero. Without this gate the first report of a new
     * rung could be dropped for being smaller than the last one of the old.
     */
    @Volatile
    private var acceptAnyProgress = true

    /**
     * Tor's failure count when the current route started.
     *
     * Tor counts bootstrap problems for the life of the process. Subtracting
     * where this route began is the difference between "this route has failed
     * three times" and "something has failed three times since the app
     * started", and only the first of those is a reason to move on.
     */
    @Volatile
    private var problemBaseline: Int? = null

    /**
     * The bridges tor is currently configured with.
     *
     * Kept because `Bridge` is a list option and `SETCONF` replaces the whole
     * list: adding one means sending all of them again.
     */
    @Volatile
    private var activeBridges: List<BridgeLine> = emptyList()

    @Volatile
    private var activeTransport: Transport = Transport.DIRECT

    /**
     * When a route was last added to the running attempt.
     *
     * A route that arrives late needs its own share of patience: giving up two
     * seconds after adding it would make adding it pointless.
     */
    @Volatile
    var lastRouteAddedAtMillis: Long = 0L
        private set

    var socks: SocksEndpoint? = null
        private set
    var dnsPort: Int = 0
        private set

    val isRunning: Boolean get() = binding != null && connection != null

    /**
     * Writes the torrc and brings tor up, returning once the control port
     * answers and accepts commands.
     *
     * The ports are passed in rather than discovered. tor is started with its
     * network disabled so the first route can be chosen before a single packet
     * is sent, and while the network is disabled tor has no SOCKS or DNS
     * listener to discover: it is handed `DisableNetwork` as its "close
     * everything but the control port" flag. Asking it where it listens at this
     * point answers with an empty string, which is exactly what made the app
     * report that tor had failed to start when it had done nothing of the kind.
     *
     * Falls back to a minimal configuration if tor rejects the first one,
     * because a single unrecognised option is enough for it to refuse to start
     * — and reporting that as "the network is blocked" would be a lie.
     */
    suspend fun start(
        torrc: String,
        fallbackTorrc: String?,
        socksPort: Int,
        dnsPort: Int,
    ): Boolean {
        socks = SocksEndpoint("tcp", "127.0.0.1:$socksPort")
        this.dnsPort = dnsPort

        if (startOnce(torrc)) return true
        if (fallbackTorrc == null) return false

        VeilLog.w("tor", "tor would not start; retrying with a minimal configuration")
        stop()
        socks = SocksEndpoint("tcp", "127.0.0.1:$socksPort")
        this.dnsPort = dnsPort
        return startOnce(fallbackTorrc)
    }

    private suspend fun startOnce(torrc: String): Boolean = withContext(Dispatchers.IO) {
        if (binding != null) {
            VeilLog.w("tor", "a previous session was still bound; cleaning it up first")
            stop()
        }
        _bootstrap.value = Bootstrap()
        _lastError.value = null

        TorService.getTorrc(context).writeText(torrc)
        VeilLog.d("tor", "torrc written (${torrc.lines().size} lines)")
        listenForErrors()

        val bound = CompletableDeferred<TorService?>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bound.complete((service as? TorService.LocalBinder)?.service)
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

        val service = withTimeoutOrNull(BIND_TIMEOUT_MILLIS) { bound.await() }
        if (service == null) {
            VeilLog.e("tor", "tor service did not bind in time")
            return@withContext false
        }

        // The control connection is opened on tor's own schedule. Give up early
        // if tor has already told us it will not start.
        val control = withTimeoutOrNull(CONTROL_PORT_TIMEOUT_MILLIS) {
            var attempt = service.torControlConnection
            while (attempt == null) {
                if (_lastError.value != null) return@withTimeoutOrNull null
                delay(150)
                attempt = service.torControlConnection
            }
            attempt
        }
        if (control == null) {
            VeilLog.e("tor", "control port never answered: ${_lastError.value ?: "timed out"}")
            return@withContext false
        }

        // The field is published the instant the socket is wrapped, which is
        // before the service has authenticated on it. A command sent in that
        // window comes back "514 Authentication required" — and the app would
        // read that as tor being broken. Wait for a command to actually work.
        if (!awaitUsable(control)) {
            VeilLog.e("tor", "control port never accepted a command")
            return@withContext false
        }

        connection = control
        attachEvents(control)
        VeilLog.i("tor", "control port up; socks=$socks dns=$dnsPort (network still off)")
        true
    }

    /**
     * Waits until the control connection answers a trivial command.
     *
     * `GETINFO version` is the cheapest thing tor will answer, and it fails
     * while the connection is unauthenticated, which is precisely the state
     * being waited out.
     */
    private suspend fun awaitUsable(control: TorControlConnection): Boolean =
        withTimeoutOrNull(CONTROL_READY_TIMEOUT_MILLIS) {
            var ready = false
            while (!ready) {
                val version = runCatching { control.getInfo("version") }.getOrNull()
                if (!version.isNullOrBlank()) {
                    VeilLog.i("tor", "tor ${version.trim()}")
                    ready = true
                } else if (_lastError.value != null) {
                    return@withTimeoutOrNull false
                } else {
                    delay(120)
                }
            }
            true
        } ?: false

    // --- Switching routes without restarting --------------------------------

    /**
     * Points tor at a different way in.
     *
     * `RESETCONF` comes first because bridge lines are a list: setting new ones
     * without clearing the old would leave tor trying both. Both bridges and
     * `UseBridges` go in one `SETCONF`, which tor applies atomically — so a
     * single malformed line rejects the whole rung rather than half-applying
     * it.
     */
    suspend fun applyRoute(transport: Transport, bridges: List<BridgeLine>): Boolean =
        withContext(Dispatchers.IO) {
            val control = connection ?: return@withContext false
            runCatching {
                control.resetConf(listOf("Bridge", "UseBridges"))
                val lines = buildList {
                    if (transport == Transport.DIRECT || bridges.isEmpty()) {
                        add("UseBridges 0")
                    } else {
                        add("UseBridges 1")
                        bridges.forEach { add("Bridge ${it.raw}") }
                    }
                    // Circuit timings travel with the route. A path through a
                    // volunteer's browser needs different patience from a
                    // direct one, and getting this wrong is what makes a
                    // working Snowflake connection feel broken.
                    addAll(Torrc.tuning(transport))
                }
                control.setConf(lines)
                activeBridges = bridges
                activeTransport = transport
                VeilLog.i(
                    "tor",
                    "route set to ${transport.torName} with ${bridges.size} bridge line(s)",
                )
                true
            }.getOrElse {
                // Almost always a bridge line tor would not parse. Saying which
                // one matters: the alternative is a rung that fails instantly
                // and looks like censorship.
                VeilLog.e("tor", "tor rejected the ${transport.torName} route: ${it.message}")
                bridges.forEach { VeilLog.d("tor", "  offered: ${it.raw}") }
                _lastError.value = it.message
                false
            }
        }

    /**
     * Adds a way in alongside the ones already configured, rather than instead
     * of them.
     *
     * This is the part of Mullvad's design that transfers directly. Their
     * client does not try one obfuscation, wait for it to fail, and then try
     * the next: it starts them a second apart, sends the same traffic through
     * all of them, and keeps whichever answers first. Waiting out a dead route
     * before starting a live one is time spent for no information.
     *
     * Tor can be driven the same way, because bridges are a list and it tries
     * the ones it has. Measured against tor 0.4.8: adding a second bridge while
     * the first is still being attempted is accepted, leaves the first alone,
     * and launches a connection to the new one straight away.
     *
     * The stagger matters and is not just politeness. A fan of simultaneous
     * handshakes is one of the things current DPI is reported to score, so the
     * routes are started a few seconds apart, and only routes that are
     * different *in kind* are raced — one HTTPS connection, one WebRTC session
     * and one connection to a phantom look like three unrelated things, where
     * three obfs4 bridges at once look like exactly what they are.
     */
    suspend fun addRoute(transport: Transport, bridges: List<BridgeLine>): Boolean =
        withContext(Dispatchers.IO) {
            val control = connection ?: return@withContext false
            if (bridges.isEmpty()) return@withContext false
            val merged = (activeBridges + bridges).distinctBy { it.raw }
            runCatching {
                val lines = buildList {
                    add("UseBridges 1")
                    merged.forEach { add("Bridge ${it.raw}") }
                    // The timings have to suit the most patient racer: a rung
                    // cut off at obfs4's budget while Snowflake was still
                    // finding a proxy is the classic way to conclude, wrongly,
                    // that nothing works.
                    addAll(Torrc.tuning(slowerOf(activeTransport, transport)))
                }
                control.setConf(lines)
                activeBridges = merged
                activeTransport = slowerOf(activeTransport, transport)
                lastRouteAddedAtMillis = System.currentTimeMillis()
                VeilLog.i("tor", "also trying ${transport.torName} (${merged.size} bridges now)")
                true
            }.getOrElse {
                VeilLog.w("tor", "could not add ${transport.torName}: ${it.message}")
                false
            }
        }

    /**
     * Which of the racing routes tor actually connected through.
     *
     * Without this the app would learn nothing from a race it won: the whole
     * point of remembering what worked on a network is knowing which of the
     * things tried was the one that did. Bridges are matched by the fingerprint
     * on their line, which is the same identity tor reports for the connection.
     */
    fun connectedTransport(): Transport? = runCatching {
        val connected = connection?.getInfo("orconn-status").orEmpty()
            .lineSequence()
            .filter { it.contains("CONNECTED") }
            .mapNotNull { FINGERPRINT_IN_STATUS.find(it)?.groupValues?.getOrNull(1)?.uppercase() }
            .toSet()
        if (connected.isEmpty()) return null
        activeBridges
            .firstOrNull { it.fingerprint?.uppercase() in connected }
            ?.transportEnum
    }.getOrNull()

    /**
     * Turns tor's network on or off.
     *
     * Starting with it off, and enabling it only once a route is chosen, keeps
     * tor from spending its first half-minute on a direct connection nobody
     * asked for. Turning it off again between rungs is not cosmetic either: it
     * is what closes every connection to the bridge that just failed, so the
     * next rung starts clean.
     */
    suspend fun setNetworkEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val control = connection ?: return@withContext false
        runCatching {
            control.setConf("DisableNetwork", if (enabled) "0" else "1")
            true
        }.getOrElse {
            VeilLog.e("tor", "could not toggle the network", it)
            _lastError.value = it.message
            false
        }
    }

    /**
     * Confirms tor really opened the listeners the torrc asked for.
     *
     * They only exist once the network is enabled, and they are what the tunnel
     * is about to be pointed at, so this is the last point at which a mismatch
     * can be reported as itself rather than as a tunnel that carries nothing.
     */
    suspend fun awaitListeners(): Boolean = withContext(Dispatchers.IO) {
        val control = connection ?: return@withContext false
        val expected = socks?.port ?: return@withContext false

        val seen = withTimeoutOrNull(LISTENER_TIMEOUT_MILLIS) {
            var raw = ""
            while (raw.isEmpty()) {
                raw = runCatching { control.getInfo("net/listeners/socks") }
                    .getOrNull().orEmpty().trim()
                if (raw.isEmpty()) delay(200)
            }
            raw
        }

        if (seen.isNullOrEmpty()) {
            // Worth saying loudly, but not worth abandoning the route over: the
            // port is one we chose and wrote into the configuration, and tor
            // opens its listeners on its own schedule. Treating a slow answer
            // here as a dead route would fail every rung for a reason that is
            // not about the network at all.
            VeilLog.w("tor", "no SOCKS listener reported yet; expecting 127.0.0.1:$expected")
            if (!LoopbackPorts.isFree(expected)) {
                VeilLog.e("tor", "port $expected is held by something else")
            }
            return@withContext true
        }
        if (!seen.contains(":$expected")) {
            VeilLog.w("tor", "SOCKS listener is $seen, not the configured port $expected")
            socks = SocksEndpoint("tcp", seen.substringBefore(' ').trim('"'))
        }
        VeilLog.i("tor", "listeners up: socks=$seen")
        true
    }

    /**
     * Forgets bootstrap progress so the next route is measured from zero, and
     * lets the next report through whatever its value.
     */
    fun resetBootstrap() {
        acceptAnyProgress = true
        // Cleared rather than zeroed: the baseline is whatever tor reports
        // first for this route, which is not known until it says something.
        problemBaseline = null
        _bootstrap.value = Bootstrap()
    }

    /**
     * Reads bootstrap state directly instead of waiting to be told.
     *
     * tor announces progress only when it increases, and it keeps counting
     * across a route change, so a rung that starts where the last one left off
     * can be genuinely making progress while emitting nothing at all. Polling
     * turns that from a false stall into what it is.
     */
    suspend fun refreshBootstrap(): Bootstrap = withContext(Dispatchers.IO) {
        val control = connection ?: return@withContext _bootstrap.value
        runCatching { control.getInfo("status/bootstrap-phase") }
            .getOrNull()
            ?.let { onStatusClient(it) }
        _bootstrap.value
    }

    // --- Events and errors --------------------------------------------------

    private fun listenForErrors() {
        if (errorReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (message.isNotBlank()) {
                    VeilLog.e("tor", "tor reported: $message")
                    _lastError.value = message
                }
            }
        }
        runCatching {
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(receiver, IntentFilter(TorService.ACTION_ERROR))
            errorReceiver = receiver
        }.onFailure { VeilLog.w("tor", "could not listen for tor errors: $it") }
    }

    private fun attachEvents(control: TorControlConnection) {
        val listener = RawEventListener { keyword, data ->
            when (keyword) {
                TorControlCommands.EVENT_STATUS_CLIENT -> onStatusClient(data.orEmpty())
                TorControlCommands.EVENT_WARN_MSG -> {
                    VeilLog.w("tor", data.orEmpty())
                    _bootstrap.value = _bootstrap.value.copy(lastWarning = data)
                }
                TorControlCommands.EVENT_ERR_MSG -> {
                    VeilLog.e("tor", data.orEmpty())
                    _lastError.value = data
                }
                TorControlCommands.EVENT_NOTICE_MSG -> VeilLog.d("tor", data.orEmpty())
            }
        }
        eventListener = listener
        control.addRawEventListener(listener)
        subscribe(control)
        // TorService subscribes on the same connection, and SETEVENTS replaces
        // the list rather than adding to it — so whichever of us calls last
        // decides what both of us receive. It asks for STATUS_CLIENT only, a
        // moment after authenticating, which is the same moment we get here.
        // Asking again shortly afterwards costs one command and settles it.
        io.launch {
            delay(EVENT_REASSERT_MILLIS)
            if (connection === control) subscribe(control)
        }
    }

    private fun subscribe(control: TorControlConnection) = runCatching {
        control.setEvents(
            listOf(
                // STATUS_CLIENT has to stay in the list: it is how the tor
                // service learns that tor came up, not just how we do.
                TorControlCommands.EVENT_STATUS_CLIENT,
                TorControlCommands.EVENT_NOTICE_MSG,
                TorControlCommands.EVENT_WARN_MSG,
                TorControlCommands.EVENT_ERR_MSG,
            ),
        )
    }.onFailure { VeilLog.w("tor", "could not subscribe to events: $it") }

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

        // A failure report carries COUNT and RECOMMENDATION and repeats the
        // percentage it is stuck at, so it must not be mistaken for progress.
        val count = field(data, "COUNT")?.toIntOrNull()
        if (count != null) {
            // The first report after a route change only tells us where tor's
            // counter stands; the failures that belong to this route are the
            // ones after that.
            val baseline = problemBaseline ?: count.also { problemBaseline = it }
            val mine = (count - baseline).coerceAtLeast(0)
            val recommendation = field(data, "RECOMMENDATION").orEmpty()
            _bootstrap.value = previous.copy(problems = mine, recommendation = recommendation)
            if (mine > 0) {
                VeilLog.w("tor", "bootstrap stuck at $percent% (failure $mine here): $summary")
            }
            return
        }

        if (acceptAnyProgress || percent >= previous.percent) {
            acceptAnyProgress = false
            _bootstrap.value = previous.copy(
                percent = percent,
                tag = tag,
                summary = summary,
                // Progress means the previous failures are behind us.
                problems = 0,
                recommendation = "",
            )
            VeilLog.i("tor", "bootstrap $percent% $summary")
        }
    }

    /**
     * Makes sure tor has a circuit ready before something needs one.
     *
     * Tor keeps spare circuits so that opening a connection does not wait for
     * one to be built, and it decides how many to keep from how much the client
     * has been asking for lately. A tunnel that has been quiet for a while
     * therefore has no spares — and the next thing the user does pays the full
     * cost of building a circuit, which over a slow transport is the difference
     * between a page loading and a page appearing to hang.
     *
     * Asking for one costs nothing when spares already exist, and one circuit
     * built quietly in the background when they do not.
     */
    suspend fun ensureSpareCircuit(): Boolean = withContext(Dispatchers.IO) {
        val control = connection ?: return@withContext false
        runCatching {
            val built = control.getInfo("circuit-status").orEmpty()
                .lineSequence()
                .count { it.contains(" BUILT ") && it.contains("PURPOSE=GENERAL") }
            if (built >= SPARE_CIRCUITS) return@runCatching false
            control.extendCircuit("0", "")
            VeilLog.d("tor", "asked for a spare circuit ($built built)")
            true
        }.getOrElse {
            VeilLog.d("tor", "could not pre-build a circuit: ${it.message}")
            false
        }
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

    /**
     * What tor itself says it has moved, read from its control port.
     *
     * Worth having next to the app's own byte counters during a diagnostic:
     * the tunnel's counters include everything the device sent at the TUN
     * interface, whereas these are what actually crossed a circuit. A large
     * gap between the two is traffic being dropped rather than carried.
     */
    fun describeTraffic(): String? = runCatching {
        val read = connection?.getInfo("traffic/read")?.toLongOrNull() ?: return@runCatching null
        val written = connection?.getInfo("traffic/written")?.toLongOrNull() ?: 0L
        "${read / 1024} KB in, ${written / 1024} KB out through tor"
    }.getOrNull()

    suspend fun stop() = withContext(Dispatchers.IO) {
        val control = connection
        eventListener?.let { runCatching { control?.removeRawEventListener(it) } }
        eventListener = null
        connection = null

        errorReceiver?.let {
            runCatching { LocalBroadcastManager.getInstance(context).unregisterReceiver(it) }
        }
        errorReceiver = null

        // A HALT on a wedged control port never returns. Ask, wait briefly,
        // and move on: the service is being unbound either way.
        if (control != null) {
            val halt = io.launch {
                runCatching { control.shutdownTor(TorControlCommands.SIGNAL_HALT) }
            }
            withTimeoutOrNull(2_000) { halt.join() }
        }

        binding?.let { runCatching { context.unbindService(it) } }
        binding = null
        runCatching { context.stopService(Intent(context, TorService::class.java)) }

        // tor releases its listeners a moment after the service goes away, and
        // starting again before that produces a port clash that looks exactly
        // like censorship.
        val port = socks?.takeIf { it.isLoopbackTcp }?.port ?: 0
        if (port > 0) {
            withTimeoutOrNull(4_000) { while (!LoopbackPorts.isFree(port)) delay(200) }
        } else {
            delay(500)
        }
        socks = null
        dnsPort = 0
        activeBridges = emptyList()
        activeTransport = Transport.DIRECT
        lastRouteAddedAtMillis = 0L
        _bootstrap.value = Bootstrap()
        VeilLog.i("tor", "stopped")
    }

    /** The more patient of two transports, for timings shared by a race. */
    private fun slowerOf(a: Transport, b: Transport): Transport =
        if (StrategyPlanner.budgetMillis(b) > StrategyPlanner.budgetMillis(a)) b else a

    private companion object {
        val QUOTED_SUMMARY = Regex("SUMMARY=\"([^\"]*)\"")

        /** `$AAAA...AAAA~nickname` at the start of an orconn-status line. */
        val FINGERPRINT_IN_STATUS = Regex("\\$([0-9A-Fa-f]{40})")

        /** Binding a service on a cold start is slow, but not this slow. */
        const val BIND_TIMEOUT_MILLIS = 20_000L

        /**
         * tor has to read its configuration, verify it and open a control
         * socket. On a slow phone that is a few seconds; much beyond this and
         * it is not coming.
         */
        const val CONTROL_PORT_TIMEOUT_MILLIS = 40_000L

        /** How long the control port gets to finish authenticating. */
        const val CONTROL_READY_TIMEOUT_MILLIS = 15_000L

        /** Opening two loopback listeners is immediate unless something is wrong. */
        const val LISTENER_TIMEOUT_MILLIS = 8_000L

        /** Long enough for the tor service to have finished its own SETEVENTS. */
        const val EVENT_REASSERT_MILLIS = 2_500L

        /** How many built general-purpose circuits count as "enough spare". */
        const val SPARE_CIRCUITS = 2

        /** Pulls `KEY=value` out of a control-port status line. */
        fun field(data: String, key: String): String? =
            Regex("\\b$key=([^\\s]+)").find(data)?.groupValues?.getOrNull(1)
    }
}
