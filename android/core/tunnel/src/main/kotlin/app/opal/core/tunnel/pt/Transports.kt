package app.opal.core.tunnel.pt

import IPtProxy.Controller
import IPtProxy.IPtProxy
import IPtProxy.OnTransportEvents
import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.bridge.TransportKind
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Events reported by IPtProxy (called on Go threads, re-published here). */
sealed interface TransportEvent {
    val transport: String

    /** For Snowflake: a proxy was reached. For Lyrebird transports: the listener started. */
    data class Connected(override val transport: String) : TransportEvent

    /** Snowflake broker/rendezvous/proxy errors; repeats until a proxy is reached. */
    data class Error(override val transport: String, val message: String) : TransportEvent

    data class Stopped(override val transport: String, val message: String?) : TransportEvent
}

/**
 * Pluggable transports via IPtProxy (Lyrebird: obfs4, meek_lite, webtunnel; Snowflake). Each
 * transport is a local SOCKS5 listener on 127.0.0.1 with a random port; Tor reaches it through
 * `ClientTransportPlugin <name> socks5 127.0.0.1:<port>`. Bridge-line arguments (url, fronts, ice,
 * cert, …) travel per connection in the SOCKS handshake, so one listener serves all bridges of a
 * type.
 */
internal class Transports(stateDir: File, debuggable: Boolean) {

    private val _events =
        MutableSharedFlow<TransportEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<TransportEvent> = _events.asSharedFlow()

    private val callbacks =
        object : OnTransportEvents {
            override fun connected(name: String) {
                _events.tryEmit(TransportEvent.Connected(name))
            }

            override fun error(name: String, error: Exception) {
                _events.tryEmit(TransportEvent.Error(name, error.message.orEmpty()))
            }

            override fun stopped(name: String, error: Exception?) {
                _events.tryEmit(TransportEvent.Stopped(name, error?.message))
            }
        }

    private val controller: Controller by lazy {
        stateDir.mkdirs()
        IPtProxy.newController(
            stateDir.absolutePath,
            // Logs go to StateDir/ipt.log: only in debuggable builds, never with addresses.
            debuggable,
            false,
            if (debuggable) "INFO" else "ERROR",
            callbacks,
        )
    }

    private val ports = LinkedHashMap<String, Int>()

    val versions: String
        get() = "Lyrebird ${IPtProxy.lyrebirdVersion()}, Snowflake ${IPtProxy.snowflakeVersion()}"

    /**
     * Snowflake defaults for bridge lines that omit rendezvous parameters (e.g. custom lines).
     * Values come from the bundled/updated built-in Snowflake line, never from memory.
     */
    @Synchronized
    fun configureSnowflakeDefaults(reference: BridgeLine?) {
        val args = reference?.args ?: return
        args["url"]?.let { controller.snowflakeBrokerUrl = it }
        (args["fronts"] ?: args["front"])?.let { controller.snowflakeFrontDomains = it }
        args["ice"]?.let { controller.snowflakeIceServers = it }
        args["ampcache"]?.let { controller.snowflakeAmpCacheUrl = it }
        // One active proxy plus none held idle, as in Tor Browser: extra idle peers speed up
        // failover but occupy scarce volunteer proxies (see CLAUDE.md).
        controller.snowflakeMaxPeers = 1L
    }

    /** Starts the listeners for [kinds] that are not running yet; returns name → local port. */
    @Synchronized
    fun ensure(kinds: Collection<TransportKind>): Map<String, Int> {
        for (kind in kinds) {
            val name = kind.ptName ?: continue
            if (name in ports) continue
            controller.start(name, "")
            val port = controller.port(name).toInt()
            check(port in 1..65535) { "IPtProxy returned no port for $name" }
            ports[name] = port
        }
        return HashMap(ports)
    }

    @Synchronized
    fun ensure(name: String): Int {
        ports[name]?.let {
            return it
        }
        controller.start(name, "")
        return controller.port(name).toInt().also { ports[name] = it }
    }

    @Synchronized fun running(): Map<String, Int> = HashMap(ports)

    @Synchronized
    fun stop(kinds: Collection<TransportKind>) {
        for (kind in kinds) {
            val name = kind.ptName ?: continue
            if (ports.remove(name) != null) controller.stop(name)
        }
    }

    @Synchronized
    fun stopAll() {
        for (name in ports.keys) controller.stop(name)
        ports.clear()
    }
}
