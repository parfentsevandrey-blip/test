package com.lumina.calendarwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** App-wide DataStore holding the widget configuration shared by every placed widget. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_settings")

/**
 * Single source of truth for [WidgetSettings], backed by Preferences DataStore.
 *
 * The in-app customization screen and the Glance widget both read from here, which is what keeps
 * them in lock-step: change a control, persist, ask Glance to re-render, done.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<WidgetSettings> =
        context.settingsDataStore.data.map { it.toSettings() }

    suspend fun get(): WidgetSettings = context.settingsDataStore.data.first().toSettings()

    suspend fun update(transform: (WidgetSettings) -> WidgetSettings) {
        val next = transform(get())
        context.settingsDataStore.edit { it.write(next) }
    }

    suspend fun replace(settings: WidgetSettings) {
        context.settingsDataStore.edit { it.write(settings) }
    }

    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val lightPresetId = stringPreferencesKey("light_preset_id")
        val darkPresetId = stringPreferencesKey("dark_preset_id")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val useCustomColors = booleanPreferencesKey("use_custom_colors")
        val accentColor = longPreferencesKey("accent_color")
        val backgroundColor = longPreferencesKey("background_color")
        val surfaceColor = longPreferencesKey("surface_color")
        val dayTextColor = longPreferencesKey("day_text_color")
        val mutedTextColor = longPreferencesKey("muted_text_color")
        val weekendTextColor = longPreferencesKey("weekend_text_color")
        val todayColor = longPreferencesKey("today_color")
        val backgroundOpacity = intPreferencesKey("background_opacity")
        val gradient = stringPreferencesKey("gradient")
        val cornerRadius = intPreferencesKey("corner_radius")

        val viewMode = stringPreferencesKey("view_mode")
        val density = stringPreferencesKey("density")
        val headerFormat = stringPreferencesKey("header_format")
        val headerAlignment = stringPreferencesKey("header_alignment")
        val showHairline = booleanPreferencesKey("show_hairline")
        val showGridLines = booleanPreferencesKey("show_grid_lines")
        val showOtherMonthDays = booleanPreferencesKey("show_other_month_days")
        val lockSixRows = booleanPreferencesKey("lock_six_rows")

        val weekdayLabelFormat = stringPreferencesKey("weekday_label_format")
        val weekdayLabelCase = stringPreferencesKey("weekday_label_case")
        val fontScalePercent = intPreferencesKey("font_scale_percent")
        val headerWeight = stringPreferencesKey("header_weight")
        val dayNumberWeight = stringPreferencesKey("day_number_weight")

        val firstDayOfWeek = stringPreferencesKey("first_day_of_week")
        val showWeekNumbers = booleanPreferencesKey("show_week_numbers")
        val weekend = stringPreferencesKey("weekend")
        val highlightWeekends = booleanPreferencesKey("highlight_weekends")
        val todayStyle = stringPreferencesKey("today_style")
        val selectedStyle = stringPreferencesKey("selected_style")
        val showEventIndicators = booleanPreferencesKey("show_event_indicators")
        val eventIndicatorStyle = stringPreferencesKey("event_indicator_style")

        val tapDayAction = stringPreferencesKey("tap_day_action")
        val tapHeaderAction = stringPreferencesKey("tap_header_action")
        val longPressAction = stringPreferencesKey("long_press_action")

        val timeFormat = stringPreferencesKey("time_format")
        val localeTag = stringPreferencesKey("locale_tag")
        val selectedEpochDay = longPreferencesKey("selected_epoch_day")
    }

    private fun Preferences.toSettings(): WidgetSettings {
        val d = WidgetSettings()
        return WidgetSettings(
            themeMode = enum(this[K.themeMode], d.themeMode),
            lightPresetId = this[K.lightPresetId] ?: d.lightPresetId,
            darkPresetId = this[K.darkPresetId] ?: d.darkPresetId,
            dynamicColor = this[K.dynamicColor] ?: d.dynamicColor,
            useCustomColors = this[K.useCustomColors] ?: d.useCustomColors,
            accentColor = this[K.accentColor] ?: d.accentColor,
            backgroundColor = this[K.backgroundColor] ?: d.backgroundColor,
            surfaceColor = this[K.surfaceColor] ?: d.surfaceColor,
            dayTextColor = this[K.dayTextColor] ?: d.dayTextColor,
            mutedTextColor = this[K.mutedTextColor] ?: d.mutedTextColor,
            weekendTextColor = this[K.weekendTextColor] ?: d.weekendTextColor,
            todayColor = this[K.todayColor] ?: d.todayColor,
            backgroundOpacity = this[K.backgroundOpacity] ?: d.backgroundOpacity,
            gradient = enum(this[K.gradient], d.gradient),
            cornerRadiusDp = this[K.cornerRadius] ?: d.cornerRadiusDp,
            viewMode = enum(this[K.viewMode], d.viewMode),
            density = enum(this[K.density], d.density),
            headerFormat = enum(this[K.headerFormat], d.headerFormat),
            headerAlignment = enum(this[K.headerAlignment], d.headerAlignment),
            showHairline = this[K.showHairline] ?: d.showHairline,
            showGridLines = this[K.showGridLines] ?: d.showGridLines,
            showOtherMonthDays = this[K.showOtherMonthDays] ?: d.showOtherMonthDays,
            lockSixRows = this[K.lockSixRows] ?: d.lockSixRows,
            weekdayLabelFormat = enum(this[K.weekdayLabelFormat], d.weekdayLabelFormat),
            weekdayLabelCase = enum(this[K.weekdayLabelCase], d.weekdayLabelCase),
            fontScalePercent = this[K.fontScalePercent] ?: d.fontScalePercent,
            headerWeight = enum(this[K.headerWeight], d.headerWeight),
            dayNumberWeight = enum(this[K.dayNumberWeight], d.dayNumberWeight),
            firstDayOfWeek = enum(this[K.firstDayOfWeek], d.firstDayOfWeek),
            showWeekNumbers = this[K.showWeekNumbers] ?: d.showWeekNumbers,
            weekend = enum(this[K.weekend], d.weekend),
            highlightWeekends = this[K.highlightWeekends] ?: d.highlightWeekends,
            todayStyle = enum(this[K.todayStyle], d.todayStyle),
            selectedStyle = enum(this[K.selectedStyle], d.selectedStyle),
            showEventIndicators = this[K.showEventIndicators] ?: d.showEventIndicators,
            eventIndicatorStyle = enum(this[K.eventIndicatorStyle], d.eventIndicatorStyle),
            tapDayAction = enum(this[K.tapDayAction], d.tapDayAction),
            tapHeaderAction = enum(this[K.tapHeaderAction], d.tapHeaderAction),
            longPressAction = enum(this[K.longPressAction], d.longPressAction),
            timeFormat = enum(this[K.timeFormat], d.timeFormat),
            localeTag = this[K.localeTag] ?: d.localeTag,
            selectedEpochDay = this[K.selectedEpochDay] ?: d.selectedEpochDay,
        )
    }

    private fun MutablePreferences.write(s: WidgetSettings) {
        this[K.themeMode] = s.themeMode.name
        this[K.lightPresetId] = s.lightPresetId
        this[K.darkPresetId] = s.darkPresetId
        this[K.dynamicColor] = s.dynamicColor
        this[K.useCustomColors] = s.useCustomColors
        this[K.accentColor] = s.accentColor
        this[K.backgroundColor] = s.backgroundColor
        this[K.surfaceColor] = s.surfaceColor
        this[K.dayTextColor] = s.dayTextColor
        this[K.mutedTextColor] = s.mutedTextColor
        this[K.weekendTextColor] = s.weekendTextColor
        this[K.todayColor] = s.todayColor
        this[K.backgroundOpacity] = s.backgroundOpacity
        this[K.gradient] = s.gradient.name
        this[K.cornerRadius] = s.cornerRadiusDp
        this[K.viewMode] = s.viewMode.name
        this[K.density] = s.density.name
        this[K.headerFormat] = s.headerFormat.name
        this[K.headerAlignment] = s.headerAlignment.name
        this[K.showHairline] = s.showHairline
        this[K.showGridLines] = s.showGridLines
        this[K.showOtherMonthDays] = s.showOtherMonthDays
        this[K.lockSixRows] = s.lockSixRows
        this[K.weekdayLabelFormat] = s.weekdayLabelFormat.name
        this[K.weekdayLabelCase] = s.weekdayLabelCase.name
        this[K.fontScalePercent] = s.fontScalePercent
        this[K.headerWeight] = s.headerWeight.name
        this[K.dayNumberWeight] = s.dayNumberWeight.name
        this[K.firstDayOfWeek] = s.firstDayOfWeek.name
        this[K.showWeekNumbers] = s.showWeekNumbers
        this[K.weekend] = s.weekend.name
        this[K.highlightWeekends] = s.highlightWeekends
        this[K.todayStyle] = s.todayStyle.name
        this[K.selectedStyle] = s.selectedStyle.name
        this[K.showEventIndicators] = s.showEventIndicators
        this[K.eventIndicatorStyle] = s.eventIndicatorStyle.name
        this[K.tapDayAction] = s.tapDayAction.name
        this[K.tapHeaderAction] = s.tapHeaderAction.name
        this[K.longPressAction] = s.longPressAction.name
        this[K.timeFormat] = s.timeFormat.name
        this[K.localeTag] = s.localeTag
        this[K.selectedEpochDay] = s.selectedEpochDay
    }
}

private inline fun <reified T : Enum<T>> enum(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
