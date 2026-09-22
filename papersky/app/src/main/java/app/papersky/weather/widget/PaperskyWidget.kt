package app.papersky.weather.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import app.papersky.weather.AppContainer
import app.papersky.weather.MainActivity
import app.papersky.weather.container
import app.papersky.weather.core.data.PlacesData
import app.papersky.weather.core.model.Forecast
import app.papersky.weather.core.model.Place
import app.papersky.weather.core.model.SampleForecast
import app.papersky.weather.core.model.UserSettings
import app.papersky.weather.widget.ui.WidgetContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Snapshot of everything one widget needs to draw itself. */
data class WidgetData(
    val place: Place?,
    val forecast: Forecast?,
    val settings: UserSettings,
    val refreshing: Boolean,
    val isSample: Boolean = false,
)

fun PlacesData.resolve(placeId: String): Place? = when (placeId) {
    Place.HERE -> device ?: saved.firstOrNull()
    else -> saved.firstOrNull { it.id == placeId } ?: device ?: saved.firstOrNull()
}

/**
 * The Papersky home-screen widget.
 *
 * [SizeMode.Exact] composes once per size the launcher reports (typically portrait + landscape),
 * so the layout planner can use every dp of any grid size from 1×1 upwards.
 */
class PaperskyWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(270.dp, 100.dp), DpSize(270.dp, 190.dp), DpSize(270.dp, 300.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        c.weather.ensureLoaded()
        val places = c.places.snapshot()
        val settings = c.settings.current()
        provideContent {
            val config = WidgetConfig.from(currentState<Preferences>())
            val initial = remember(config.placeId) {
                val place = places.resolve(config.placeId)
                WidgetData(place, place?.let { c.weather.peek(it.id) }, settings, refreshing = false)
            }
            val data by remember(config.placeId) { dataFlow(c, config.placeId) }.collectAsState(initial)
            WidgetContent(config, data, System.currentTimeMillis(), openAppIntent(context, data.place?.id))
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val c = context.container
        c.weather.ensureLoaded()
        val places = c.places.snapshot()
        val settings = c.settings.current()
        val now = System.currentTimeMillis()
        val place = places.resolve(Place.HERE)
        val real = place?.let { c.weather.peek(it.id) }
        val data = if (real != null) {
            WidgetData(place, real, settings, refreshing = false)
        } else {
            WidgetData(SampleForecast.place, SampleForecast.build(now), settings, refreshing = false, isSample = true)
        }
        provideContent { WidgetContent(WidgetConfig(), data, now, openAppIntent(context, null)) }
    }

    companion object {
        const val EXTRA_PLACE_ID = "papersky.place"

        fun openAppIntent(context: Context, placeId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_PLACE_ID, placeId)

        fun dataFlow(c: AppContainer, placeId: String): Flow<WidgetData> =
            combine(c.places.data, c.settings.settings, c.syncStatus.pending, c.weather.refreshing) { places, settings, pending, refreshing ->
                val place = places.resolve(placeId)
                Triple(place, settings, pending || (place != null && place.id in refreshing))
            }
                .distinctUntilChanged()
                .flatMapLatest { (place, settings, busy) ->
                    val forecasts = place?.let { c.weather.observe(it.id) } ?: kotlinx.coroutines.flow.flowOf(null)
                    forecasts.map { WidgetData(place, it, settings, busy) }
                }
    }
}
