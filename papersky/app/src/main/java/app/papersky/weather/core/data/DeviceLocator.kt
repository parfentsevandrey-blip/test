package app.papersky.weather.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.papersky.weather.core.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * City-level device location without Play Services: the platform fused provider where available,
 * falling back to network/GPS. Weather only needs coarse accuracy, so only
 * ACCESS_COARSE_LOCATION is ever requested.
 */
class DeviceLocator(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundPermission(): Boolean = hasPermission() && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun isLocationEnabled(): Boolean = manager != null && LocationManagerCompat.isLocationEnabled(manager)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun locate(timeoutMillis: Long = 12_000): Location? {
        val lm = manager ?: return null
        if (!hasPermission()) return null
        val fresh = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val provider = bestProvider(lm)
                if (provider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                        if (cont.isActive) cont.resume(it)
                    }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (e: IllegalArgumentException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        return fresh ?: lastKnown(lm)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? = runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
    }.getOrNull()

    private fun bestProvider(lm: LocationManager): String? {
        val enabled = lm.getProviders(true)
        return listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled }
    }

    /** Reverse-geocodes a friendly name; falls back to null when no geocoder backend exists. */
    suspend fun describe(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): Place {
        val address = if (Geocoder.isPresent()) reverse(latitude, longitude, locale) else null
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        return Place(
            id = Place.HERE,
            name = name ?: "",
            region = address?.adminArea?.takeIf { it != name },
            country = address?.countryName,
            latitude = latitude,
            longitude = longitude,
            isDeviceLocation = true,
        )
    }

    private suspend fun reverse(latitude: Double, longitude: Double, locale: Locale): Address? {
        val geocoder = Geocoder(context, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }
}
