package app.rosa.weather.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coarse device location without Google Play services: the platform fused provider (API 31+)
 * with network/GPS fallbacks. Weather needs ~km accuracy, so only
 * `ACCESS_COARSE_LOCATION` is ever requested — kinder to privacy and battery.
 */
@Singleton
class DeviceLocationProvider @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundPermission(): Boolean = hasPermission() && granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun isLocationEnabled(): Boolean = manager?.isLocationEnabled == true

    /** A reasonably fresh fix, or `null` if unavailable within [timeout]. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeout: Duration = 12.seconds, acceptLastKnownYoungerThan: Duration = 20.minutes): Location? {
        val lm = manager ?: return null
        if (!hasPermission() || !lm.isLocationEnabled) return null

        val lastKnown = providers(lm).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
        val lastKnownAgeMs = lastKnown?.let {
            (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
        }
        if (lastKnown != null && lastKnownAgeMs != null && lastKnownAgeMs < acceptLastKnownYoungerThan.inWholeMilliseconds) {
            return lastKnown
        }
        val provider = providers(lm).firstOrNull() ?: return lastKnown
        val fresh = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, executor) { location -> cont.resume(location) }
            }
        }
        return fresh ?: lastKnown
    }

    private fun providers(lm: LocationManager): List<String> = buildList {
        if (lm.hasProvider(LocationManager.FUSED_PROVIDER) && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
        add(LocationManager.PASSIVE_PROVIDER)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
