package app.papersky.weather.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.papersky.weather.core.data.AppJson
import app.papersky.weather.core.model.Place
import app.papersky.weather.scene.PaletteMode
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetBackground { Scene, Paper, Clear }

@Serializable
enum class WidgetDensity { Compact, Balanced, Airy }

@Serializable
enum class TapAction { OpenApp, Refresh }

/** Everything a person can tune on one widget instance. Stored in that widget's Glance state. */
@Serializable
data class WidgetConfig(
    val placeId: String = Place.HERE,
    val palette: PaletteMode = PaletteMode.Auto,
    val background: WidgetBackground = WidgetBackground.Scene,
    val opacity: Float = 1f,
    /** Corner radius in dp; -1 follows the launcher's system radius. */
    val corner: Int = -1,
    val textScale: Float = 1f,
    val density: WidgetDensity = WidgetDensity.Balanced,
    val showLocation: Boolean = true,
    val showCondition: Boolean = true,
    val showHiLo: Boolean = true,
    val showFeelsLike: Boolean = false,
    val showClock: Boolean = false,
    val showWhisper: Boolean = true,
    val showHourly: Boolean = true,
    val showDaily: Boolean = true,
    val showDetails: Boolean = true,
    val showRefresh: Boolean = false,
    val showUpdated: Boolean = false,
    val hourStep: Int = 1,
    val tap: TapAction = TapAction.OpenApp,
) {
    fun toPreferences(): Preferences = mutablePreferencesOf(KEY to AppJson.encodeToString(serializer(), this))

    companion object {
        val KEY = stringPreferencesKey("papersky.widget.config")

        fun from(prefs: Preferences?): WidgetConfig =
            prefs?.get(KEY)?.let { runCatching { AppJson.decodeFromString(serializer(), it) }.getOrNull() } ?: WidgetConfig()
    }
}

/** Starting points offered in the editor and in "add to home screen". */
enum class WidgetPreset(val config: WidgetConfig, val cols: Int, val rows: Int) {
    LivingWindow(WidgetConfig(), 4, 2),
    Pocket(WidgetConfig(showHourly = false, showDaily = false, showDetails = false, showWhisper = false), 1, 1),
    Tower(WidgetConfig(showDetails = false, showWhisper = false), 1, 3),
    Ribbon(WidgetConfig(showDaily = false, showDetails = false), 4, 1),
    PaperNote(WidgetConfig(background = WidgetBackground.Paper, palette = PaletteMode.Linen, showClock = true), 3, 3),
    NightLight(WidgetConfig(palette = PaletteMode.Ink, showDetails = false), 4, 3),
    Glass(WidgetConfig(background = WidgetBackground.Clear, showDetails = false, showWhisper = false, opacity = 0f), 4, 2),
    Riso(WidgetConfig(palette = PaletteMode.Riso, showWhisper = true), 2, 2),
}
