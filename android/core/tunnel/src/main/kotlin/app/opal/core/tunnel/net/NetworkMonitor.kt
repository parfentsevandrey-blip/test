package app.opal.core.tunnel.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.opal.core.model.tunnel.NetworkKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The device's default *underlying* network. This process is excluded from its own VPN, so the
 * default network seen here is Wi-Fi/cellular, never our tunnel.
 */
internal class NetworkMonitor(context: Context, scope: CoroutineScope) {

    data class Status(val network: Network?, val kind: NetworkKind?, val validated: Boolean) {
        val isConnected: Boolean
            get() = network != null
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val status: StateFlow<Status> = callbackFlow {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    trySend(
                        Status(
                            network,
                            kindOf(caps),
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        )
                    )
                }

                override fun onLost(network: Network) {
                    trySend(Status(null, null, false))
                }
            }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, initial())

    private fun initial(): Status {
        val network = connectivity.activeNetwork ?: return Status(null, null, false)
        val caps =
            connectivity.getNetworkCapabilities(network)
                ?: return Status(network, NetworkKind.Other, false)
        return Status(
            network,
            kindOf(caps),
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    private fun kindOf(caps: NetworkCapabilities): NetworkKind =
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
            else -> NetworkKind.Other
        }
}
