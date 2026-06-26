package com.monthcalendar.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_settings")

/** App-wide widget appearance / behaviour options. */
data class CalendarSettings(
    val mondayFirst: Boolean = true,
    val showEvents: Boolean = true,
    val showWeekNumbers: Boolean = false,
    val accent: Accent = Accent.DYNAMIC,
)

enum class Accent(val key: String) {
    /** Material You — palette derived from the wallpaper (Android 12+). */
    DYNAMIC("dynamic"),
    INDIGO("indigo"),
    GREEN("green"),
    ROSE("rose"),
    AMBER("amber");

    companion object {
        fun fromKey(k: String?): Accent = entries.firstOrNull { it.key == k } ?: DYNAMIC
    }
}

class CalendarSettingsStore(private val context: Context) {
    private object Keys {
        val MONDAY_FIRST = booleanPreferencesKey("monday_first")
        val SHOW_EVENTS = booleanPreferencesKey("show_events")
        val WEEK_NUMBERS = booleanPreferencesKey("week_numbers")
        val ACCENT = stringPreferencesKey("accent")
    }

    val flow: Flow<CalendarSettings> = context.settingsStore.data.map { p ->
        CalendarSettings(
            mondayFirst = p[Keys.MONDAY_FIRST] ?: true,
            showEvents = p[Keys.SHOW_EVENTS] ?: true,
            showWeekNumbers = p[Keys.WEEK_NUMBERS] ?: false,
            accent = Accent.fromKey(p[Keys.ACCENT]),
        )
    }

    suspend fun get(): CalendarSettings = flow.first()

    suspend fun save(settings: CalendarSettings) {
        context.settingsStore.edit { p ->
            p[Keys.MONDAY_FIRST] = settings.mondayFirst
            p[Keys.SHOW_EVENTS] = settings.showEvents
            p[Keys.WEEK_NUMBERS] = settings.showWeekNumbers
            p[Keys.ACCENT] = settings.accent.key
        }
    }
}
