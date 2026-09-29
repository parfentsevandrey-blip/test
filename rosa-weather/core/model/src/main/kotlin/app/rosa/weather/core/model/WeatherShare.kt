package app.rosa.weather.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How Rosa Weather lends its weather to Rosa Calendar. The calendar keeps no cities and fetches
 * nothing: it asks the weather app, through a provider only an app signed like it may call, for
 * the forecasts of the places its widgets show, and the weather app tells it whenever they change.
 * Without the weather app the calendar simply shows no weather.
 */
object WeatherShare {
    const val WEATHER_PACKAGE = "app.rosa.weather"
    const val CALENDAR_PACKAGE = "app.rosa.calendar"

    /** The weather app's provider; see [METHOD_SNAPSHOT] and [METHOD_REFRESH]. */
    const val AUTHORITY = "app.rosa.weather.share"

    /**
     * The saved places, the units, and the forecasts of the places named in the call's argument —
     * comma-separated place ids as widgets store them ([Place.FOLLOW_APP_ID], [Place.CURRENT_ID] or
     * a city's id), each resolved the way the weather app's own widgets resolve it.
     */
    const val METHOD_SNAPSHOT = "snapshot"

    /** Brings the weather up to date: the argument "force" fetches even what is fresh. */
    const val METHOD_REFRESH = "refresh"
    const val ARG_FORCE = "force"

    /** The key of the JSON in the provider's answer. */
    const val KEY_JSON = "json"

    /** Sent to the calendar app whenever the weather or the city it follows changes. */
    const val ACTION_WEATHER_CHANGED = "app.rosa.calendar.action.WEATHER_CHANGED"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
        explicitNulls = false
        allowSpecialFloatingPointValues = true
    }

    fun encode(snapshot: WeatherSnapshot): String = json.encodeToString(WeatherSnapshot.serializer(), snapshot)

    /** Null when the text isn't a snapshot this version understands. */
    fun decode(text: String): WeatherSnapshot? = runCatching { json.decodeFromString(WeatherSnapshot.serializer(), text) }.getOrNull()

    /** The place ids a call for [placeIds] names, as its argument. */
    fun argument(placeIds: Collection<String>): String = placeIds.distinct().joinToString(",")

    fun placeIds(argument: String?): List<String> = argument.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** The weather app's weather at one moment, as the calendar gets it. */
@Serializable
data class WeatherSnapshot(
    val units: Units = Units(),
    val places: SavedPlaces = SavedPlaces(),
    /** The forecasts of the places asked for, by the id of the place they resolved to. */
    val forecasts: Map<String, Forecast> = emptyMap(),
    /** Places whose weather is being fetched right now. */
    val refreshing: Set<String> = emptySet(),
    /** How often the weather app refreshes, in minutes: older weather is stale. */
    val refreshMinutes: Int = AppSettings.DEFAULT_REFRESH_MINUTES,
)
