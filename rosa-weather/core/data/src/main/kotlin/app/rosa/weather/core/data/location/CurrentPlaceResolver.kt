package app.rosa.weather.core.data.location

import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.model.Place
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the device location into the special [Place.CURRENT_ID] place and persists it, so
 * background work (widgets) can keep using the last good fix without location access.
 */
@Singleton
class CurrentPlaceResolver @Inject constructor(
    private val location: DeviceLocationProvider,
    private val geocoder: ReverseGeocoder,
    private val places: PlacesRepository,
) {
    /**
     * @param inBackground true when called from a worker: then background-location permission is
     * required by the platform, otherwise we silently keep the last known place.
     */
    suspend fun resolve(inBackground: Boolean): Place? {
        val saved = places.snapshot()
        if (!saved.followDeviceLocation) return null
        val allowed = if (inBackground) location.hasBackgroundPermission() else location.hasPermission()
        if (!allowed) return saved.lastDeviceLocation

        val fix = location.current() ?: return saved.lastDeviceLocation
        val previous = saved.lastDeviceLocation
        val moved = previous == null ||
            WeatherRepository.distanceKm(previous.latitude, previous.longitude, fix.latitude, fix.longitude) > RENAME_KM
        val name = if (!moved && previous.name.isNotBlank()) {
            PlaceName(previous.name, previous.region, previous.country, previous.countryCode)
        } else {
            geocoder.lookup(fix.latitude, fix.longitude)
        }
        val place = Place(
            id = Place.CURRENT_ID,
            name = name?.name ?: previous?.name.orEmpty(),
            latitude = fix.latitude,
            longitude = fix.longitude,
            region = name?.region,
            country = name?.country,
            countryCode = name?.countryCode,
        )
        places.setDeviceLocation(place)
        return place
    }

    private companion object {
        const val RENAME_KM = 2.5
    }
}
