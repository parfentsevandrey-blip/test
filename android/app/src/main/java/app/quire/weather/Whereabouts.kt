package app.quire.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Where to ask about.
 *
 * Coarse location only, and last-known rather than a live fix: weather is a property of a
 * kilometre and an hour, so waking the GPS for it would cost more than it could possibly buy. If
 * the system has no recent position at all, the last place a forecast was fetched for is used
 * instead — a phone indoors for a day should still show the weather where it is.
 */
object Whereabouts {

    /** Providers in the order they are worth asking: cheapest and most recent first. */
    private val PROVIDERS = listOf(
        LocationManager.PASSIVE_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    )

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The best position the system already has, or null.
     *
     * "Best" is the most recent, not the most precise: a fresh network fix a block out beats an
     * exact one from last Tuesday, because the point is which weather to ask about.
     */
    // The permission is checked on the line below and every call is wrapped besides; lint cannot
    // see through the helper, and annotating the whole call chain would push the claim outwards
    // to callers that have no business making it.
    @SuppressLint("MissingPermission")
    fun last(context: Context): Location? {
        if (!granted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return PROVIDERS
            .mapNotNull { provider ->
                runCatching {
                    if (manager.isProviderEnabled(provider)) {
                        manager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    /**
     * A name for a position, in the reader's language.
     *
     * Geocoding is a network call behind a system service, so it is only ever done on the thread
     * that is already fetching, and a failure is a blank rather than a stop — a temperature with
     * no place name is still the temperature.
     */
    fun name(context: Context, latitude: Double, longitude: Double): String {
        if (!Geocoder.isPresent()) return ""
        return runCatching {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
            val address = results?.firstOrNull() ?: return ""
            // Locality is the city; sub-locality is the district, which is what a widget has room
            // for and what somebody standing in it would answer.
            address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: address.countryName
                ?: ""
        }.getOrDefault("")
    }
}
