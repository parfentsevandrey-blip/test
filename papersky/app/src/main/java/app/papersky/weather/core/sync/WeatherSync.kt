package app.papersky.weather.core.sync

import android.content.Context
import android.location.Location
import app.papersky.weather.AppContainer
import app.papersky.weather.core.model.Place
import app.papersky.weather.widget.WidgetDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide sync flags that widgets and screens can observe for spinners and notes. */
class SyncStatus {
    /** A refresh was requested and has not finished yet (covers the worker start-up gap). */
    val pending = MutableStateFlow(false)
    val lastFailureAt = MutableStateFlow<Long?>(null)
}

/**
 * The one place that decides *what* to download. Used by the background worker and by the app
 * when it comes to the foreground, so both paths behave identically.
 */
class WeatherSync(private val context: Context, private val c: AppContainer) {

    data class Outcome(val attempted: Int, val succeeded: Int) {
        val allFailed: Boolean get() = attempted > 0 && succeeded == 0
    }

    /**
     * Re-resolves the device position. In the background this only happens when the user opted
     * into background location; otherwise widgets keep using the last position seen in the app.
     */
    suspend fun refreshDevicePlace(foreground: Boolean): Place? {
        val locator = c.locator
        val previous = c.places.snapshot().device
        if (!locator.hasPermission()) return previous
        if (!foreground && !(locator.hasBackgroundPermission() && c.settings.current().backgroundLocation)) return previous
        val location = locator.locate() ?: return previous
        if (previous != null && previous.name.isNotBlank() && distanceMeters(previous, location) < 1_500f) return previous
        val described = locator.describe(location.latitude, location.longitude)
        val place = described.copy(name = described.name.ifBlank { previous?.name.orEmpty() })
        c.places.updateDevice(place)
        return c.places.snapshot().device
    }

    suspend fun sync(foreground: Boolean, force: Boolean, onlyPlaceIds: Set<String>? = null): Outcome {
        val settings = c.settings.current()
        refreshDevicePlace(foreground)
        val data = c.places.snapshot()
        val all = listOfNotNull(data.device) + data.saved
        val wanted = onlyPlaceIds ?: (WidgetDirectory.placeIdsInUse(context) + settings.selectedPlaceId)
        // Small lists are refreshed whole so switching cities in the app is instant.
        val targets = if (onlyPlaceIds == null && all.size <= 6) all else all.filter { it.id in wanted }
        if (targets.isEmpty()) return Outcome(0, 0)

        val maxAge = if (force) 0L else MIN_REFRESH_AGE_MS
        val results = coroutineScope { targets.map { async { c.weather.refresh(it, maxAge) } }.awaitAll() }
        val ok = results.count { it.isSuccess }
        if (ok < results.size) c.syncStatus.lastFailureAt.update { System.currentTimeMillis() }
        return Outcome(results.size, ok)
    }

    private fun distanceMeters(place: Place, location: Location): Float {
        val out = FloatArray(1)
        Location.distanceBetween(place.latitude, place.longitude, location.latitude, location.longitude, out)
        return out[0]
    }

    companion object {
        const val MIN_REFRESH_AGE_MS = 10 * 60_000L
    }
}
