package app.rosa.calendar

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import app.rosa.calendar.widget.CalendarWidgetUpdater
import app.rosa.weather.core.data.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class CalendarApplication : Application() {
    @Inject lateinit var widgets: CalendarWidgetUpdater

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private var lastUiMode = 0

    override fun onCreate() {
        super.onCreate()
        lastUiMode = resources.configuration.uiMode
        // The widgets are pictures: when the system theme flips while we're alive, redraw them now
        // (a calendar in the dark theme is painted by moonlight).
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (newConfig.uiMode != lastUiMode) {
                    lastUiMode = newConfig.uiMode
                    scope.launch {
                        widgets.update()
                        widgets.settle()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit

            override fun onTrimMemory(level: Int) = Unit
        })
    }
}
