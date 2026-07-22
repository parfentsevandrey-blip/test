package com.lumina.calendarwidget.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumina.calendarwidget.CalendarApplication
import com.lumina.calendarwidget.data.WidgetSettings
import com.lumina.calendarwidget.widget.CalendarGlanceWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the customization screen.
 *
 * Edits update an in-memory [StateFlow] instantly (so the live preview never lags), while the
 * write to DataStore and the widget re-render are debounced — the same settings object powers the
 * on-screen preview and every placed widget, so they can never drift apart.
 */
class CustomizeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as CalendarApplication).settingsRepository

    private val _settings = MutableStateFlow(WidgetSettings())
    val settings = _settings.asStateFlow()

    private var saveJob: Job? = null

    init {
        viewModelScope.launch { _settings.value = repo.get() }
    }

    /** Apply an edit: reflect immediately, then debounce the persist + widget refresh. */
    fun set(newSettings: WidgetSettings) {
        _settings.value = newSettings
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(160)
            repo.replace(newSettings)
            CalendarGlanceWidget().updateAll(getApplication())
        }
    }

    fun edit(transform: (WidgetSettings) -> WidgetSettings) = set(transform(_settings.value))

    fun resetToDefaults() = set(WidgetSettings())

    /** Force a widget refresh now (e.g. right after the calendar permission is granted). */
    fun refreshWidgets() {
        viewModelScope.launch { CalendarGlanceWidget().updateAll(getApplication()) }
    }
}
