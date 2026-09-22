package app.papersky.weather.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.papersky.weather.container
import app.papersky.weather.core.sync.SyncScheduler
import kotlinx.coroutines.launch

/** Registry of placed widgets and the levers the rest of the app pulls to refresh them. */
object WidgetDirectory {

    suspend fun installed(context: Context): List<Pair<GlanceId, WidgetConfig>> {
        val manager = GlanceAppWidgetManager(context)
        return manager.getGlanceIds(PaperskyWidget::class.java).map { it to readConfig(context, it) }
    }

    suspend fun readConfig(context: Context, id: GlanceId): WidgetConfig =
        WidgetConfig.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, id))

    suspend fun writeConfig(context: Context, id: GlanceId, config: WidgetConfig) {
        updateAppWidgetState(context, id) { prefs -> prefs[WidgetConfig.KEY] = config.toPreferences()[WidgetConfig.KEY]!! }
        PaperskyWidget().update(context, id)
    }

    suspend fun placeIdsInUse(context: Context): Set<String> =
        runCatching { installed(context).map { it.second.placeId }.toSet() }.getOrDefault(emptySet())

    suspend fun updateAll(context: Context) {
        runCatching { PaperskyWidget().updateAll(context) }
    }

    /**
     * Android 15+ widget pickers show "generated previews" rendered from our real composition.
     * The platform rate-limits these calls, so publish at most every few hours.
     */
    suspend fun publishPreviewsIfDue(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences("papersky.widgets", Context.MODE_PRIVATE)
        val last = prefs.getLong("previews_at", 0)
        val now = System.currentTimeMillis()
        if (now - last < 6 * 3600_000L) return
        runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(PaperskyWidgetReceiver::class) }
            .onSuccess { prefs.edit { putLong("previews_at", now) } }
    }
}

class PaperskyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaperskyWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val c = context.container
        c.appScope.launch {
            SyncScheduler.ensurePeriodic(context, c.settings.current().updateIntervalMinutes)
            // First placement (or data wiped): fetch right away instead of waiting for the period.
            c.weather.ensureLoaded()
            val missing = WidgetDirectory.placeIdsInUse(context).any { id ->
                val place = c.places.snapshot().resolve(id)
                place == null || c.weather.peek(place.id) == null
            }
            if (missing) SyncScheduler.refreshNow(context, force = false)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.container.appScope.launch { WidgetDirectory.publishPreviewsIfDue(context) }
    }
}

/** Tap on the paper refresh glyph (or the whole widget when configured so). */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncScheduler.refreshNow(context, force = true)
        PaperskyWidget().update(context, glanceId)
    }
}

/**
 * Receives the launcher's confirmation after "Add to home screen" from inside the app and applies
 * the chosen preset to the freshly placed widget.
 */
class PinnedWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val preset = intent.getStringExtra(EXTRA_PRESET)?.let { runCatching { WidgetPreset.valueOf(it) }.getOrNull() } ?: WidgetPreset.LivingWindow
        val placeId = intent.getStringExtra(EXTRA_PLACE_ID)
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                val config = if (placeId != null) preset.config.copy(placeId = placeId) else preset.config
                WidgetDirectory.writeConfig(context, id, config)
            } catch (_: IllegalArgumentException) {
                // Widget vanished before we could configure it.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRESET = "papersky.preset"
        const val EXTRA_PLACE_ID = "papersky.place"
    }
}
