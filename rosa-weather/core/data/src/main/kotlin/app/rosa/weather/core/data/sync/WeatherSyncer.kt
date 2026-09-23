package app.rosa.weather.core.data.sync

import app.rosa.weather.core.data.location.CurrentPlaceResolver
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.RefreshResult
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.util.NetworkMonitor
import app.rosa.weather.core.model.Place
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncOutcome { Updated, UpToDate, Offline, Failed, NothingToSync }

/**
 * The single place that decides *what* to refresh: every followed place plus every place a
 * widget shows, skipping forecasts that are still fresh. Always notifies listeners at the end so
 * widgets re-render with the current time even when nothing new was downloaded.
 */
@Singleton
class WeatherSyncer @Inject constructor(
    private val weather: WeatherRepository,
    private val places: PlacesRepository,
    private val widgetConfigs: WidgetConfigRepository,
    private val settings: SettingsRepository,
    private val currentPlace: CurrentPlaceResolver,
    private val network: NetworkMonitor,
    private val listeners: Set<@JvmSuppressWildcards WeatherSyncListener>,
) {
    private val mutex = Mutex()

    suspend fun sync(reason: SyncReason, force: Boolean = false, inBackground: Boolean = true): SyncOutcome =
        mutex.withLock {
            try {
                syncLocked(reason, force, inBackground)
            } finally {
                notifyListeners(reason)
            }
        }

    /** Re-render only: no network, no location. */
    suspend fun render(reason: SyncReason = SyncReason.Render) = notifyListeners(reason)

    private suspend fun syncLocked(reason: SyncReason, force: Boolean, inBackground: Boolean): SyncOutcome {
        val current = currentPlace.resolve(inBackground)
        val targets = targets(current)
        if (targets.isEmpty()) return SyncOutcome.NothingToSync
        weather.retainOnly(targets.map { it.id }.toSet())
        if (!network.isOnline()) return SyncOutcome.Offline

        val interval = settings.current().refreshIntervalMinutes
        // Refresh a little before the interval elapses so periodic runs don't just miss it.
        val maxAge = if (force) 0L else (interval * 60L * 0.8).toLong()
        val results = coroutineScope {
            targets.map { place -> async { weather.refresh(place, maxAge) } }.awaitAll()
        }
        return when {
            results.any { it is RefreshResult.Updated } && results.none { it is RefreshResult.Failed } -> SyncOutcome.Updated
            results.all { it is RefreshResult.UpToDate } -> SyncOutcome.UpToDate
            results.any { it is RefreshResult.Updated || it is RefreshResult.UpToDate } -> SyncOutcome.Updated
            else -> SyncOutcome.Failed
        }
    }

    private suspend fun targets(current: Place?): List<Place> {
        val saved = places.snapshot()
        val device = current ?: saved.lastDeviceLocation
        val widgetPlaceIds = widgetConfigs.snapshot().byId.values.map { it.placeId }.toSet()
        return buildList {
            if (device != null && (saved.followDeviceLocation || Place.CURRENT_ID in widgetPlaceIds)) add(device)
            addAll(saved.places)
            widgetPlaceIds.filter { id -> id != Place.CURRENT_ID && none { it.id == id } }
                .mapNotNull(saved::find)
                .forEach(::add)
        }.distinctBy { it.id }
    }

    private suspend fun notifyListeners(reason: SyncReason) {
        listeners.forEach { listener -> runCatching { listener.onWeatherChanged(reason) } }
    }
}
