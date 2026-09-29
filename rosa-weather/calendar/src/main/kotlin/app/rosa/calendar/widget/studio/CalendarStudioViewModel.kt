package app.rosa.calendar.widget.studio

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.calendar.data.CalendarSettings
import app.rosa.calendar.data.CalendarSettingsRepository
import app.rosa.calendar.weather.SharedWeather
import app.rosa.calendar.widget.CalendarWidgetUpdater
import app.rosa.calendar.widget.calendarContent
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.WeatherSnapshot
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.WidgetContent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarStudioState(
    val config: WidgetConfig,
    /** The places Rosa Weather follows; empty without it. */
    val places: List<Place>,
    /** The city open in Rosa Weather — what "as in Rosa Weather" means right now. */
    val appPlace: Place?,
    val content: WidgetContent,
    val settings: CalendarSettings,
    /** Whether Rosa Weather lends its weather at all. */
    val weatherLent: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val configs: WidgetConfigRepository,
    weather: SharedWeather,
    settings: CalendarSettingsRepository,
    private val updater: CalendarWidgetUpdater,
) : ViewModel() {
    val widgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID

    private val manager = AppWidgetManager.getInstance(context)
    private val config = MutableStateFlow(CalendarWidgetUpdater.DEFAULT_CONFIG)

    /** The widget's size on the home screen now, so the studio opens at the size you have. */
    val initialSize: SizeF? = runCatching {
        WidgetSizes.from(manager.getAppWidgetOptions(widgetId), manager.getAppWidgetInfo(widgetId)).firstOrNull()
    }.getOrNull()

    init {
        viewModelScope.launch {
            val stored = configs.snapshot().byId[widgetId]
            // A new calendar starts its weeks where the app does.
            config.value = stored ?: CalendarWidgetUpdater.DEFAULT_CONFIG.let { default ->
                default.copy(calendar = default.calendar.copy(weekStart = settings.current().weekStart))
            }
        }
    }

    /** The weather lent for the place the widget shows, asked for again whenever it changes. */
    private val lent = combine(config.map { it.placeId }.distinctUntilChanged(), weather.changes) { placeId, _ -> placeId }
        .mapLatest<String, WeatherSnapshot?> { placeId -> weather.snapshot(listOf(placeId, Place.FOLLOW_APP_ID)) }
        .onStart { emit(null) }

    val state: StateFlow<CalendarStudioState?> = combine(config, lent, settings.settings) { cfg, snapshot, s ->
        val now = System.currentTimeMillis() / 1000
        CalendarStudioState(
            config = cfg,
            places = snapshot?.places?.all.orEmpty(),
            appPlace = snapshot?.places?.selected,
            content = calendarContent(cfg, snapshot, now),
            settings = s,
            weatherLent = snapshot != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (WidgetConfig) -> WidgetConfig) = config.update(transform)

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            configs.put(widgetId, config.value)
            updater.update(intArrayOf(widgetId))
            onSaved()
        }
    }
}
