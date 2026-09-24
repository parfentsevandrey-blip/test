package app.rosa.weather.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SavedPlacesTest {
    private val moscow = Place("geo:524901", "Москва", 55.75, 37.62)
    private val paris = Place("geo:2988507", "Paris", 48.85, 2.35)
    private val here = Place(Place.CURRENT_ID, "Химки", 55.89, 37.44)

    @Test
    fun `widget following the app shows the open city`() {
        val saved = SavedPlaces(places = listOf(moscow, paris), selectedId = paris.id)
        assertThat(saved.forWidget(Place.FOLLOW_APP_ID)).isEqualTo(paris)
    }

    @Test
    fun `new widgets follow the app`() {
        assertThat(WidgetConfig().placeId).isEqualTo(Place.FOLLOW_APP_ID)
    }

    @Test
    fun `unknown device location falls back to the app's city`() {
        // Location not granted (or no fix yet), city added via search: the reported device case.
        val saved = SavedPlaces(places = listOf(moscow), lastDeviceLocation = null, selectedId = moscow.id)
        assertThat(saved.forWidget(Place.CURRENT_ID)).isEqualTo(moscow)
    }

    @Test
    fun `known device location wins for location widgets`() {
        val saved = SavedPlaces(places = listOf(moscow), lastDeviceLocation = here, selectedId = moscow.id)
        assertThat(saved.forWidget(Place.CURRENT_ID)).isEqualTo(here)
        assertThat(saved.forWidget(Place.FOLLOW_APP_ID)).isEqualTo(moscow)
    }

    @Test
    fun `pinned city stays pinned and falls back once removed`() {
        val saved = SavedPlaces(places = listOf(moscow, paris), selectedId = moscow.id)
        assertThat(saved.forWidget(paris.id)).isEqualTo(paris)
        assertThat(saved.copy(places = listOf(moscow)).forWidget(paris.id)).isEqualTo(moscow)
    }

    @Test
    fun `stale selection resolves to the first page like the app does`() {
        val saved = SavedPlaces(places = listOf(moscow, paris), selectedId = "geo:gone")
        assertThat(saved.selected).isEqualTo(moscow)
        val deviceSelectedButNotFollowed = SavedPlaces(
            places = listOf(paris),
            followDeviceLocation = false,
            lastDeviceLocation = here,
            selectedId = Place.CURRENT_ID,
        )
        assertThat(deviceSelectedButNotFollowed.selected).isEqualTo(paris)
    }

    @Test
    fun `nothing followed means nothing to show`() {
        assertThat(SavedPlaces().forWidget(Place.FOLLOW_APP_ID)).isNull()
        assertThat(SavedPlaces().forWidget(Place.CURRENT_ID)).isNull()
    }
}
