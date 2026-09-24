package app.opal.core.tunnel.ipc

import android.os.Binder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import app.opal.core.tunnel.TunnelController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * AIDL server in `:tunnel`. The service is exported (VpnService requirement) but protected by
 * BIND_VPN_SERVICE, and every call is additionally checked to come from our own UID.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TunnelBinder(private val controller: TunnelController, scope: CoroutineScope) :
    ITunnelService.Stub() {

    private val listeners = RemoteCallbackList<ITunnelListener>()
    // RemoteCallbackList broadcasts must not overlap: publish from one thread.
    private val publisher = Dispatchers.Default.limitedParallelism(1)

    init {
        scope.launch(publisher) {
            controller.snapshot.collect { s -> broadcast { it.onSnapshot(s.toJson()) } }
        }
        scope.launch(publisher) {
            controller.traffic.filterNotNull().collect { t ->
                broadcast { it.onTraffic(t.read, t.written, t.totalRead, t.totalWritten) }
            }
        }
    }

    private fun broadcast(action: (ITunnelListener) -> Unit) {
        val n = listeners.beginBroadcast()
        try {
            for (i in 0 until n) {
                try {
                    action(listeners.getBroadcastItem(i))
                } catch (_: RemoteException) {
                    // Dead listeners are removed by RemoteCallbackList itself.
                }
            }
        } finally {
            listeners.finishBroadcast()
        }
    }

    private fun enforceCaller() {
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("Foreign caller")
    }

    override fun snapshot(): String {
        enforceCaller()
        return controller.snapshot.value.toJson()
    }

    override fun registerListener(listener: ITunnelListener) {
        enforceCaller()
        listeners.register(listener)
        runCatching { listener.onSnapshot(controller.snapshot.value.toJson()) }
    }

    override fun unregisterListener(listener: ITunnelListener) {
        enforceCaller()
        listeners.unregister(listener)
    }

    override fun disconnect() {
        enforceCaller()
        controllerScope { controller.disconnect() }
    }

    override fun newIdentity() {
        enforceCaller()
        controllerScope { controller.newIdentity() }
    }

    override fun prewarm(timeoutMillis: Long) {
        enforceCaller()
        controllerScope { controller.prewarm(timeoutMillis) }
    }

    override fun cancelPrewarm() {
        enforceCaller()
        controllerScope { controller.cancelPrewarm() }
    }

    override fun refreshDirectory(timeoutMillis: Long, result: ITunnelResult) {
        enforceCaller()
        controllerScope {
            val ok = controller.refreshDirectory(timeoutMillis)
            runCatching { result.onResult(ok) }
        }
    }

    override fun diagnostics(): String {
        enforceCaller()
        return controller.exportDiagnostics()
    }

    private val commandScope = scope

    private fun controllerScope(block: suspend () -> Unit) {
        commandScope.launch { block() }
    }
}
