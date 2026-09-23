package app.rosa.weather.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rosa.weather.core.data.repository.PlacesRepository
import app.rosa.weather.core.data.repository.SettingsRepository
import app.rosa.weather.core.data.repository.WeatherRepository
import app.rosa.weather.core.data.repository.WidgetConfigRepository
import app.rosa.weather.core.data.repository.resolvedUnits
import app.rosa.weather.core.model.Place
import app.rosa.weather.core.model.SampleForecast
import app.rosa.weather.core.model.WidgetConfig
import app.rosa.weather.widget.WidgetUpdater
import app.rosa.weather.widget.provider.WidgetKind
import app.rosa.weather.widget.render.WidgetContent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlacedWidget(val id: Int, val kind: WidgetKind, val config: WidgetConfig, val size: SizeF?, val content: WidgetContent)

data class WidgetsUiState(
    val placed: List<PlacedWidget> = emptyList(),
    val sampleContent: WidgetContent? = null,
)

@HiltViewModel
class WidgetsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    configs: WidgetConfigRepository,
    places: PlacesRepository,
    weather: WeatherRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val tick = MutableStateFlow(0)

    val state: StateFlow<WidgetsUiState> = combine(configs.all, places.saved, weather.forecasts, settings.settings, tick) { cfgs, saved, forecasts, s, _ ->
        val manager = AppWidgetManager.getInstance(context)
        val now = System.currentTimeMillis() / 1000
        val units = s.resolvedUnits()
        val fallbackPlace = saved.all.firstOrNull()
        fun contentFor(place: Place?) = WidgetContent(
            placeName = place?.name.orEmpty(),
            isCurrentLocation = place?.isCurrentLocation ?: true,
            forecast = place?.let { forecasts[it.id] } ?: SampleForecast.create(nowEpochSeconds = now),
            nowEpochSeconds = now,
            units = units,
        )
        val placed = WidgetUpdater.allWidgetIds(context).map { id ->
            val info = manager.getAppWidgetInfo(id)
            val kind = WidgetKind.forProvider(info?.provider?.className)
            val config = cfgs.byId[id] ?: kind.defaultConfig
            val options = manager.getAppWidgetOptions(id)
            val size = runCatching { app.rosa.weather.widget.provider.WidgetSizes.from(options, info).first() }.getOrNull()
            PlacedWidget(id, kind, config, size, contentFor(saved.find(config.placeId) ?: fallbackPlace))
        }
        WidgetsUiState(placed, contentFor(fallbackPlace))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetsUiState())

    /** Re-read placed widgets (e.g. after returning from the launcher's pin dialog). */
    fun refresh() {
        tick.value++
    }
}
