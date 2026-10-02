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
import dev.halo.core.isRemoved
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
 *
 * Members come and go while the node runs (the member journal travels between
 * devices), and each one needs its own route: when they change, the tunnel
 * restarts with the new routes.
 */
class HaloVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var node: HaloNode? = null
    private var poller: Job? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var networkWatch: ConnectivityManager.NetworkCallback? = null
    /** The member addresses routed into the current tunnel. */
    private var routed: Set<String> = emptySet()

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
            check(!isRemoved(dir)) { getString(R.string.error_removed) }
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
            routed = members.map { it.ip }.toSet()
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
        var round = 0
        while (scope.isActive) {
            // Members' public addresses arrive while the node runs.
            if (round++ % MEMBERS_EVERY == 0) Halo.reloadMembers()
            if (!started.isRunning()) {
                Halo.reportError("node stopped")
                stopNode()
                stopSelf()
                return
            }
            val peers = started.peers()
            Halo.publish(peers)
            if (peers.map { it.ip }.toSet() != routed) {
                Log.i(TAG, "the members changed; restarting the tunnel with their routes")
                Halo.reloadMembers()
                scope.launch {
                    stopNode()
                    startNode()
                }
                return
            }
            delay(POLL_MS)
        }
    }

    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            /** The network the node runs over: the one it started on, at first. */
            private var current: Network? = connectivity.activeNetwork

            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
                // Registering reports the network the node just started on:
                // nothing moved, and its fresh connections should stay.
                if (network == current) return
                current = network
                synchronized(lock) { node?.networkChanged() }
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
                current = null
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
        private const val MEMBERS_EVERY = 10
        private const val TAG = "halo"
    }
}
