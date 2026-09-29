package app.rosa.calendar.weather

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.rosa.weather.core.model.WeatherShare
import app.rosa.weather.core.model.WeatherSnapshot
import app.rosa.weather.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The weather, borrowed from Rosa Weather ([WeatherShare]): the calendar keeps no cities and
 * fetches nothing of its own. Everything here is null or a no-op when the weather app isn't
 * installed — the calendar then shows no weather — or is too old to lend it.
 */
@Singleton
class SharedWeather @Inject constructor(@ApplicationContext private val context: Context) {
    private val uri: Uri = Uri.parse("content://${WeatherShare.AUTHORITY}")

    @Volatile private var lastRefresh = 0L

    private val versions = MutableStateFlow(0)

    /** Counts the weather app's news: screens showing the weather ask again when it moves on. */
    val changes: StateFlow<Int> = versions.asStateFlow()

    /** The weather app said its weather, or the city it has open, changed. */
    fun changed() = versions.update { it + 1 }

    /** Whether Rosa Weather is here to lend its weather. */
    fun installed(): Boolean = context.packageManager.resolveContentProvider(WeatherShare.AUTHORITY, 0) != null

    /** The weather app's places and units, and the forecasts of [placeIds] (as widgets store them). */
    suspend fun snapshot(placeIds: Collection<String>): WeatherSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.call(uri, WeatherShare.METHOD_SNAPSHOT, WeatherShare.argument(placeIds), null)
                ?.getString(WeatherShare.KEY_JSON)
                ?.let(WeatherShare::decode)
        }.getOrNull()
    }

    /**
     * Asks the weather app to bring its weather up to date; it says so ([WeatherShare.ACTION_WEATHER_CHANGED])
     * once it has. Unless [force]d, asked at most every quarter of an hour: when there is no
     * network, a calendar redrawn on every answer mustn't keep asking.
     */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < MIN_REFRESH_GAP_MILLIS) return@withContext
        lastRefresh = now
        runCatching { context.contentResolver.call(uri, WeatherShare.METHOD_REFRESH, if (force) WeatherShare.ARG_FORCE else null, null) }
    }

    /** Opens Rosa Weather on [placeId]; null when it isn't installed. */
    fun open(placeId: String?): Intent? =
        context.packageManager.getLaunchIntentForPackage(WeatherShare.WEATHER_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.apply { if (placeId != null) putExtra(WidgetUpdater.EXTRA_PLACE_ID, placeId) }

    private companion object {
        const val MIN_REFRESH_GAP_MILLIS = 15 * 60 * 1000L
    }
}
