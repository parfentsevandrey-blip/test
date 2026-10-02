package dev.halo.app

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import dev.halo.core.HaloNode
import dev.halo.core.deviceInfo
import dev.halo.core.listMembers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the TUN device and the Rust node.
 *
 * Only the addresses of member devices are routed into the tunnel, so the rest of
 * the phone's traffic never touches it. The app itself is excluded from the VPN:
 * its own sockets carry the tunnel and must not go through it.
 *
 * iroh cannot watch the network on Android, so the service passes on every change
 * of the default network (Wi-Fi to mobile and back) and tells the system which
 * network the VPN runs over.
 */
class HaloVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var node: HaloNode? = null
    private var poller: Job? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var networkWatch: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                stopNode()
                stopSelf()
            }
            ACTION_RESTART -> scope.launch {
                stopNode()
                startNode()
            }
            // Started by the app or by the system for an always-on VPN.
            else -> scope.launch { startNode() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over, or the user turned this one off in the settings.
        scope.launch {
            stopNode()
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopNode()
        scope.cancel()
        super.onDestroy()
    }

    private fun startNode(): Unit = synchronized(lock) {
        if (node != null) return
        try {
            val dir = Halo.stateDir
            val me = deviceInfo(dir)
            val members = listMembers(dir)
            check(members.isNotEmpty()) { getString(R.string.error_no_devices) }
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(MTU)
                .addAddress(me.ip, 32)
                .addDisallowedApplication(packageName)
            members.forEach { builder.addRoute(it.ip, 32) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            val tun = checkNotNull(builder.establish()) { getString(R.string.error_vpn_denied) }
            multicast = getSystemService(WifiManager::class.java)
                ?.createMulticastLock("halo")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            val started = HaloNode.start(dir, tun.detachFd(), MTU.toUShort())
            node = started
            Halo.reportError(null)
            Halo.setRunning(true)
            poller = scope.launch { poll(started) }
            watchNetwork()
            Log.i(TAG, "up: ${me.ip}, ${members.size} devices")
        } catch (e: Exception) {
            Log.e(TAG, "failed to start", e)
            Halo.reportError(e.message)
            releaseMulticast()
            stopSelf()
        }
    }

    private suspend fun poll(started: HaloNode) {
        while (scope.isActive) {
            if (!started.isRunning()) {
                Halo.reportError("node stopped")
                stopNode()
                stopSelf()
                return
            }
            Halo.publish(started.peers())
            delay(POLL_MS)
        }
    }

    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed(arrayOf(network))

            override fun onLost(network: Network) = changed(null)

            private fun changed(underlying: Array<Network>?) {
                setUnderlyingNetworks(underlying)
                synchronized(lock) { node?.networkChanged() }
            }
        }
        // The app is outside its own VPN, so its default network is Wi-Fi or mobile.
        connectivity.registerDefaultNetworkCallback(callback)
        networkWatch = callback
    }

    private fun stopNode(): Unit = synchronized(lock) {
        networkWatch?.let { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) }
        networkWatch = null
        poller?.cancel()
        poller = null
        node?.let {
            it.stop()
            it.close()
        }
        node = null
        releaseMulticast()
        Halo.setRunning(false)
    }

    private fun releaseMulticast() {
        multicast?.takeIf { it.isHeld }?.release()
        multicast = null
    }

    companion object {
        const val ACTION_STOP = "dev.halo.app.STOP"
        const val ACTION_RESTART = "dev.halo.app.RESTART"
        const val MTU = 1280
        private const val POLL_MS = 1000L
        private const val TAG = "halo"
    }
}
