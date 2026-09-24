package app.rosa.weather.widget.studio

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.model.AppSettings
import app.rosa.weather.core.model.Forecast
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.widget.WidgetUpdater
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.provider.WidgetSizes
import app.rosa.weather.widget.render.WidgetContent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioState(
    val config: WidgetConfig,
    val places: List<Place>,
    /** The city open in the app — what "same as app" currently means. */
    val appPlace: Place?,
    val content: WidgetContent,
    val settings: AppSettings,
    val forecast: Forecast?,
)

@HiltViewModel
class WidgetStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
    private val configs: WidgetConfigRepository,
    places: PlacesRepository,
    weather: WeatherRepository,
    settings: SettingsRepository,
    private val updater: WidgetUpdater,
) : ViewModel() {
    val widgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID

    private val manager = AppWidgetManager.getInstance(context)
    private val kind = WidgetKind.forProvider(manager.getAppWidgetInfo(widgetId)?.provider?.className)
    private val config = MutableStateFlow(kind.defaultConfig)

    /** Current launcher size of the widget, so the studio opens at the size you actually have. */
    val initialSize: SizeF? = runCatching {
        WidgetSizes.from(manager.getAppWidgetOptions(widgetId), manager.getAppWidgetInfo(widgetId)).firstOrNull()
    }.getOrNull()

    init {
        viewModelScope.launch {
            configs.snapshot().byId[widgetId]?.let { stored -> config.value = stored }
        }
    }

    val state: StateFlow<StudioState?> = combine(config, places.saved, weather.forecasts, settings.settings) { cfg, saved, forecasts, s ->
        val now = System.currentTimeMillis() / 1000
        // Exactly the rule the widget itself uses, so the preview never promises something else.
        val place = saved.forWidget(cfg.placeId)
        val real = place?.let { forecasts[it.id] }
        val forecast = real ?: SampleForecast.create(SampleForecast.Scenario.RainyAfternoon, nowEpochSeconds = now)
        StudioState(
            config = cfg,
            places = saved.all,
            appPlace = saved.selected,
            content = WidgetContent(
                placeName = place?.name ?: context.getString(app.rosa.weather.widget.R.string.widget_preview_place),
                isCurrentLocation = place?.isCurrentLocation ?: true,
                forecast = forecast,
                nowEpochSeconds = now,
                units = s.resolvedUnits(),
            ),
            settings = s,
            forecast = real,
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
