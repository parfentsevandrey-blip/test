package app.opal.core.tunnel.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.IBinder
import android.os.RemoteException
import app.opal.core.model.tunnel.TrafficSample
import app.opal.core.model.tunnel.TunnelSnapshot
import app.opal.core.tunnel.TunnelService
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI-process side of the tunnel: binds to [TunnelService] in `:tunnel` and mirrors its state.
 * Binding is reference-counted by [acquire]/[release] (e.g. while the UI is started), so the tunnel
 * process is not kept alive by an idle UI.
 */
class TunnelClient(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _snapshot = MutableStateFlow(TunnelSnapshot())
    val snapshot: StateFlow<TunnelSnapshot> = _snapshot.asStateFlow()

    private val _traffic = MutableStateFlow<TrafficSample?>(null)
    val traffic: StateFlow<TrafficSample?> = _traffic.asStateFlow()

    private val service = MutableStateFlow<ITunnelService?>(null)
    @Volatile private var trafficSeq = 0L
    private var users = 0
    private var bound = false

    private val listener =
        object : ITunnelListener.Stub() {
            override fun onSnapshot(json: String) {
                val parsed = runCatching { TunnelSnapshot.fromJson(json) }.getOrNull() ?: return
                _snapshot.value = parsed
            }

            override fun onTraffic(read: Long, written: Long, totalRead: Long, totalWritten: Long) {
                _traffic.value = TrafficSample(read, written, totalRead, totalWritten, ++trafficSeq)
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val s = ITunnelService.Stub.asInterface(binder)
                try {
                    s.registerListener(listener)
                    service.value = s
                } catch (_: RemoteException) {
                    service.value = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // The :tunnel process died; the system rebinds automatically when it restarts.
                service.value = null
                _traffic.value = null
            }

            override fun onBindingDied(name: ComponentName?) {
                service.value = null
                rebind()
            }
        }

    fun acquire() {
        users++
        if (!bound) bind()
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0 && bound) unbind()
    }

    private fun bind() {
        bound =
            app.bindService(TunnelService.controlIntent(app), connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbind() {
        runCatching { service.value?.unregisterListener(listener) }
        runCatching { app.unbindService(connection) }
        bound = false
        service.value = null
    }

    private fun rebind() {
        if (bound) runCatching { app.unbindService(connection) }
        bound = false
        if (users > 0) bind()
    }

    /** Intent to request VPN consent, or null when it is already granted. */
    fun consentIntent(): Intent? = VpnService.prepare(app)

    /** Starts the VPN (consent must have been granted). */
    fun connect() {
        TunnelService.connect(app)
    }

    fun disconnect() = command { it.disconnect() }

    fun newIdentity() = command { it.newIdentity() }

    fun prewarm(timeoutMillis: Long) = command { it.prewarm(timeoutMillis) }

    fun cancelPrewarm() = command { it.cancelPrewarm() }

    suspend fun diagnostics(): String? = withService { it.diagnostics() }

    /** Used by the background worker; binds temporarily if needed. */
    suspend fun refreshDirectory(timeoutMillis: Long): Boolean {
        acquire()
        try {
            val s =
                withTimeoutOrNull(BIND_TIMEOUT_MS) { service.filterNotNull().first() }
                    ?: return false
            return withTimeoutOrNull(timeoutMillis + BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val callback =
                        object : ITunnelResult.Stub() {
                            override fun onResult(success: Boolean) {
                                if (cont.isActive) cont.resume(success)
                            }
                        }
                    try {
                        s.refreshDirectory(timeoutMillis, callback)
                    } catch (_: RemoteException) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            } ?: false
        } finally {
            release()
        }
    }

    private fun command(block: (ITunnelService) -> Unit) {
        scope.launch { withService(block) }
    }

    private suspend fun <T> withService(block: (ITunnelService) -> T): T? {
        acquire()
        try {
            val s =
                withTimeoutOrNull(BIND_TIMEOUT_MS) { service.filterNotNull().first() }
                    ?: return null
            return try {
                block(s)
            } catch (_: RemoteException) {
                null
            }
        } finally {
            release()
        }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
