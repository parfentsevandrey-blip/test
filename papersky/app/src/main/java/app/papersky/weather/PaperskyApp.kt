package app.papersky.weather

import android.app.Application
import android.content.ComponentCallbacks
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import app.papersky.weather.core.sync.SyncScheduler
import app.papersky.weather.widget.WidgetDirectory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PaperskyApp : Application(), ContainerHost {
    override lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Paper, kraft, cork and linen are generated once, off the main thread, before first use.
        container.appScope.launch(kotlinx.coroutines.Dispatchers.Default) { app.papersky.weather.scene.MaterialTextures.warmUp() }

        container.appScope.launch {
            val settings = container.settings.current()
            SyncScheduler.ensurePeriodic(this@PaperskyApp, settings.updateIntervalMinutes)
        }

        // Units and interval changes must reach the home screen right away.
        container.appScope.launch {
            container.settings.settings
                .map { it.units to it.updateIntervalMinutes }
                .distinctUntilChanged()
                .drop(1)
                .collect { (_, interval) ->
                    SyncScheduler.ensurePeriodic(this@PaperskyApp, interval, replace = true)
                    WidgetDirectory.updateAll(this@PaperskyApp)
                }
        }

        // Turning the village on or off redraws the widgets' dioramas too.
        container.appScope.launch {
            container.settings.settings
                .map { it.village }
                .distinctUntilChanged()
                .drop(1)
                .collect { WidgetDirectory.updateAll(this@PaperskyApp) }
        }

        // Wallpaper colours (Material You palette), dark theme, font scale and locale all feed
        // into widget rendering; re-render while our process is alive to pick them up.
        registerComponentCallbacks(object : ComponentCallbacks {
            private var last = Configuration(resources.configuration)
            override fun onConfigurationChanged(newConfig: Configuration) {
                val diff = last.diff(newConfig)
                last = Configuration(newConfig)
                val relevant = ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_FONT_SCALE or
                    ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_DENSITY
                if (diff and relevant != 0) {
                    container.appScope.launch { WidgetDirectory.updateAll(this@PaperskyApp) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }
}
