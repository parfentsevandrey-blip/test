package app.quire.weather.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.quire.weather.Forecast
import app.quire.weather.WeatherRefresh
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

    /** Shows what is stored, then asks for anything newer. */
    fun refresh(force: Boolean = false) {
        val app = getApplication<Application>()
        located = Whereabouts.granted(app)
        forecast = WeatherStore.load(app)
        if (!located) return
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
}
