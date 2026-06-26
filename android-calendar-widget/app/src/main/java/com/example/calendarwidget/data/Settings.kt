package com.example.calendarwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User-configurable widget settings (раздел 6.5). Persisted in a single
 * app-wide DataStore so every widget instance shares them; the widget reads
 * the same store inside `provideGlance`, and the settings screen calls
 * `MonthWidget().updateAll()` after each change to push updates live.
 */
data class WidgetSettings(
    val accent: String = DEFAULT_ACCENT,
    val bgOpacity: Float = 0.55f,
    val radius: Int = 28,
    val firstDayMonday: Boolean = true,
    val showAgenda: Boolean = true,
    val fontScale: Float = 1.0f,
    /** Theme used only for the live preview in the settings screen. */
    val previewDark: Boolean = true,
) {
    companion object {
        const val DEFAULT_ACCENT = "#7C9CFF"

        /** Accent palette offered to the user (раздел 7). */
        val ACCENTS = listOf(
            "#7C9CFF", "#54E6C0", "#FF8A6B", "#C9A6FF", "#FFD27D", "#8AE0A0",
        )
        val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f)
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "widget_settings",
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACCENT = stringPreferencesKey("accent")
        val BG_OPACITY = floatPreferencesKey("bg_opacity")
        val RADIUS = intPreferencesKey("radius")
        val FIRST_DAY_MONDAY = booleanPreferencesKey("first_day_monday")
        val SHOW_AGENDA = booleanPreferencesKey("show_agenda")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val PREVIEW_DARK = booleanPreferencesKey("preview_dark")
    }

    val settings: Flow<WidgetSettings> = context.settingsDataStore.data.map { it.toSettings() }

    /** One-shot suspend read for use inside `provideGlance`. */
    suspend fun read(): WidgetSettings = context.settingsDataStore.data.first().toSettings()

    suspend fun update(transform: (WidgetSettings) -> WidgetSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.ACCENT] = updated.accent
            prefs[Keys.BG_OPACITY] = updated.bgOpacity
            prefs[Keys.RADIUS] = updated.radius
            prefs[Keys.FIRST_DAY_MONDAY] = updated.firstDayMonday
            prefs[Keys.SHOW_AGENDA] = updated.showAgenda
            prefs[Keys.FONT_SCALE] = updated.fontScale
            prefs[Keys.PREVIEW_DARK] = updated.previewDark
        }
    }

    private fun Preferences.toSettings(): WidgetSettings {
        val defaults = WidgetSettings()
        return WidgetSettings(
            accent = this[Keys.ACCENT] ?: defaults.accent,
            bgOpacity = this[Keys.BG_OPACITY] ?: defaults.bgOpacity,
            radius = this[Keys.RADIUS] ?: defaults.radius,
            firstDayMonday = this[Keys.FIRST_DAY_MONDAY] ?: defaults.firstDayMonday,
            showAgenda = this[Keys.SHOW_AGENDA] ?: defaults.showAgenda,
            fontScale = this[Keys.FONT_SCALE] ?: defaults.fontScale,
            previewDark = this[Keys.PREVIEW_DARK] ?: defaults.previewDark,
        )
    }
}
