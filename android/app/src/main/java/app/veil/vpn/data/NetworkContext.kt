package app.veil.vpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import java.security.MessageDigest

enum class NetworkKind { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

/**
 * Enough of an identity for the current network to remember what worked on it,
 * without ever learning anything that identifies the user.
 *
 * Deliberately built from things that need no runtime permission: the transport
 * type, the link's own DNS servers and domain, and the SIM's country. Reading
 * the Wi-Fi SSID would need location access, which a VPN has no business asking
 * for. The result is hashed, so what is stored is an opaque key rather than a
 * record of the networks the user has been on.
 */
data class NetworkContext(
    val kind: NetworkKind,
    val fingerprint: String,
    val countryIso: String?,
) {
    val isOnline: Boolean get() = kind != NetworkKind.NONE

    companion object {

        fun inspect(context: Context): NetworkContext {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity?.activeNetwork
            val capabilities = network?.let(connectivity::getNetworkCapabilities)

            val kind = when {
                capabilities == null -> NetworkKind.NONE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
                else -> NetworkKind.OTHER
            }

            val linkParts = network?.let(connectivity::getLinkProperties)?.let { link ->
                buildList {
                    add(link.domains.orEmpty())
                    link.dnsServers.forEach { add(it.hostAddress.orEmpty()) }
                }
            }.orEmpty()

            val telephony = runCatching {
                context.getSystemService(TelephonyManager::class.java)
            }.getOrNull()
            val operator = runCatching { telephony?.simOperator.orEmpty() }.getOrDefault("")
            val country = runCatching {
                telephony?.networkCountryIso?.ifEmpty { telephony.simCountryIso }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.lowercase()

            val material = buildString {
                append(kind.name)
                append('|')
                // Only the mobile country code, never the full operator or any
                // subscriber identifier.
                append(operator.take(3))
                append('|')
                append(linkParts.sorted().joinToString(","))
            }

            return NetworkContext(kind, sha256(material).take(16), country)
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
