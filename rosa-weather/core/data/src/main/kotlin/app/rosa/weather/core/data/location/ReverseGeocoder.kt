package app.rosa.weather.core.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class PlaceName(val name: String, val region: String?, val country: String?, val countryCode: String?)

/** Turns coordinates into a human place name using the platform geocoder (async API 33+). */
@Singleton
class ReverseGeocoder @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun lookup(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): PlaceName? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        val address = withTimeoutOrNull(8.seconds) {
            suspendCancellableCoroutine<Address?> { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        } ?: return null
        val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.featureName ?: return null
        return PlaceName(name, address.adminArea, address.countryName, address.countryCode)
    }
}
