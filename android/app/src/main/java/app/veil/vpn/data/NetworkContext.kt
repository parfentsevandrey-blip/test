package app.veil.vpn.data

import android.content.Context
import android.os.Build
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
    /**
     * The resolvers this network hands out, as "host:port".
     *
     * Only used when the user has asked for some names to skip the tunnel: the
     * point of that is to reach a destination the way the local network does,
     * and that includes resolving it the way the local network does.
     */
    val dnsServers: List<String> = emptyList(),
    /**
     * The name of the resolver Android's own Private DNS is set to, when it is
     * on and the mode is strict; null otherwise.
     *
     * It matters because Private DNS takes every application's lookups over
     * TLS to that resolver, through the tunnel but past the tunnel's own
     * resolver — so the ad blocker sees nothing, the names asked to skip the
     * tunnel are never noticed, and each lookup costs a circuit round trip.
     * Nothing here can turn it off; the user can, and has to be told why.
     */
    val privateDns: String? = null,
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

            val link = network?.let(connectivity::getLinkProperties)
            val resolvers = link?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?.filter { it.isNotBlank() }
                ?.map { if (it.contains(':')) "[$it]:53" else "$it:53" }
                .orEmpty()
            val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && link != null &&
                link.isPrivateDnsActive
            ) {
                link.privateDnsServerName ?: "opportunistic"
            } else {
                null
            }
            val linkParts = link?.let {
                buildList {
                    add(it.domains.orEmpty())
                    addAll(resolvers)
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

            return NetworkContext(kind, sha256(material).take(16), country, resolvers, privateDns)
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
