package app.papersky.weather.core.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.papersky.weather.container
import app.papersky.weather.widget.WidgetDirectory
import kotlinx.coroutines.launch

/**
 * Time zone, clock and language changes all alter what a widget should say without any new data
 * (local times, day names, translations), so re-render immediately. These broadcasts are exempt
 * from the implicit-broadcast restrictions, hence the manifest registration.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        app.container.appScope.launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        SyncScheduler.ensurePeriodic(app, app.container.settings.current().updateIntervalMinutes)
                        SyncScheduler.syncWhenOnline(app)
                    }
                }
                WidgetDirectory.updateAll(app)
                SyncScheduler.scheduleSceneShift(app)
            } finally {
                pending.finish()
            }
        }
    }
}
