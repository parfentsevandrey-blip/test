package app.rosa.weather.core.model

import kotlinx.serialization.Serializable

/**
 * A location the user follows. [id] is stable across sessions: [CURRENT_ID] for the device
 * location, `geo:<geonameId>` for places picked from search. Widgets may also point at
 * [FOLLOW_APP_ID], which is not a place but "whatever city is open in the app".
 */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val timezone: String? = null,
) {
    val isCurrentLocation: Boolean get() = id == CURRENT_ID

    /** Places closer than ~1 km share a forecast; this key lets caches dedupe them. */
    val coordinateKey: String
        get() = "%.2f,%.2f".format(java.util.Locale.ROOT, latitude, longitude)

    companion object {
        const val CURRENT_ID = "current"

        /** Widgets only: show the city that is open in the app. */
        const val FOLLOW_APP_ID = "app"
    }
}
