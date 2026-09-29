package app.rosa.weather.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.data.sync.SyncReason
import app.rosa.weather.core.data.sync.SyncScheduler
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.WeatherShare
import app.rosa.weather.core.model.WeatherSnapshot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Lends the weather to Rosa Calendar ([WeatherShare]): the saved places, the units and the
 * forecasts of the places its widgets show, and a refresh when they have grown old. Only an app
 * signed with the same key as this one may call it.
 */
class WeatherShareProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Graph {
        fun weather(): WeatherRepository
        fun places(): PlacesRepository
        fun settings(): SettingsRepository
        fun scheduler(): SyncScheduler
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        requireSameSigner(context)
        val graph = EntryPointAccessors.fromApplication(context.applicationContext, Graph::class.java)
        return when (method) {
            WeatherShare.METHOD_SNAPSHOT -> {
                val snapshot = runBlocking { snapshot(graph, WeatherShare.placeIds(arg)) }
                Bundle().apply { putString(WeatherShare.KEY_JSON, WeatherShare.encode(snapshot)) }
            }
            WeatherShare.METHOD_REFRESH -> {
                graph.scheduler().refreshNow(force = arg == WeatherShare.ARG_FORCE, reason = SyncReason.SystemEvent)
                Bundle.EMPTY
            }
            else -> null
        }
    }

    /** The calling app must be signed like this one: the weather isn't lent to anyone else. */
    private fun requireSameSigner(context: Context) {
        val caller = Binder.getCallingUid()
        if (caller == Process.myUid()) return
        if (context.packageManager.checkSignatures(caller, Process.myUid()) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Rosa lends its weather only to Rosa's own apps")
        }
    }

    private suspend fun snapshot(graph: Graph, placeIds: List<String>): WeatherSnapshot {
        val saved = graph.places().snapshot()
        val settings = graph.settings().current()
        val cache = graph.weather().forecasts.first()
        // Each id resolved exactly as the weather app's own widgets resolve it.
        val places = placeIds.mapNotNull { saved.forWidget(it) }.distinctBy { it.id }
        val now = System.currentTimeMillis() / 1000
        return WeatherSnapshot(
            units = settings.resolvedUnits(),
            places = saved,
            forecasts = places.mapNotNull { place -> cache[place.id]?.let { place.id to it.lent(now) } }.toMap(),
            refreshing = graph.weather().refreshing.value,
            refreshMinutes = settings.refreshIntervalMinutes,
        )
    }

    /** The days and the hours around now: all a calendar shows, and the answer stays small. */
    private fun Forecast.lent(now: Long): Forecast = copy(hourly = hourly.filter { it.time in (now - 3 * 3600)..(now + 72 * 3600) })

    // Nothing is shared as rows: only through call().
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
