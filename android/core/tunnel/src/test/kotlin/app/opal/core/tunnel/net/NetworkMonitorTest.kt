package app.opal.core.tunnel.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.opal.core.model.tunnel.NetworkKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Found on Android 17: our own VPN showed up as a "network change" on every connect. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkMonitorTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private fun caps(vararg transports: Int, validated: Boolean = false): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { c ->
            transports.forEach { shadowOf(c).addTransportType(it) }
            if (validated) shadowOf(c).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    private fun monitor(): Pair<NetworkMonitor, ConnectivityManager.NetworkCallback> {
        shadowOf(connectivity).setDefaultNetworkActive(false)
        val monitor = NetworkMonitor(context, TestScope(UnconfinedTestDispatcher()))
        val callback = shadowOf(connectivity).networkCallbacks.single()
        return monitor to callback
    }

    @Test
    fun `a VPN network is never taken for the underlying network`() {
        val (monitor, callback) = monitor()
        val mobile = ShadowNetwork.newInstance(100)
        val vpn = ShadowNetwork.newInstance(104)
        callback.onCapabilitiesChanged(mobile, caps(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals(mobile, monitor.status.value.network)
        // The VPN also carries the underlying CELLULAR transport.
        callback.onCapabilitiesChanged(
            vpn,
            caps(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_CELLULAR),
        )
        assertEquals(mobile, monitor.status.value.network)
        assertEquals(NetworkKind.Cellular, monitor.status.value.kind)
    }

    /**
     * Found on Android 17 (1.0.5): with our VPN up, the default network callback of our own process
     * reported only the VPN, so Wi-Fi ↔ mobile switches went unseen.
     */
    @Test
    fun `the underlying network is the best one that is not a VPN`() {
        val request = NetworkMonitor.UNDERLYING
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
    }

    @Test
    fun `a switch and Android validating the new network are both reported`() {
        val (monitor, callback) = monitor()
        val mobile = ShadowNetwork.newInstance(100)
        val wifi = ShadowNetwork.newInstance(109)
        callback.onCapabilitiesChanged(mobile, caps(NetworkCapabilities.TRANSPORT_CELLULAR))
        callback.onCapabilitiesChanged(wifi, caps(NetworkCapabilities.TRANSPORT_WIFI))
        assertEquals(wifi, monitor.status.value.network)
        assertEquals(NetworkKind.Wifi, monitor.status.value.kind)
        assertFalse(monitor.status.value.validated)
        callback.onCapabilitiesChanged(
            wifi,
            caps(NetworkCapabilities.TRANSPORT_WIFI, validated = true),
        )
        assertTrue(monitor.status.value.validated)
        // The best network changed: the one it replaced is not lost for us.
        callback.onLost(mobile)
        assertEquals(wifi, monitor.status.value.network)
    }

    @Test
    fun `losing another network does not drop the current one`() {
        val (monitor, callback) = monitor()
        val wifi = ShadowNetwork.newInstance(7)
        callback.onCapabilitiesChanged(wifi, caps(NetworkCapabilities.TRANSPORT_WIFI))
        callback.onLost(ShadowNetwork.newInstance(104))
        assertEquals(wifi, monitor.status.value.network)
        callback.onLost(wifi)
        assertNull(monitor.status.value.network)
    }
}
