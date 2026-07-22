package com.lumina.calendarwidget

import android.app.Application
import com.lumina.calendarwidget.data.SettingsRepository

/** Application entry point. Holds a single shared [SettingsRepository]. */
class CalendarApplication : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
