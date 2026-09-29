package app.rosa.weather.widget.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.widget.WidgetPreviews
import app.rosa.weather.widget.WidgetUpdater

/**
 * Clock, time-zone and locale changes invalidate everything a widget shows ("now", hour labels,
 * language) without any new data arriving, so we re-render immediately. These broadcasts are
 * exempt from the implicit-broadcast restrictions and may be declared in the manifest.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.widgetGraph()
        launchAsync {
            graph.updater().update()
            if (WidgetUpdater.hasWidgets(context)) {
                graph.scheduler().ensurePeriodic(graph.settings().current().refreshIntervalMinutes)
            }
            when (intent.action) {
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    WidgetPreviews.publish(context, force = true)
                    graph.scheduler().refreshNow(force = false, reason = SyncReason.SystemEvent)
                }
                Intent.ACTION_BOOT_COMPLETED ->
                    graph.scheduler().refreshNow(force = false, reason = SyncReason.SystemEvent)
            }
        }
    }
}
