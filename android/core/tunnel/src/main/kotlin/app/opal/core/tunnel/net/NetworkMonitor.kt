package app.opal.core.tunnel.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.opal.core.model.tunnel.NetworkKind
import app.opal.core.tunnel.util.LogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The device's *underlying* network: the one this process's own sockets use (it is excluded from
 * its own VPN), Wi-Fi or cellular, never our tunnel.
 *
 * Android 12+: the best network that is not a VPN, ranked as the system ranks its default. The
 * default network callback cannot be used there while our VPN runs: Android hands the VPN to its
 * owner as default network although the owner is excluded from it (seen on Android 17, the VPN
 * satisfies the owner's default request), so that callback reported only the VPN — ignored below —
 * and Wi-Fi ↔ mobile switches, losses and Android's validation of the new network went unseen.
 * Older versions keep the default network callback.
 */
internal class NetworkMonitor(
    context: Context,
    scope: CoroutineScope,
    private val log: LogBuffer? = null,
) {

    data class Status(val network: Network?, val kind: NetworkKind?, val validated: Boolean) {
        val isConnected: Boolean
            get() = network != null
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val status: StateFlow<Status> = callbackFlow {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                private var current: Network? = null
                private var last: Status? = null

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    // Only the default network callback (before Android 12) reports our VPN (its
                    // capabilities also carry the underlying CELLULAR/WIFI transport). A VPN is
                    // never the underlying network: treating it as one made every connect look
                    // like a network change.
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                    val status =
                        Status(
                            network,
                            kindOf(caps),
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        )
                    if (status != last) {
                        log?.d(
                            TAG,
                            "Network $network: ${status.kind}, validated=${status.validated}",
                        )
                    }
                    current = network
                    last = status
                    trySend(status)
                }

                override fun onLost(network: Network) {
                    if (network != current) return
                    log?.d(TAG, "Network $network lost")
                    current = null
                    last = null
                    trySend(Status(null, null, false))
                }
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivity.registerBestMatchingNetworkCallback(
                UNDERLYING,
                callback,
                Handler(Looper.getMainLooper()),
            )
        } else {
            connectivity.registerDefaultNetworkCallback(callback)
        }
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initial())

    private fun initial(): Status {
        val network = connectivity.activeNetwork ?: return Status(null, null, false)
        val caps =
            connectivity.getNetworkCapabilities(network)
                ?: return Status(network, NetworkKind.Other, false)
        // Same as in the callback: wait for the real underlying network instead.
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return Status(null, null, false)
        return Status(
            network,
            kindOf(caps),
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    internal companion object {
        private const val TAG = "network"

        /** Internet, not a VPN (`NetworkRequest.Builder` adds NOT_VPN, TRUSTED, NOT_RESTRICTED). */
        val UNDERLYING: NetworkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
    }

    private fun kindOf(caps: NetworkCapabilities): NetworkKind =
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
            else -> NetworkKind.Other
        }
}
