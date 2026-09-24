package app.rosa.weather.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HapticsLevel { Off, Subtle, Rich }

/** How much GPU the living sky may spend. [Auto] follows battery saver and thermal status. */
@Serializable
enum class EffectsQuality { Auto, Battery, Balanced, Cinematic }

@Serializable
data class AppSettings(
    /** `null` until the user picks units explicitly: then regional defaults apply. */
    val units: Units? = null,
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val haptics: HapticsLevel = HapticsLevel.Rich,
    val effects: EffectsQuality = EffectsQuality.Auto,
    val tiltLighting: Boolean = true,
    val backgroundLocation: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_REFRESH_MINUTES = 30
        val RefreshChoices = listOf(15, 30, 60, 120, 240)
    }
}

@Serializable
data class SavedPlaces(
    val places: List<Place> = emptyList(),
    val followDeviceLocation: Boolean = true,
    /** Last resolved device location, used by widgets while the app is in the background. */
    val lastDeviceLocation: Place? = null,
    val selectedId: String? = null,
) {
    /** Everything the user follows, device location first. */
    val all: List<Place>
        get() = buildList {
            if (followDeviceLocation && lastDeviceLocation != null) add(lastDeviceLocation)
            addAll(places)
        }

    fun find(id: String): Place? = if (id == Place.CURRENT_ID) lastDeviceLocation else places.firstOrNull { it.id == id }

    /** The city open in the app — the first one followed when nothing (valid) was picked. */
    val selected: Place?
        get() = all.firstOrNull { it.id == selectedId } ?: all.firstOrNull()

    /**
     * What a widget bound to [placeId] shows. Never empty while the user follows any place: an
     * unknown device location or a removed city falls back to the city open in the app, so a
     * widget never asks for a place the app already has.
     */
    fun forWidget(placeId: String): Place? = when (placeId) {
        Place.FOLLOW_APP_ID -> selected
        Place.CURRENT_ID -> lastDeviceLocation ?: selected
        else -> places.firstOrNull { it.id == placeId } ?: selected
    }
}
