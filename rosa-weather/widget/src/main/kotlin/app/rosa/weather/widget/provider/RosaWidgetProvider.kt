package app.rosa.weather.widget.provider

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.widget.WidgetUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun updater(): WidgetUpdater
    fun scheduler(): SyncScheduler
    fun configs(): WidgetConfigRepository
    fun settings(): SettingsRepository
}

internal fun Context.widgetGraph(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Runs [block] off the main thread while keeping the broadcast alive (≈10 s budget). */
internal fun BroadcastReceiver.launchAsync(block: suspend CoroutineScope.() -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken render must never take the process down; the next tick retries.
            Log.w("RosaWidget", "Widget update failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * Shared provider logic. Rendering never happens on the main thread and never waits for the
 * network: widgets are drawn from cache immediately, then a background sync refreshes them.
 */
abstract class RosaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val graph = context.widgetGraph()
        launchAsync {
            graph.updater().update(appWidgetIds)
            graph.scheduler().ensurePeriodic(graph.settings().current().refreshIntervalMinutes)
            if (graph.updater().anyStale(appWidgetIds)) {
                graph.scheduler().refreshNow(force = false, reason = SyncReason.SystemEvent)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized, rotated or moved: re-layout for the new exact sizes straight from cache.
        val graph = context.widgetGraph()
        launchAsync { graph.updater().update(intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val graph = context.widgetGraph()
        launchAsync { graph.configs().remove(appWidgetIds.toList()) }
    }

    override fun onEnabled(context: Context) {
        val graph = context.widgetGraph()
        launchAsync { graph.scheduler().ensurePeriodic(graph.settings().current().refreshIntervalMinutes) }
    }

    override fun onDisabled(context: Context) {
        // Last instance of *this* kind removed; stop background refresh only if no widget remains.
        val graph = context.widgetGraph()
        if (WidgetUpdater.allWidgetIds(context).isEmpty()) graph.scheduler().cancelAll()
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val graph = context.widgetGraph()
        launchAsync {
            graph.configs().remap(oldWidgetIds, newWidgetIds)
            graph.updater().update(newWidgetIds)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val graph = context.widgetGraph()
            launchAsync {
                graph.updater().markRefreshing(id)
                graph.scheduler().refreshNow(force = true, reason = SyncReason.UserRequest)
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "app.rosa.weather.widget.action.REFRESH"
    }
}

class GlassWidgetProvider : RosaWidgetProvider()

class SkyWidgetProvider : RosaWidgetProvider()

class AlmanacWidgetProvider : RosaWidgetProvider()
