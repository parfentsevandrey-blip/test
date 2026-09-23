package app.rosa.weather

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.widget.WidgetPreviews
import app.rosa.weather.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class RosaApplication : Application(), WorkConfiguration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var scheduler: SyncScheduler
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastUiMode = 0

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        lastUiMode = resources.configuration.uiMode
        scope.launch {
            if (WidgetUpdater.hasWidgets(this@RosaApplication)) {
                scheduler.ensurePeriodic(settings.current().refreshIntervalMinutes)
            }
            WidgetPreviews.publish(this@RosaApplication)
        }
        // Widgets are bitmaps: when the system theme flips while we're alive, redraw them now
        // instead of waiting for the next scheduled tick.
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (newConfig.uiMode != lastUiMode) {
                    lastUiMode = newConfig.uiMode
                    scope.launch { widgetUpdater.update() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit

            override fun onTrimMemory(level: Int) = Unit
        })
    }
}
