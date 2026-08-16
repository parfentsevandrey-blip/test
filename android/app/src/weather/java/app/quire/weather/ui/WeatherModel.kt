package app.quire.weather.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.quire.weather.Forecast
import app.quire.weather.Place
import app.quire.weather.PlaceSearch
import app.quire.weather.Pressure
import app.quire.weather.Degrees
import app.quire.weather.RainAlert
import app.quire.weather.WeatherRefresh
import app.quire.weather.WeatherSettings
import app.quire.weather.WindUnit
import app.quire.weather.WeatherStore
import app.quire.weather.WeatherWidgetProvider
import app.quire.weather.Whereabouts

/**
 * What the weather screen reads and what it can ask for.
 *
 * The stored forecast is shown immediately and the fetch happens behind it, because the answer to
 * "what is it doing outside" is nearly always the one from twenty minutes ago and waiting for the
 * network to confirm it is a worse experience than showing it with its age.
 */
class WeatherModel(app: Application) : AndroidViewModel(app) {

    var forecast by mutableStateOf(WeatherStore.load(app))
        private set

    var located by mutableStateOf(Whereabouts.granted(app))
        private set

    var loading by mutableStateOf(false)
        private set

    /** True when the place was named rather than measured, so the screen can say which. */
    var pinned by mutableStateOf(WeatherStore.pinned(app))
        private set

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<Place>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    /** A snapshot of the settings, so the screen recomposes when one of them is changed. */
    var settings by mutableStateOf(Settings.from(WeatherSettings.get(app)))
        private set

    /** Whether the platform would let an alert through, so the screen can say if it would not. */
    var canNotify by mutableStateOf(RainAlert.allowed(app))
        private set

    /** What the settings screen reads; the store behind it is written straight through. */
    data class Settings(
        val period: Int,
        val liveSky: Boolean,
        val alerts: Boolean,
        val threshold: Int,
        val degrees: Degrees,
        val wind: WindUnit,
        val pressure: Pressure,
    ) {
        companion object {
            fun from(store: WeatherSettings) = Settings(
                period = store.periodMinutes,
                liveSky = store.liveSky,
                alerts = store.alerts,
                threshold = store.threshold,
                degrees = store.degrees,
                wind = store.wind,
                pressure = store.pressure,
            )
        }
    }

    /** Shows what is stored, then asks for anything newer. */
    fun refresh(force: Boolean = false) {
        val app = getApplication<Application>()
        located = Whereabouts.granted(app)
        pinned = WeatherStore.pinned(app)
        forecast = WeatherStore.load(app)
        // A named place needs no permission; only a measured one does.
        if (!located && !pinned) return
        loading = true
        WeatherRefresh.request(app, force) { fetched ->
            // The callback lands on the fetching thread; Compose state is safe to write from any
            // thread, and the widgets are told at the same moment for the same reason.
            loading = false
            if (fetched != null) {
                forecast = fetched
                WeatherWidgetProvider.requestUpdate(app)
            }
        }
    }

    fun permissionGranted() {
        located = true
        refresh(force = true)
    }

    private fun store() = WeatherSettings.get(getApplication())

    private fun reread() {
        settings = Settings.from(store())
        canNotify = RainAlert.allowed(getApplication())
    }

    /**
     * Changing the interval reschedules the job. A periodic job keeps the interval it was armed
     * with, so writing the preference alone would change nothing at all.
     */
    fun setPeriod(minutes: Int) {
        store().periodMinutes = minutes
        WeatherRefresh.schedule(getApplication())
        reread()
    }

    fun setLiveSky(on: Boolean) {
        store().liveSky = on
        reread()
    }

    fun setAlerts(on: Boolean) {
        store().alerts = on
        // Turning them off should also forget that today was announced, so turning them back on
        // tomorrow does not stay silent because of a decision made under the old setting.
        if (!on) store().alertedOn = ""
        reread()
    }

    fun setThreshold(percent: Int) {
        store().threshold = percent
        reread()
    }

    fun setDegrees(unit: Degrees) {
        store().degrees = unit
        reread()
    }

    fun setWind(unit: WindUnit) {
        store().wind = unit
        reread()
    }

    fun setPressure(unit: Pressure) {
        store().pressure = unit
        reread()
    }

    /** Called when the notification permission dialog has been answered, either way. */
    fun notificationsAnswered() {
        canNotify = RainAlert.allowed(getApplication())
    }

    /**
     * Looks a place up by name.
     *
     * Each search supersedes the last: a slow answer for "Mos" must not land on top of the
     * results for "Moscow", so the query it was asked for is checked before it is shown.
     */
    fun search(text: String) {
        query = text
        if (text.trim().length < PlaceSearch.MIN_QUERY) {
            results = emptyList()
            searching = false
            return
        }
        searching = true
        SEARCH.execute {
            val found = runCatching { PlaceSearch.search(text) }.getOrDefault(emptyList())
            if (query == text) {
                results = found
                searching = false
            }
        }
    }

    /** Takes the named place as the answer, and stops asking the device where it is. */
    fun choose(place: Place) {
        val app = getApplication<Application>()
        WeatherStore.pin(app, place)
        pinned = true
        query = ""
        results = emptyList()
        forecast = null
        refresh(force = true)
    }

    /** Hands the choice back to the device. */
    fun useMyLocation() {
        val app = getApplication<Application>()
        WeatherStore.unpin(app)
        pinned = false
        forecast = null
        refresh(force = true)
    }

    private companion object {
        val SEARCH: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor { r ->
                Thread(r, "quire-places").apply { isDaemon = true }
            }
    }
}
