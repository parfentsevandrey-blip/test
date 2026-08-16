package com.cozyhome.weather.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.Geocoder
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object LocationHelper {

    /** Best-effort locality name for coordinates via the platform Geocoder. */
    suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, java.util.Locale.forLanguageTag("ru"))
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            }.getOrNull()
        }

    /** Returns a coarse current location or null. Caller must hold ACCESS_COARSE_LOCATION. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null

        lm.getLastKnownLocation(provider)?.let { last ->
            if (System.currentTimeMillis() - last.time < 15 * 60_000) return last
        }

        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}
